package edu.cmu.ml.rtw.users.jayantk.grounding;

import ilog.concert.IloException;
import ilog.concert.IloLinearNumExpr;
import ilog.concert.IloNumVar;
import ilog.cplex.IloCplex;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.io.NullOutputStream;
import com.jayantkrish.jklol.inference.MarginalCalculator.ZeroProbabilityError;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.tensor.DenseTensor;
import com.jayantkrish.jklol.tensor.DenseTensorBuilder;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.tensor.TensorBuilder;

import edu.cmu.ml.rtw.users.jayantk.semparse.RelationType;

public abstract class AbstractQueryTree implements QueryTree {
  
  // Lock for creating new instances of the cplex class.
  // (To avoid license server interference.)
  private static final AtomicBoolean cplexCreationLock = new AtomicBoolean(false);
    private static IloCplex[] theCplex = null;
    private static boolean[] cplexInUse = null;
    private static final int NUM_CPLEXES = 20;
  
  // Lagrange multipliers + weights for the output variable.
  private ParallelFactors outputLocalWeights;
  private final List<QueryTree> subtrees;
  
  // If relation is non-null, this node corresponds to a predicate in the
  // database.
  private final RelationType relation;
  
  // if non-null, contains the variable array for the current CPLEX ilp instance.
  private IloNumVar[] vars; 

  private final boolean isHardConstraint;

  public AbstractQueryTree(ParallelFactors outputLocalWeights, 
			   List<QueryTree> subtrees, RelationType relation,
			   boolean isHardConstraint) {
    this.outputLocalWeights = outputLocalWeights;
    this.subtrees = ImmutableList.copyOf(subtrees);
    this.relation = relation;
    this.isHardConstraint = isHardConstraint;
  }
  
  public boolean hasPredicate() {
    return relation != null;
  }
  
  public RelationType getPredicate() {
    return relation;
  }
  
  public void getAllPredicatesInTree(Set<RelationType> relations) {
    if (relation != null) {
      relations.add(relation);
    }
    
    for (QueryTree subtree : subtrees) {
      subtree.getAllPredicatesInTree(relations);
    }
  }
  
  public boolean isHardConstraint() {
    return isHardConstraint;
  }
  
  @Override
  public boolean isLeaf() {
    return subtrees.size() == 0;
  }

  @Override
  public List<QueryTree> getSubtrees() {
    return subtrees;
  }

  @Override
  public ParallelFactors getOutputLocalWeights() {
    return outputLocalWeights;
  }
  
  @Override
  public void updateOutputLocalWeights(Tensor gradient) {
    outputLocalWeights = outputLocalWeights.elementwiseAddition(gradient);
  }
  
  @Override
  public MultiTree<Tensor> locallyDecodeVariables() {    
    List<MultiTree<Tensor>> subtreeAssignments = Lists.newArrayList();
    for (QueryTree subtree : subtrees) {
      subtreeAssignments.add(subtree.locallyDecodeVariables());
    }

    Tensor bestAssignments = outputLocalWeights.getBestAssignments();
    return new MultiTree<Tensor>(bestAssignments, subtreeAssignments);
  }
  
  public String getAssignmentString(MultiTree<Tensor> assignment) {
    StringBuilder sb = new StringBuilder();
    if (subtrees.size() == 0) {
      sb.append(relation);
      sb.append(": ");
      sb.append(outputLocalWeights.getTensorAssignmentString(assignment.getValue()));
      sb.append(outputLocalWeights.getTensor());
    } else {
      for (int i = 0; i < subtrees.size(); i++) {
        QueryTree subtree = subtrees.get(i);
        sb.append(subtree.getAssignmentString(assignment.getChildren().get(i)));
        if (i != subtrees.size() - 1) {
          sb.append("\n");
        }
      }
    }

    return sb.toString();
  }
      
  @Override
  public MultiTree<List<Tensor>> locallyDecodeFactors() {
    List<MultiTree<List<Tensor>>> subtreeAssignments = Lists.newArrayList();
    for (QueryTree subtree : subtrees) {
      subtreeAssignments.add(subtree.locallyDecodeFactors());
    }
    
    List<Tensor> bestFactorAssignment = locallyDecodeFactor();
    return new MultiTree<List<Tensor>>(bestFactorAssignment, subtreeAssignments); 
  }
  
