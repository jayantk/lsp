package edu.cmu.ml.rtw.users.jayantk.grounding;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.training.GradientOracle;
import com.jayantkrish.jklol.training.LogFunction;

import edu.cmu.ml.rtw.users.jayantk.grounding.GroundingModelEmOracle.GroundingExpectation;

public class GroundingModelLoglikelihoodOracle implements GradientOracle<GroundingModel, GroundingExpectation> {
  
  private final GroundingModelFamily family;
  
  public GroundingModelLoglikelihoodOracle(GroundingModelFamily family) {
    this.family = Preconditions.checkNotNull(family);
  }
  
  @Override
  public SufficientStatistics initializeGradient() {
    return family.getNewSufficientStatistics();
  }
    
  @Override
  public GroundingModel instantiateModel(SufficientStatistics parameters) {
    return family.instantiateModel(parameters);
  } 

  
  @Override
  public double accumulateGradient(SufficientStatistics gradient, SufficientStatistics currentParameters,
      GroundingModel model, GroundingExpectation example, LogFunction log) {
    throw new UnsupportedOperationException("Not yet implemented");
    /*
    List<String> inputWords = example.getWords();
    
    // Update the unconditional expectations for the parser. 
    BeamSearchCfgFactor parser = model.getParser();
    DiscreteObjectFactor parseFactor = parser.conditional(
        parser.getTerminalVariable().outcomeArrayToAssignment(inputWords))
        .coerceToDiscreteObject();
    double parsePartitionFunction = parseFactor.getTotalUnnormalizedProbability();
    for (Assignment parseAssignment : parseFactor.assignments()) {
      // Subtract feature expectations from each generated parse from the gradient.
      ParseTree parse = (ParseTree) parseAssignment.getOnlyValue();
      double parseWeight = parseFactor.getUnnormalizedProbability(parseAssignment);
      
      family.incrementParserParameters(gradient, inputWords, parse, (-1.0 * parseWeight) / parsePartitionFunction);
    }
    // Update the parse parameters for the true parse.
    family.incrementParserParameters(gradient, inputWords, example.getObservedParse(), 1.0);
    
    List<RelationType> observedRelations = example.getRelationsInLogicalForm();
    List<Tensor> observedRelationAssignments = example.getObservedRelationAssignments();
    int numRels = observedRelations.size();
    for (int i = 0; i < numRels; i++) {
      Relation
    }
    */
  }
}
