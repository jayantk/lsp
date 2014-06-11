package edu.cmu.ml.rtw.users.jayantk.grounding;

import static ch.lambdaj.Lambda.extract;
import static ch.lambdaj.Lambda.on;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.jayantkrish.jklol.ccg.lambda.Expression;
import com.jayantkrish.jklol.cfg.BeamSearchCfgFactor;
import com.jayantkrish.jklol.cfg.ParseTree;
import com.jayantkrish.jklol.inference.MarginalCalculator.ZeroProbabilityError;
import com.jayantkrish.jklol.models.DiscreteObjectFactor;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.training.DefaultLogFunction;
import com.jayantkrish.jklol.training.GradientOracle;
import com.jayantkrish.jklol.training.LogFunction;
import com.jayantkrish.jklol.training.StochasticGradientTrainer;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.BoundedHeap;
import com.jayantkrish.jklol.util.IndexedList;
import com.jayantkrish.jklol.util.IoUtils;

import edu.cmu.ml.rtw.time.utils.IoUtil;
import edu.cmu.ml.rtw.users.jayantk.semparse.Lexicon;

public class GroundingModelTrainer implements GradientOracle<GroundingModel, GroundingExample>, Serializable {
  static final long serialVersionUID = 10275531232837410L;

  private final GroundingModelFamily family;
  private final GroundingInferenceAlgorithm inferenceAlgorithm;
  private final List<Domain> domains;
  private final IndexedList<String> domainNames;

  private final int maxParses;
  private final boolean addImplicitDeterminer;

  public GroundingModelTrainer(GroundingModelFamily family,
      GroundingInferenceAlgorithm inferenceAlgorithm,
      List<Domain> domains, IndexedList<String> domainNames, int maxParses,
      boolean addImplicitDeterminer) {

    this.family = Preconditions.checkNotNull(family);
    this.inferenceAlgorithm = Preconditions.checkNotNull(inferenceAlgorithm);
    this.domains = domains;
    this.domainNames = domainNames;

    this.maxParses = maxParses;
    this.addImplicitDeterminer = addImplicitDeterminer;
  }

  /**
   * Creates a grounding model trainer that uses dual decomposition to infer the
   * hidden variable values during training.
   * 
   * @param family the grounding model family to train.
   * @param maxDualDecompositionIter
   * @return
   */
  public static GroundingModelTrainer createWithDualDecomposition(GroundingModelFamily family,
      final int maxDualDecompositionIter, List<Domain> domains, IndexedList<String> domainNames,
								  boolean addImplicitDeterminer, int maxParses) {
      return new GroundingModelTrainer(family, new DdInferenceAlgorithm(maxDualDecompositionIter), 
				       domains, domainNames, maxParses, addImplicitDeterminer);
  }

  /**
   * Creates a grounding model that uses an integer linear program to exactly
   * infer the optimal hidden variable values during training.
   * 
   * @param family
   * @return
   */
  public static GroundingModelTrainer createWithIlp(GroundingModelFamily family,
      List<Domain> domains, IndexedList<String> domainNames, final boolean useLpRelaxation,
						    boolean addImplicitDeterminer, int maxParses) {
      return new GroundingModelTrainer(family, new IlpInferenceAlgorithm(useLpRelaxation), 
				       domains, domainNames, maxParses, addImplicitDeterminer);
  }

  @Override
    public double accumulateGradient(SufficientStatistics gradient, SufficientStatistics parameters, GroundingModel model,
      GroundingExample example, LogFunction log) {
    Tensor expectedGrounding = example.getGrounding();
    Domain domain = domains.get(domainNames.getIndex(example.getDomainName()));

    if (example.hasObservedRelation()) {
      // The training example provides truth-value observations of a particular semantic
      // predicate
      return accumulateGradientForPredicate(model, example.getObservedRelationName(), domain, expectedGrounding, gradient, parameters, log);
    } if (example.hasLogicalForm()) { 
	return accumulateGradientWithObservedLogicalForm(model, example.getWords(), example.getWordScores(),
                                                   example.getLogicalForm(), gradient, parameters, log);
    } else {
      // The training example does not have a semantic parse within it.
      List<List<String>> parserInput = example.getWords();
      log.logMessage(parserInput);
      return accumulateGradientWithUnobservedParse(model, parserInput, example.getWordScores(), domain, expectedGrounding, gradient, parameters, log);
    }
  }

