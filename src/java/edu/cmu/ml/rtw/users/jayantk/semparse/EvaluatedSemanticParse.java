package edu.cmu.ml.rtw.users.jayantk.semparse;

import java.util.Map;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;

/**
 * Augments a {@code SemanticParse} with additional data at each node. This
 * class is used to ground / evaluate the variables in a semantic parse.
 * 
 * @param T augmented data type.
 * 
 * @author jayantk
 */
public class EvaluatedSemanticParse<T> {

  private final SemanticPredicate semanticType;
  private final Map<Integer, EvaluatedSemanticParse<T>> arguments;
  private final T data;

  protected EvaluatedSemanticParse(SemanticPredicate semanticType, 
      Map<Integer, EvaluatedSemanticParse<T>> arguments, T data) {
    this.semanticType = Preconditions.checkNotNull(semanticType);
    this.arguments = Preconditions.checkNotNull(arguments);
    this.data = data;
  }
  
  public SemanticPredicate getSemanticType() {
    return semanticType;
  }

  public T getData() {
    return data;
  }

  public Map<Integer, EvaluatedSemanticParse<T>> getArguments() {
    return arguments;
  }

  /**
   * Evaluates the semantic parse. This performs constraint propagation,
   * searching over possible values for the variables at each node of the parse
   * tree.
   * 
   * @param parse
   * @param combinator
   * @return
   */
  public static <T> EvaluatedSemanticParse<T> fromSemanticType(SemanticPredicate relationSemanticType,
      SemanticParseNodeCombinator<T> combinator) {
    EvaluatedSemanticParse<T> upwardPass = EvaluatedSemanticParse.<T> fromSemanticTypeHelper(relationSemanticType, combinator);
    // This assumes there are no constraints imposed on the root node's values
    // from elsewhere.
    return upwardPass.propagateArgumentConstraintsDownward(combinator, upwardPass.getData());
  }

  private static <T> EvaluatedSemanticParse<T> fromSemanticTypeHelper(SemanticPredicate semanticType,
      SemanticParseNodeCombinator<T> combinator) {
    // Evaluate every subparse.
    Map<Integer, EvaluatedSemanticParse<T>> evaluatedParses = Maps.newHashMap();
    Map<Integer, T> childData = Maps.newHashMap();
    for (Map.Entry<Integer, SemanticPredicate> childType : semanticType.getArguments().entrySet()) {
      evaluatedParses.put(childType.getKey(), EvaluatedSemanticParse.<T> fromSemanticTypeHelper(
          childType.getValue(), combinator));
      childData.put(childType.getKey(), 
          evaluatedParses.get(childType.getKey()).getData());
    }

    // Initialize the data at this node.
    T data = combinator.initializeNodeData(semanticType, childData);
 
    return new EvaluatedSemanticParse<T>(semanticType, evaluatedParses, data);
  }

  /**
   * This method is similar to the downward pass of junction tree, which
   * propagates constraints from the root of the junction tree to its
   * descendants.
   * 
   * @return
   */
  private EvaluatedSemanticParse<T> propagateArgumentConstraintsDownward(SemanticParseNodeCombinator<T> combinator,
      T newData) {
    Map<Integer, EvaluatedSemanticParse<T>> newArguments = Maps.newHashMap();
    for (Map.Entry<Integer, EvaluatedSemanticParse<T>> oldArgument : arguments.entrySet()) {
      // Update the argument's data based on the current node's data
      T newArgumentData = combinator.constrainNodeData(oldArgument.getValue().semanticType, 
          oldArgument.getValue().getData(), newData, oldArgument.getKey());

      newArguments.put(oldArgument.getKey(),
          oldArgument.getValue().propagateArgumentConstraintsDownward(combinator, newArgumentData));
    }

    return new EvaluatedSemanticParse<T>(semanticType, newArguments, newData);
  }

  /**
   * A function for computing values at nodes of a semantic parse tree.
   * 
   * @author jayantk
   * @param <T>
   */
  public static interface SemanticParseNodeCombinator<T> {

    /**
     * Computes a value for a node, based on its children.
     * 
     * @param parseNode
     * @param childData
     * @return
     */
    public T initializeNodeData(SemanticPredicate semanticType, Map<Integer, T> childData);

    /**
     * Computes a value for a node based on its current value, its parent's
     * value, and the way the two nodes are combined.
     * 
     * @param nodeToConstrain
     * @param parentData
     * @param combination
     * @return
     */
    public T constrainNodeData(SemanticPredicate semanticType, 
        T nodeToConstrain, T parentData, int parentArgumentNumber);
  }
}
