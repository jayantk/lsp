package edu.cmu.ml.rtw.users.jayantk.grounding;

import java.util.List;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class MultiTree<T> {
  
  private final T value;
  private final List<MultiTree<T>> children;
  
  public MultiTree(T value, List<? extends MultiTree<T>> children) {
    this.value = value;
    this.children = ImmutableList.copyOf(children);
  }
  
  public boolean isLeaf() {
    return children.size() == 0;
  }
  
  public List<MultiTree<T>> getChildren() {
    return children;
  }
  
  public T getValue() {
    return value;
  }
  
  public List<T> getChildValues() {
    List<T> childValues = Lists.newArrayList();
    for (MultiTree<T> child : children) {
      childValues.add(child.getValue());
    }
    return childValues;
  }
  
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("(");
    sb.append(value);
    for (MultiTree<T> child : children) {
      sb.append(" ");
      sb.append(child);
    } 
    sb.append(")");
    return sb.toString();
  }
}
