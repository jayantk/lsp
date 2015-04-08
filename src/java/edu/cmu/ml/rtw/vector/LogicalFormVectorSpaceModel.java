package edu.cmu.ml.rtw.vector;

import java.util.List;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.lambda.ApplicationExpression;
import com.jayantkrish.jklol.ccg.lambda.CommutativeOperator;
import com.jayantkrish.jklol.ccg.lambda.Expression;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.ccg.lambda.LambdaExpression;
import com.jayantkrish.jklol.ccg.lambda.QuantifierExpression;

import edu.cmu.ml.rtw.users.jayantk.grounding.GroundingExample;

public class LogicalFormVectorSpaceModel implements VectorSpaceModelInterface {

  public LogicalFormVectorSpaceModel() {
  }

  @Override
  public Expression getFormula(GroundingExample example) {
    String expressionString = getExpressionString(example.getLogicalForm(), example.getDomainName());
    expressionString = "(op:logistic " + expressionString + ")";
    return ExpressionParser.lambdaCalculus().parseSingleExpression(expressionString);
  }

  private String getExpressionString(Expression logicalForm, String domainName) {
    if (logicalForm == null) {
      String unknownFuncVector = "t:catFeatures" + ":UNKNOWN";
      String categoryTensorName = VectorModelTrainer.getCategoryTensorName(domainName);
      return "(op:matvecmul " + categoryTensorName + " " + unknownFuncVector + ")";
    } else if (logicalForm instanceof ApplicationExpression) {
      // TODO: relations.
      ApplicationExpression a = (ApplicationExpression) logicalForm;
      String funcName = a.getFunction().toString();
      String funcVector = "t:catFeatures" + ":" + funcName;

      String categoryTensorName = VectorModelTrainer.getCategoryTensorName(domainName);

      return "(op:matvecmul " + categoryTensorName + " " + funcVector + ")";
    } else if (logicalForm instanceof CommutativeOperator) {
      CommutativeOperator a = (CommutativeOperator) logicalForm;

      List<String> expressionStrings = Lists.newArrayList();
      for (Expression subexpression : a.getArguments()) {
        expressionStrings.add(getExpressionString(subexpression, domainName));
      }
      
      if (expressionStrings.size() > 1) {
        return "(op:add " + Joiner.on(" ").join(expressionStrings) + ")";
      } else {
        return expressionStrings.get(0);
      }
    } else if (logicalForm instanceof LambdaExpression) {
      return getExpressionString(((LambdaExpression) logicalForm).getBody(), domainName); 
    } else if (logicalForm instanceof QuantifierExpression) {
      return getExpressionString(((QuantifierExpression) logicalForm).getBody(), domainName); 
    } else {
      throw new IllegalArgumentException("Unsupported logical form:" + logicalForm);
    }
  }
}