  private double accumulateGradientForPredicate(GroundingModel model, String relationName,
                                                Domain domain, Tensor expectedGrounding, SufficientStatistics gradient, SufficientStatistics parameters, LogFunction log) {
    QueryTree queryGraph = model.getQueryForRelationName(relationName, domain);
    if(queryGraph == null){
	return 0.0;
    }

    MultiTree<Tensor> bestCostAugmentedAssignment = costAugmentedInference(queryGraph, expectedGrounding);
    double costAugmentedWeight = queryGraph.getWeight(bestCostAugmentedAssignment);

    QueryTree truthConditionedQuery = model.getQueryForRelationName(relationName, domain);
    MultiTree<Tensor> bestTruthConditionalAssignment = truthConditionalInference(truthConditionedQuery, expectedGrounding);
    double truthConditionalWeight = model.getQueryForRelationName(relationName, domain)
        .getWeight(bestTruthConditionalAssignment);

    log.logMessage("cost augmented: " + queryGraph.getAssignmentString(bestCostAugmentedAssignment));
    log.logMessage("truth conditioned: " + queryGraph.getAssignmentString(bestTruthConditionalAssignment));

    family.incrementGroundingParameters(domain.getName(), gradient, queryGraph, bestCostAugmentedAssignment, -1.0);
    family.incrementGroundingParameters(domain.getName(), gradient, truthConditionedQuery, bestTruthConditionalAssignment, 1.0);

    // Return the negative hinge loss
    return Math.min(0.0, truthConditionalWeight - costAugmentedWeight);
  }

  private double accumulateGradientWithObservedLogicalForm(GroundingModel model, List<List<String>> parserCandidates, 
							   double[] parserCandidateWeights, Expression logicalForm,
							   SufficientStatistics gradient, SufficientStatistics parameters, LogFunction log) {
      BeamSearchCfgFactor parser = model.getParser();
      List<WordParseCombination> bestParseCandidates = generateParseCandidates(parser, parserCandidates, parserCandidateWeights);
      System.out.println(bestParseCandidates.size() + " word combinations");

      List<String> bestPredictedWords = null, bestTrueWords = null;
      ParseTree bestPredictedParse = null, bestTrueParse = null;
      double bestPredictedScore = Double.NEGATIVE_INFINITY, bestTrueScore = Double.NEGATIVE_INFINITY;

      for (WordParseCombination candidateParse : bestParseCandidates) {
	  ParseTree parse = candidateParse.getParse();
	  double score = candidateParse.getWeight();
	  Expression predictedExpression = model.getLogicalFormFromParseTree(parse).simplify();
	  boolean functionallyEqual = predictedExpression.functionallyEquals(logicalForm.simplify());

	  if (functionallyEqual && score > bestTrueScore) {
	      bestTrueWords = candidateParse.getWords();
	      bestTrueParse = parse;
	      bestTrueScore = score;
	  }

	  double predictedScore = score;
	  if (!functionallyEqual) {
	      predictedScore += 1;
	  }
	  
	  if (predictedScore > bestPredictedScore) {
	      bestPredictedWords = candidateParse.getWords();
	      bestPredictedParse = parse;
	      bestPredictedScore = predictedScore;
	  }
      }

      if (bestPredictedParse == null || bestTrueParse == null) {
	  log.logMessage("search error: " + parserCandidates + " " + logicalForm);
	  throw new ZeroProbabilityError();
      }

      log.logMessage("predicted lf: " + bestPredictedScore + " " + model.getLogicalFormFromParseTree(bestPredictedParse));
      log.logMessage("actual lf: " + bestTrueScore + " " + model.getLogicalFormFromParseTree(bestTrueParse));

      family.incrementParserParameters(gradient, parameters, bestPredictedWords, bestPredictedParse, -1.0);
      family.incrementParserParameters(gradient, parameters, bestTrueWords, bestTrueParse, 1.0);

      return bestPredictedScore - bestTrueScore;
  }

