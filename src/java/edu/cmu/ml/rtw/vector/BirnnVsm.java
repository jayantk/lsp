package edu.cmu.ml.rtw.vector;

import java.util.List;

import com.jayantkrish.jklol.ccg.lambda.Expression;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;

import edu.cmu.ml.rtw.users.jayantk.grounding.GroundingExample;

public class BirnnVsm implements VectorSpaceModelInterface {
  
  private final int dimensionality;

  public BirnnVsm(int dimensionality) {
    this.dimensionality = dimensionality;
  }
  
  @Override
  public Expression getFormula(GroundingExample example) {
    List<String> words = example.getWords().get(0);
    String expressionString = getExpressionStringFirstLast(words);

    String domainCategoryFeaturesName = VectorModelTrainer.getCategoryTensorName(example.getDomainName());
    expressionString = "(op:logistic (op:matvecmul " + domainCategoryFeaturesName + "(op:tanh (op:matvecmul t:catFeatures;"
        + dimensionality + ":output_params " + expressionString + "))))";

    return ExpressionParser.lambdaCalculus().parseSingleExpression(expressionString);
  }

  private String getExpressionStringFirstLast(List<String> words) {
    String word = words.get(0);
    if (words.size() == 1) {
      return "t:" + dimensionality + ":" + word;
    } else {
		String s1 = getForwardExpressionString(words);
		String s2 = getBackwardExpressionString(words);
		String W1 = "t:" + dimensionality + ";" + dimensionality + ":Wf";
		String W2 = "t:" + dimensionality + ";" + dimensionality + ":Wb";

      return "(op:logistic (op:add (op:matvecmul " + W1 + " " + s1 + ") " + 
		  "(op:matvecmul " + W2 + " " + s2 + ")))";
    }
  }

  private String getForwardExpressionString(List<String> words) {
    String word = words.get(0);
	  String W1 = "t:" + dimensionality + ";" + dimensionality + ":W1_f";
    if (words.size() == 1) {
			String bias = "t:" + dimensionality + ":b_f1";
      return "(op:logistic (op:add (op:matvecmul " + W1 + " t:" + dimensionality + ":" + word + ")" + bias + "))";
    } else {
      String rest = getForwardExpressionString(words.subList(1, words.size()));
	  String W2 = "t:" + dimensionality + ";" + dimensionality + ":W2_f";
		String bias = "t:" + dimensionality + ":b_f2";
	  String cur_word = "t:" + dimensionality + ":" + word;

      return "(op:logistic (op:add (op:matvecmul " + W1 + " " + cur_word + ") (op:matvecmul " + W2 + " " + rest + ") " + bias + "))";
    }
  }

  private String getBackwardExpressionString(List<String> words) {
    String word = words.get(words.size()-1);
	  String W1 = "t:" + dimensionality + ";" + dimensionality + ":W1_b";
    if (words.size() == 1) {
			String bias = "t:" + dimensionality + ":b_b1";
      return "(op:logistic (op:add (op:matvecmul " + W1 + " t:" + dimensionality + ":" + word + ")" + bias + "))";
    } 
		else {
      String rest = getBackwardExpressionString(words.subList(0, words.size()-1));
			String W2 = "t:" + dimensionality + ";" + dimensionality + ":W2_b";
			String bias = "t:" + dimensionality + ":b_b2";
			String cur_word = "t:" + dimensionality + ":" + word;

      return "(op:logistic (op:add (op:matvecmul " + W1 + " " + cur_word + ") (op:matvecmul " + W2 + " " + rest + ") " + bias + "))";
    }
  }
}
