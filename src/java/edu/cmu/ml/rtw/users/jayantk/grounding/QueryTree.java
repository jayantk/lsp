package edu.cmu.ml.rtw.users.jayantk.grounding;

import ilog.concert.IloException;
import ilog.concert.IloLinearNumExpr;
import ilog.concert.IloNumVar;
import ilog.cplex.IloCplex;

import java.util.List;
import java.util.Set;

import com.jayantkrish.jklol.tensor.Tensor;

import edu.cmu.ml.rtw.users.jayantk.semparse.RelationType;

/**
 * A query against a probabilistic / weighted database. Each node of a query
 * tree represents either a predicate (i.e., a table in the weighted database)
 * or a logical connective (e.g., conjunction). This class evaluates the most
 * likely assignments of the variables in a query using approximate inference.
 * <p>
 * Assignments to the variables of a query tree are represented by
 * {@code MultiTree<Tensor>} objects. Each level of the returned tree is an
 * assignment to either predicate variables or intermediate variables created to
 * evaluate the query.
 * <p>
 * Note that {@code QueryTree} is mutable, and that the
 * {@link #dualDecomposition} method modifies the weights of the predicates
 * stored in the query tree. Use {@link #copy()} to retain the original query
 * tree, if desired.
 * 
 * @author jayantk
 */
public interface QueryTree {

  public boolean hasPredicate();

  /**
   * Returns {@code null} if this node does not have a predicate.
   * 
   * @return
   */
  public RelationType getPredicate();
  
  /**
   * Gets all predicates which occur at any node in this tree. Each
   * occurring predicate is added to {@code accumulator}.
   * 
   * @return
   */
  public void getAllPredicatesInTree(Set<RelationType> accumulator);

  /**
   * Returns true if this node has no children.
   * 
   * @return
   */
  public boolean isLeaf();

  /**
   * Gets any children of this node.
   * 
   * @return
   */
  public List<QueryTree> getSubtrees();

  /**
   * Returns an assignment to the variables in this query by locally computing
   * the MAP assignment for each set of intermediate / predicate variables. Note
   * that the returned assignment may not satisfy the constraint structure
   * specified by the query.
   * 
   * @return
   */
  public MultiTree<Tensor> locallyDecodeVariables();

  /**
   * Returns the MAP (highest weight) assignment to the variables in the query.
   */
  public MultiTree<Tensor> evaluateQueryMap();

  /**
   * Returns a string representation of an assignment.
   * 
   * @param assignment
   * @return
   */
  public String getAssignmentString(MultiTree<Tensor> assignment);

  /**
   * Gets the weight of an assignment, ignoring any hard constraints.
   * 
   * @param assignment
   * @return
   */
  public double getWeight(MultiTree<Tensor> assignment);

  public QueryTree copy();

  /**
   * Returns a copy of this tree whose weights have been reparameterized by
   * running dual decomposition.
   * 
   * @param maxIterations
   * @return
   */
  public QueryTree reparameterizeDualDecomposition(int maxIterations);

  /**
   * Reparameterizes the factors in this object so that locally decoding the
   * factors is likely to return the MAP assignment.
   * 
   * @param maxIterations
   */
  public void dualDecomposition(int maxIterations);

  /**
   * Gets the weights associated with the output predicate of this object. The
   * returned weights represent the relative "likelihoods" of selecting
   * particular true/false values for each variable in the output set.
   * 
   * @return
   */
  public ParallelFactors getOutputLocalWeights();

  // Stuff for implementing dual decomposition.

  public MultiTree<List<Tensor>> locallyDecodeFactors();

  public int subgradientUpdate(MultiTree<Tensor> localAssignment,
      MultiTree<List<Tensor>> factorAssignment, double stepSize);

  public void updateOutputLocalWeights(Tensor gradient);
  
    
  /**
   * Find the MAP assignment to this query tree using an integer linear programming solver.
   * <p>
   * If {@code useLpRelaxation} is true, this returns an approximate solution obtained by
   * relaxing the original program to a linear program and rounding the result.
   * @return
   */
  public MultiTree<Tensor> ilpInference(boolean useLpRelaxation, boolean applyWeakSupervisionConstraints);
  
  // Helper for adding constraints and variables to an existing ILP instance.
    public IloNumVar[] augmentIlp(IloCplex cplex, IloLinearNumExpr objective, boolean useLpRelaxation, boolean applyWeakSupervisionConstraints) throws IloException;
  
  public MultiTree<Tensor> decodeIlpSolution(IloCplex cplex) throws IloException;
}
