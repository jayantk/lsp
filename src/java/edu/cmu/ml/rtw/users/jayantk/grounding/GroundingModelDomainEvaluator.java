package edu.cmu.ml.rtw.users.jayantk.grounding;

import static ch.lambdaj.Lambda.extract;
import static ch.lambdaj.Lambda.on;

import java.util.List;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.collect.Iterables;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.training.DefaultLogFunction;
import com.jayantkrish.jklol.util.IndexedList;

import edu.cmu.ml.rtw.time.utils.IoUtil;
import edu.cmu.ml.rtw.users.jayantk.semparse.Lexicon;

public class GroundingModelDomainEvaluator {
		public static void main(String[] args) throws Exception {
		    OptionParser parser = new OptionParser();
		    OptionSpec<String> domainDir = parser.accepts("domainDir").withRequiredArg().ofType(String.class).required();
		    OptionSpec<String> ccgLexicon = parser.accepts("lexicon").withRequiredArg().ofType(String.class).required();
		    OptionSpec<String> modelFilename = parser.accepts("modelFilename").withRequiredArg().ofType(String.class).required();
        OptionSpec<Integer> iterations = parser.accepts("iterations").withOptionalArg().ofType(Integer.class).defaultsTo(5);
        OptionSpec<Integer> dualDecompositionIterations = parser.accepts("ddIterations").withOptionalArg().ofType(Integer.class).defaultsTo(1000);
        OptionSpec<Double> initialStepSize = parser.accepts("initialStepSize").withOptionalArg().ofType(Double.class).defaultsTo(1.0);
        OptionSpec<Integer> maxTrainingExamples = parser.accepts("maxTrainingExamples").withOptionalArg().ofType(Integer.class).defaultsTo(10000000);
        OptionSpec<Double> l2regularization = parser.accepts("l2regularization").withOptionalArg().ofType(Double.class).defaultsTo(0.01);
        OptionSpec<Double> l1regularization = parser.accepts("l1regularization").withOptionalArg().ofType(Double.class).defaultsTo(0.00);
        
		    OptionSet options = parser.parse(args);

		    System.out.println("creating new model");
		    List<Domain> domains = Domain.readDomainsFromDirectory(options.valueOf(domainDir), "training.txt",
									   null, options.valueOf(maxTrainingExamples), false, false, false);
		    IndexedList<String> domainNames = IndexedList.create(extract(domains, on(Domain.class).getName()));
		    
		    GroundingModelFamily family = GroundingModelUtilities.constructGroundingModel(domains, Lexicon.fromFile(IoUtil.LoadFile(options.valueOf(ccgLexicon))));
		    
		    // adaptation dataset
		    Iterable<GroundingExample> adaptationData = Iterables.concat(extract(domains, on(Domain.class).getTrainingExamples())); 
		    SufficientStatistics initialParams = family.getNewSufficientStatistics();
		    initialParams = GroundingModelTrainer.trainGroundingModel(family, adaptationData, 
									      options.valueOf(iterations), options.valueOf(dualDecompositionIterations), options.valueOf(initialStepSize), true, 1, "ilp", 
												   options.valueOf(l2regularization), options.valueOf(l1regularization), 
									      domains, domainNames, true, false, initialParams, new DefaultLogFunction(), 10);
		    
		    GroundingModel untrainedGroundingModel = family.instantiateModel(initialParams);
		    
		    System.out.println("loading trained model");
		    GroundingModel trainedGroundingModel = GroundingModel.fromSerializedFile(options.valueOf(modelFilename));

		    System.out.println("transferring parameters");
		    GroundingModel.transferParameters(trainedGroundingModel, untrainedGroundingModel);
		    		    
		    //run on test data
        Iterable<GroundingExample> testData = Iterables.concat(extract(domains, on(Domain.class).getTestExamples()));

		    System.out.println("TEST DATA: ");
		    GroundingModelUtilities.logDatasetError(untrainedGroundingModel, testData, domains, null, false);
		    
		    System.out.println("saving to convertedModel.ser");
		    GroundingModel.toSerializedFile("convertedModel.ser", untrainedGroundingModel);
		 }
}
