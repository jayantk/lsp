package edu.cmu.ml.rtw.users.jayantk.grounding;

import ilog.concert.IloException;
import ilog.concert.IloLinearNumExpr;
import ilog.concert.IloNumVar;
import ilog.cplex.IloCplex;

import java.util.Arrays;
import java.util.List;

import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.util.Assignment;

public class DeterminerQueryTree extends AbstractQueryTree {
  
  private final QueryTree subtree;
  private final boolean forceChildUnique;

  public DeterminerQueryTree(QueryTree subtree, boolean forceChildUnique) {
    super(subtree.getOutputLocalWeights(), Arrays.asList(subtree), null, true);
    this.subtree = subtree;
    this.forceChildUnique = forceChildUnique;
  }

  @Override
  public MultiTree<Tensor> evaluateQueryMap() {
    throw new UnsupportedOperationException("Cannot decode without an ILP");
  }

  @Override
  public QueryTree copy() {
    return new DeterminerQueryTree(subtree.copy(), forceChildUnique);
  }

  @Override
  protected List<Tensor> locallyDecodeFactor() {
    throw new UnsupportedOperationException("Not yet implemented.");
  }

  @Override
  protected int localSubgradientUpdate(Tensor rootAssignment, List<Tensor> childAssignments, List<Tensor> factorAssignment, double stepSize) {
    throw new UnsupportedOperationException("Not yet implemented.");
  }

  @Override
  protected IloNumVar[] augmentIlpHelper(IloCplex cplex, IloLinearNumExpr objective, boolean useLpRelaxation,
					 boolean applyWeakSupervisionConstraints) throws IloException {
    IloNumVar[] myVars = addLocalWeightsToIlp(cplex, objective, useLpRelaxation);
    IloNumVar[] childVars = subtree.augmentIlp(cplex, objective, useLpRelaxation, applyWeakSupervisionConstraints);

    // Impose a subset constraint: the output of this node must be a subset of 
    // its input.
    ParallelFactors myWeights = getOutputLocalWeights();
    ParallelFactors childWeights = subtree.getOutputLocalWeights();
    for (int i = 0; i < myVars.length; i++) {
      Assignment key = myWeights.ilpIndexToAssignment(i);
      int childIndex = childWeights.getIlpVariableIndex(key);
      
      IloLinearNumExpr constraint = cplex.linearNumExpr();
      constraint.addTerm(-1.0, myVars[i]);
      constraint.addTerm(1.0, childVars[childIndex]);
      
      cplex.addGe(constraint, 0.0);
    }
    
    if (applyWeakSupervisionConstraints) {
	// Impose a constraint on myVars that exactly one variable is active.
	IloLinearNumExpr constraint = cplex.linearNumExpr();
	for (int i = 0; i < myVars.length; i++) {
	    constraint.addTerm(1.0, myVars[i]);
	}
	cplex.addEq(constraint, 1.0);
    
	if (forceChildUnique) {

	    // Some determiners mandate a unique child value.
	    constraint = cplex.linearNumExpr();
	    for (int i = 0; i < childVars.length; i++) {
		constraint.addTerm(1.0, childVars[i]);
	    }
	    cplex.addEq(constraint, 1.0);
	}
    }

    return myVars;
  }
}
