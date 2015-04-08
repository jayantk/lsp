package edu.cmu.ml.rtw.vector;

import java.util.List;

import com.jayantkrish.jklol.ccg.lambda.Expression;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;

import edu.cmu.ml.rtw.users.jayantk.grounding.GroundingExample;

/**
 * Sequence RNN of the form:
 * 
 * w_i = vector for i'th word
 * M = RNN parameter matrix
 * b = RNN bias parameter
 * h_i = f(w_i + b +  M h_(i-1) ) 
 * 
 * @author jayantk
 */
public class SequenceRnnVectorSpaceModel implements VectorSpaceModelInterface {
  
  private final int dimensionality;

  public SequenceRnnVectorSpaceModel(int dimensionality) {
    this.dimensionality = dimensionality;
  }
  
  @Override
  public Expression getFormula(GroundingExample example) {
    List<String> words = example.getWords().get(0);
    String expressionString = getExpressionString(words);
    
    String domainCategoryFeaturesName = VectorModelTrainer.getCategoryTensorName(example.getDomainName());
    expressionString = "(op:logistic (op:matvecmul " + domainCategoryFeaturesName + "(op:tanh (op:matvecmul t:catFeatures;"
        + dimensionality + ":output_params " + expressionString + "))))";

    return ExpressionParser.lambdaCalculus().parseSingleExpression(expressionString);
  }
  
  private String getExpressionString(List<String> words) {
    String word = words.get(0);
    String wordParam = "t:" + dimensionality + ":" + word;
    String globalParams = "t:" + dimensionality + ";" + dimensionality  + ":special_global_params";
    String biasParams = "t:" + dimensionality + ":special_global_bias";

    if (words.size() == 1) {
      return "(op:tanh (op:add " + wordParam + " " + biasParams + "))";
    } else {
      String rest = getExpressionString(words.subList(1, words.size()));
      return "(op:tanh (op:add " + wordParam + " " + biasParams + " (op:matvecmul "
          + globalParams + " " + rest + ")))";
    }
  }
}
