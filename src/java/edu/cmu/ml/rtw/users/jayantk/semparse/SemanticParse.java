package edu.cmu.ml.rtw.users.jayantk.semparse;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

import com.google.common.base.Preconditions;

import edu.cmu.ml.rtw.users.jayantk.semparse.Lexicon.TypeRaisingRule;
import edu.cmu.ml.rtw.users.jayantk.semparse.SemanticParser.RuleType;

/**
 * A semantic parse is a predicate-argument structure extracted from a sentence.
 * A parse represents a conjunctive formula in first-order logic, where each
 * {@code SemanticParse} node represents a single predicate, and the arguments
 * of each node represent shared variables. Variables in the formula are assumed
 * to be existentially quantified.
 * 
 * {@code SemanticParse}s are constructed by recursively adding arguments to
 * existing parse nodes using {@link #addArgument}. This process propagates
 * value constraints from the arguments to their parent parse. After the tree is
 * fully constructed, one must call
 * {@link #propagateArgumentConstraintsDownward()} to propagate the value
 * constraints from parents to their arguments.
 * 
 * Semantic parses are immutable.
 * 
 * @author jayantk
 */
public class SemanticParse implements Serializable{

  private final Type predicate;
  private final SemanticPredicate semanticType;
  private final List<Object> predicateTrigger;

  private final SemanticParse headParse;
  // Argument to this parse.
  private final SemanticParse childParse;
  // How the argument is combined with this
  private final RuleType argumentCombination;

  public static final SemanticParse EMPTY = new SemanticParse(null, null, null, null, null, null);
  public static AtomicType NOUN = new AtomicType("N", Collections.<AtomicType> emptyList());

  protected SemanticParse(Type predicate, SemanticPredicate semanticType, List<Object> predicateTrigger,
      SemanticParse headParse, SemanticParse childParse, RuleType argumentCombination) {
    this.predicate = predicate;
    this.semanticType = semanticType;
    this.predicateTrigger = predicateTrigger;
    this.headParse = headParse;
    this.childParse = childParse;
    this.argumentCombination = argumentCombination;
  }

  /**
   * Creates a semantic parse node containing {@code predicate}, with possible
   * values {@code values}.
   * 
   * @param predicate
   * @param values
   * @return
   */
  public static SemanticParse createFromType(Type predicate,
      SemanticPredicate semanticType, List<Object> predicateTrigger) {
    Preconditions.checkNotNull(predicate);
    Preconditions.checkNotNull(semanticType);
    Preconditions.checkNotNull(predicateTrigger);
    return new SemanticParse(predicate, semanticType, predicateTrigger, null, null, null);
  }

  /**
   * Gets the syntactic type of the current node, e.g., (N\N). The syntactic
   * type of the parse may encode some component of its semantics, for example
   * the required semantic type of each argument (e.g, argument 1 must be an
   * athlete).
   * 
   * @return
   */
  public Type getSyntacticType() {
    return predicate;
  }

  /**
   * Gets the predicate-argument structure of the semantic parse that is headed
   * at this node. The returned predicate is the semantic predicate for the root
   * node; the entire substructure may be traversed by following its argument
   * pointers.
   * 
   * @return
   */
  public SemanticPredicate getSemanticPredicate() {
    return semanticType;
  }

  public List<Object> getTrigger() {
    return predicateTrigger;
  }

  public List<Object> getHeadTrigger() {
    if (childParse == null || headParse == null || predicate instanceof DirectedFunctionType) {
      return getTrigger();
    } else {
      if (headParse.getRuleType() != null && headParse.getRuleType().equals(RuleType.TYPE_RAISE)) {
        // Make sure the rule works for noun compounds.
        return getTrigger();
      } else if (((TupleType) predicate).getSyntacticType().hasAncestor(NOUN)) {
        return childParse.getHeadTrigger();
      } else {
        return getTrigger();
      }
    }
  }

  public SemanticParse getHead() {
    return headParse;
  }

