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
		String s1 = getForwardExpressionString(words);
		String s2 = getBackwardExpressionString(words);

      return "(op:logistic " + s1 + " " + s2 + ")";
    }
  }
  
  private String getForwardExpressionString(List<String> words) {
    String word = words.get(0);
    if (words.size() == 1) {
      return "t:" + dimensionality + ":" + word;
    } else {
      String rest = getForwardExpressionString(words.subList(1, words.size()));
	  String W1 = "t:" + dimensionality + ";" + dimensionality + ":W1_f";
	  String W2 = "t:" + dimensionality + ";" + dimensionality + ":W2_f";
	  String cur_word = "t:" + dimensionality + ":" + word;

      return "(op:logistic (op:add (op:matvecmul " + W1 + " " + cur_word + ") (op:matvecmul " + W2 + " " + rest + ")))";
    }
  }

  private String getBackwardExpressionString(List<String> words) {
    String word = words.get(words.size()-1);
    if (words.size() == 1) {
      return "t:" + dimensionality + ":" + word;
    } else {
      String rest = geBackwardExpressionString(words.subList(0, words.size()-1));
	  String W1 = "t:" + dimensionality + ";" + dimensionality + ":W1_b";
	  String W2 = "t:" + dimensionality + ";" + dimensionality + ":W2_b";
	  String cur_word = "t:" + dimensionality + ":" + word;

      return "(op:logistic (op:add (op:matvecmul " + W1 + " " + cur_word + ") (op:matvecmul " + W2 + " " + rest + ")))";
    }
  }
}