  private double accumulateGradientWithUnobservedParse(GroundingModel model, List<List<String>> parserCandidates,
      double[] parserCandidateWeights,
      Domain domain, Tensor expectedGrounding, SufficientStatistics gradient, SufficientStatistics parameters, LogFunction log) {
    // TODO: this can be refactored using two calls to GroundingModelInference. 
    // (Do this after the TACL revisions.)

    // These store the highest-weight unconditional prediction and prediction
    // conditioned on the output.
    List<String> bestPredictedWords = null, bestTrueWords = null;
    ParseTree bestPredictedParse = null, bestTrueParse = null;
    MultiTree<Tensor> bestPredictedGrounding = null, bestTrueGrounding = null;
    MultiTree<Tensor> bestPredictedUnconditionalGrounding = null, bestTrueUnconditionalGrounding = null;
    QueryTree bestPredictedQuery = null, bestTrueQuery = null;
    double bestPredictedWeight = Double.NEGATIVE_INFINITY, bestTrueWeight = Double.NEGATIVE_INFINITY;
    double bestPredictedParseWeight = Double.NEGATIVE_INFINITY, bestTrueParseWeight = Double.NEGATIVE_INFINITY;
    double bestPredictedGroundingWeight = Double.NEGATIVE_INFINITY, bestTrueGroundingWeight = Double.NEGATIVE_INFINITY;

    BeamSearchCfgFactor parser = model.getParser();
    List<WordParseCombination> bestParseCandidates = generateParseCandidates(parser, parserCandidates, parserCandidateWeights);
    System.out.println(bestParseCandidates.size() + " word combinations");

    for (WordParseCombination candidateParse : bestParseCandidates) {
      // For each parse tree generated by the beam search, instantiate its
      // grounding factor graph and perform inference.
      ParseTree parse = candidateParse.getParse();
      // log.logMessage(parse);
      log.logMessage("inference: " + candidateParse.getWords() + " " + model.getSemanticParseFromParseTree(parse));
      double parseWeight = candidateParse.getWeight();

      // Instantiate the factor graph (/ probabilistic database query)
      // corresponding to
      // the current parse tree. Identify the best predicted grounding for this
      // tree.
      QueryTree queryGraph = model.getQueryFromParse(parse, domain, addImplicitDeterminer);

      // Get the weight of the best grounding with no conditioning on its output.
      // This value controls for the fact that not all predicates occur in every 
      // logical form.
      MultiTree<Tensor> bestUnconditionalAssignment = queryGraph.locallyDecodeVariables();
      double unconditionalWeight = queryGraph.getWeight(bestUnconditionalAssignment);

      // Find the best cost augmented assignment.
      MultiTree<Tensor> bestAssignment = null;
      double groundingWeight = Double.NEGATIVE_INFINITY;
      try {
        bestAssignment = costAugmentedInference(queryGraph, expectedGrounding);
        // log.logMessage(queryGraph.getAssignmentString(bestAssignment));
        groundingWeight = queryGraph.getWeight(bestAssignment) - unconditionalWeight;
      } catch (ZeroProbabilityError e) {} // groundingWeight = -infty 

      if (parseWeight + groundingWeight > bestPredictedWeight) {
        // This prediction is more likely than the current best prediction.
        bestPredictedWords = candidateParse.getWords();
        bestPredictedParse = parse;
        bestPredictedUnconditionalGrounding = bestUnconditionalAssignment;
        bestPredictedGrounding = bestAssignment;
        bestPredictedQuery = queryGraph;
        bestPredictedParseWeight = parseWeight;
        bestPredictedGroundingWeight = groundingWeight;
        bestPredictedWeight = parseWeight + groundingWeight;
      }

      // Instantiate factor graph (/ probabilistic database query), conditioning
      // on the observed grounding.
      QueryTree conditionalQueryGraph = model.getQueryFromParse(parse, domain, addImplicitDeterminer);
      MultiTree<Tensor> bestConditionalAssignment = null;
      double conditionalGroundingWeight = Double.NEGATIVE_INFINITY;
      try {
        bestConditionalAssignment = truthConditionalInference(conditionalQueryGraph, expectedGrounding);
        // log.logMessage(queryGraph.getAssignmentString(bestConditionalAssignment));
        QueryTree conditionalQueryGraphScorable = model.getQueryFromParse(parse, domain, addImplicitDeterminer);
        conditionalGroundingWeight = conditionalQueryGraphScorable.getWeight(bestConditionalAssignment) - unconditionalWeight;
      } catch (ZeroProbabilityError e) {} // groundingWeight = -infty

      if (Double.isInfinite(bestTrueWeight) || parseWeight + conditionalGroundingWeight >= bestTrueWeight) {
        bestTrueWords = candidateParse.getWords();
        bestTrueParse = parse;
        bestTrueUnconditionalGrounding = bestUnconditionalAssignment;
        bestTrueGrounding = bestConditionalAssignment;
        bestTrueQuery = conditionalQueryGraph;
        bestTrueParseWeight = parseWeight;
        bestTrueGroundingWeight = conditionalGroundingWeight;
        bestTrueWeight = parseWeight + conditionalGroundingWeight;
      }

      // System.err.println("grounding weight " + groundingWeight);
      Preconditions.checkState(!Double.isNaN(groundingWeight), "Grounding Weights are NaN:" + groundingWeight);
      Preconditions.checkState(!Double.isNaN(parseWeight), "Parse Weights are NaN");
      Preconditions.checkState(!Double.isNaN(conditionalGroundingWeight), "Conditional Weights are NaN");
    }

    log.logMessage("predicted input: " + bestPredictedWords);
    log.logMessage("predicted parse: " + model.getSemanticParseFromParseTree(bestPredictedParse));
    log.logMessage("true input: " + bestTrueWords);
    log.logMessage("true parse: " + model.getSemanticParseFromParseTree(bestTrueParse));
    log.logMessage("parse weights: " + bestPredictedParseWeight + " " + bestTrueParseWeight);
    log.logMessage("grounding weight deltas: " + bestPredictedGroundingWeight + " " + bestTrueGroundingWeight);
    log.logMessage("weights: " + bestPredictedWeight + " " + bestTrueWeight);

    if (bestPredictedParse == null || bestTrueParse == null) {
      throw new ZeroProbabilityError();
    }

    // Some examples may be unsatisfiable under the given grammar. Ignore them.
    if (Double.isInfinite(bestTrueWeight) && bestTrueWeight < 0) {
      return 0.0;
    }

    // Gradient is features of the best correct assignment - the best predicted
    // assignment,
    // assuming the prediction and truth disagree on the observed groundings.
    log.logMessage(bestPredictedWeight + " " + bestTrueWeight);
    log.logMessage("predicted grounding: " +
        bestPredictedQuery.getAssignmentString(bestPredictedGrounding));
    log.logMessage("true grounding: " +
        bestTrueQuery.getAssignmentString(bestTrueGrounding));
    family.incrementGroundingParameters(domain.getName(), gradient, bestPredictedQuery, bestPredictedGrounding, -1.0);
    family.incrementGroundingParameters(domain.getName(), gradient, bestPredictedQuery, bestPredictedUnconditionalGrounding, 1.0);
    family.incrementParserParameters(gradient, parameters, bestPredictedWords, bestPredictedParse, -1.0);
    family.incrementGroundingParameters(domain.getName(), gradient, bestTrueQuery, bestTrueGrounding, 1.0);
    family.incrementGroundingParameters(domain.getName(), gradient, bestTrueQuery, bestTrueUnconditionalGrounding, -1.0);
    family.incrementParserParameters(gradient, parameters, bestTrueWords, bestTrueParse, 1.0);

    // Return the negative hinge loss
    return Math.min(0.0, bestTrueWeight - bestPredictedWeight);
  }
  