  @Override
  public double getWeight(MultiTree<Tensor> assignment) {
    double weight = 0.0;
    List<QueryTree> subtrees = getSubtrees();
    List<MultiTree<Tensor>> subtreeAssignments = assignment.getChildren();
    for (int i = 0; i < subtrees.size(); i++) {
      weight += subtrees.get(i).getWeight(subtreeAssignments.get(i));
    }

    if (this instanceof PredicateQueryTree && isHardConstraint) {
      double assignmentWeight = getOutputLocalWeights().getAssignmentWeight(assignment.getValue());
      ParallelFactors outputWeights = getOutputLocalWeights();
      double bestAssignmentWeight = outputWeights.getAssignmentWeight(outputWeights.getBestAssignments());

      // 0.0001 is a tolerance parameter.
      if (Math.abs(assignmentWeight - bestAssignmentWeight) >= 0.0001) {
        weight = Double.NEGATIVE_INFINITY;
      }
    } else {
      weight += getOutputLocalWeights().getAssignmentWeight(assignment.getValue());
    }

    // TODO: get weights from the constraints (/ factors) 
    return weight;
  }
  
  @Override
  public QueryTree reparameterizeDualDecomposition(int maxIterations) {
    QueryTree copy = copy();
    copy.dualDecomposition(maxIterations);
    return copy;
  }
  
  protected abstract List<Tensor> locallyDecodeFactor();
  
  @Override
  public int subgradientUpdate(MultiTree<Tensor> localAssignments, 
      MultiTree<List<Tensor>> factorAssignments, double stepSize) {
    int disagreementCount = 0;
    if (!isLeaf()) {
      disagreementCount += localSubgradientUpdate(localAssignments.getValue(), 
          localAssignments.getChildValues(), factorAssignments.getValue(), stepSize);
      
      List<MultiTree<Tensor>> localChildAssignments = localAssignments.getChildren();
      List<MultiTree<List<Tensor>>> factorChildAssignments = factorAssignments.getChildren();
      for (int i = 0; i < subtrees.size(); i++) {
        disagreementCount += subtrees.get(i).subgradientUpdate(localChildAssignments.get(i), 
            factorChildAssignments.get(i), stepSize);
      }
    }
    return disagreementCount;
  }

  protected abstract int localSubgradientUpdate(Tensor rootAssignment,
      List<Tensor> childAssignments, List<Tensor> factorAssignment, double stepSize);

  @Override
  public void dualDecomposition(int maxIterations) {
    // Try reparameterizing subtrees first, for improved performance.
    for (QueryTree subtree : subtrees) {
      subtree.dualDecomposition(maxIterations);
    }

    int numDisagreements = 1;
    int i = 0;
    for (; i < maxIterations && numDisagreements > 0; i++) {
      MultiTree<Tensor> localAssignment = locallyDecodeVariables();
      MultiTree<List<Tensor>> factorAssignment = locallyDecodeFactors();

      numDisagreements = subgradientUpdate(localAssignment, factorAssignment, 1.0 / Math.sqrt(i + 1));
      //System.out.println(i + ": " + numDisagreements + " disagreements");
    }
    System.out.println(i + " iterations, " + numDisagreements + " disagreements");
  }

  @Override  
      public MultiTree<Tensor> ilpInference(boolean useLpRelaxation, boolean applyWeakSupervisionConstraints) {

    int cplexIndex = -1;
    IloCplex cplex = null;
    try {
      synchronized (cplexCreationLock) {
        // Initialize the available CPLEX instances.
        if (theCplex == null) {
          theCplex = new IloCplex[NUM_CPLEXES];
          cplexInUse = new boolean[NUM_CPLEXES];
          for (int i = 0 ; i < NUM_CPLEXES; i++) {
            System.out.println("initializing cplex " + i);
            theCplex[i] = new IloCplex();
            cplexInUse[i] = false;
          }
        }

        // Grab an instance for this inference.
        for (int i = 0; i < NUM_CPLEXES; i++) {
          if (!cplexInUse[i]) {
            System.out.println("cplex " + i);
            System.out.println(theCplex[i]);
            cplex = theCplex[i];
            cplexInUse[i] = true;
            cplexIndex = i;
            break;
          }
        }
      }
      Preconditions.checkState(cplexIndex != -1, "Not enough CPLEX objects were allocated for all threads.");
    } catch (IloException e) {
	// Fail if CPLEX cannot be instantiated -- no exceptions are expected.
	throw new RuntimeException(e);
    }

    boolean success = false;
    MultiTree<Tensor> assignment = null;
    try {
	// Redirect (and ignore) the cplex output 
	cplex.setOut(new NullOutputStream());
	IloLinearNumExpr objective = cplex.linearNumExpr(); 

	this.augmentIlp(cplex, objective, useLpRelaxation, applyWeakSupervisionConstraints);

	cplex.addMaximize(objective);
	// Not setting this parameter interacts poorly with equality constraints?!
	// cplex.setParam(IloCplex.IntParam.AggInd, 0);
	// cplex.setParam(IloCplex.DoubleParam.TiLim, 5);
	boolean status = cplex.solve();

	assignment = decodeIlpSolution(cplex);
	// System.out.println(assignment);
	success = true;
    } catch (IloException e) {
	// Failure here means that inference could not find
	// a solution. This is captured by success = false
	System.out.println("Ilo exception: " + e);
    }

    try {
	cplex.clearModel();
	synchronized (cplexCreationLock) {
	    cplexInUse[cplexIndex] = false;
	}
    } catch (IloException e) {
	// Failure clearing the state of CPLEX.
	throw new RuntimeException(e);
    }

    if (success) {
	return assignment;
    } else {
	throw new ZeroProbabilityError();
    }
  }

