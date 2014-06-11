package edu.cmu.ml.rtw.users.jayantk.grounding;

import ilog.concert.IloException;
import ilog.concert.IloLinearNumExpr;
import ilog.concert.IloNumVar;
import ilog.cplex.IloCplex;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.tensor.Backpointers;
import com.jayantkrish.jklol.tensor.DenseTensor;
import com.jayantkrish.jklol.tensor.DenseTensorBuilder;
import com.jayantkrish.jklol.tensor.SparseTensor;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.tensor.TensorBuilder;
import com.jayantkrish.jklol.util.AllAssignmentIterator;
import com.jayantkrish.jklol.util.Assignment;

public class ExistentialQueryTree extends AbstractQueryTree {
  
  // The weights (/ lagrange multipliers) that the factor places 
  // on the output and child variables.
  // These weights are stored in log space.
  private ParallelFactors childFactorWeights;
  private ParallelFactors outputFactorWeights;
  
  private Set<Integer> dimensionsToEliminate;
  private final int valueDimension;

  public ExistentialQueryTree(ParallelFactors outputLocalWeights, QueryTree subtree) {
      super(outputLocalWeights, Arrays.asList(subtree), null, true);
    
    childFactorWeights = subtree.getOutputLocalWeights().emptyCopy();
    outputFactorWeights = outputLocalWeights.emptyCopy();
    
    dimensionsToEliminate = Sets.newHashSet();
    dimensionsToEliminate.addAll(childFactorWeights.getIndexVariables().getVariableNums());
    dimensionsToEliminate.removeAll(outputFactorWeights.getIndexVariables().getVariableNums());

    Preconditions.checkArgument(dimensionsToEliminate.size() >= 1);
    Preconditions.checkArgument(childFactorWeights.getValueVariables().size() == 1);
    valueDimension = childFactorWeights.getValueVariables().getVariableNums().get(0);
  }

  private ExistentialQueryTree(ParallelFactors outputLocalWeights, List<QueryTree> subtrees, 
      ParallelFactors childFactorWeights, ParallelFactors outputFactorWeights, 
      Set<Integer> dimensionsToEliminate, int valueDimension) {
      super(outputLocalWeights, subtrees, null, true);
    this.childFactorWeights = childFactorWeights;
    this.outputFactorWeights = outputFactorWeights;
    this.dimensionsToEliminate = dimensionsToEliminate;
    this.valueDimension = valueDimension;
  }
  
  public static ExistentialQueryTree eliminateVariables(QueryTree subtree, Collection<Integer> toEliminate) {
    ParallelFactors output = subtree.getOutputLocalWeights();
    
    VariableNumMap newVars = output.getIndexVariables().removeAll(toEliminate);
    VariableNumMap newValues = output.getValueVariables().removeAll(toEliminate);
    VariableNumMap allVars = newVars.union(newValues);
    
    int[] newDims = Ints.toArray(allVars.getVariableNums());
    int[] newSizes = allVars.getVariableSizes();
    ParallelFactors existentialOutput = new ParallelFactors(DenseTensor.constant(newDims, newSizes, 0.0),
        newVars, newValues);
    
    return new ExistentialQueryTree(existentialOutput, subtree); 
  }

