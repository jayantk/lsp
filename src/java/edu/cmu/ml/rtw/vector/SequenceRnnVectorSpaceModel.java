package edu.cmu.ml.rtw.vector;

import java.util.List;

import com.google.common.base.Preconditions;
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
  private final String sigmoidOp;

  public SequenceRnnVectorSpaceModel(int dimensionality, String sigmoidOp) {
    this.dimensionality = dimensionality;
    this.sigmoidOp = Preconditions.checkNotNull(sigmoidOp);
  }

  @Override
  public Expression getFormula(GroundingExample example) {
    List<String> words = example.getWords().get(0);
    String expressionString = getExpressionString(words);
    
    String domainCategoryFeaturesName = VectorModelTrainer.getCategoryTensorName(example.getDomainName());
    //expressionString = "(op:logistic (op:matvecmul " + domainCategoryFeaturesName + " (op:matvecmul t:catFeatures;" + dimensionality + ":output_params " + expressionString + ")))";
    expressionString = "(op:logistic (op:matvecmul " + domainCategoryFeaturesName + " (op:matvecmul t:catFeatures;" + dimensionality + ":output_params " + expressionString + ")))";

    return ExpressionParser.lambdaCalculus().parseSingleExpression(expressionString);
  }

  private String getExpressionString(List<String> words) {
    String word = words.get(0);
    String wordParam = "t:" + dimensionality + ":" + word;
    String transitionParams = "t:" + dimensionality + ";" + dimensionality  + ":transition_params";
    String transitionBiasParams = "t:" + dimensionality + ":transition_bias";
    String wordMatrixParams = "t:" + dimensionality + ";" + dimensionality  + ":word_matrix_params";
    String wordBiasParams = "t:" + dimensionality + ":word_bias";

    //String wordSeqString = "(op:add (op:matvecmul " + wordMatrixParams + " " + wordParam + ") " + wordBiasParams + ")";
    String wordSeqString = wordParam;
    
    if (words.size() == 1) {
      return wordSeqString;
    } else {
      String rest = getExpressionString(words.subList(1, words.size()));
      return "(op:tanh (op:add " + wordSeqString + " " + transitionBiasParams + " (op:matvecmul " + transitionParams + " " + rest + ")))";
    }
  }
}