  @Override
  public IloNumVar[] augmentIlp(IloCplex cplex, IloLinearNumExpr objective, boolean useLpRelaxation, boolean applyWeakSupervisionConstraints) throws IloException {
      this.vars = augmentIlpHelper(cplex, objective, useLpRelaxation, applyWeakSupervisionConstraints);
    return vars;
  }

  protected IloNumVar[] addLocalWeightsToIlp(IloCplex cplex, IloLinearNumExpr objective, boolean useLpRelaxation) throws IloException {
    // Augment the objective with the weights for each value.
    Tensor weights = getOutputLocalWeights().getTensor();
    VariableNumMap valueVar = getOutputLocalWeights().getValueVariables();
    Preconditions.checkState(valueVar.size() == 1);

    // Negate the weights of the false (0) assignment, add them to the weight for the true assignment 
    Tensor tfTensor = new DenseTensor(valueVar.getVariableNumsArray(), 
        valueVar.getVariableSizes(), new double[] {-1.0, 1.0});
    Tensor result = weights.elementwiseProduct(tfTensor).sumOutDimensions(valueVar.getVariableNums());

    // System.out.println(result.size() + " ");

    // Create an ILP variable for each variable in this.
    int numVars = result.size();
    IloNumVar[] vars = null;
    if (useLpRelaxation) {
      vars = cplex.numVarArray(numVars, 0.0, 1.0);
    } else {
      vars = cplex.boolVarArray(numVars);
    }

    StringBuilder sb = new StringBuilder(" constraints: ");

    double[] values = Arrays.copyOf(result.getValues(), result.getValues().length);
    for (int i = 0; i < values.length; i++) {
      // CPLEX doesn't like infinite values in the objective. Convert these to equality constraints.
      if (Double.isInfinite(values[i])) {
        if (values[i] > 0.0) {
          sb.append(1);
          cplex.addEq(vars[i], 1);
        } else {
          sb.append(0);
          cplex.addEq(vars[i], 0);
        }
        values[i] = 0.0;
      }
    }
    // System.out.println(sb.toString());

    objective.addTerms(values, vars);
    return vars;
  }

  protected abstract IloNumVar[] augmentIlpHelper(IloCplex cplex, IloLinearNumExpr objective, 
						  boolean useLpRelaxation, boolean applyWeakSupervisionConstraints) throws IloException;

  @Override
  public MultiTree<Tensor> decodeIlpSolution(IloCplex cplex) throws IloException {
    Tensor myValue = decodeIlpSolutionHelper(vars, cplex);

    List<MultiTree<Tensor>> subtreeValues = Lists.newArrayList();
    for (QueryTree subtree : subtrees) {
      subtreeValues.add(subtree.decodeIlpSolution(cplex));
    }

    return new MultiTree<Tensor>(myValue, subtreeValues);
  }

  protected Tensor decodeIlpSolutionHelper(IloNumVar[] vars, IloCplex cplex) throws IloException {
    Tensor weights = outputLocalWeights.getTensor();
    TensorBuilder valueBuilder = new DenseTensorBuilder(weights.getDimensionNumbers(), weights.getDimensionSizes());

    double[] values = cplex.getValues(vars);
    // System.out.println(Arrays.toString(values));

    Tensor variableIndexes = weights.sumOutDimensions(outputLocalWeights.getValueVariables().getVariableNums());

    // StringBuilder sb = new StringBuilder();
    // sb.append(variableIndexes.size() + " ");

    for (int i = 0; i < variableIndexes.size(); i++) {
      int[] key = variableIndexes.keyNumToDimKey(variableIndexes.indexToKeyNum(i));

      // If solution is to the LP relaxation, round to nearest int.
      // If solution is to an ILP, rounding is irrelevant.
      int value = (int) Math.round(values[i]);
      long keyNum = valueBuilder.dimKeyPrefixToKeyNum(key) + value;
      valueBuilder.putByKeyNum(keyNum, 1.0);

      // sb.append(value);
    }

    return valueBuilder.build();
  }
}
