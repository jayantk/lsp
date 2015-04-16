
package edu.cmu.ml.rtw.vector;

import java.util.List;
import java.util.Tree;
import java.util.Pair;

import com.jayantkrish.jklol.ccg.lambda.Expression;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;

import edu.cmu.ml.rtw.users.jayantk.grounding.GroundingExample;

public class Mvrnn implements VectorSpaceModelInterface {
  
  private final int n; // vs dimension
	private final int r; // For n reduction

  public Mvrnn(int n, int r) {
    this.n = n;
		this.r = r;
  }
  
  @Override
  public Expression getFormula(GroundingExample example) {
    List<String> words = example.getWords().get(0);
    String expressionString = getExpressionString(words);

    String domainCategoryFeaturesName = VectorModelTrainer.getCategoryTensorName(example.getDomainName());
    expressionString = "(op:logistic (op:matvecmul " + domainCategoryFeaturesName + " (op:matvecmul t:catFeatures;" + n + ":output_params " + expressionString + ")))";

    return ExpressionParser.lambdaCalculus().parseSingleExpression(expressionString);
  }

  private String getExpressionString(Tree<String> t) {
		Pair<String, String> p = getExpressionStringPair(t.root.left, t.root.right);
		return "(op:matvecmul " + p.getRight() + " " + p.getLeft() + ")";
  }

	private Pair<String, String> getExpressionStringPair(Node<String> left, Node<String> right) {
		String a = "";
		String b = "";
		String A = "";
		String B = "";

		if (left.data == "") {
			Pair<String, String> p = getExpressionStringPair(left.left, left.right);
			a = p.getLeft();
			A = p.getRight();
		}
		else {
			// Leaf
			String aw = left.data;
			a = "t:" + n + ":" + aw;
			String DA = "(op:diag t:" + n + ":" + aw + ")";
			String AU = "t:" + n + ";" + r + ":" + aw;
			String AV = "t:" + r + ";" + n + ":" + aw;
			A = "(op:add (op:matvecmul " + AU + " " + AV + ") " + DA + ")";
		}

		if (right.data == "") {
			Pair<String, String> p = getExpressionStringPair(right.left, right.right);
			b = p.getLeft();
			B = p.getRight();
		}
		else {
			// Leaf
			String bw = right.data;
			b = "t:" + n + ":" + bw;
			String DB = "(op:diag t:" + n + ":" + bw + ")";
			String BU = "t:" + n + ";" + r + ":" + bw;
			String BV = "t:" + r + ";" + n + ":" + bw;
			B = "(op:add (op:matvecmul " + BU + " " + BV + ") " + DB + ")";
		}

		String WA = "t:" + n + ";" + n + ":WA";
		String WB = "t:" + n + ";" + n + ":WB";

		String Ba = "(op:matvecmul " + B + " " + a;
		String Ab = "(op:matvecmul " + A + " " + b;

		String WmA = "t:" + n + ";" + n + ":WA";
		String WmB = "t:" + n + ";" + n + ":WB";

		String p = "(op:tanh (op:add (op:matvecmul "+WA+" "+Ba+") (op:matvecmul "+WB+" "+Ab+")))";
		String P = "(op:tanh (op:add (op:matvecmul "+WmA+" "+A+") (op:matvecmul "+WmB+" "+B+")))";

		return new Pair<String, String>(p, P);
	}
}
