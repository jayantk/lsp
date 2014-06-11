package edu.cmu.ml.rtw.users.jayantk.grounding;

import java.io.Serializable;
import java.util.Arrays;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.ListSufficientStatistics;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.models.parametric.TensorSufficientStatistics;
import com.jayantkrish.jklol.tensor.DenseTensorBuilder;
import com.jayantkrish.jklol.tensor.Tensor;

/**
 * Mixture of gaussians for generating groundings.
 * 
 * @author jayantk
 */
public class GaussianGroundingFamily implements GroundingFamily, Serializable {
  
  private static final long serialVersionUID = 1L;
  
  // The feature vectors of entities in this domain.
  private final VariableNumMap featureVars;
  private final DiscreteFactor domainVectors;
  
  private final VariableNumMap variableVars;
  private final VariableNumMap valueVars;

  // Per-class parameters for class prior probability and
  // class mean.
  public static final String PRIOR_PARAMETERS = "priors";
  public static final String MEAN_PARAMETERS = "means";
  
  // These parameters are shared across classes and standardize the 
  // features before the class probabilities are applied.
  public static final String GLOBAL_VARIANCE_PARAMETERS = "global_variance";
  public static final String GLOBAL_MEAN_PARAMETERS = "global_mean";
  
  public GaussianGroundingFamily(VariableNumMap featureVars, DiscreteFactor domainVectors, 
      VariableNumMap variableVars, VariableNumMap valueVars) {
    this.featureVars = Preconditions.checkNotNull(featureVars);
    this.domainVectors = Preconditions.checkNotNull(domainVectors);
    this.variableVars = Preconditions.checkNotNull(variableVars);
    this.valueVars = Preconditions.checkNotNull(valueVars);
    
   Preconditions.checkArgument(domainVectors.marginalize(valueVars).equals(domainVectors.maxMarginalize(valueVars)
        .product(valueVars.getNumberOfPossibleAssignments())));
  }

  @Override
  public DiscreteVariable getFeatureVariable() {
    return (DiscreteVariable) featureVars.getOnlyVariable();
  }

    @Override
    public VariableNumMap getIndexVariables() {
	return variableVars;
    }

    @Override
    public VariableNumMap getValueVariables() {
	return valueVars;
    }


  @Override
  public DiscreteFactor getFeatureVectors() {
    return domainVectors;
  }

  @Override
  public ParallelFactors getFactorFromParameters(SufficientStatistics parameters) {
    GaussianParameters gaussianParameters = computeParameters(parameters);
    
    DiscreteFactor means = gaussianParameters.means;
    DiscreteFactor variances = gaussianParameters.variances;
    DiscreteFactor priors = gaussianParameters.priors;
    
    // Calculate the component of the gaussians in the exponent.
    DiscreteFactor meanDeltas = domainVectors.add(means.product(-1.0));
    DiscreteFactor outcomeWeights = meanDeltas.product(variances.inverse()).product(meanDeltas)
        .marginalize(featureVars.getVariableNums()).product(-1.0 / 2);

    // Add in gaussian scaling factors. -1/2 of (The log determinant of the variance,
    // plus k * log(2 pi)) 
    DiscreteVariable featureVariable = (DiscreteVariable) featureVars.getOnlyVariable();
    Tensor scalingFactor = variances.getWeights().elementwiseLog()
        .sumOutDimensions(featureVars.getVariableNums()).elementwiseAddition(featureVariable.numValues() * Math.log(2 * Math.PI))
        .elementwiseProduct(-1.0 / 2);
    // Incorporate logs of the prior probabilities of each class label.
    scalingFactor = scalingFactor.elementwiseAddition(priors.getWeights().elementwiseLog());
    DiscreteFactor additiveTerms = new TableFactor(valueVars, scalingFactor);
    
    outcomeWeights = outcomeWeights.add(additiveTerms);
    return new ParallelFactors(outcomeWeights.getWeights(), variableVars, valueVars);
  }

  @Override
  public SufficientStatistics getNewSufficientStatistics() {
    // Global parameters for standardizing features.
    SufficientStatistics globalMeans = new TensorSufficientStatistics(featureVars,
        new DenseTensorBuilder(featureVars.getVariableNumsArray(), featureVars.getVariableSizes()));
    SufficientStatistics globalVariances = new TensorSufficientStatistics(featureVars,
        new DenseTensorBuilder(featureVars.getVariableNumsArray(), featureVars.getVariableSizes()));
    
    VariableNumMap meanVars = valueVars.union(featureVars);
    // Both of these parameters contain the value variable and feature variable.
    // They are parameters for an axis-aligned gaussian (diagonal covariance). 
    SufficientStatistics means = new TensorSufficientStatistics(meanVars,
        new DenseTensorBuilder(meanVars.getVariableNumsArray(), meanVars.getVariableSizes()));
    /*
    SufficientStatistics variances = new TensorSufficientStatistics(meanVars,
        new DenseTensorBuilder(meanVars.getVariableNumsArray(), meanVars.getVariableSizes()));
        */

    // Contains only the value variables. The prior probability of each value.
    SufficientStatistics classPriors = new TensorSufficientStatistics(valueVars,
        new DenseTensorBuilder(valueVars.getVariableNumsArray(), valueVars.getVariableSizes()));
    
    return new ListSufficientStatistics(Arrays.asList(GLOBAL_MEAN_PARAMETERS, GLOBAL_VARIANCE_PARAMETERS, 
        MEAN_PARAMETERS, PRIOR_PARAMETERS), Arrays.asList(globalMeans, globalVariances, means, classPriors));
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters, int numFeatures) {
    GaussianParameters gaussianParameters = computeParameters(parameters);

    DiscreteFactor means = gaussianParameters.means;
    DiscreteFactor variances = gaussianParameters.variances;
    DiscreteFactor priors = gaussianParameters.priors;

    StringBuilder sb = new StringBuilder();
    sb.append(priors.describeAssignments(priors.getMostLikelyAssignments(numFeatures)));
    
    DiscreteFactor falseFactor = TableFactor.pointDistribution(valueVars, valueVars.outcomeArrayToAssignment("F")).product(-1.0);
    DiscreteFactor trueFactor = TableFactor.pointDistribution(valueVars, valueVars.outcomeArrayToAssignment("T"));
    DiscreteFactor reweighting = trueFactor.add(falseFactor);
    DiscreteFactor meanDeltas = means.product(reweighting).marginalize(valueVars.getVariableNums());

    sb.append(meanDeltas.describeAssignments(meanDeltas.getMostLikelyAssignments(numFeatures)));

    /*
    List<Assignment> assignments = meanDeltas.getMostLikelyAssignments(numFeatures);
    for (Assignment assignment : assignments) {
      sb.append(assignment + ": " + means.getUnnormalizedProbability(assignment) 
          + " / " + variances.getUnnormalizedProbability(assignment));
      sb.append("\n");
    }
    */

    return sb.toString();
  }

