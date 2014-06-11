package edu.cmu.ml.rtw.users.jayantk.grounding;

import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.tensor.Tensor;

public interface GroundingFamily {
  
  public DiscreteVariable getFeatureVariable();

  public VariableNumMap getIndexVariables();
  
  public VariableNumMap getValueVariables();
  
  public DiscreteFactor getFeatureVectors();

  public ParallelFactors getFactorFromParameters(SufficientStatistics parameters);

  public SufficientStatistics getNewSufficientStatistics();

  public String getParameterDescription(SufficientStatistics params, int numFeatures);

  /**
   * Increments the sufficient statistics based on a hard assignment (setting of values to all variables). 
   * @param parameters
   * @param assignment
   * @param multiplier
   */
  public void incrementSufficientStatistics(SufficientStatistics gradient, SufficientStatistics parameters,
      Tensor assignment, double multiplier);

  public void incrementSufficientStatistics(SufficientStatistics gradient, SufficientStatistics parameters,
      ParallelFactors probabilities, double multiplier);

}