  private List<WordParseCombination> generateParseCandidates(BeamSearchCfgFactor parser,
      List<List<String>> candidates, double[] candidateScores) {
    BoundedHeap<WordParseCombination> heap = new BoundedHeap<WordParseCombination>(maxParses,
        new WordParseCombination[0]);
    int numOffered = 0;
    for (int i = 0 ; i < candidates.size(); i++) {
      List<String> parserInput = candidates.get(i);
      DiscreteObjectFactor parseFactor = parser.conditional(
          parser.getTerminalVariable().outcomeArrayToAssignment(parserInput))
          .coerceToDiscreteObject();
      // TODO: Put weights on the different possible interpretations.
      // (This may be unnecessary since the parser may learn to highly weight 
      // likely words.)
      double wordWeight = candidateScores[i];
      for (Assignment parseAssignment : parseFactor.getMostLikelyAssignments(maxParses)) {
        // For each parse tree generated by the beam search, instantiate its
        // grounding factor graph and perform inference.
        ParseTree parse = (ParseTree) parseAssignment.getOnlyValue();
        // log.logMessage(parse);

        double parseWeight = parseFactor.getUnnormalizedLogProbability(parseAssignment);
        double totalWeight = parseWeight + wordWeight;
        WordParseCombination current = new WordParseCombination(parserInput, parse, totalWeight);
        heap.offer(current, current.getWeight());
        numOffered++;
      }
    }
    System.out.println("Num offered: " + numOffered);
    return heap.getItems();
  }

