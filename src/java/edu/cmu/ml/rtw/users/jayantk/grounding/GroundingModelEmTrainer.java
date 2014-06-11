package edu.cmu.ml.rtw.users.jayantk.grounding;

import static ch.lambdaj.Lambda.extract;
import static ch.lambdaj.Lambda.on;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.base.Function;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.jayantkrish.jklol.cli.AbstractCli;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.training.DefaultLogFunction;
import com.jayantkrish.jklol.training.ExpectationMaximization;
import com.jayantkrish.jklol.util.IndexedList;

import edu.cmu.ml.rtw.time.utils.IoUtil;
import edu.cmu.ml.rtw.users.jayantk.semparse.Lexicon;

/**
 * Command line program for training the generative grounding model
 * using hard EM.
 * 
 * @author jayantk
 */
public class GroundingModelEmTrainer extends AbstractCli {
  
  private OptionSpec<String> domainDir;
  private OptionSpec<String> trainingFilename;
  private OptionSpec<String> ccgLexicon;
  private OptionSpec<Integer> iterations;
  private OptionSpec<Integer> maxParses;
  private OptionSpec<String> modelFilename;
  
  private OptionSpec<Integer> maxTrainingExamples;
  private OptionSpec<Integer> maxCurriculumLevel;
  private OptionSpec<Void> noCurriculum;
  private OptionSpec<Void> crossValidation;
  private OptionSpec<Void> skipUnparseable;
  private OptionSpec<Void> standardizeFeatures;
    private OptionSpec<Void> hardEm;
  
  public GroundingModelEmTrainer() {
    super(CommonOptions.MAP_REDUCE);
  }
  
  @Override
  public void initializeOptions(OptionParser parser) {
    domainDir = parser.accepts("domainDir").withRequiredArg().ofType(String.class).required();
    trainingFilename = parser.accepts("trainingFilename").withOptionalArg().ofType(String.class).defaultsTo("training.txt");
    ccgLexicon = parser.accepts("lexicon").withRequiredArg().ofType(String.class).required();
    iterations = parser.accepts("iterations").withOptionalArg().ofType(Integer.class).defaultsTo(5);
    maxParses = parser.accepts("maxParses").withOptionalArg().ofType(Integer.class).defaultsTo(10);
    modelFilename = parser.accepts("modelFilename").withRequiredArg().ofType(String.class).required();
    
    maxTrainingExamples = parser.accepts("maxTrainingExamples").withRequiredArg()
        .ofType(Integer.class).defaultsTo(1000000);
    
    crossValidation = parser.accepts("crossValidation");
    noCurriculum = parser.accepts("noCurriculum");
    maxCurriculumLevel = parser.accepts("maxCurriculumLevel").withRequiredArg().ofType(Integer.class).defaultsTo(Integer.MAX_VALUE);

    skipUnparseable = parser.accepts("skipUnparseable");
    standardizeFeatures = parser.accepts("standardizeFeatures");
    hardEm = parser.accepts("hardEm");
  }
  
  public static SufficientStatistics trainGroundingModel(GroundingModelFamily family,
      Iterable<GroundingExample> trainingData, int iterations, int maxParses, List<Domain> domains, 
							 IndexedList<String> domainNames, boolean useCurriculum, int maxCurriculumLevel, boolean useHardEm) {

    Function<QueryTree,MultiTree<Tensor>> inference = new Function<QueryTree, MultiTree<Tensor>>() {
      @Override
      public MultiTree<Tensor> apply(QueryTree query) {
        if (query.isLeaf()) {
          // This case is easy, so don't bother loading up the ILP solver.
          return query.locallyDecodeVariables();
        }
        return query.ilpInference(false, true);
      }
    };
    GroundingModelInference groundingInference = new GroundingModelInference(inference, maxParses);
    GroundingModelEmOracle oracle = new GroundingModelEmOracle(family, groundingInference, domains,
        domainNames, 1.0, 0.5, 0.5, useHardEm);

    ExpectationMaximization em = new ExpectationMaximization(iterations, new DefaultLogFunction());
    SufficientStatistics initialParams = oracle.smoothParameters(family.getNewSufficientStatistics());

    // Train the model in stages.
    if (useCurriculum) {
      int maxLevel = 0;
      for (GroundingExample datum : trainingData) {
        maxLevel = (int) Math.max(maxLevel, datum.getCurriculumLevel());
      }
      maxLevel = Math.min(maxCurriculumLevel + 1, maxLevel + 1);
      System.out.println(maxLevel + " Curriculum Levels");
      for (int i = 0; i < maxLevel; i++) {
        System.out.println("Training level: " + i);
        List<GroundingExample> currentData = Lists.newArrayList();
        for (GroundingExample datum : trainingData) {
          if (datum.getCurriculumLevel() <= i) {
            currentData.add(datum);
          }
        }
        initialParams = em.train(oracle, initialParams, currentData);
      }
    } else {
      initialParams = em.train(oracle, initialParams, trainingData);
    }
    return initialParams;
  }

