package edu.cmu.ml.rtw.users.jayantk.grounding;

import ilog.concert.IloException;
import ilog.concert.IloLinearNumExpr;
import ilog.concert.IloNumVar;
import ilog.cplex.IloCplex;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.VariableNumMap.VariableRelabeling;
import com.jayantkrish.jklol.tensor.Backpointers;
import com.jayantkrish.jklol.tensor.DenseTensor;
import com.jayantkrish.jklol.tensor.DenseTensorBuilder;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.util.Assignment;

public class ConjunctionQueryTree extends AbstractQueryTree {

  // A set of constant weights which determine likely local assignments
  // to this factor.
  private final VariableNumMap truthTableVariables;
  private ParallelFactors constraintFactor;
  
  // The individual variables for the factors combined in constraintFactor.
  private List<VariableNumMap> factorVars;
  private final List<VariableRelabeling> factorRelabelings;

  // Lagrange multipliers for the output variable in the AND constraintFactor.
  private VariableNumMap outputVars;
  private final VariableRelabeling outputRelabeling;
  
  public ConjunctionQueryTree(ParallelFactors truthTable, ParallelFactors outputOutcomes,
      ParallelFactors outputLocalWeights, List<QueryTree> subtrees, List<VariableRelabeling> subtreeRelabelings) {
      super(outputLocalWeights, subtrees, null, true);
    
    Tensor tensor = DenseTensorBuilder.copyOf(outputOutcomes.getTensor().outerProduct(truthTable.getTensor().elementwiseLog())).buildNoCopy();
    truthTableVariables = truthTable.getAllVariables();
    constraintFactor = new ParallelFactors(tensor, outputOutcomes.getAllVariables(), truthTable.getAllVariables());
    Preconditions.checkArgument(truthTable.getAllVariables().size() == (subtrees.size() + 1));

    factorRelabelings = subtreeRelabelings;
    factorVars = Lists.newArrayList();
    for (int i = 0; i < subtrees.size(); i++) {
      QueryTree subtree = subtrees.get(i);
      factorVars.add(subtree.getOutputLocalWeights().getAllVariables()); 
    }

    outputVars = outputLocalWeights.getAllVariables();
    VariableNumMap truthTableVar = truthTableVariables.intersection(
        truthTableVariables.getVariableNums().get(subtrees.size()));
    outputRelabeling = VariableRelabeling.identity(outputLocalWeights.getIndexVariables()).union(
        VariableRelabeling.createFromVariables(outputLocalWeights.getValueVariables(),
        truthTableVar));
  }
  
  
  private ConjunctionQueryTree(ParallelFactors outputLocalWeights, List<QueryTree> subtrees, 
      VariableNumMap truthTableVariables, ParallelFactors constraintFactor, List<VariableNumMap> factorVars, 
      List<VariableRelabeling> factorRelabelings, VariableNumMap outputVars, VariableRelabeling outputRelabeling) {
      super(outputLocalWeights, subtrees, null, true);
    this.truthTableVariables = truthTableVariables;
    this.constraintFactor = constraintFactor;
    this.factorVars = factorVars;
    this.factorRelabelings = factorRelabelings;
    this.outputVars = outputVars;
    this.outputRelabeling = outputRelabeling;
  }

  
  public static ConjunctionQueryTree createConjunction(ParallelFactors truthTable, QueryTree main, QueryTree input,
      int inputVarNum) {
    ParallelFactors conjunctionOutputWeights = main.getOutputLocalWeights().emptyCopy();
    ParallelFactors conjunctionOutputOutcomes = ParallelFactors.fromVariables(
        conjunctionOutputWeights.getIndexVariables(), VariableNumMap.EMPTY, 1.0);
    
    List<VariableRelabeling> relabelings = Lists.newArrayList();
    VariableNumMap truthTableVars = truthTable.getAllVariables();
    VariableNumMap truthTableVar = truthTableVars.intersection(truthTableVars.getVariableNums().get(0));
    relabelings.add(VariableRelabeling.identity(conjunctionOutputWeights.getIndexVariables()).union(
        VariableRelabeling.createFromVariables(conjunctionOutputWeights.getValueVariables(), truthTableVar)));
    
    truthTableVar = truthTableVars.intersection(truthTableVars.getVariableNums().get(1));
    VariableNumMap mainInput = conjunctionOutputWeights.getAllVariables().intersection(inputVarNum);
    ParallelFactors inputTensor = input.getOutputLocalWeights();

    relabelings.add(VariableRelabeling.createFromVariables(inputTensor.getIndexVariables(), mainInput).union(
        VariableRelabeling.createFromVariables(inputTensor.getValueVariables(), truthTableVar)));
    
    // System.out.println(relabelings);
    
    return new ConjunctionQueryTree(truthTable, conjunctionOutputOutcomes, 
        conjunctionOutputWeights, Arrays.asList(main, input), relabelings); 
  }