  @Override
  public void incrementSufficientStatistics(SufficientStatistics parameters,
      SufficientStatistics currentParameters, Tensor assignment,
      double multiplier) {
    ListSufficientStatistics parameterList = parameters.coerceToList();

    TensorSufficientStatistics globalFeatureSums = ((TensorSufficientStatistics) parameterList
        .getStatisticByName(GLOBAL_MEAN_PARAMETERS));
    TensorSufficientStatistics globalFeatureSquareSums = ((TensorSufficientStatistics) parameterList
        .getStatisticByName(GLOBAL_VARIANCE_PARAMETERS)); 
    TensorSufficientStatistics classFeatureSums = ((TensorSufficientStatistics) parameterList
        .getStatisticByName(MEAN_PARAMETERS));
    TensorSufficientStatistics priorCounts = ((TensorSufficientStatistics) parameterList
        .getStatisticByName(PRIOR_PARAMETERS));
    
    Tensor featureWeights = domainVectors.getWeights();
    Tensor featureSumIncrement = featureWeights.elementwiseProduct(assignment)
        .sumOutDimensions(variableVars.getVariableNums());
    classFeatureSums.increment(featureSumIncrement, multiplier);
    globalFeatureSums.increment(featureSumIncrement.sumOutDimensions(
        valueVars.getVariableNums()), multiplier);
    
    Tensor featureSquareSumIncrement = featureWeights.elementwiseProduct(featureWeights)
        .elementwiseProduct(assignment).sumOutDimensions(variableVars.getVariableNums());
    // For per-class variances:
    // featureSquareSums.increment(featureSquareSumIncrement, multiplier);
    globalFeatureSquareSums.increment(featureSquareSumIncrement.sumOutDimensions(
        valueVars.getVariableNums()), multiplier);

    Tensor priorIncrement = assignment.sumOutDimensions(variableVars.getVariableNums());
    priorCounts.increment(priorIncrement, multiplier);
  }

  @Override
  public void incrementSufficientStatistics(SufficientStatistics parameters, 
      SufficientStatistics currentParameters, ParallelFactors probabilities, double multiplier) {
    throw new UnsupportedOperationException();
  }

  private GaussianParameters computeParameters(SufficientStatistics parameters) {
    ListSufficientStatistics parameterList = parameters.coerceToList();
    
    DiscreteFactor globalFeatureSums = ((TensorSufficientStatistics) parameterList
        .getStatisticByName(GLOBAL_MEAN_PARAMETERS)).getFactor();
    DiscreteFactor globalFeatureSumSquares = ((TensorSufficientStatistics) parameterList
        .getStatisticByName(GLOBAL_VARIANCE_PARAMETERS)).getFactor();
    DiscreteFactor priorCounts = ((TensorSufficientStatistics) parameterList
        .getStatisticByName(PRIOR_PARAMETERS)).getFactor();

    double numExamples = priorCounts.marginalize(priorCounts.getVars()).getUnnormalizedProbability();
    DiscreteFactor globalMean = globalFeatureSums.product(1.0 / numExamples);
    DiscreteFactor globalVariance = globalFeatureSumSquares.product(1.0 / numExamples).add(
        globalMean.product(globalMean).product(-1.0));

    DiscreteFactor classFeatureSums = ((TensorSufficientStatistics) parameterList
        .getStatisticByName(MEAN_PARAMETERS)).getFactor();

    DiscreteFactor classMeans = classFeatureSums.product(priorCounts.inverse());
    DiscreteFactor classVariances = TableFactor.unity(valueVars).outerProduct(globalVariance);
    DiscreteFactor classPriors = priorCounts.product(1.0 / numExamples);

    return new GaussianParameters(classMeans, classVariances, classPriors);
  }
  
  private static class GaussianParameters {
    public DiscreteFactor means; 
    public DiscreteFactor variances;
    public DiscreteFactor priors;
    
    public GaussianParameters(DiscreteFactor means, DiscreteFactor variances, DiscreteFactor priors) {
      this.means = means;
      this.variances = variances;
      this.priors = priors;
    }
  }
}