  @Override
  public void run(OptionSet options) {
    // Read domains and lexicon from files, construct the grounding model family.
    List<Domain> domains = Domain.readDomainsFromDirectory(options.valueOf(domainDir), 
        options.valueOf(trainingFilename), null, options.valueOf(maxTrainingExamples), true, 
							   false, options.has(standardizeFeatures));
    System.out.println(domains);
    IndexedList<String> domainNames = IndexedList.create(extract(domains, on(Domain.class).getName()));
    GroundingModelFamily family = GroundingModelUtilities.constructGroundingModel(domains,
        Lexicon.fromFile(IoUtil.LoadFile(options.valueOf(ccgLexicon))));

    // Choose an inference procedure.
    String inferenceAlg = "ilp";
    System.out.println("Inference algorithm: " + inferenceAlg);

    // Construct cross-validation folds, if necessary.
    Multimap<String, GroundingExample> folds = null;
    if (options.has(crossValidation)) {
      folds = GroundingModelTrainer.getCrossValidationFolds(domains);
    } else {
      folds = ArrayListMultimap.create();
      folds.putAll("default", Iterables.concat(extract(domains, on(Domain.class).getTrainingExamples())));
    }

    // Filter out unparseable examples, or throw an error if an example cannot be parsed.
    Multimap<String, GroundingExample> filteredFolds = ArrayListMultimap.create();
    for (String key : folds.keySet()) {
      Collection<GroundingExample> foldExamples = folds.get(key);
      List<GroundingExample> filtered = GroundingModelTrainer.filterParseableExamples(family, foldExamples, !options.has(skipUnparseable));
      filteredFolds.putAll(key, filtered);
      System.out.println("fold: " + key + " " + filtered.size() + " training examples");
    }

    Map<String, GroundingModel> modelFolds = Maps.newHashMap();
    Map<String, SufficientStatistics> modelParams = Maps.newHashMap();
    for (String key : filteredFolds.keySet()) {
      Collection<GroundingExample> foldExamples = filteredFolds.get(key);
      SufficientStatistics trainedParams = GroundingModelEmTrainer.trainGroundingModel(family, foldExamples,
										       options.valueOf(iterations), options.valueOf(maxParses), domains, domainNames, !options.has(noCurriculum),
										       options.valueOf(maxCurriculumLevel), options.has(hardEm));

      // System.out.println(family.getParameterDescription(trainedParams));
      GroundingModel trainedGroundingModel = family.instantiateModel(trainedParams);
      modelFolds.put(key, trainedGroundingModel);
      modelParams.put(key, trainedParams);

      System.out.println("TRAINING DATA: ");
      GroundingModelUtilities.logDatasetError(trainedGroundingModel, foldExamples, domains, null, false);
    }

    /* Save the model to a java serialized file */
    try {
      System.out.println("Saving to " + options.valueOf(modelFilename));
      FileOutputStream fos = new FileOutputStream(options.valueOf(modelFilename));
      ObjectOutputStream out = new ObjectOutputStream(fos);

      if (!options.has("crossValidation")) {
        out.writeObject(Iterables.getOnlyElement(modelFolds.values()));
      } else {
        out.writeObject(modelFolds);
      }
      out.close();
      fos.close();
    } catch (IOException ex) {
      ex.printStackTrace();
    }

    System.exit(0);
  }

  public static void main(String[] args) {
    new GroundingModelEmTrainer().run(args);
  }
}
