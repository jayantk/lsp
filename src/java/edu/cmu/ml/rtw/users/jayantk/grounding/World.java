package edu.cmu.ml.rtw.users.jayantk.grounding;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.util.IndexedList;

/**
 * A world, i.e., a set of groundings for predicates.
 * 
 * @author jayantk
 */
public class World {

  private VariableNumMap groundingVar1;
  private VariableNumMap groundingVar2;
  private VariableNumMap booleanVar;
  
  private final IndexedList<String> predicateNames;
  private final List<ParallelFactors> groundingFactors;
  
  public World(VariableNumMap groundingVar1, VariableNumMap groundingVar2, VariableNumMap booleanVar,
      IndexedList<String> predicateNames, List<ParallelFactors> groundingFactors) {
    this.groundingVar1 = Preconditions.checkNotNull(groundingVar1);
    this.groundingVar2 = Preconditions.checkNotNull(groundingVar2);
    this.booleanVar = Preconditions.checkNotNull(booleanVar);
    
    this.predicateNames = Preconditions.checkNotNull(predicateNames);
    this.groundingFactors = ImmutableList.copyOf(groundingFactors);
  }
  
  public VariableNumMap getGroundingVariable1() {
    return groundingVar1;
  }
  
  public VariableNumMap getGroundingVariable2() {
    return groundingVar2;
  }
  
  public VariableNumMap getBooleanVariable() {
    return booleanVar;
  }

    public List<String> getRelationNames() {
	return predicateNames.items();
    }

    public boolean containsRelation(String relationName) {
	return predicateNames.contains(relationName);
    }
  
  public ParallelFactors getFactorForRelation(String relationName) {
    Preconditions.checkArgument(predicateNames.contains(relationName),
        "No such relation: %s", relationName);
    int index = predicateNames.getIndex(relationName);
    return groundingFactors.get(index);
  }
}
