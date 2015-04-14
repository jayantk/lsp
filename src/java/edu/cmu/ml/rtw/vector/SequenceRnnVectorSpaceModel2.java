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
public class SequenceRnnVectorSpaceModel2 implements VectorSpaceModelInterface {
  
  private final int dimensionality;

  public SequenceRnnVectorSpaceModel2(int dimensionality) {
    this.dimensionality = dimensionality;
  }
  
  @Override
  public Expression getFormula(GroundingExample example) {
    List<String> words = example.getWords().get(0);
    String expressionString = getExpressionString(words);

    expressionString = "(op:logistic (op:matvecmul t:catFeatures;" + dimensionality + ":output_params " + expressionString + "))";

    return ExpressionParser.lambdaCalculus().parseSingleExpression(expressionString);
  }
  
  private String getExpressionString(List<String> words) {
    String word = words.get(0);
    if (words.size() == 1) {
      return "t:" + dimensionality + ":" + word;
    } else {
      String rest = getExpressionString(words.subList(1, words.size()));
      String W1 = "t:" + dimensionality + ";" + dimensionality + ":W1";
      String W2 = "t:" + dimensionality + ";" + dimensionality + ":W2";
      String cur_word = "t:" + dimensionality + ":" + word;

      return "(op:logistic (op:add (op:matvecmul " + W1 + " " + cur_word + ") (op:matvecmul " + W2 + " " + rest + ")))";
    }
  }
}
