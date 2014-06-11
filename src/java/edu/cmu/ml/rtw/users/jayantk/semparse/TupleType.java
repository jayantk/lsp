package edu.cmu.ml.rtw.users.jayantk.semparse;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterators;
import com.google.common.collect.Sets;

public class TupleType implements Type {
  
  private final Type syntax, semantics;

  private TupleType(Type syntax, Type semantics) {
    this.syntax = Preconditions.checkNotNull(syntax);
    this.semantics = Preconditions.checkNotNull(semantics);
  }
  
  public static TupleType create(Type syntax, Type semantics) {
    return new TupleType(syntax, semantics);
  }
  
  public Type getSyntacticType() {
    return syntax;
  }
  
  public Type getSemanticType() {
    return semantics;
  }

  @Override
  public boolean hasAncestor(Type possibleAncestor) {
    if (!(possibleAncestor instanceof TupleType)) {
      return false;
    }
    TupleType ancestorTuple = (TupleType) possibleAncestor;
    return syntax.hasAncestor(ancestorTuple.getSyntacticType()) &&
        semantics.hasAncestor(ancestorTuple.getSemanticType());
  }

  @Override
  public Iterator<TupleType> getAncestorIterator() {
    return new AncestorIterator(this);
  }

  @Override
  public Set<TupleType> getAncestors() {
    Set<TupleType> ancestors = Sets.newHashSet();
    Iterators.addAll(ancestors, getAncestorIterator());
    return ancestors;
  }
  
  @Override
  public Collection<? extends Type> getGeneralizations() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getName() {
    return "(" + syntax + "," + semantics + ")";
  }
  
  @Override
  public String toString() {
    return getName();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((semantics == null) ? 0 : semantics.hashCode());
    result = prime * result + ((syntax == null) ? 0 : syntax.hashCode());
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
    TupleType other = (TupleType) obj;
    if (semantics == null) {
      if (other.semantics != null)
        return false;
    } else if (!semantics.equals(other.semantics))
      return false;
    if (syntax == null) {
      if (other.syntax != null)
        return false;
    } else if (!syntax.equals(other.syntax))
      return false;
    return true;
  }
  
  private static class AncestorIterator implements Iterator<TupleType> {
    private final TupleType type;
    private Iterator<? extends Type> syntaxIterator;
    private Iterator<? extends Type> semanticsIterator;
    
    private Type curSemanticType;
    
    public AncestorIterator(TupleType type) {
      this.type = type;
      this.syntaxIterator = type.getSyntacticType().getAncestorIterator();
      this.semanticsIterator = type.getSemanticType().getAncestorIterator();
      this.curSemanticType = semanticsIterator.next();
    }

    @Override
    public boolean hasNext() {
      return syntaxIterator.hasNext() || semanticsIterator.hasNext();
    }

    @Override
    public TupleType next() {
      if (syntaxIterator.hasNext()) {
        return TupleType.create(syntaxIterator.next(), curSemanticType);
      } else {
        // Reset the syntax iterator for the next semantic element.
        curSemanticType = semanticsIterator.next();
        syntaxIterator = type.getSyntacticType().getAncestorIterator();
        return TupleType.create(syntaxIterator.next(), curSemanticType);
      }
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }
}
