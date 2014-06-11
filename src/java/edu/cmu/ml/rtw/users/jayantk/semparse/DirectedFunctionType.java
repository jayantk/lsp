package edu.cmu.ml.rtw.users.jayantk.semparse;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;

/**
 * Represents functional categories in CCG. Has a direction (left, right or
 * either) on which side it expects an argument.
 * 
 * @author jayantk
 */
public class DirectedFunctionType implements Type, Serializable{

  /**
   * 
   */
  private static final long serialVersionUID = 4117131179482403519L;
  private final Type argumentType;
  private final Type returnType;
  private final boolean acceptsDescendants;
  
  private final List<String> lexicalEntry; 

  public static final AtomicType EITHER = new AtomicType("EITHER", Collections.<AtomicType> emptyList());
  public static final AtomicType LEFT = new AtomicType("LEFT", Arrays.asList(EITHER));
  public static final AtomicType RIGHT = new AtomicType("RIGHT", Arrays.asList(EITHER));

  // Must be one of the directions above.
  private final AtomicType direction;

  public DirectedFunctionType(Type argumentType, Type returnType, 
      boolean acceptsDescendants, AtomicType direction, List<String> lexicalEntry) {
    this.argumentType = Preconditions.checkNotNull(argumentType);
    this.returnType = Preconditions.checkNotNull(returnType);
    this.acceptsDescendants = acceptsDescendants;
    this.direction = direction;
    Preconditions.checkArgument(direction == LEFT || direction == RIGHT ||
        direction == EITHER);
    this.lexicalEntry = Preconditions.checkNotNull(lexicalEntry);
  }

  public Type getArgumentType() {
    return argumentType;
  }

  public Type getReturnType() {
    return returnType;
  }

  /**
   * Returns the location where arguments to this may be located (left, right or
   * either side).
   */
  public Type getArgumentDirection() {
    return direction;
  }


  public boolean takesArgumentOnLeft() {
    return LEFT.hasAncestor(direction);
  }

  public boolean takesArgumentOnRight() {
    return RIGHT.hasAncestor(direction);
  }
  
  public boolean acceptsDescendantTypesAsArgument() {
    return acceptsDescendants;
  }

  @Override
  public boolean hasAncestor(Type possibleAncestor) {
    if (!(possibleAncestor instanceof DirectedFunctionType)) {
      return false;
    }
    DirectedFunctionType ancestorAsFunction = (DirectedFunctionType) possibleAncestor;
    return argumentType.hasAncestor(ancestorAsFunction.getArgumentType()) &&
        returnType.hasAncestor(ancestorAsFunction.getArgumentType()) &&
        direction.hasAncestor(ancestorAsFunction.getArgumentDirection()) &&
        acceptsDescendants == ancestorAsFunction.acceptsDescendants && 
        lexicalEntry.equals(ancestorAsFunction.lexicalEntry);
  }

  @Override
  public Iterator<? extends Type> getAncestorIterator() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Set<? extends Type> getAncestors() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Collection<? extends Type> getGeneralizations() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getName() {
    String base = null;
    if (lexicalEntry.size() > 0) {
      base = argumentType.getName() + "_" + lexicalEntry.toString();
    } else {
      base = argumentType.getName();
    }
    String directionStr = null;
    if (direction == LEFT) {
      directionStr = "\\";
    } else if (direction == RIGHT) {
      directionStr = "/";
    } else {
      directionStr = "|";
    }

    return returnType.getName() + directionStr + base;
  }

  @Override
  public String toString() {
    return getName();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = result + 17 * (acceptsDescendants ? 1 : 0);
    result = prime * result + ((argumentType == null) ? 0 : argumentType.hashCode());
    result = prime * result + ((returnType == null) ? 0 : returnType.hashCode());
    result = prime * result + ((direction == null) ? 0 : direction.hashCode());
    result = prime * result + lexicalEntry.hashCode();
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
    DirectedFunctionType other = (DirectedFunctionType) obj;
    if (acceptsDescendants != other.acceptsDescendants) {
      return false;
    }
    if (argumentType == null) {
      if (other.argumentType != null)
        return false;
    } else if (!argumentType.equals(other.argumentType))
      return false;
    if (returnType == null) {
      if (other.returnType != null)
        return false;
    } else if (!returnType.equals(other.returnType))
      return false;
    if (direction == null) {
      if (other.direction != null) 
        return false;
    } else if (!direction.equals(other.direction))
      return false;
    if (lexicalEntry == null) {
      if (other.lexicalEntry != null)
        return false;
    } else if (!lexicalEntry.equals(other.lexicalEntry))
      return false;
    return true;
  }
}
