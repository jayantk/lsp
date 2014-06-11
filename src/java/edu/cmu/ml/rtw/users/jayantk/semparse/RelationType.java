package edu.cmu.ml.rtw.users.jayantk.semparse;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

/**
 * TODO: rename to RelationSpec
 * 
 * {@code RelationSpec}s can be thought of as a set of substitution rules,
 * whereby tuples with elements of certain argument types can be subsumed by a
 * predicate type.
 * 
 * @author jayantk
 */
public class RelationType implements Serializable{

  private final String name;
  // The atomic type which this relationType falls under in
  // the predicate generalizations hierarchy.
  private final AtomicType relationAtomicType;
  private final ImmutableList<Type> argumentTypes;

  public RelationType(String name, AtomicType relationAtomicType, List<? extends Type> argumentTypes) { 
    this.name = Preconditions.checkNotNull(name);
    this.relationAtomicType = relationAtomicType;
    this.argumentTypes = ImmutableList.copyOf(argumentTypes);
  }
  
  public static RelationType createWithNewAtomicType(String name, List<? extends Type> argumentTypes) {
    return new RelationType(name, new AtomicType(name, Collections.<AtomicType>emptyList()), argumentTypes);
  }

  public String getName() {
    return name;
  }
  
  public Type getType() {
    return relationAtomicType;
  }

  public List<Type> getArgumentTypes() {
    return argumentTypes;
  }

    public int getNumArguments() {
	return argumentTypes.size();
    }
  
  @Override
  public String toString() {
      return name + ":" + getNumArguments();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((argumentTypes == null) ? 0 : argumentTypes.hashCode());
    result = prime * result + ((name == null) ? 0 : name.hashCode());
    result = prime * result + ((relationAtomicType == null) ? 0 : relationAtomicType.hashCode());
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
    RelationType other = (RelationType) obj;
    if (argumentTypes == null) {
      if (other.argumentTypes != null)
        return false;
    } else if (!argumentTypes.equals(other.argumentTypes))
      return false;
    if (name == null) {
      if (other.name != null)
        return false;
    } else if (!name.equals(other.name))
      return false;
    if (relationAtomicType == null) {
      if (other.relationAtomicType != null)
        return false;
    } else if (!relationAtomicType.equals(other.relationAtomicType))
      return false;
    return true;
  }  
}