  /**
   * Augments {@code queryGraph} with costs (representing the margin) and
   * decodes the best assignment. After returning, {@code queryGraph} can be
   * used to evaluate the probability of the returned grounding.
   * 
   * @param queryGraph
   * @param expectedGrounding
   * @return
   */
  private MultiTree<Tensor> costAugmentedInference(QueryTree queryGraph, Tensor expectedGrounding) {
    // Uncomment this to train a structured SVM (as opposed to a structured
    // perceptron)
    // But uncommenting seems to make inference very slow.
    // When commented out, the model is a structured perceptron (with
    // regularization?)
    queryGraph.updateOutputLocalWeights(expectedGrounding.elementwiseProduct(-1.0).elementwiseAddition(1.0));
    // System.out.println("Running cost-augmented inference");
    return inferenceAlgorithm.apply(queryGraph, false);
  }

  /**
   * This method mutates {@code queryGraph} so that it is no longer suitable for
   * scoring the returned assignment. (Score the returned assignment with an
   * exact copy of {@code queryGraph}.) This occurs because each incorrect
   * output gets a negative infinite weight, and 0.0 * -Inf = NaN.
   */
  private MultiTree<Tensor> truthConditionalInference(QueryTree queryGraph, Tensor expectedGrounding) {
    queryGraph.updateOutputLocalWeights(expectedGrounding.elementwiseLog());
    // System.out.println("Running truth-conditioned inference");
    return inferenceAlgorithm.apply(queryGraph, true);
  }

  @Override
  public SufficientStatistics initializeGradient() {
    return family.getNewSufficientStatistics();
  }

  @Override
  public GroundingModel instantiateModel(SufficientStatistics parameters) {
    return family.instantiateModel(parameters);
  }

  public static SufficientStatistics trainGroundingModel(GroundingModelFamily family,
      Iterable<GroundingExample> trainingData, int iterations, int dualDecompositionIterations,
      double initialStepSize, boolean decayStepSize, int batchSize, String inferenceAlgorithm, double l2regularization, 
      double l1regularization, List<Domain> domains, IndexedList<String> domainNames, boolean useCurriculum, boolean addImplicitDeterminer,
							 SufficientStatistics initialParams, LogFunction log, int maxParses) {

    GroundingModelTrainer oracle = null;
    if (inferenceAlgorithm.equals("dualDecomposition")) {
	oracle = GroundingModelTrainer.createWithDualDecomposition(family, dualDecompositionIterations, domains, domainNames, addImplicitDeterminer, maxParses);
    } else if (inferenceAlgorithm.equals("ilp")) {
	oracle = GroundingModelTrainer.createWithIlp(family, domains, domainNames, false, addImplicitDeterminer, maxParses);
    } else if (inferenceAlgorithm.equals("lp")) {
	oracle = GroundingModelTrainer.createWithIlp(family, domains, domainNames, true, addImplicitDeterminer, maxParses);
    }

    StochasticGradientTrainer trainer;
    if (l2regularization > 0.0) {
      trainer = StochasticGradientTrainer.createWithL2Regularization(
          iterations * Iterables.size(trainingData) / batchSize, batchSize, initialStepSize, decayStepSize, false,
          l2regularization, log);
    } else {
      trainer = StochasticGradientTrainer.createWithL1Regularization(
          iterations * Iterables.size(trainingData) / batchSize, batchSize, initialStepSize, decayStepSize, false,
          l1regularization, log);
    }

    // Train the model in stages.
    if (useCurriculum) {
      int maxLevel = 0;
      for (GroundingExample datum : trainingData) {
        maxLevel = (int) Math.max(maxLevel, datum.getCurriculumLevel());
      }
      maxLevel++;
      System.out.println(maxLevel + " Curriculum Levels");
      for (int i = 0; i < maxLevel; i++) {
        System.out.println("Training level: " + i);
        List<GroundingExample> currentData = Lists.newArrayList();
        for (GroundingExample datum : trainingData) {
          if (datum.getCurriculumLevel() <= i) {
            currentData.add(datum);
          }
        }
        initialParams = trainer.train(oracle, initialParams, currentData);
      }
    } else {
      initialParams = trainer.train(oracle, initialParams, trainingData);
    }

    return initialParams;
  }

