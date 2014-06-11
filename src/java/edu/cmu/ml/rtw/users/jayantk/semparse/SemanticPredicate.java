package edu.cmu.ml.rtw.users.jayantk.semparse;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.primitives.Ints;

public class SemanticPredicate implements Serializable{

  private final RelationType relation;
  private final List<Integer> argumentOrder;
  // Use -1 to indicate that this node (and neither argument) is the output.
  private final int outputArgument;
  
  private final Map<Integer, Object> fixedArguments;
  private final Map<Integer, SemanticPredicate> filledArguments;
  
  public SemanticPredicate(RelationType relation, List<Integer> argumentOrder, int outputArgument, 
      Map<Integer, Object> fixedArguments) {
    this.relation = relation;
    this.argumentOrder = ImmutableList.copyOf(argumentOrder);
    this.outputArgument = outputArgument;
    this.fixedArguments = Maps.newHashMap(fixedArguments);
    this.filledArguments = Maps.newHashMap();
  }
  
  public SemanticPredicate(RelationType relation, List<Integer> argumentOrder, int outputArgument, 
      Map<Integer, Object> fixedArguments, Map<Integer, SemanticPredicate> filledArguments) {
    this.relation = relation;
    this.argumentOrder = ImmutableList.copyOf(argumentOrder);
    this.outputArgument = outputArgument;
    this.fixedArguments = Maps.newHashMap(fixedArguments);
    this.filledArguments = Maps.newHashMap(filledArguments);
  }
  
  public static SemanticPredicate createFromCategory(RelationType categoryType) {
    return new SemanticPredicate(categoryType, Ints.asList(), 0, Collections.<Integer, Object>emptyMap());
  }
  
  public static SemanticPredicate createFromRelationWithArgOrder(RelationType relationType, 
								    int[] argOrder, int outputArgument) {
    return new SemanticPredicate(relationType, Ints.asList(argOrder), outputArgument, 
				    Collections.<Integer, Object>emptyMap());
  }
  
  public static SemanticPredicate createFromConcept(RelationType categoryType, Object concept) {
    Map<Integer, Object> map = Maps.newHashMap();
    map.put(0, concept);
    return new SemanticPredicate(categoryType, Ints.asList(), 0, map);
  }
  
  public RelationType getRelation() {
    return relation;
  }
  
  public Map<Integer, Object> getFixedArguments() {
    return fixedArguments;
  }
  
  public Map<Integer, SemanticPredicate> getArguments() {
    return filledArguments;
  }
  
  public int getOutputArgument() {
    return outputArgument;
  }
  
  public List<Integer> getArgumentOrder() {
    return argumentOrder;
  }
  
  public SemanticPredicate apply(SemanticPredicate argument) {
    Preconditions.checkArgument(argumentOrder.size() > 0, 
        "%s has no arguments. Tried applying to %s", this, argument);
    int argumentNum = argumentOrder.get(argumentOrder.size() - 1);
    Map<Integer, SemanticPredicate> newFilledArguments = Maps.newHashMap(filledArguments);
    Preconditions.checkState(!newFilledArguments.containsKey(argumentNum));
    newFilledArguments.put(argumentNum, argument);
    return new SemanticPredicate(relation, argumentOrder.subList(0, argumentOrder.size() -1),
        outputArgument, fixedArguments, newFilledArguments);
  }
  
  public SemanticPredicate curryArgument(int argumentNumber) {
    List<Integer> newArgumentOrder = Lists.newArrayList(argumentOrder);
    newArgumentOrder.add(argumentNumber);
    return new SemanticPredicate(relation, newArgumentOrder, 
        outputArgument, fixedArguments, filledArguments);
  }
  
  /**
   * Replaces any fixed arguments in this with the given fixed arguments.
   * 
   * @param newFixedArguments
   * @return
   */
  public SemanticPredicate addFixedArguments(Map<Integer, Object> newFixedArguments) {
    return new SemanticPredicate(relation, argumentOrder, 
        outputArgument, newFixedArguments, filledArguments);
  }
  
  public String toShortString() {
      if (relation.getArgumentTypes().size() == 1 && fixedArguments.containsKey(0)) {
	  // If the parse is just a concept, simply print it out. 
	  return fixedArguments.get(0).toString();
      }
    return relation.toString() + argumentOrder.toString();
  }
  
  @Override
  public String toString() {
    if (relation.getArgumentTypes().size() == 1 && fixedArguments.containsKey(0)) {
      // If the parse is just a concept, simply print it out. 
      return fixedArguments.get(0).toString();
    }
    
    StringBuilder builder = new StringBuilder();    
    builder.append(relation.toString());
    builder.append("(");
    for (int i = 0; i < relation.getArgumentTypes().size(); i++) {
      if (i > 0) { builder.append(","); }
      builder.append(i);
      builder.append(":");
      if (fixedArguments.containsKey(i)) {
        builder.append(fixedArguments.get(i));
      } else if (filledArguments.containsKey(i)) {
        builder.append(filledArguments.get(i).toString());
      } else {
        builder.append("<empty>");
      }
    }
    builder.append(")");
    builder.append(argumentOrder.toString());
    return builder.toString();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((argumentOrder == null) ? 0 : argumentOrder.hashCode());
    result = prime * result + ((filledArguments == null) ? 0 : filledArguments.hashCode());
    result = prime * result + ((fixedArguments == null) ? 0 : fixedArguments.hashCode());
    result = prime * result + outputArgument;
    result = prime * result + ((relation == null) ? 0 : relation.hashCode());
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
    SemanticPredicate other = (SemanticPredicate) obj;
    if (argumentOrder == null) {
      if (other.argumentOrder != null)
        return false;
    } else if (!argumentOrder.equals(other.argumentOrder))
      return false;
    if (filledArguments == null) {
      if (other.filledArguments != null)
        return false;
    } else if (!filledArguments.equals(other.filledArguments))
      return false;
    if (fixedArguments == null) {
      if (other.fixedArguments != null)
        return false;
    } else if (!fixedArguments.equals(other.fixedArguments))
      return false;
    if (outputArgument != other.outputArgument)
      return false;
    if (relation == null) {
      if (other.relation != null)
        return false;
    } else if (!relation.equals(other.relation))
      return false;
    return true;
  }
}
