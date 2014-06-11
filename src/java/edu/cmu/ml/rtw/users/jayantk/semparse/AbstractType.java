package edu.cmu.ml.rtw.users.jayantk.semparse;

import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.common.collect.Sets;

public abstract class AbstractType implements Type, Serializable{
  
  private final String name;
  private final ImmutableList<Type> parents;
  
  public AbstractType(String name, Collection<? extends Type> parents) {
    this.name = name;
    this.parents = ImmutableList.copyOf(parents);
  }

  @Override
  public boolean hasAncestor(Type possibleAncestor) {
    Preconditions.checkNotNull(possibleAncestor);
    Iterator<? extends Type> ancestorIterator = getAncestorIterator();
    while(ancestorIterator.hasNext()) {
      if (possibleAncestor.equals(ancestorIterator.next())) {
        return true;
      }
    }
    return false;
  }

  /**
   * The returned iterator includes {@code this}.
   */
  @Override
  public Iterator<? extends Type> getAncestorIterator() {
    Iterator<Type> ancestorIterator = Iterators.concat(Iterators.transform(parents.iterator(), 
        new Function<Type, Iterator<? extends Type>>() {
      @Override
      public Iterator<? extends Type> apply(Type t) {
        return t.getAncestorIterator();
      }
    })); 
    return Iterators.concat(Iterators.singletonIterator(this), ancestorIterator);
  }

  @Override
  public Set<? extends Type> getAncestors() {
    Set<Type> ancestors = Sets.newHashSet();
    Iterators.addAll(ancestors, getAncestorIterator());
    return ancestors;
  }

  public Collection<? extends Type> getGeneralizations() {
    return parents;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String toString() {
    return name;
  }
}
