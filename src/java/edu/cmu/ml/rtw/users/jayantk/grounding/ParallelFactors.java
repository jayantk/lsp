package edu.cmu.ml.rtw.users.jayantk.grounding;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Iterator;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.VariableNumMap.VariableRelabeling;
import com.jayantkrish.jklol.tensor.Backpointers;
import com.jayantkrish.jklol.tensor.DenseTensor;
import com.jayantkrish.jklol.tensor.DenseTensorBuilder;
import com.jayantkrish.jklol.tensor.SparseTensorBuilder;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.tensor.TensorBase.KeyValue;
import com.jayantkrish.jklol.util.Assignment;

/**
 * {@code ParallelFactors} represents a set of factors over an identical set of
 * values. The values are retrieved by {@link #getValueVariables()}. The factors
 * themselves are indexed by another set of variables, given by
 * {@link #getIndexVariables()}. This class contains one factor for each
 * possible assignment to this set of index variables.
 * <p>
 * {@code Tensor}s are used to represent simultaneous assignments to all of the
 * factors in a {@code ParalellFactors} object. An assignment tensor has the
 * same dimensions as the tensor in this object, with a value of 1.0 for each
 * key assigned to. Note that there should be exactly one such key in the
 * assignment tensor for each possible assignment to the index variables of
 * this.
 * 
 * @author jayantk
 */
public class ParallelFactors implements Serializable {
  static final long serialVersionUID = 10275539472837496L;
  private final Tensor tensor;
  private Tensor indexTensor;
  
  private final VariableNumMap varNums;
  private final VariableNumMap valueNums;

  public ParallelFactors(Tensor tensor, VariableNumMap varNums, VariableNumMap valueNums) {
      this.tensor = DenseTensor.copyOf(Preconditions.checkNotNull(tensor));
    this.indexTensor = null;
    this.varNums = Preconditions.checkNotNull(varNums);
    this.valueNums = Preconditions.checkNotNull(valueNums);

    // TODO: all of varNums are expected to occur before valueNums (have lower
    // numbers)
  }

  /**
   * Returns the best assignment to each variable in this, represented as an
   * indicator tensor.
   * 
   * @return
   */
  public Tensor getBestAssignments() {
    Backpointers backpointers = new Backpointers();
    tensor.maxOutDimensions(valueNums.getVariableNums(), backpointers);
    return backpointers.getOldKeyIndicatorTensor();
  }

    public DiscreteFactor getBestAssignmentsFactor() {
	Tensor tensor = getBestAssignments();
	return new TableFactor(varNums.union(valueNums), tensor);
    }

  public double getAssignmentWeight(Tensor assignment) {
    Preconditions.checkArgument(Arrays.equals(assignment.getDimensionNumbers(),
        tensor.getDimensionNumbers()));
    
    /*
    System.out.println("assignment: " + assignment);
    System.out.println("tensor: " + tensor);
    System.out.println(tensor.elementwiseProduct(assignment));
    */

    return tensor.elementwiseProduct(assignment).sumOutDimensions(
        Ints.asList(tensor.getDimensionNumbers())).getByDimKey(new int[] {});
  }

  public Tensor getTensor() {
    return tensor;
  }
  
  public DiscreteFactor getFactor() {
    return new TableFactor(varNums.union(valueNums), tensor);
  }

  public VariableNumMap getAllVariables() {
    return valueNums.union(varNums);
  }

  public VariableNumMap getValueVariables() {
    return valueNums;
  }

  public VariableNumMap getIndexVariables() {
    return varNums;
  }

  public int[] getDimensionSizes() {
    return tensor.getDimensionSizes();
  }

  public ParallelFactors elementwiseAddition(Tensor other) {
    return new ParallelFactors(DenseTensor.copyOf(tensor.elementwiseAddition(other)),
        varNums, valueNums);
  }

  public ParallelFactors relabelVariables(VariableRelabeling relabeling) {
    return new ParallelFactors(tensor.relabelDimensions(relabeling.getVariableIndexReplacementMap()),
        relabeling.apply(varNums), relabeling.apply(valueNums));
  }

  /**
   * Returns a copy of {@code this} with the same variables, but all zero
   * weights in the tensor.
   * 
   * @return
   */
  public ParallelFactors emptyCopy() {
    return new ParallelFactors(new DenseTensorBuilder(tensor.getDimensionNumbers(), tensor.getDimensionSizes(), 0.0).buildNoCopy(),
        varNums, valueNums);
  }
  
  private Tensor getIndexTensor() {
    if (indexTensor == null) {
      indexTensor = tensor.sumOutDimensions(valueNums.getVariableNums());
    }
    return indexTensor;
  }
  
  public Assignment ilpIndexToAssignment(int index) {
    int[] dimKey = getIndexTensor().keyNumToDimKey(index);
    return varNums.intArrayToAssignment(dimKey);
  }
  
  public int getIlpVariableIndex(Assignment assignment) {
    int[] dimKey = varNums.assignmentToIntArray(assignment);
    return (int) getIndexTensor().dimKeyToKeyNum(dimKey);
  }

  public static ParallelFactors fromVariables(VariableNumMap variableVars, VariableNumMap valueVars, double initialValue) {
    VariableNumMap allVars = variableVars.union(valueVars);
    Tensor weights = DenseTensor.constant(Ints.toArray(allVars.getVariableNums()),
        allVars.getVariableSizes(), initialValue);

    return new ParallelFactors(SparseTensorBuilder.copyOf(weights).build(), variableVars, valueVars);
  }

  public String getTensorAssignmentString(Tensor tensor) {
    int[] valueDims = Ints.toArray(valueNums.getVariableNums());
    int[] oneVals = new int[valueDims.length];
    Arrays.fill(oneVals, 1);

    StringBuilder sb = new StringBuilder();
    sb.append("{");
    Tensor sliced = tensor.slice(valueDims, oneVals);
    Iterator<KeyValue> iter = sliced.keyValueIterator();
    while (iter.hasNext()) {
      KeyValue k = iter.next();
      if (k.getValue() != 0.0) {
	  sb.append(varNums.intArrayToAssignment(k.getKey()).getValues());
	  sb.append(" ");
      }
    }
    sb.append("}");

    return sb.toString();
  }

    public String getParameterDescription() {
	DiscreteFactor discreteFactor = new TableFactor(getAllVariables(), tensor);
	return discreteFactor.getParameterDescription();
    }
}
