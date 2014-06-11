package edu.cmu.ml.rtw.users.jayantk.grounding;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.loglinear.DiscreteLogLinearFactor;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.models.parametric.TensorSufficientStatistics;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.tensor.TensorBase.KeyValue;
import com.jayantkrish.jklol.util.Assignment;

public class RelationGroundingFamily implements Serializable, GroundingFamily {

  private static final long serialVersionUID = 1L;
  private final DiscreteLogLinearFactor factor;
  
  private final VariableNumMap variableVars;
  private final VariableNumMap valueVars;

    private final boolean rescaleObjective;
  
    public RelationGroundingFamily(DiscreteLogLinearFactor factor, VariableNumMap variableVars, VariableNumMap valueVars, boolean rescaleObjective) {
    this.factor = Preconditions.checkNotNull(factor);
    this.variableVars = Preconditions.checkNotNull(variableVars);
    this.valueVars = Preconditions.checkNotNull(valueVars);
    this.rescaleObjective = rescaleObjective;

    Preconditions.checkArgument(factor.getVars().containsAll(variableVars));
    Preconditions.checkArgument(factor.getVars().containsAll(valueVars));
  }
  
  public DiscreteVariable getFeatureVariable() {
    return (DiscreteVariable) factor.getFeatureVariables().getOnlyVariable();
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
    return factor.getFeatureValues();
  }

  public List<Double> getFeatureWeights(TensorSufficientStatistics parameters){	  
	  List<Double> weightList = new ArrayList<Double>();
	  Iterator<KeyValue> iterator = parameters.get().keyValueIterator();	
	  while(iterator.hasNext()){
	    KeyValue k = iterator.next();
	    weightList.add(k.getValue());
	  }

	  return weightList;
  }
  
  /* (non-Javadoc)
   * @see edu.cmu.ml.rtw.users.jayantk.grounding.GroundingFamily#getFactorFromParameters(com.jayantkrish.jklol.models.parametric.SufficientStatistics)
   */
  @Override
  public ParallelFactors getFactorFromParameters(SufficientStatistics parameters) {   
	    DiscreteFactor groundingFactor = factor.getModelFromParameters(parameters);
	    Tensor weights = groundingFactor.getWeights().elementwiseLog();
	    if (rescaleObjective) {
		weights = weights.elementwiseProduct(1.0 / variableVars.getNumberOfPossibleAssignments());
	    }

    return new ParallelFactors(weights, variableVars, valueVars);
  }
    
  /* (non-Javadoc)
   * @see edu.cmu.ml.rtw.users.jayantk.grounding.GroundingFamily#getNewSufficientStatistics()
   */
  @Override
  public SufficientStatistics getNewSufficientStatistics() {
    return factor.getNewSufficientStatistics();
  }
  
  /* (non-Javadoc)
   * @see edu.cmu.ml.rtw.users.jayantk.grounding.GroundingFamily#getParameterDescription(com.jayantkrish.jklol.models.parametric.SufficientStatistics, int)
   */
  @Override
  public String getParameterDescription(SufficientStatistics params, int numFeatures) {
    return factor.getParameterDescription(params, numFeatures);
  }

  /* (non-Javadoc)
   * @see edu.cmu.ml.rtw.users.jayantk.grounding.GroundingFamily#incrementSufficientStatistics(com.jayantkrish.jklol.models.parametric.SufficientStatistics, com.jayantkrish.jklol.tensor.Tensor, double)
   */
  @Override
  public void incrementSufficientStatistics(SufficientStatistics gradient, 
                                            SufficientStatistics parameters, 
      Tensor assignment, double multiplier) {
    DiscreteFactor marginal = new TableFactor(factor.getVars(), assignment); 
    double myMultiplier = multiplier;
    if (rescaleObjective) {
	myMultiplier = multiplier / variableVars.getNumberOfPossibleAssignments();
    }

    factor.incrementSufficientStatisticsFromMarginal(gradient, parameters, marginal,
        Assignment.EMPTY, myMultiplier, 1.0);
  }
  
  /* (non-Javadoc)
   * @see edu.cmu.ml.rtw.users.jayantk.grounding.GroundingFamily#incrementSufficientStatistics(com.jayantkrish.jklol.models.parametric.SufficientStatistics, edu.cmu.ml.rtw.users.jayantk.grounding.ParallelFactors, double)
   */
  @Override
  public void incrementSufficientStatistics(SufficientStatistics gradient, 
                                            SufficientStatistics parameters, 
      ParallelFactors probabilities, double multiplier) {
    DiscreteFactor marginal = new TableFactor(factor.getVars(), probabilities.getTensor());

    double myMultiplier = multiplier;
    if (rescaleObjective) {
	myMultiplier = multiplier / variableVars.getNumberOfPossibleAssignments();
    }

    factor.incrementSufficientStatisticsFromMarginal(gradient, parameters, marginal,
        Assignment.EMPTY, myMultiplier, 1.0);
  }
}