  public static List<GroundingExample> filterParseableExamples(GroundingModelFamily family,
      Iterable<GroundingExample> trainingData, boolean expectAllParseable) {
    List<GroundingExample> filteredData = Lists.newArrayList();
    GroundingModel model = family.instantiateModel(family.getNewSufficientStatistics());
    boolean allParseable = true;
    for (GroundingExample example : trainingData) {
      if (example.hasObservedRelation()) {
        filteredData.add(example);
        continue;
      }
      BeamSearchCfgFactor parser = model.getParser();
      boolean parseable = false;
      for (List<String> words : example.getWords()) {
        System.out.println(words);
	System.out.println(example.hasLogicalForm() ? example.getLogicalForm() : "NO LF");
        DiscreteObjectFactor parseFactor = parser.conditional(
            parser.getTerminalVariable().outcomeArrayToAssignment(words))
            .coerceToDiscreteObject();
        if (parseFactor.size() == 0) {
          allParseable = false;
          System.out.println("NO PARSE OF: " + words);
        } else {
          parseable = true;
        }
      }
      if (parseable) {
        filteredData.add(example);
      }
    }

    Preconditions.checkState(!expectAllParseable || allParseable);
    return filteredData;
  }

  public static List<GroundingExample> filterBySupervision(Iterable<GroundingExample> data, boolean fullSupervision) {
      List<GroundingExample> returnVals = Lists.newArrayList();
      for (GroundingExample example : data) {
	  if (fullSupervision && (example.hasLogicalForm() || example.hasObservedRelation())) {
	      returnVals.add(example);
	  } else if (!fullSupervision) {
	      if (example.hasLogicalForm()) {
		  // Strip logical forms.
		  returnVals.add(new GroundingExample(example.getWords(), example.getWordScores(), example.getGrounding(), example.getDomainName(), example.getCurriculumLevel(), null));
	      } 
	      else if(!example.hasObservedRelation()){
		  returnVals.add(example);
	      }
	  }
      }
      return returnVals;
  }

  public static Multimap<String, GroundingExample> getCrossValidationFolds(List<Domain> domains) {
    Multimap<String, GroundingExample> map = ArrayListMultimap.create();
    for (int i = 0; i < domains.size(); i++) {
      String curFold = domains.get(i).getName();
      if (domains.get(i).getTrainingExamples().size() == 0) {
        // domains with no training examples can be skipped without
        // affecting the error.
        continue;
      }
      for (int j = 0; j < domains.size(); j++) {
        if (i == j) {
          continue;
        }
        else {
          map.putAll(curFold, domains.get(j).getTrainingExamples());
        }
      }
    }
    return map;
  }

