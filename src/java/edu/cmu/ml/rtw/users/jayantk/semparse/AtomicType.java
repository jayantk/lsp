package edu.cmu.ml.rtw.users.jayantk.semparse;

import java.util.Collection;

public class AtomicType extends AbstractType {
  
  public AtomicType(String name, Collection<AtomicType> parentTypes) {
    super(name, parentTypes);
  }
  
  @Override
  public String toString() {
    return getName();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((getName() == null) ? 0 : getName().hashCode());
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
    AtomicType other = (AtomicType) obj;
    if (getName() == null) {
	if (other.getName() != null)
        return false;
    } else if (!getName().equals(other.getName()))
      return false;
    return true;
  }
}
