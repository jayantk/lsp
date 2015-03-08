package edu.cmu.ml.rtw.vector;

import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.lambda.Expression;
import com.jayantkrish.jklol.cvsm.Cvsm;
import com.jayantkrish.jklol.tensor.Tensor;

import edu.cmu.ml.rtw.users.jayantk.grounding.Domain;
import edu.cmu.ml.rtw.users.jayantk.grounding.GroundingModel.GroundingPrediction;
import edu.cmu.ml.rtw.users.jayantk.grounding.GroundingModelInterface;
import edu.cmu.ml.rtw.users.jayantk.grounding.ParallelFactors;
import edu.cmu.ml.rtw.users.jayantk.grounding.QueryTree;
import edu.cmu.ml.rtw.users.jayantk.grounding.UnigramLanguageModel;
import edu.cmu.ml.rtw.users.jayantk.grounding.World;
import edu.cmu.ml.rtw.users.jayantk.semparse.SemanticPredicate;

public class VectorSpaceGroundingModel implements GroundingModelInterface {

  private final Cvsm model;
  
  public VectorSpaceGroundingModel(Cvsm model) {
    this.model = Preconditions.checkNotNull(model);
  }
  
  @Override
  public boolean hasRelationName(String relationName) {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public QueryTree getQueryForRelationName(String relationName, Domain domain) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public World getWorldForDomain(Domain domain) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public GroundingPrediction getPrediction(List<List<String>> inputCandidates,
      double[] inputWeights, Domain domain, boolean addImplicitDeterminer) {
    
    return null;
  }

  @Override
  public Expression getExpressionFromSemanticParse(SemanticPredicate semanticParse) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public List<String> generateTextFromGrounding(Tensor groundingAssignment, Domain domain, UnigramLanguageModel languageModel, boolean forceRelation) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public ParallelFactors getCompleteGrounding(Expression logicalForm, World world) {
    // TODO Auto-generated method stub
    return null;
  }

}
