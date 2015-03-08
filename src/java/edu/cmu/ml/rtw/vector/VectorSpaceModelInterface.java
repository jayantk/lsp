package edu.cmu.ml.rtw.vector;

import com.jayantkrish.jklol.ccg.lambda.Expression;

import edu.cmu.ml.rtw.users.jayantk.grounding.GroundingExample;

/**
 * Interface representing a technique for mapping from {@link GroundingExample}s
 * (i.e., text strings) to a vector. Produces a formula containing optimizable
 * parameters for each example.
 * 
 * @author jayantk
 */
public interface VectorSpaceModelInterface {

  public Expression getFormula(GroundingExample example);
}
