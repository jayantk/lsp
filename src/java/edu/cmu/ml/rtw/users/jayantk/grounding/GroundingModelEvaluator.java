package edu.cmu.ml.rtw.users.jayantk.grounding;
import static ch.lambdaj.Lambda.extract;
import static ch.lambdaj.Lambda.on;

import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.util.List;
import java.util.Map;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.jayantkrish.jklol.util.IndexedList;

import edu.cmu.ml.rtw.users.jayantk.grounding.GroundingModelUtilities.EvaluationScore;

public class GroundingModelEvaluator {
  public static void main(String[] args) throws Exception {
    OptionParser parser = new OptionParser();
    OptionSpec<String> domainDir = parser.accepts("domainDir").withRequiredArg().ofType(String.class).required();
    OptionSpec<String> modelFilename = parser.accepts("modelFilename").withRequiredArg().ofType(String.class).required();
    // The name of the training file.
    OptionSpec<String> crossValidationTrainingFile = parser.accepts("crossValidationTrainingFile").withRequiredArg().ofType(String.class).defaultsTo("training.txt");
    OptionSpec<String> goldKbFile = parser.accepts("goldKbFile").withRequiredArg().ofType(String.class);

    OptionSpec<Integer> maxTrainingExamples = parser.accepts("maxTrainingExamples").withOptionalArg().ofType(Integer.class).defaultsTo(10000000);
    parser.accepts("testOnTraining");
    parser.accepts("crossValidation");
    parser.accepts("skipUnparseable");
    OptionSpec<Void> implicitDeterminer = parser.accepts("implicitDeterminer");
    OptionSpec<Void> generative = parser.accepts("generative");
    OptionSet options = parser.parse(args);

    String goldKbFilename = options.has(goldKbFile) ? options.valueOf(goldKbFile) : null;

    if (options.has("crossValidation")) {
      Map<String, GroundingModel> modelFolds = readModelFolds(options.valueOf(modelFilename));
      GroundingModel sample = Iterables.get(modelFolds.values(), 0);
      List<Domain> domains = Domain.readDomainsFromDirectory(options.valueOf(domainDir), options.valueOf(crossValidationTrainingFile), 
          goldKbFilename, options.valueOf(maxTrainingExamples), sample, options.has(generative), false, false);

      testCrossValidation(modelFolds, domains, options.has(implicitDeterminer));
      return;
    }

    GroundingModel trainedGroundingModel = GroundingModel.fromSerializedFile(options.valueOf(modelFilename));
    List<Domain> domains = Domain.readDomainsFromDirectory(options.valueOf(domainDir), options.valueOf(crossValidationTrainingFile), 
        goldKbFilename, options.valueOf(maxTrainingExamples), trainedGroundingModel, options.has(generative), 
							   false, false);

    Iterable<GroundingExample> testData;
    if(options.has("testOnTraining")){
      testData = Iterables.concat(extract(domains, on(Domain.class).getTrainingExamples()));
    }
    else{
      testData = Iterables.concat(extract(domains, on(Domain.class).getTestExamples()));   
    }

    //System.out.println(trainedGroundingModel.getParameterDescriptionXML());
    System.out.println(trainedGroundingModel.getParameterDescription(20));

    //run on test data
    System.out.println("TEST DATA: ");
    GroundingModelUtilities.logDatasetError(trainedGroundingModel, testData, domains, null, options.has(implicitDeterminer));
  }

  public static Map<String, GroundingModel> readModelFolds(String modelFilename) {
    Map<String, GroundingModel> trainedModels = null;
    try {
      //load the grounding model
      System.out.println("Loading file:" + modelFilename);
      ObjectInputStream in = new ObjectInputStream(new FileInputStream(modelFilename));
      trainedModels = (Map<String, GroundingModel>)in.readObject();
      in.close();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return trainedModels;
  }

  public static void testCrossValidation(Map<String, GroundingModel> trainedModels, List<Domain> domains,
      boolean implicitDeterminer) {
    Map<Integer, EvaluationScore> scores = Maps.newHashMap();
    IndexedList<String> domainNames = IndexedList.create(extract(domains, on(Domain.class).getName()));
    for (String key : trainedModels.keySet()) {
      Iterable<GroundingExample> testData = domains.get(domainNames.getIndex(key)).getTrainingExamples();
      GroundingModel model = trainedModels.get(key);
      Map<Integer, EvaluationScore> foldScores = GroundingModelUtilities
          .logDatasetError(model, testData, domains, null, implicitDeterminer);

      for (Integer level : foldScores.keySet()) {
        if (!scores.containsKey(level)) {
          scores.put(level, foldScores.get(level));
        } else {
          scores.put(level, scores.get(level).add(foldScores.get(level)));
        }
      }
    }

    System.out.println("ALL FOLDS");
    EvaluationScore overall = EvaluationScore.zero();
    for (int level : scores.keySet()) {
      System.out.println("LEVEL " + level);
      System.out.println(scores.get(level));
      overall = overall.add(scores.get(level));
    }

    System.out.println("OVERALL");
    System.out.println(overall);
  }
}
