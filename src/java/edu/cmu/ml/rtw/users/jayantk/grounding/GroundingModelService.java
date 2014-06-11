package edu.cmu.ml.rtw.users.jayantk.grounding;

import java.util.List;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.base.Joiner;

import edu.cmu.ml.rtw.users.jayantk.grounding.GroundingModel.GroundingPrediction;

/**
 * Service which produces predictions from a serialized grounding
 * model.
 * 
 * @author jayantk
 */
public class GroundingModelService {

  public static void main(String[] args) throws Exception {
    OptionParser parser = new OptionParser();
    OptionSpec<String> modelFilename = parser.accepts("modelFilename").withRequiredArg().ofType(String.class).required();
    OptionSpec<String> domainFilename = parser.accepts("domain").withRequiredArg().ofType(String.class).required();
    OptionSpec<Void> generative = parser.accepts("generative");
    OptionSet options = parser.parse(args);
    
    GroundingModel groundingModel = GroundingModel.fromSerializedFile(options.valueOf(modelFilename));
    Domain domain = Domain.readDomainFromDirectory(options.valueOf(domainFilename), groundingModel, options.has(generative), false, null, null);
    List<String> query = options.nonOptionArguments();
    
    GroundingPrediction prediction = groundingModel.getPredictionFromWords(query, domain, false);
    System.out.println("INPUT: " + Joiner.on(" ").join(query));
    System.out.println("PARSE: " + prediction.getSemanticParse());
    
    ParallelFactors predictedTensor = prediction.getQueryTree().getOutputLocalWeights();
    System.out.println("GROUNDING: " + predictedTensor.getTensorAssignmentString(prediction.getAssignment().getValue()));
  }
}