  @Override
  protected List<Tensor> locallyDecodeFactor() {
    // System.out.println("constraintFactor: " + constraintFactor.getTensor());
    // System.out.println("outputFactorWeights: " + outputFactorWeights.getTensor().elementwiseExp());
    // System.out.println("result: " + result);
    
    Backpointers backpointers = new Backpointers();
    constraintFactor.getTensor().maxOutDimensions(constraintFactor.getValueVariables().getVariableNums(), backpointers);
            
    return Arrays.asList((Tensor) backpointers.getOldKeyIndicatorTensor());
  }
  
  @Override
  protected int localSubgradientUpdate(Tensor unrelabeledLocalRootAssignments, 
      List<Tensor> unrelabeledLocalChildAssignments, List<Tensor> factorAssignments, double stepSize) { 
    
    Tensor localRootAssignments = unrelabeledLocalRootAssignments.relabelDimensions(
        outputRelabeling.getVariableIndexReplacementMap());
    // System.out.println(localRootAssignments);
    List<Tensor> localChildAssignments = Lists.newArrayList();
    for (int i = 0; i < unrelabeledLocalChildAssignments.size(); i++) {
      localChildAssignments.add(unrelabeledLocalChildAssignments.get(i).relabelDimensions(
          factorRelabelings.get(i).getVariableIndexReplacementMap()));
      // System.out.println(localChildAssignments.get(i));
    }
    
    // Compute assignments to the constraintFactor which disagree with the local assignments
    Tensor factorAssignment = factorAssignments.get(0);
    // System.out.println(factorAssignment);
    Tensor intersection = factorAssignment.elementwiseProduct(localRootAssignments)
        .elementwiseProduct(localChildAssignments);
    Tensor disagreements = factorAssignment.elementwiseAddition(intersection.elementwiseProduct(-1));
    int numDisagreements = (int) disagreements.sumOutDimensions(Ints.asList(disagreements.getDimensionNumbers())).getByDimKey(new int[] {});
    // System.out.println("disagreements: " + disagreements);
    // System.out.println("output factor: " + getOutputLocalWeights().getTensor());
    // System.out.println("num disagreements: " + numDisagreements);

    // Compute the component of the gradient that depends on the optimal constraintFactor assignments.

    VariableNumMap disagreementFactorVars = outputRelabeling.apply(getOutputLocalWeights().getIndexVariables());
    VariableNumMap outputValueVars = outputRelabeling.apply(getOutputLocalWeights().getValueVariables());    
    Tensor outputFactorGradient = computeFactorGradient(disagreements, disagreementFactorVars,
        outputValueVars);
    // System.out.println("output factor gradient: " + outputFactorGradient);
    List<Tensor> childFactorGradients = Lists.newArrayList();
    for (int i = 0; i < factorVars.size(); i++) {
      VariableNumMap factorValueVars = factorRelabelings.get(i).apply(getSubtrees().get(i).getOutputLocalWeights().getValueVariables());
      childFactorGradients.add(computeFactorGradient(disagreements, 
          disagreementFactorVars, factorValueVars));
      // System.out.println("child factor gradient " + i + ":" + childFactorGradients.get(i));
    }
    
    // Compute the component of the gradient which depends on the optimal local assignments.
    VariableNumMap disagreementVariableDims = outputRelabeling.apply(getOutputLocalWeights().getIndexVariables());
    Tensor outputLocalGradient = computeLocalGradient(disagreements, disagreementVariableDims, 
        localRootAssignments, getOutputLocalWeights().getValueVariables(), outputRelabeling);
    // System.out.println("output local gradient: " + outputLocalGradient); 
    List<Tensor> childLocalGradients = Lists.newArrayList();
    for (int i = 0; i < factorVars.size() ; i++) {
      childLocalGradients.add(computeLocalGradient(disagreements, disagreementVariableDims,
          localChildAssignments.get(i), getSubtrees().get(i).getOutputLocalWeights().getValueVariables(), 
          factorRelabelings.get(i)));
      // System.out.println("child local gradient: " + childLocalGradients.get(i));
    }

    // Perform the gradient update for the local (single variable) factors.
    // System.out.println(Arrays.toString(getOutputLocalWeights().getDimensionNumbers().getVariableNumsArray()));
    //  System.out.println(Arrays.toString(outputLocalGradient.getDimensionNumbers()));
    // System.out.println(outputRelabeling.inverse());
    // System.out.println("output local gradient " + outputLocalGradient.relabelDimensions(outputRelabeling.inverse().getVariableIndexReplacementMap()));
    // System.out.println("output factor gradient " + outputFactorGradient);
    updateOutputLocalWeights(outputLocalGradient.elementwiseProduct(-1.0 * stepSize).relabelDimensions(outputRelabeling.inverse().getVariableIndexReplacementMap()));
    updateOutputLocalWeights(outputFactorGradient.elementwiseProduct(stepSize).relabelDimensions(outputRelabeling.inverse().getVariableIndexReplacementMap()));
    List<QueryTree> subtrees = getSubtrees();
    for (int i = 0; i < factorVars.size(); i++) {
      Set<Integer> dimsNotInFactor = Sets.newHashSet(Ints.asList(childLocalGradients.get(i).getDimensionNumbers()));
      dimsNotInFactor.removeAll(factorRelabelings.get(i).apply(
          subtrees.get(i).getOutputLocalWeights().getAllVariables()).getVariableNums());
      Tensor localGradient = childLocalGradients.get(i).sumOutDimensions(dimsNotInFactor).relabelDimensions(
          factorRelabelings.get(i).inverse().getVariableIndexReplacementMap());
      Tensor factorGradient = childFactorGradients.get(i).sumOutDimensions(dimsNotInFactor).relabelDimensions(
          factorRelabelings.get(i).inverse().getVariableIndexReplacementMap());
      // System.out.println("child local gradient " + localGradient);
      // System.out.println("child factor gradient " + factorGradient);

      subtrees.get(i).updateOutputLocalWeights(localGradient.elementwiseProduct(-1.0 * stepSize));
      subtrees.get(i).updateOutputLocalWeights(factorGradient.elementwiseProduct(stepSize));
    }
    
    // Perform the gradient update for the conjunction constraintFactor.
    DenseTensorBuilder factorGradientBuilder = new DenseTensorBuilder(constraintFactor.getAllVariables().getVariableNumsArray(), 
        constraintFactor.getDimensionSizes());
    factorGradientBuilder.incrementWithMultiplier(outputLocalGradient, stepSize);
    factorGradientBuilder.incrementWithMultiplier(outputFactorGradient, -1.0 * stepSize);
    for (int i = 0; i < factorVars.size(); i++) {
      Tensor localGradient = childLocalGradients.get(i);
      Tensor factorGradient = childFactorGradients.get(i);

      factorGradientBuilder.incrementWithMultiplier(localGradient, stepSize);
      factorGradientBuilder.incrementWithMultiplier(factorGradient, -1.0 * stepSize);
    }
    
    constraintFactor = constraintFactor.elementwiseAddition(factorGradientBuilder.buildNoCopy());
    // System.out.println("new weights: " + constraintFactor.getTensor());
    return numDisagreements;
  }
    
