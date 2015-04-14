
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

    expressionString = "(op:logistic (op:matvecmul t:catFeatures;" + n + ":output_params " + expressionString + "))";

    return ExpressionParser.lambdaCalculus().parseSingleExpression(expressionString);
  }

  private Pair<String, String> getExpressionStringVector(Tree<String> t) {
		return getExpressionString(t.root.left, t.root.right);
  }

	private Pair<String, String> getExpressionString(Node<String> left, Node<String> right) {
		String a = "";
		String b = "";
		String A = "";
		String B = "";

		if (left.data == "") {
			Pair<String, String> p = getExpressionString(left.left, left.right);
			a = p.getLeft();
			A = p.getRight();
		}
		else {
			a = "t:" + n + ":" + aw;
			String AU = "t:" + n + ";" + r + ":" + aw;
			String AV = "t:" + r + ";" + n + ":" + aw;
			A = "(op:matvecmul AU AV)";
		}

		if (right.data == "") {
			Pair<String, String> p = getExpressionString(right.left, right.right);
			b = p.getLeft();
			B = p.getRight();
		}
		else {
			b = "t:" + n + ":" + bw;
			String BU = "t:" + n + ";" + r + ":" + bw;
			String BV = "t:" + r + ";" + n + ":" + bw;
			B = "(op:matvecmul BU BV)";
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