  public static void main(String[] args) throws Exception {
    OptionParser parser = new OptionParser();
    OptionSpec<String> domainDir = parser.accepts("domainDir").withRequiredArg().ofType(String.class).required();
    OptionSpec<String> trainingFilename = parser.accepts("trainingFilename").withOptionalArg().ofType(String.class).defaultsTo("training.txt");
    OptionSpec<String> goldKbFile = parser.accepts("goldKbFile").withRequiredArg().ofType(String.class);
    OptionSpec<String> ccgLexicon = parser.accepts("lexicon").withRequiredArg().ofType(String.class).required();
    OptionSpec<Integer> iterations = parser.accepts("iterations").withOptionalArg().ofType(Integer.class).defaultsTo(5);
    OptionSpec<String> modelFilename = parser.accepts("modelFilename").withRequiredArg().ofType(String.class).required();
    OptionSpec<String> modelFamilyFilename = parser.accepts("modelFamilyFilename").withRequiredArg().ofType(String.class);
    OptionSpec<String> modelParametersFilename = parser.accepts("modelParametersFilename").withRequiredArg().ofType(String.class);

    OptionSpec<Integer> dualDecompositionIterations = parser.accepts("ddIterations").withOptionalArg().ofType(Integer.class).defaultsTo(1000);
    parser.accepts("rescaleGroundingObjective");
    parser.accepts("useIlp");
    parser.accepts("useLp");
    parser.accepts("crossValidation");
    parser.accepts("noCurriculum");
    OptionSpec<Void> implicitDeterminer = parser.accepts("implicitDeterminer");
    OptionSpec<Void> fullSupervision = parser.accepts("fullSupervision");

    OptionSpec<Integer> batchSize = parser.accepts("batchSize").withOptionalArg().ofType(Integer.class).defaultsTo(1);
    OptionSpec<Double> initialStepSize = parser.accepts("initialStepSize").withOptionalArg().ofType(Double.class).defaultsTo(1.0);
    OptionSpec<Integer> maxTrainingExamples = parser.accepts("maxTrainingExamples").withOptionalArg().ofType(Integer.class).defaultsTo(10000000);
    OptionSpec<Double> l2regularization = parser.accepts("l2regularization").withOptionalArg().ofType(Double.class).defaultsTo(0.00);
    OptionSpec<Double> l1regularization = parser.accepts("l1regularization").withOptionalArg().ofType(Double.class).defaultsTo(0.00);
    OptionSpec<Integer> maxParsesForInference = parser.accepts("maxParses").withOptionalArg().ofType(Integer.class).defaultsTo(10);
    parser.accepts("skipUnparseable");

    OptionSet options = parser.parse(args);

    // Provide either l2 or l1 regularization, not both.
    Preconditions.checkArgument(!(options.has(l2regularization) && options.has(l1regularization)));
    // Either LP or ILP inference or neither (dual decomposition). 
    Preconditions.checkArgument(!(options.has("useIlp") && options.has("useLp")));

    // Read domains and lexicon from files, construct the grounding model family.
    String goldKbFilename = options.has(goldKbFile) ? options.valueOf(goldKbFile) : null;
    List<Domain> domains = Domain.readDomainsFromDirectory(options.valueOf(domainDir), 
        options.valueOf(trainingFilename), goldKbFilename, options.valueOf(maxTrainingExamples),
        false, options.has("rescaleGroundingObjective"), false);
    System.out.println(domains);
    IndexedList<String> domainNames = IndexedList.create(extract(domains, on(Domain.class).getName()));
    GroundingModelFamily family = GroundingModelUtilities.constructGroundingModel(domains,
										  Lexicon.fromFile(IoUtil.LoadFile(options.valueOf(ccgLexicon))));

    // Choose an inference procedure.
    String inferenceAlg = "dualDecomposition";
    if (options.has("useIlp")) {
      inferenceAlg = "ilp";
    } else if (options.has("useLp")) {
      inferenceAlg = "lp";
    }
    System.out.println("Inference algorithm: " + inferenceAlg);

    // Construct cross-validation folds, if necessary.
    Multimap<String, GroundingExample> folds = null;
    if (options.has("crossValidation")) {
      folds = getCrossValidationFolds(domains);
    } else {
      folds = ArrayListMultimap.create();
      folds.putAll("default", Iterables.concat(extract(domains, on(Domain.class).getTrainingExamples())));
    }

    // Filter out unparseable examples, or throw an error if an example cannot be parsed.
    Multimap<String, GroundingExample> filteredFolds = ArrayListMultimap.create();
    for (String key : folds.keySet()) {
      Collection<GroundingExample> foldExamples = folds.get(key);
      List<GroundingExample> filtered = filterParseableExamples(family, foldExamples, !options.has("skipUnparseable"));
      List<GroundingExample> supervisionFiltered = filterBySupervision(filtered, options.has(fullSupervision));
      filteredFolds.putAll(key, supervisionFiltered);
      System.out.println("fold: " + key + " " + supervisionFiltered.size() + " training examples");
    }

    Map<String, GroundingModel> modelFolds = Maps.newHashMap();
    Map<String, SufficientStatistics> modelParams = Maps.newHashMap();
    for (String key : filteredFolds.keySet()) {
      Collection<GroundingExample> foldExamples = filteredFolds.get(key);
      SufficientStatistics trainedParams = GroundingModelTrainer.trainGroundingModel(family, foldExamples, options.valueOf(iterations),
          options.valueOf(dualDecompositionIterations), options.valueOf(initialStepSize), true, options.valueOf(batchSize), inferenceAlg,
          options.valueOf(l2regularization), options.valueOf(l1regularization), domains, domainNames, !options.has("noCurriculum"), options.has(implicitDeterminer),
										     family.getNewSufficientStatistics(), new DefaultLogFunction(), options.valueOf(maxParsesForInference));

      // System.out.println(family.getParameterDescription(trainedParams));
      GroundingModel trainedGroundingModel = family.instantiateModel(trainedParams);
      modelFolds.put(key, trainedGroundingModel);
      modelParams.put(key, trainedParams);

      System.out.println("TRAINING DATA: ");
      GroundingModelUtilities.logDatasetError(trainedGroundingModel, foldExamples, domains, null, options.has(implicitDeterminer));
    }

    /* Save the model to a java serialized file */
    try
    {
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
    } catch (IOException ex)
    {
      ex.printStackTrace();
    }

    if (options.has(modelFamilyFilename)) {
      System.out.println("Saving family to " + options.valueOf(modelFamilyFilename));
      IoUtils.serializeObjectToFile(family, options.valueOf(modelFamilyFilename));
    }

    if (options.has(modelParametersFilename)) {
      System.out.println("Saving parameters to " + options.valueOf(modelParametersFilename));
      if (!options.has("crossValidation")) {
        IoUtils.serializeObjectToFile(Iterables.getOnlyElement(modelParams.values()), 
            options.valueOf(modelParametersFilename));
      } else {
        IoUtils.serializeObjectToFile(modelParams,
            options.valueOf(modelParametersFilename));
      }
    }

    System.exit(0);
  }