  /**
   * Compute the number of disagreements on dimensionNumbers
   * @param disagreements
   * @param variableTensor
   * @return
   */
  private Tensor computeFactorGradient(Tensor disagreements, VariableNumMap factorVariables, 
      VariableNumMap valueVars) {
    Set<Integer> disagreementDimSet = Sets.newHashSet(Ints.asList(disagreements.getDimensionNumbers()));
    disagreementDimSet.removeAll(factorVariables.union(valueVars).getVariableNums());
    
    // System.out.println(factorToLocal);
    // System.out.println(Arrays.toString(disagreements.getDimensionNumbers()));

    return disagreements.sumOutDimensions(disagreementDimSet);
  }
  
  private Tensor computeLocalGradient(Tensor disagreements, VariableNumMap disagreementVarNums, 
      Tensor factorLocalAssignment, VariableNumMap factorLocalValueVars, VariableRelabeling localToFactor) {
    // Count the number of times each variable was involved in a disagreement. 
    Set<Integer> disagreementDimSet = Sets.newHashSet(Ints.asList(disagreements.getDimensionNumbers()));
    disagreementDimSet.removeAll(disagreementVarNums.getVariableNums());
    Tensor disagreementVarCounts = disagreements.sumOutDimensions(disagreementDimSet);

    VariableNumMap variableFactorValues = localToFactor.apply(factorLocalValueVars);
    DenseTensor possibilities = DenseTensor.constant(variableFactorValues.getVariableNumsArray(),  
        variableFactorValues.getVariableSizes(), 1.0);
    // System.out.println(factorLocalValueVars);
    // System.out.println("possibilities: " + possibilities);
    // System.out.println(Arrays.toString(disagreements.getDimensionNumbers()));
    // System.out.println(Arrays.toString(disagreementVarCounts.getDimensionNumbers()));
    // System.out.println(Arrays.toString(possibilities.getDimensionNumbers())); 
    Tensor expandedVarCounts = disagreementVarCounts.outerProduct(possibilities);
    
    // System.out.println(Arrays.toString(expandedVarCounts.getDimensionNumbers()));
    // System.out.println(Arrays.toString(factorLocalAssignment.getDimensionNumbers()));
    // Local gradient is 1 for the optimal assignment to the disagreeing variable,
    // each time that variable is involved in a disagreement.
    // System.out.println(localToFactor.inverse().getVariableIndexReplacementMap());
    // System.out.println(expandedVarCounts);
    // System.out.println(factorLocalAssignment);
    return expandedVarCounts.elementwiseProduct(factorLocalAssignment); 
  }