  @Override
  protected List<Tensor> locallyDecodeFactor() {
    Tensor childTensor = childFactorWeights.getTensor();
    // Compute the weight for the outcome where all inputs are 0 and the output is correspondingly 0.
    
    Tensor zeroWeights = childTensor.slice(new int[] {valueDimension}, new int[] {0});
    Tensor combinedZeroWeights = zeroWeights.sumOutDimensions(dimensionsToEliminate)
        .elementwiseAddition(outputFactorWeights.getTensor().slice(new int[] {valueDimension}, new int[] {0}));

    // Compute the weight for the outcome where at least one input is 1 and the output is 1.
    Backpointers backpointers = new Backpointers();
    Tensor maxWeights = childTensor.maxOutDimensions(Ints.asList(valueDimension), backpointers);
    Tensor backpointerIndicators = backpointers.getOldKeyIndicatorTensor();
    TensorBuilder bestAssignments = DenseTensorBuilder.copyOf(backpointerIndicators);
    Tensor oneCounts = backpointerIndicators.slice(new int[] {valueDimension}, new int[] {1})
        .sumOutDimensions(dimensionsToEliminate);
    TensorBuilder childOneWeights = DenseTensorBuilder.copyOf(maxWeights.sumOutDimensions(dimensionsToEliminate));

    Tensor oneWeights = childTensor.slice(new int[] {valueDimension}, new int[] {1});
    Backpointers deltaBackpointers = new Backpointers();
    Tensor bestDeltas = oneWeights.elementwiseAddition(zeroWeights.elementwiseProduct(-1.0))
        .maxOutDimensions(dimensionsToEliminate, deltaBackpointers);

    // System.out.println(oneCounts);
    for (long i = 0; i < oneCounts.getMaxKeyNum(); i++) { 
      if (oneCounts.get(i) == 0) {
        // System.out.println("forcing: "+ i);
        // Not a single highest-probability outcome is 1. Adjust the assignment so that
        // the value closest to 1 is set to 1 (and thus the output can be 1).
        double curWeight = childOneWeights.get(i);
        childOneWeights.putByKeyNum(i, 
            curWeight + bestDeltas.get(i));
        long changedKeyNum = deltaBackpointers.getBackpointer(i);
        int[] dimKey = zeroWeights.keyNumToDimKey(changedKeyNum);
        int[] newKey = Arrays.copyOf(dimKey, dimKey.length + 1);
        
        newKey[newKey.length - 1] = 0;
        // System.out.println(Arrays.toString(newKey));
        bestAssignments.put(newKey, 0.0);
        newKey[newKey.length - 1] = 1;
        // System.out.println(Arrays.toString(newKey));
        bestAssignments.put(newKey, 1.0);
      }
    }
    // System.out.println(bestAssignments.buildNoCopy());
    
    Tensor combinedOneWeights = childOneWeights.buildNoCopy().elementwiseAddition(
        outputFactorWeights.getTensor().slice(new int[] {valueDimension}, new int[] {1}));
    TensorBuilder indicatorTensor = new DenseTensorBuilder(combinedOneWeights.getDimensionNumbers(), 
        combinedOneWeights.getDimensionSizes()); 
    for (long i = 0; i < combinedOneWeights.getMaxKeyNum(); i++) {
      if (combinedOneWeights.get(i) > combinedZeroWeights.get(i)) {
        indicatorTensor.putByKeyNum(i, 1.0);
      }
    }
    // System.out.println("indicator: " + indicatorTensor.buildNoCopy());
    
    Tensor childAllZeros = new DenseTensorBuilder(bestAssignments.getDimensionNumbers(), 
        bestAssignments.getDimensionSizes(), 1.0).buildNoCopy();
    Tensor childZeros = childAllZeros.elementwiseProduct(SparseTensor.singleElement(new int[] {valueDimension}, 
        new int[] {2}, new int[] {0}, 1.0));
    
    Tensor rootAllOnes = new DenseTensorBuilder(Ints.toArray(outputFactorWeights.getAllVariables().getVariableNums()), 
        outputFactorWeights.getDimensionSizes(), 1.0).buildNoCopy();
    Tensor rootOnes = rootAllOnes.elementwiseProduct(SparseTensor.singleElement(new int[] {valueDimension}, 
        new int[] {2}, new int[] {1}, 1.0));
    Tensor rootZeros = rootAllOnes.elementwiseProduct(SparseTensor.singleElement(new int[] {valueDimension}, 
        new int[] {2}, new int[] {0}, 1.0));
    
    // Combine the zero and one return values.
    Tensor oneIndicator = indicatorTensor.build();
    Tensor oneReturnValue = bestAssignments.build().elementwiseProduct(oneIndicator);
    Tensor oneRootValue = rootOnes.elementwiseProduct(oneIndicator);
    indicatorTensor.multiply(-1.0);
    indicatorTensor.increment(1.0);
    Tensor zeroIndicator = indicatorTensor.build();
    Tensor zeroReturnValue = childZeros.elementwiseProduct(zeroIndicator);    
    Tensor zeroRootValue = rootZeros.elementwiseProduct(zeroIndicator);
    
    Tensor childReturnValue = oneReturnValue.elementwiseAddition(zeroReturnValue);
    Tensor rootReturnValue = oneRootValue.elementwiseAddition(zeroRootValue);
    
    // System.out.println("root: " + rootReturnValue);
    // System.out.println("child: " + childReturnValue); 
    return Arrays.asList(rootReturnValue, childReturnValue);
  }

