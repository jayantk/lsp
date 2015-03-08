package edu.cmu.ml.rtw.vector;

import java.util.List;

import com.jayantkrish.jklol.ccg.lambda.Expression;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;

import edu.cmu.ml.rtw.users.jayantk.grounding.GroundingExample;

public class SequenceRnnVectorSpaceModel implements VectorSpaceModelInterface {

  @Override
  public Expression getFormula(GroundingExample example) {
    List<String> words = example.getWords().get(0);
    String expressionString = getExpressionString(words);

    return ExpressionParser.lambdaCalculus().parseSingleExpression(expressionString);
  }
  
  private String getExpressionString(List<String> words) {
    String word = words.get(0).toLowerCase();
    if (words.size() == 1) {
      return "t1:" + word;
    } else {
      String rest = getExpressionString(words.subList(1, words.size()));
      return "(op:add t1:" + word + " " + rest + ")";
    }
  }
}
