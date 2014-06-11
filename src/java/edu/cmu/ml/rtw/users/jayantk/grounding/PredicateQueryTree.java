package edu.cmu.ml.rtw.users.jayantk.grounding;

import ilog.concert.IloException;
import ilog.concert.IloLinearNumExpr;
import ilog.concert.IloNumVar;
import ilog.cplex.IloCplex;

import com.google.common.collect.Lists;

import java.util.Collections;
import java.util.List;

import com.jayantkrish.jklol.tensor.Tensor;

import edu.cmu.ml.rtw.users.jayantk.semparse.RelationType;

public class PredicateQueryTree extends AbstractQueryTree {

  public PredicateQueryTree(RelationType relation, ParallelFactors localFactor,
      boolean isHardConstraint) {
    super(localFactor, Collections.<QueryTree> emptyList(), relation, isHardConstraint);

    // System.out.println(relation + " " + localFactor.getTensor());
  }

  @Override
  public PredicateQueryTree copy() {
    return new PredicateQueryTree(getPredicate(), getOutputLocalWeights(), isHardConstraint());
  }

  @Override
  protected int localSubgradientUpdate(Tensor rootAssignment,
      List<Tensor> childAssignments, List<Tensor> factorAssignment, double stepSize) {
    throw new UnsupportedOperationException("Cannot update leaves (no factor)");
  }

  @Override
  protected List<Tensor> locallyDecodeFactor() {
    // Note that this tensor isn't used to update the subgradient,
    // so the returned value is irrelevant.
    return Collections.emptyList();
  }

  public MultiTree<Tensor> evaluateQueryMap() {
    List<MultiTree<Tensor>> subtrees = Lists.newArrayList();
    Tensor bestAssignments = getOutputLocalWeights().getBestAssignments();

    return new MultiTree<Tensor>(bestAssignments, subtrees);
  }

  @Override
  protected IloNumVar[] augmentIlpHelper(IloCplex cplex, IloLinearNumExpr objective, 
					 boolean useLpRelaxation, boolean applyWeakSupervisionConstraints) throws IloException {
    return addLocalWeightsToIlp(cplex, objective, useLpRelaxation);
  }

  @Override
  public String toString() {
    return getPredicate().toString();
  }
}
