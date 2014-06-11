package edu.cmu.ml.rtw.users.jayantk.grounding;

import java.io.Serializable;
import java.util.List;

import com.jayantkrish.jklol.ccg.lambda.Expression;
import com.jayantkrish.jklol.tensor.Tensor;

/**
 * Represents training data for the grounded language learning model. Grounding
 * examples can represent one of two types of training data:
 * <ol>
 * <li>A series of words in natural language, with an accompanying expected
 * grounding
 * <li>A semantic predicate with an accompanying grounding.
 * <ol>
 * 
 * The former type of data is the expected weakly-supervised data, while the
 * second type of data is used for fully-supervised training. The
 * {@link #hasObservedRelation()} method determines which of the two classes
 * this example falls into.
 * 
 * @author jayantk
 */
public class GroundingExample implements Serializable {

  private final List<List<String>> words;
    private final double[] wordScores;
  private final String relationName;
  private final Tensor groundingTensor;
  private final String domainName;

  // For curriculum learning. Lower levels get trained on first.
  private final int curriculumLevel;

    // Annotated logical form
    private final Expression logicalForm;

  /**
   * Create a grounding example consisting of {@code words} paired with
   * {@code groundingTensor}.
   * 
   * @param words
   * @param wordScores
   * @param groundingTensor
   * @param domainName
   * @param curriculumLevel
   */
    public GroundingExample(List<List<String>> words, double[] wordScores,Tensor groundingTensor,
			    String domainName, int curriculumLevel, Expression logicalForm) {
    this.words = words;
    this.wordScores = wordScores;
    this.relationName = null;
    this.groundingTensor = groundingTensor;
    this.domainName = domainName;
    this.curriculumLevel = curriculumLevel;
    this.logicalForm = logicalForm;
  }

  /**
   * Create a grounding example that observes {@code groundingTensor} as the
   * groundings for the predicate {@code relationName}.
   * 
   * @param relationName
   * @param groundingTensor
   * @param domainName
   * @param curriculumLevel
   */
    public GroundingExample(String relationName, Tensor groundingTensor, String domainName, int curriculumLevel,
			    Expression logicalForm) {
    this.words = null;
    this.wordScores = null;
    this.relationName = relationName;
    this.groundingTensor = groundingTensor;
    this.domainName = domainName;
    this.curriculumLevel = curriculumLevel;
    this.logicalForm = logicalForm;
  }

  public int getCurriculumLevel() {
    return curriculumLevel;
  }

  public boolean hasObservedRelation() {
    return relationName != null;
  }

  public String getObservedRelationName() {
    return relationName;
  }

    public boolean hasLogicalForm() {
	return logicalForm != null;
    }

    public Expression getLogicalForm() {
	return logicalForm;
    }

  /**
   * Gets the possible word sequences in the input. 
   * 
   * @return
   */
  public List<List<String>> getWords() {
    return words;
  }

    public double[] getWordScores() {
	return wordScores;
    }

  /**
   * Gets the expected grounding for the words.
   * 
   * @return
   */
  public Tensor getGrounding() {
    return groundingTensor;
  }

  /**
   * Gets the domain to which this example belongs.
   * 
   * @return
   */
  public String getDomainName() {
    return domainName;
  }
}