  public SemanticParse getChild() {
    return childParse;
  }

  /**
   * Gets how the argument to {@code this} is combined with the predicate in
   * {@code this}. For example, the two parses may be composed. See
   * {@link RuleType} for more details.
   * 
   * @return
   */
  public RuleType getRuleType() {
    return argumentCombination;
  }

  public SemanticParse apply(SemanticParse other) {
    Preconditions.checkState(predicate instanceof DirectedFunctionType, "Tried applying to %s", predicate);
    DirectedFunctionType predicateAsFunction = (DirectedFunctionType) predicate;
    SemanticPredicate newSemanticType = semanticType.apply(other.getSemanticPredicate());

    return new SemanticParse(predicateAsFunction.getReturnType(),
        newSemanticType, predicateTrigger, this, other, RuleType.APPLICATION);
  }

  public SemanticParse typeRaise(TypeRaisingRule rule) {
    Preconditions.checkArgument(predicate.hasAncestor(rule.getInputType()));
    return new SemanticParse(rule.getOutputType(), rule.getSemanticType().apply(semanticType),
        predicateTrigger, this, null, RuleType.TYPE_RAISE);
  }

  public SemanticParse typeRaiseAndApply(TypeRaisingRule rule, SemanticParse other) {
    Preconditions.checkArgument(predicate.hasAncestor(rule.getInputType()));
    return typeRaise(rule).apply(other);

    /*
     * DirectedFunctionType outputAsFunction = (DirectedFunctionType)
     * rule.getOutputType(); SemanticType newSemanticType =
     * rule.getSemanticType(
     * ).apply(semanticType).apply(other.getSemanticType());
     * 
     * return new SemanticParse(outputAsFunction.getReturnType(),
     * newSemanticType, predicateTrigger, this, other, RuleType.TYPE_RAISE);
     */
  }

  @Override
  public String toString() {
    if (this == SemanticParse.EMPTY) {
      return "EMPTY";
    }
    StringBuilder builder = new StringBuilder();
    toStringHelper(builder, null);
    return builder.toString();
  }

  private void toStringHelper(StringBuilder builder, List<Object> parentTrigger) {
    if (parentTrigger == null || !parentTrigger.equals(predicateTrigger)) {
      builder.append(predicateTrigger);
      builder.append(":");
      builder.append(predicate.toString());
      builder.append(":");
      builder.append(semanticType.toShortString());
    }
    if (headParse != null) {
      headParse.toStringHelper(builder, predicateTrigger);
    }
    if (childParse != null) {
      builder.append("(");
      childParse.toStringHelper(builder, predicateTrigger);
      builder.append(")");
    }
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((argumentCombination == null) ? 0 : argumentCombination.hashCode());
    result = prime * result + ((childParse == null) ? 0 : childParse.hashCode());
    result = prime * result + ((headParse == null) ? 0 : headParse.hashCode());
    result = prime * result + ((predicate == null) ? 0 : predicate.hashCode());
    result = prime * result + ((predicateTrigger == null) ? 0 : predicateTrigger.hashCode());
    result = prime * result + ((semanticType == null) ? 0 : semanticType.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    SemanticParse other = (SemanticParse) obj;
    if (argumentCombination != other.argumentCombination)
      return false;
    if (childParse == null) {
      if (other.childParse != null)
        return false;
    } else if (!childParse.equals(other.childParse))
      return false;
    if (headParse == null) {
      if (other.headParse != null)
        return false;
    } else if (!headParse.equals(other.headParse))
      return false;
    if (predicate == null) {
      if (other.predicate != null)
        return false;
    } else if (!predicate.equals(other.predicate))
      return false;
    if (predicateTrigger == null) {
      if (other.predicateTrigger != null)
        return false;
    } else if (!predicateTrigger.equals(other.predicateTrigger))
      return false;
    if (semanticType == null) {
      if (other.semanticType != null)
        return false;
    } else if (!semanticType.equals(other.semanticType))
      return false;
    return true;
  }
}
