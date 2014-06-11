package edu.cmu.ml.rtw.users.jayantk.semparse;

import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

/**
 * A data type with an inheritance hierarchy. Used to represent argument type
 * constraints of relations, etc.
 * 
 * @author jayantk
 */
public interface Type extends Serializable{

  public boolean hasAncestor(Type possibleAncestor);

  /**
   * The returned iterator includes {@code this}.
   */
  public Iterator<? extends Type> getAncestorIterator();
  
  public Set<? extends Type> getAncestors();
  
  public Collection<? extends Type> getGeneralizations();
  
  public String getName();
  
  @Override 
  public int hashCode();
  
  @Override
  public boolean equals(Object o);
}
