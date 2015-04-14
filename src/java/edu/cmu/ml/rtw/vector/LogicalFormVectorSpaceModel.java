package edu.cmu.ml.rtw.vector;

import java.util.List;
import java.util.Map;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.jayantkrish.jklol.ccg.lambda.ApplicationExpression;
import com.jayantkrish.jklol.ccg.lambda.CommutativeOperator;
import com.jayantkrish.jklol.ccg.lambda.Expression;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.ccg.lambda.LambdaExpression;
import com.jayantkrish.jklol.ccg.lambda.QuantifierExpression;
import com.jayantkrish.jklol.util.Pair;

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
    }
    System.out.println(logicalForm);
    
    logicalForm = logicalForm.simplify();
    Preconditions.checkArgument(logicalForm instanceof LambdaExpression);
    String arg = ((LambdaExpression) logicalForm).getArguments().get(0).getName();
    Expression body = ((LambdaExpression) logicalForm).getBody();
    
    if (body instanceof QuantifierExpression) {
      body = ((QuantifierExpression) body).getBody();
    }
    
    List<Expression> subexpressions = Lists.newArrayList();
    if (body instanceof CommutativeOperator) {
      subexpressions.addAll(((CommutativeOperator) body).getArguments());
    } else {
      subexpressions.add(body);
    }
    
    return getExpressionStringConjunction(subexpressions, arg, domainName);
  }

  private String getExpressionStringConjunction(List<Expression> subexpressions,
      String queryVar, String domainName) {
    Multimap<String, String> variableMap = HashMultimap.create();
    Map<Pair<String, String>, ApplicationExpression> variableRelationMap = Maps.newHashMap();
    Multimap<String, ApplicationExpression> variableCategoryMap = HashMultimap.create();
    
    for (Expression subexpression : subexpressions) {
      ApplicationExpression a = (ApplicationExpression) subexpression;

      if (a.getArguments().size() == 1) {
        String arg1 = a.getArguments().get(0).toString();
        
        variableCategoryMap.put(arg1, a);
      } else if (a.getArguments().size() == 2) {
        String arg1 = a.getArguments().get(0).toString();
        String arg2 = a.getArguments().get(1).toString();
    
        Pair<String, String> args = Pair.of(arg1, arg2);
        variableMap.put(arg1, arg2);
        variableMap.put(arg2, arg1);
        
        variableRelationMap.put(args, a);
      }
    }

    return buildExpressionFromGraph(null, queryVar, domainName, variableMap, variableRelationMap, variableCategoryMap);
  }
  
  private String buildExpressionFromGraph(String parent, String root,
      String domainName, Multimap<String, String> variableMap,
      Map<Pair<String,String>, ApplicationExpression> variableRelationMap,
      Multimap<String, ApplicationExpression> variableCategoryMap) {
    // System.out.println("buildexpression: " + root);
    
    List<String> expressionStrings = Lists.newArrayList();
    for (Expression subexpression : variableCategoryMap.get(root)) {
      ApplicationExpression a = (ApplicationExpression) subexpression;
      String funcName = a.getFunction().toString();

      if (a.getArguments().size() == 1) {
        String funcVector = "t:catFeatures" + ":" + funcName;
        String categoryTensorName = VectorModelTrainer.getCategoryTensorName(domainName);
        String expressionString = "(op:matvecmul " + categoryTensorName + " " + funcVector + ")";
        expressionStrings.add(expressionString);
      }
    }

    for (String child : variableMap.get(root)) {
      if (!child.equals(parent)) {
        expressionStrings.add(buildExpressionFromGraph(root, child, domainName,
            variableMap, variableRelationMap, variableCategoryMap));
      }
    }
    
    String baseExpressionString = null;
    if (expressionStrings.size() == 1) {
      baseExpressionString = expressionStrings.get(0);
    } else {
      baseExpressionString = "(op:add " + Joiner.on(" ").join(expressionStrings) + ")";
    }

    // System.out.println("base:" + baseExpressionString);
    String expressionString = null;
    if (parent != null) {
      Pair<String, String> argsInOrder = Pair.of(parent, root);
      ApplicationExpression a = variableRelationMap.get(argsInOrder);
      if (a != null) {
        String funcName = a.getFunction().toString();
        String funcVector = "t:relFeatures" + ":" + funcName;
        String relationTensorName = VectorModelTrainer.getRelationTensorName(domainName);
        expressionString = "(op:matvecmul (op:matvecmul " + relationTensorName + " " + funcVector + ") " + baseExpressionString + ")";
      }

      Pair<String, String> argsOutOfOrder = Pair.of(root, parent);
      a = variableRelationMap.get(argsOutOfOrder);
      if (a != null) {
        String funcName = a.getFunction().toString();
        String funcVector = "t:relFeatures" + ":" + funcName + "-inv";
        String relationTensorName = VectorModelTrainer.getRelationTensorName(domainName);
        expressionString = "(op:matvecmul (op:matvecmul " + relationTensorName + " " + funcVector + ") " + baseExpressionString + ")";
      }
    } else {
      expressionString = baseExpressionString;
    }
    Preconditions.checkState(expressionString != null);
    // System.out.println("expressionString:" + expressionString);

    return expressionString;
  }
}
