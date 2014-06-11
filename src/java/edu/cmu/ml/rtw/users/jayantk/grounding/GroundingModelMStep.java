package edu.cmu.ml.rtw.users.jayantk.grounding;

import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.parallel.Mapper;
import com.jayantkrish.jklol.parallel.Reducer.SimpleReducer;
import com.jayantkrish.jklol.tensor.Tensor;

import edu.cmu.ml.rtw.users.jayantk.grounding.GroundingModelEmOracle.GroundingExpectation;
import edu.cmu.ml.rtw.users.jayantk.semparse.RelationType;

/**
 * Mapreduce pipeline for maximizing the grounding parameters of the
 * CGG grounding model.
 * 
 * @author jayantk
 */
public class GroundingModelMStep extends Mapper<GroundingExpectation, SufficientStatistics> {
  
  private final boolean onlyUseDeltas;
  private final GroundingModelFamily family;
  
  public GroundingModelMStep(GroundingModelFamily family, boolean onlyUseDeltas) {
    this.family = Preconditions.checkNotNull(family);
    this.onlyUseDeltas = onlyUseDeltas;
  }

  @Override
  public SufficientStatistics map(GroundingExpectation expectation) {
    SufficientStatistics newParameters = family.getNewSufficientStatistics();

    if (onlyUseDeltas) {
      MultiTree<Tensor> deltaAssignment = computeDeltaAssignment(expectation.getAssignment(),
          expectation.getUnconditionalAssignment(), expectation.getQuery());
      family.incrementGroundingParameters(expectation.getDomain().getName(), 
          newParameters, expectation.getQuery(), deltaAssignment, 1.0);
    } else {
      family.incrementGroundingParameters(expectation.getDomain().getName(), 
          newParameters, expectation.getQuery(), expectation.getAssignment(), 1.0);
    }

    // Calculate expectations for predicates which are not part of the grounding.
    Set<RelationType> allRelations = Sets.newHashSet(expectation.getPredicates()); 
    Set<RelationType> relationsInQuery = Sets.newHashSet();
    expectation.getQuery().getAllPredicatesInTree(relationsInQuery);
    allRelations.removeAll(relationsInQuery);

    String domainName = expectation.getDomain().getName();
    for (RelationType relation : allRelations) {
      ParallelFactors marginal = expectation.getUnconditionalMarginalForPredicate(relation);

      if (onlyUseDeltas) {
	  family.incrementGroundingParameters(domainName, newParameters, relation, marginal, 1.0);
      } else {
	  Tensor assignment = marginal.getBestAssignments();
	  ParallelFactors assignmentDistribution = new ParallelFactors(assignment, marginal.getIndexVariables(),
								       marginal.getValueVariables());
	  family.incrementGroundingParameters(domainName, newParameters, relation, marginal, 1.0);
      }
    }

    return newParameters;
  }  
  
  private static MultiTree<Tensor> computeDeltaAssignment(MultiTree<Tensor> conditional, 
      MultiTree<Tensor> unconditional, QueryTree query) {
    Tensor currentDelta = null;
    if (conditional.isLeaf()) {
      ParallelFactors queryFactor = query.getOutputLocalWeights();
      int[] valueDimensions = queryFactor.getValueVariables().getVariableNumsArray();
      Tensor unnormalizedLogMarginals = queryFactor.getTensor();
      Tensor maxLogMarginals = unnormalizedLogMarginals.maxOutDimensions(valueDimensions);
      unnormalizedLogMarginals = unnormalizedLogMarginals.elementwiseAddition(maxLogMarginals.elementwiseProduct(-1.0));
      
      Tensor unnormalizedMarginals = unnormalizedLogMarginals.elementwiseExp();
      Tensor marginals = unnormalizedMarginals.elementwiseProduct(
          unnormalizedMarginals.sumOutDimensions(valueDimensions).elementwiseInverse());

      Tensor currentEqual = conditional.getValue().elementwiseProduct(unconditional.getValue());
      Tensor delta = conditional.getValue().elementwiseAddition(currentEqual.elementwiseProduct(-1.0));
      Tensor variablesWhichAreEqual = currentEqual.sumOutDimensions(valueDimensions);
      currentDelta = marginals.elementwiseProduct(variablesWhichAreEqual).elementwiseAddition(delta);

      /*
      ParallelFactors marginal = new ParallelFactors(currentDelta, queryFactor.getIndexVariables(), 
						     queryFactor.getValueVariables());
      System.out.println(query.getPredicate().getName() + " : " + 
			 marginal.getParameterDescription());
      */
    } else {
      // Nonterminal nodes don't matter for the purpose of updating parameters.
      currentDelta = conditional.getValue();
    }

    List<MultiTree<Tensor>> subtreeDeltas = Lists.newArrayList();
    List<MultiTree<Tensor>> conditionalSubtrees = conditional.getChildren();
    List<MultiTree<Tensor>> unconditionalSubtrees = unconditional.getChildren();
    List<QueryTree> subQueries = query.getSubtrees();
    Preconditions.checkState(conditionalSubtrees.size() == unconditionalSubtrees.size());
    for (int i = 0; i < conditionalSubtrees.size(); i++) {
      subtreeDeltas.add(computeDeltaAssignment(conditionalSubtrees.get(i), unconditionalSubtrees.get(i),
						 subQueries.get(i)));
    }

    if (subtreeDeltas.size() == 0) {
      System.out.println(currentDelta.innerProduct(currentDelta));
    }

    return new MultiTree<Tensor>(currentDelta, subtreeDeltas);
  }
  
  public static class GroundingModelReducer extends SimpleReducer<SufficientStatistics> {
    private final GroundingModelFamily family;
    
    public GroundingModelReducer(GroundingModelFamily family) {
      this.family = Preconditions.checkNotNull(family);
    }

    @Override
    public SufficientStatistics getInitialValue() {
      return family.getNewSufficientStatistics();
    }

    @Override
    public SufficientStatistics reduce(SufficientStatistics item, SufficientStatistics accumulated) {
      accumulated.increment(item, 1.0);
      return accumulated;
    }
  }
}