  private static class WordParseCombination {
    private final List<String> words;
    private final ParseTree parse;
    private final double weight;

    public WordParseCombination(List<String> words, ParseTree parse, double weight) {
      this.words = words;
      this.parse = parse;
      this.weight = weight;
    }

    public List<String> getWords() {
      return words;
    }

    public ParseTree getParse() {
      return parse;
    }

    public double getWeight() {
      return weight;
    }
  }

  private static interface GroundingInferenceAlgorithm {
      public MultiTree<Tensor> apply(QueryTree query, boolean applyWeakSupervision);
  }

  private static class IlpInferenceAlgorithm implements GroundingInferenceAlgorithm {
      private final boolean useLpRelaxation;

      public IlpInferenceAlgorithm(boolean useLpRelaxation) {
	  this.useLpRelaxation = useLpRelaxation;
      }

      @Override
      public MultiTree<Tensor> apply(QueryTree query, boolean applyWeakSupervision) {
	  if (query.isLeaf()) {
	      // This case is easy, so don't bother loading up the ILP solver.
	      return query.locallyDecodeVariables();
	  }
	  return query.ilpInference(useLpRelaxation, applyWeakSupervision);
      }
  }

  private static class DdInferenceAlgorithm implements GroundingInferenceAlgorithm {
      private final int maxDualDecompositionIter;

      public DdInferenceAlgorithm(int maxDualDecompositionIter) {
	  this.maxDualDecompositionIter = maxDualDecompositionIter;
      }

      @Override
      public MultiTree<Tensor> apply(QueryTree query, boolean applyWeakSupervision) {
	  QueryTree reparameterizedQuery = query.copy();
	  reparameterizedQuery.dualDecomposition(maxDualDecompositionIter);
	  return reparameterizedQuery.locallyDecodeVariables();
      }
  }
}