  @Override
  protected int localSubgradientUpdate(Tensor rootAssignment, List<Tensor> childAssignments, 
      List<Tensor> factorAssignments, double stepSize) {
    
    // Identify root disagreements and update the factor/local weights.
    Tensor rootFactorAssignment = factorAssignments.get(0);
    Tensor rootDisagreements = rootAssignment.elementwiseAddition(rootFactorAssignment.elementwiseProduct(-1.0));
    
    // System.out.println("root factor assignment: " + rootFactorAssignment);
    // System.out.println("root assignment: " + rootAssignment);
    // System.out.println("root disagreements: " + rootDisagreements);

    outputFactorWeights = outputFactorWeights.elementwiseAddition(rootDisagreements.elementwiseProduct(stepSize));
    updateOutputLocalWeights(rootDisagreements.elementwiseProduct(-1.0 * stepSize));
    int numRootDisagreements = (int) rootDisagreements.elementwiseProduct(rootDisagreements)
        .sumOutDimensions(Ints.asList(rootDisagreements.getDimensionNumbers())).get(0);
    
    // System.out.println("# root disagreements: " + numRootDisagreements);
    
    // Identify child disagreements and update the factor/local weights.
    Tensor childLocalAssignment = childAssignments.get(0);
    Tensor childFactorAssignment = factorAssignments.get(1);
    Tensor childDisagreements = childLocalAssignment.elementwiseAddition(
        childFactorAssignment.elementwiseProduct(-1.0));
    
    // System.out.println("child factor assignment: " + childFactorAssignment);
    // System.out.println("child assignment: " + childLocalAssignment);
    // System.out.println("child weights: " + getSubtrees().get(0).getOutputLocalWeights().getTensor());
    // System.out.println("child disagreements: " + childDisagreements);  
    
    childFactorWeights = childFactorWeights.elementwiseAddition(childDisagreements.elementwiseProduct(stepSize));
    getSubtrees().get(0).updateOutputLocalWeights(childDisagreements.elementwiseProduct(-1.0 * stepSize));
    int numChildDisagreements = (int) childDisagreements.elementwiseProduct(childDisagreements)
        .sumOutDimensions(Ints.asList(childDisagreements.getDimensionNumbers())).get(0);

    // System.out.println("# child disagreements: " + numChildDisagreements);
    
    return numRootDisagreements + numChildDisagreements;
  }
  
  @SuppressWarnings("unchecked")
  @Override
  public MultiTree<Tensor> evaluateQueryMap() {
    QueryTree subtree = Iterables.getOnlyElement(getSubtrees());
    MultiTree<Tensor> subtreeAssignment = subtree.evaluateQueryMap();
    Tensor subtreeRootAssignment = subtreeAssignment.getValue();
    
    Tensor factorAssignment = subtreeRootAssignment.maxOutDimensions(dimensionsToEliminate);
    
    return new MultiTree<Tensor>(factorAssignment, Arrays.asList(subtreeAssignment));
  }

  @Override
  public QueryTree copy() {
    List<QueryTree> subtrees = Lists.newArrayList();
    for (QueryTree subtree : getSubtrees()) {
      subtrees.add(subtree.copy());
    }
    
    return new ExistentialQueryTree(getOutputLocalWeights(), subtrees, 
        childFactorWeights, outputFactorWeights, dimensionsToEliminate, valueDimension);
  }
  
  @Override
  protected IloNumVar[] augmentIlpHelper(IloCplex cplex, IloLinearNumExpr objective, boolean useLpRelaxation,
					 boolean applyWeakSupervisionConstraints) throws IloException {
    IloNumVar[] myVars = addLocalWeightsToIlp(cplex, objective, useLpRelaxation);
    IloNumVar[] childVars = getSubtrees().get(0).augmentIlp(cplex, objective, useLpRelaxation, applyWeakSupervisionConstraints);
    
    // Implement OR constraint.
    // The output var of the OR.
    Iterator<Assignment> outputAssignmentIter = new AllAssignmentIterator(outputFactorWeights.getIndexVariables());
    while (outputAssignmentIter.hasNext()) {
      Assignment outputAssignment = outputAssignmentIter.next();
      IloNumVar orOutputVar = myVars[outputFactorWeights.getIlpVariableIndex(outputAssignment)];
      IloLinearNumExpr leConstraint = cplex.linearNumExpr();

      Iterator<Assignment> eliminateIter = new AllAssignmentIterator(childFactorWeights.getIndexVariables()
          .intersection(dimensionsToEliminate));
      while (eliminateIter.hasNext()) {
        Assignment eliminateAssignment = eliminateIter.next();

        // orOutputVar must be greater than each child variable.
        IloLinearNumExpr geConstraint = cplex.linearNumExpr();
        IloNumVar childVar = childVars[childFactorWeights.getIlpVariableIndex(eliminateAssignment.union(outputAssignment))];
        geConstraint.addTerm(-1.0, childVar);
        geConstraint.addTerm(1.0, orOutputVar);
        cplex.addGe(geConstraint, 0);
        
        // numChildren * orOutputVar must be less than the sum of all child variables.
        leConstraint.addTerm(-1.0, childVar);
      }
      
      leConstraint.addTerm(1.0, orOutputVar);
      cplex.addLe(leConstraint, 0);
    }
    
    return myVars;
  }
  
  @Override
  public String toString() {
    return "Exists(" + getSubtrees().get(0).toString() + ")";
  }
}