  @Override
  public MultiTree<Tensor> evaluateQueryMap() {
    Tensor bestAssignmentTensor = constraintFactor.getTensor();
    List<MultiTree<Tensor>> subtreeAssignments = Lists.newArrayList();
    List<QueryTree> subtrees = getSubtrees();
    for (int i = 0; i < subtrees.size(); i++) {
      QueryTree subtree = subtrees.get(i);
      MultiTree<Tensor> subtreeAssignment = subtree.evaluateQueryMap();
      subtreeAssignments.add(subtreeAssignment);
      System.out.println(factorRelabelings.get(i));
      Tensor subtreeRoot = subtreeAssignment.getValue().relabelDimensions(factorRelabelings.get(i)
          .getVariableIndexReplacementMap());
      bestAssignmentTensor = bestAssignmentTensor.elementwiseAddition(subtreeRoot.elementwiseLog());
    }

    Backpointers backpointers = new Backpointers();
    bestAssignmentTensor.maxOutDimensions(constraintFactor.getValueVariables().getVariableNums(), backpointers);

    Tensor indicatorTensor = backpointers.getOldKeyIndicatorTensor();

    VariableNumMap varsToEliminate = constraintFactor.getAllVariables().removeAll(outputRelabeling.apply(outputVars));
    Tensor returnTensor = indicatorTensor.maxOutDimensions(varsToEliminate.getVariableNums())
        .relabelDimensions(outputRelabeling.inverse().getVariableIndexReplacementMap());

    return new MultiTree<Tensor>(returnTensor, subtreeAssignments);
  }

  @Override
  public QueryTree copy() {
    List<QueryTree> subtreeCopies = Lists.newArrayList();
    for (QueryTree subtree : getSubtrees()) {
      subtreeCopies.add(subtree.copy());
    }
    
    return new ConjunctionQueryTree(getOutputLocalWeights(), subtreeCopies, truthTableVariables, 
        constraintFactor, factorVars, factorRelabelings, outputVars, outputRelabeling);
  }
  
  @Override
  protected IloNumVar[] augmentIlpHelper(IloCplex cplex, IloLinearNumExpr objective, 
					 boolean useLpRelaxation, boolean applyWeakSupervisionConstraints) throws IloException {
    IloNumVar[] myVars = addLocalWeightsToIlp(cplex, objective, useLpRelaxation);
    
    // Implement the AND constraint.
    List<IloNumVar[]> childVars = Lists.newArrayList();
    List<ParallelFactors> childFactors = Lists.newArrayList();
    List<VariableRelabeling> inverseRelabelings = Lists.newArrayList();
    List<QueryTree> subtrees = getSubtrees();
    for (int i = 0 ; i < subtrees.size(); i++) {
      QueryTree subtree = subtrees.get(i);

      childVars.add(subtree.augmentIlp(cplex, objective, useLpRelaxation, applyWeakSupervisionConstraints));
      childFactors.add(subtree.getOutputLocalWeights());
      inverseRelabelings.add(factorRelabelings.get(i).inverse());
    }

    ParallelFactors myWeights = getOutputLocalWeights();
    
    for (int i = 0; i < myVars.length; i++) {
      Assignment myKey = myWeights.ilpIndexToAssignment(i);
      // StringBuilder sb = new StringBuilder();
      // sb.append(myKey + "= AND(");

      IloLinearNumExpr constraint = cplex.linearNumExpr();
      constraint.addTerm(-1.0 * childVars.size(), myVars[i]); 
      
      for (int j = 0; j < childVars.size(); j++) {
        Assignment childKey = myKey.mapVariables(inverseRelabelings.get(j).getVariableIndexReplacementMap());
        int childVarIndex = childFactors.get(j).getIlpVariableIndex(childKey);
        constraint.addTerm(1.0, childVars.get(j)[childVarIndex]);

	// sb.append(childKey + ", ");
      }

      // System.out.println(sb.toString());
      cplex.addGe(constraint, 0.0);
      cplex.addLe(constraint, childVars.size() - 1);
    }
    
    return myVars;
  }
  
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("and(");
    for (QueryTree subtree : getSubtrees()) {
      sb.append(subtree.toString());
      sb.append(", ");
    }
    sb.append(")");

    return sb.toString();
  }
}
