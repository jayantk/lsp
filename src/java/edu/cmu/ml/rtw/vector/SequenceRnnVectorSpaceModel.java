package edu.cmu.ml.rtw.vector;

import java.util.List;

import com.jayantkrish.jklol.ccg.lambda.Expression;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;

import edu.cmu.ml.rtw.users.jayantk.grounding.GroundingExample;

public class SequenceRnnVectorSpaceModel implements VectorSpaceModelInterface {
  
  private final int dimensionality;

  public SequenceRnnVectorSpaceModel(int dimensionality) {
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
      return "(op:logistic (op:matvecmul t:" + dimensionality + ";" + dimensionality 
          + ":special_global_params (op:add t:" + dimensionality + ":" + word + " " + rest + ")))";
    }
  }
}
