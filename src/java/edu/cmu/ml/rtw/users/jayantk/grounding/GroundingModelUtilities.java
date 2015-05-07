package edu.cmu.ml.rtw.users.jayantk.grounding;

import static ch.lambdaj.Lambda.extract;
import static ch.lambdaj.Lambda.on;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.ccg.lambda.Expression;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.cfg.ParametricCfgFactor;
import com.jayantkrish.jklol.cfg.ParseTree;
import com.jayantkrish.jklol.lisp.SExpression;
import com.jayantkrish.jklol.models.ObjectVariable;
import com.jayantkrish.jklol.models.TableFactorBuilder;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.tensor.DenseTensor;
import com.jayantkrish.jklol.tensor.SparseTensor;
import com.jayantkrish.jklol.tensor.SparseTensorBuilder;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.util.AllAssignmentIterator;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.IndexedList;

import edu.cmu.ml.rtw.time.utils.IoUtil;
import edu.cmu.ml.rtw.users.jayantk.grounding.GroundingModel.GroundingPrediction;
import edu.cmu.ml.rtw.users.jayantk.semparse.DirectedFunctionType;
import edu.cmu.ml.rtw.users.jayantk.semparse.Lexicon;
import edu.cmu.ml.rtw.users.jayantk.semparse.RelationType;
import edu.cmu.ml.rtw.users.jayantk.semparse.SemanticParse;
import edu.cmu.ml.rtw.users.jayantk.semparse.SemanticParser;
import edu.cmu.ml.rtw.users.jayantk.semparse.SemanticPredicate;

public class GroundingModelUtilities implements Serializable {
  static final long serialVersionUID = 10275539472837410L;

  public static List<GroundingExample> readTrainingData(String domainName, String trainingDataFile,
      VariableNumMap groundingVar1, VariableNumMap groundingVar2, VariableNumMap booleanVar) {

    ExpressionParser<Expression> parser = ExpressionParser.lambdaCalculus();
    ExpressionParser<SExpression> sParser = ExpressionParser.sExpression(IndexedList.<String>create());
    List<GroundingExample> convertedData = Lists.newArrayList();
    for (String line : IoUtil.LoadFile(trainingDataFile)) {
      if (line.trim().length() == 0 || line.startsWith("#")) {
        continue;
      }

      String[] parts = line.split(";");
      System.out.println("myline->"+line);

      // Parse the grounding into a tensor of indicator variables.
      Tensor outputTensor = parseGroundingTensor(parts, groundingVar1, groundingVar2, booleanVar);
      System.out.println(outputTensor);

      int curriculumLevel = 0;
      Expression logicalForm = null;
      SExpression cfgParse = null;
      Expression autogeneratedLogicalForm = null;
      if (parts.length > 2) {
        curriculumLevel = Integer.parseInt(parts[2]);
      }

      if (parts.length > 3 && !parts[3].replaceAll(" ", "").isEmpty()) {
        logicalForm = parser.parseSingleExpression(parts[3]);
      }
      
      if (parts.length > 4) {
        cfgParse = sParser.parseSingleExpression(parts[4]);
      }
      
      if (parts.length > 5) {
        if (!parts[5].startsWith("NO")) {
          autogeneratedLogicalForm = parser.parseSingleExpression(parts[5]);
        }
      }

      if (line.startsWith("*")) {
        // This example contains labels for a semantic predicate.
        String relationName = parts[0].substring(1);
        convertedData.add(new GroundingExample(relationName, outputTensor, domainName, curriculumLevel, logicalForm));
      } else {
        List<String> input = Arrays.asList(parts[0].toLowerCase().split("[ ,]"));
        List<List<String>> inputCandidates = new ArrayList<List<String>>();
        double[] inputScores = new double[] { 0 };
        inputCandidates.add(input);
        convertedData.add(new GroundingExample(inputCandidates, inputScores, outputTensor, domainName,
            curriculumLevel, logicalForm, cfgParse, autogeneratedLogicalForm));
      }
    }
    return convertedData;
  }

  private static Tensor parseGroundingTensor(String[] parts, VariableNumMap groundingVar1,
      VariableNumMap groundingVar2, VariableNumMap booleanVar) {
    // Convert set of true output groundings to a tensor.
    String[] trueGroundings = null;
    if (parts.length > 1 && parts[1].trim().length() > 0) {
      trueGroundings = parts[1].split(",");
    } else { 
      // May be no groundings.
      trueGroundings = new String[] {};
    }

    if (trueGroundings.length > 0 && trueGroundings[0].contains("#")) {
      return buildRelationTensor(trueGroundings, groundingVar1, groundingVar2, booleanVar);
    } else {
      return buildCategoryTensor(trueGroundings, groundingVar1, booleanVar);
    }
  }

  public static Tensor buildCategoryTensor(String[] trueGroundings, VariableNumMap groundingVar, VariableNumMap booleanVar) {
    Set<String> trueGroundingSet = Sets.newHashSet(Arrays.asList(trueGroundings));
    Set<Object> unusedGroundings = Sets.newHashSet((groundingVar.getDiscreteVariables().get(0).getValues()));
    TableFactorBuilder outcomeBuilder = new TableFactorBuilder(groundingVar.union(booleanVar), SparseTensorBuilder.getFactory());
    for (String trueGrounding : trueGroundingSet) {
      outcomeBuilder.setWeightList(Arrays.asList(trueGrounding.trim(), "T"), 1.0);
      unusedGroundings.remove(trueGrounding.trim());
    }

    // Add corresponding false values for groundings which were not
    // observed.
    for (Object unused : unusedGroundings) {
      outcomeBuilder.setWeightList(Arrays.asList(unused, "F"), 1.0);
    }
    return outcomeBuilder.build().getWeights();
  }

  public static Tensor buildRelationTensor(String[] trueGroundings, VariableNumMap groundingVar1,
      VariableNumMap groundingVar2, VariableNumMap booleanVar) {
    TableFactorBuilder outcomeBuilder = new TableFactorBuilder(
        VariableNumMap.unionAll(groundingVar1, groundingVar2, booleanVar),
        SparseTensorBuilder.getFactory());
    // Each entry of trueGroundings is a #-separated pair of
    // groundings which are true in the relation.
    for (int i = 0; i < trueGroundings.length; i++) {
      String[] parts = trueGroundings[i].split("#");
      outcomeBuilder.setWeightList(Arrays.asList(parts[0].trim(), parts[1].trim(), "T"), 1.0);
    }

    // Unobserved grounding pairs are false.
    Iterator<Assignment> iter = new AllAssignmentIterator(groundingVar1.union(groundingVar2));
    Assignment trueAssignment = booleanVar.outcomeArrayToAssignment("T");
    Assignment falseAssignment = booleanVar.outcomeArrayToAssignment("F");
    while (iter.hasNext()) {
      Assignment groundingPair = iter.next();
      if (outcomeBuilder.getWeight(groundingPair.union(trueAssignment)) != 1.0) {
        outcomeBuilder.setWeight(groundingPair.union(falseAssignment), 1.0);
      }
    }

    return outcomeBuilder.build().getWeights();
  }

  /**
   * @param domains
   * @param ccgLexiconFile
   */
  public static GroundingModelFamily constructGroundingModel(List<Domain> domains, Lexicon<String> lexicon) {
    // Instantiate the parameterization of the grounding function.
    // Each predicate uses the category or relation features defined
    // above to instantiate sets of groundings.
    IndexedList<RelationType> groundedRelationList = new IndexedList<RelationType>();
    IndexedList<RelationType> knownRelationList = new IndexedList<RelationType>();
    Set<String> knownRelationNames = Sets.newHashSet();
    for (Domain domain : domains) {
      knownRelationNames.addAll(domain.getKnownRelationNames());
    }

    for (RelationType relation : lexicon.getPredicatesInGrammar()) {
      if (knownRelationNames.contains(relation.getName())) {
        knownRelationList.add(relation);
      } else {
        groundedRelationList.add(relation);
      }
    }

    // System.out.println("KNOWN RELATION LIST: " +
    // knownRelationList);

    // Build the GroundingModelFamily, the parameterized version of
    // the parsing
    // + grounding model.
    // Convert the semantic parser to a CFG.
    SemanticParser parser = new SemanticParser(lexicon);

    /*
     * for (List<String> wordSeq :
     * lexicon.getAllRecognizedWordSequences()) { for (LexicalCategory
     * category : lexicon.getCategories(wordSeq)) {
     * System.out.println(wordSeq + " : " + category); } }
     */

    VariableNumMap parserInput = VariableNumMap.singleton(0, "parserInput", new ObjectVariable(List.class));
    VariableNumMap parserOutput = VariableNumMap.singleton(1, "parserOutput", new ObjectVariable(ParseTree.class));
    ParametricCfgFactor parserFactor = parser.toCfgFactor(parserInput, parserOutput, 1000, new TreeValidityPredicate());

    return new GroundingModelFamily(parserFactor, groundedRelationList, knownRelationList, domains);
  }

  public static Map<Integer, EvaluationScore> logDatasetError(GroundingModel model, Iterable<GroundingExample> data,
      List<Domain> domains, UnigramLanguageModel languageModel, boolean addImplicitDeterminer) { 
    // Sort the examples into curriculum levels
    ListMultimap<Integer, GroundingExample> examplesByCurriculumLevel = ArrayListMultimap.create();
    for (GroundingExample example : data) {
      examplesByCurriculumLevel.put(example.getCurriculumLevel(), example);
    }

    Map<Integer, EvaluationScore> levelScores = Maps.newHashMap();

    EvaluationScore accumulated = EvaluationScore.zero();
    for (Integer level : examplesByCurriculumLevel.keySet()) {
      List<GroundingExample> levelExamples = examplesByCurriculumLevel.get(level);
      EvaluationScore score = scoreExamples(model, levelExamples, domains, languageModel, addImplicitDeterminer);

      accumulated = accumulated.add(score);
      levelScores.put(level, score);
    }

    for (Integer level : levelScores.keySet()) {
      System.out.println("LEVEL " + level);
      System.out.println(levelScores.get(level).toString());
    }
    System.out.println("TOTAL:");
    System.out.println(accumulated.toString());

    return levelScores;
  }

  public static EvaluationScore scoreExamples(GroundingModel model, Iterable<GroundingExample> data,
      List<Domain> domains, UnigramLanguageModel languageModel,
      boolean addImplicitDeterminer) {
    PrecisionRecall overallPr = PrecisionRecall.zero();
    PrecisionRecall overallCompletePr = PrecisionRecall.zero();

    PrecisionRecall catGroundingPr = PrecisionRecall.zero();
    PrecisionRecall relGroundingPr = PrecisionRecall.zero();
    PrecisionRecall reweightedRelGroundingPr = PrecisionRecall.zero();
    int numCatGroundingExamples = 0;
    int numRelGroundingExamples = 0;

    int numExamplesAnsweredCorrectly = 0;
    int numExamplesCompleteGroundingCorrect = 0;
    int numExamplesParsed = 0;
    int numExamplesCorrectlyParsed = 0;
    int numExamples = 0;

    double numCorrectRandomGuess = 0.0;

    IndexedList<String> domainNames = IndexedList.create(extract(domains, on(Domain.class).getName()));
    for (GroundingExample example : data) {
      System.out.println(example.getDomainName());
      int domainIndex = domainNames.getIndex(example.getDomainName());
      Domain domain = domains.get(domainIndex);
      World actualWorld = domain.getActualWorld();
      System.out.println(domain.getGroundingVariable1().getDiscreteVariables().get(0).getValues());

      if (example.hasObservedRelation()) {
	  if (model.hasRelationName(example.getObservedRelationName())) {
	      // Evaluate groundings against gold standard annotation.
	      QueryTree query = model.getQueryForRelationName(example.getObservedRelationName(), domain);
	      Tensor predicted = query.getOutputLocalWeights().getBestAssignments();
	      Tensor actual = example.getGrounding();

	      int numDims = predicted.getDimensionNumbers().length;
	      int truthValDim = numDims - 1;
	      int[] indexDims = Arrays.copyOf(predicted.getDimensionNumbers(), truthValDim);
	      PrecisionRecall currentPr = tensorToPrecisionRecall(predicted, actual);

	      // Categories and relations have their accuracy stored in
	      // different accumulators.
	      if (indexDims.length == 1) {
		  catGroundingPr = catGroundingPr.add(currentPr);
		  numCatGroundingExamples++;
	      } else {
		  relGroundingPr = relGroundingPr.add(currentPr);
		  numRelGroundingExamples++;
	      }
	  }
      } else {
        GroundingPrediction prediction = model.getPrediction(example.getWords(), example.getWordScores(), domain, addImplicitDeterminer);
        System.out.println("INPUT: " + example.getWords());
        Tensor output = example.getGrounding();
        Tensor predictedT = null;
        Tensor predictedCompleteGrounding = null;
        if (prediction == null) {
          System.out.println("UNPARSEABLE");
          predictedT = DenseTensor.constant(output.getDimensionNumbers(), output.getDimensionSizes(), 1.0);
          Tensor zeroSelector = SparseTensor.vector(predictedT.getDimensionNumbers()[1], predictedT.getDimensionSizes()[1], new double[] { 1.0, 0.0 });
          predictedT = predictedT.elementwiseProduct(zeroSelector);
        } else {
          MultiTree<Tensor> assignment = prediction.getAssignment();

          ParallelFactors predictedTensor = prediction.getQueryTree().getOutputLocalWeights();
          System.out.println("Probability: " + prediction.getQueryTree().getWeight(assignment));
          SemanticPredicate semanticParse = prediction.getSemanticParse();
          System.out.println("Parse: " + semanticParse);
          Expression predictedLf = model.getExpressionFromSemanticParse(semanticParse);
          Expression expectedLf = example.getLogicalForm();
          boolean lfCorrect = (expectedLf != null) && expectedLf.functionallyEquals(predictedLf);
          System.out.println("PREDICTED LF: " + lfCorrect + " " + predictedLf);
          System.out.println("EXPECTED LF: " + ((expectedLf != null) ? expectedLf : "NO LF"));
          System.out.println(prediction.getQueryTree().getAssignmentString(assignment));
          System.out.println("EXPECTED OUTPUT: " + predictedTensor.getTensorAssignmentString(output));
          System.out.println("PREDICTED OUTPUT: " + predictedTensor.getTensorAssignmentString(assignment.getValue()));
          if (lfCorrect) { 
            numExamplesCorrectlyParsed++; 
          }

          String predictionString = predictedTensor.getTensorAssignmentString(assignment.getValue());
          String expectedString = predictedTensor.getTensorAssignmentString(output);

          boolean correct = predictionString.equals(expectedString);

          System.out.println(correct + " level " + example.getCurriculumLevel() + " " + example.getWords() + " " + prediction.getSemanticParse() + " " + predictedTensor.getTensorAssignmentString(output) + " " + predictedTensor.getTensorAssignmentString(assignment.getValue()));

          predictedT = assignment.getValue();
          numExamplesParsed++;

          World world = model.getWorldForDomain(domain);
          ParallelFactors completeGroundingFactor = model.getCompleteGrounding(predictedLf, world);
          predictedCompleteGrounding = completeGroundingFactor.getBestAssignments();
          System.out.println("PREDICTED COMPLETE GROUNDING: " + completeGroundingFactor.getTensorAssignmentString(predictedCompleteGrounding));
        }

        if (languageModel != null) {
          List<String> generatedLanguage = model.generateTextFromGrounding(output, domain, languageModel, false);
          System.out.println("GENERATED CATEGORY LANGUAGE: " + generatedLanguage);
          generatedLanguage = model.generateTextFromGrounding(output, domain, languageModel, true);
          System.out.println("GENERATED RELATION LANGUAGE: " + generatedLanguage);
        }
        PrecisionRecall currentPr = tensorToPrecisionRecall(predictedT, output);
        overallPr = overallPr.add(currentPr);

        // Counts the number of questions for which the right answer
        // (i.e., the entire grounding set) was correctly predicted.
	boolean valueCorrect = currentPr.getAccuracy() == 1.0 && prediction != null;
        if (valueCorrect) {
          numExamplesAnsweredCorrectly++;
        }
        numExamples++;
        
        if (example.getLogicalForm() != null && actualWorld != null) {
          ParallelFactors completeGroundingFactor = model.getCompleteGrounding(example.getLogicalForm().simplify(), actualWorld);
          Tensor actualCompleteGrounding = completeGroundingFactor.getBestAssignments();

	  boolean countsForAccuracy = predictedCompleteGrounding != null;
          if (predictedCompleteGrounding == null) {
            int[] actualDims = actualCompleteGrounding.getDimensionNumbers();
            int[] actualSizes = actualCompleteGrounding.getDimensionSizes();
            predictedCompleteGrounding = DenseTensor.constant(actualDims, actualSizes, 1.0);
            Tensor zeroSelector = SparseTensor.vector(actualDims[actualDims.length - 1], actualSizes[actualSizes.length - 1], new double[] { 1.0, 0.0 });
            predictedCompleteGrounding = predictedCompleteGrounding.elementwiseProduct(zeroSelector);
          }

          PrecisionRecall currentCompletePr = tensorToPrecisionRecall(predictedCompleteGrounding, actualCompleteGrounding);
          overallCompletePr = overallCompletePr.add(currentCompletePr);
          
	  boolean correct = currentCompletePr.getAccuracy() == 1.0 && countsForAccuracy;
          if (correct) {
            numExamplesCompleteGroundingCorrect++;
          } 
          System.out.println("ACTUAL COMPLETE GROUNDING: " + correct + " "+ completeGroundingFactor.getTensorAssignmentString(actualCompleteGrounding));

	  if (!valueCorrect && correct) {
	      System.out.println("INCONSISTENCY (correct vs incorrect)");
	  }

	  // Check that the complete grounding agrees with the annotated value.
	  int[] dims = actualCompleteGrounding.getDimensionNumbers();
	  Tensor expectedValue = actualCompleteGrounding.maxOutDimensions(Arrays.copyOfRange(dims, 1, dims.length - 1));
	  Tensor annotatedValue = example.getGrounding();
	  System.out.println(Arrays.toString(expectedValue.getDimensionNumbers()));
	  System.out.println(Arrays.toString(annotatedValue.getDimensionNumbers()));

	  expectedValue = expectedValue.relabelDimensions(annotatedValue.getDimensionNumbers());

	  double overlap = expectedValue.innerProduct(annotatedValue).get(0);
	  double totalEnts = annotatedValue.innerProduct(annotatedValue).get(0);

	  if (overlap != totalEnts) {
	      System.out.println("INCONSISTENCY (value)");
	  }

        } else {
          // TODO: something here. We should get this wrong, but it's not clear exactly how.
        }

	// Compute reweighted relation metric
	if (example.hasLogicalForm() && example.getCurriculumLevel() == 2 && actualWorld != null) {
	    ParallelFactors completeGroundingFactor = model.getCompleteGrounding(example.getLogicalForm().simplify(), actualWorld);
	    Tensor actualCompleteGrounding = completeGroundingFactor.getBestAssignments();

	    World predictedWorld = model.getWorldForDomain(domain);
	    World mergedWorld = mergeCategoriesAndRelations(actualWorld, predictedWorld);
	    ParallelFactors predictedCompleteGroundingFactor = model.getCompleteGrounding(example.getLogicalForm().simplify(), mergedWorld);
	    predictedCompleteGrounding = predictedCompleteGroundingFactor.getBestAssignments();

	    PrecisionRecall currentCompletePr = tensorToPrecisionRecall(predictedCompleteGrounding, 
									actualCompleteGrounding);
	    reweightedRelGroundingPr = reweightedRelGroundingPr.add(currentCompletePr);
	}

        // Random baseline.
        numCorrectRandomGuess += 1.0 / Math.pow(2, predictedT.getDimensionSizes()[0]);

      }
    }
    return new EvaluationScore(overallPr, overallCompletePr, numExamplesAnsweredCorrectly,
			       numExamplesCompleteGroundingCorrect, numExamplesParsed, numExamplesCorrectlyParsed, 
			       numExamples, numCorrectRandomGuess, catGroundingPr, relGroundingPr, reweightedRelGroundingPr,
        numCatGroundingExamples, numRelGroundingExamples);
  }

    private static World mergeCategoriesAndRelations(World categoryWorld, World relationWorld) {
	Set<String> allPredicates = Sets.newHashSet(categoryWorld.getRelationNames());
	allPredicates.addAll(relationWorld.getRelationNames());
	
	List<String> predicates = Lists.newArrayList(allPredicates);
	List<ParallelFactors> factors = Lists.newArrayList();
	for (String predicate : predicates) {
	    if (!categoryWorld.containsRelation(predicate)) {
		factors.add(relationWorld.getFactorForRelation(predicate));
	    } else if (!relationWorld.containsRelation(predicate)) {
		factors.add(categoryWorld.getFactorForRelation(predicate));
	    } else {

		ParallelFactors catFactor = categoryWorld.getFactorForRelation(predicate);
		ParallelFactors relFactor = relationWorld.getFactorForRelation(predicate);

		Preconditions.checkState(catFactor.getIndexVariables().size() == relFactor.getIndexVariables().size());

		if (catFactor.getIndexVariables().size() == 2) {
		    factors.add(relFactor);
		} else {
		    factors.add(catFactor);
		}
	    }
	}

	return new World(categoryWorld.getGroundingVariable1(), categoryWorld.getGroundingVariable2(),
			 categoryWorld.getBooleanVariable(), IndexedList.create(predicates), factors);
    }
  
  private static PrecisionRecall tensorToPrecisionRecall(Tensor predicted, Tensor actual) {
      if (predicted.getDimensionNumbers().length == actual.getDimensionNumbers().length) {

	  int booleanVarNum = predicted.getDimensionNumbers().length - 1;
	  Tensor zeroSelector = SparseTensor.vector(predicted.getDimensionNumbers()[booleanVarNum], 
						    predicted.getDimensionSizes()[booleanVarNum], new double[] { 1.0, 0.0 });
	  Tensor oneSelector = SparseTensor.vector(predicted.getDimensionNumbers()[booleanVarNum], 
						   predicted.getDimensionSizes()[booleanVarNum], new double[] { 0.0, 1.0 });

	  Tensor correct = actual.elementwiseProduct(predicted);

	  double currentTp = correct.elementwiseProduct(oneSelector).sumOutDimensions(correct.getDimensionNumbers()).get(0);
	  double currentTn = correct.elementwiseProduct(zeroSelector).sumOutDimensions(correct.getDimensionNumbers()).get(0);
	  Tensor totals = predicted.sumOutDimensions(Ints.asList(Arrays.copyOf(correct.getDimensionNumbers(), correct.getDimensionNumbers().length - 1)));
	  double currentFp = totals.get(1) - currentTp;
	  double currentFn = totals.get(0) - currentTn;
    
	  return new PrecisionRecall(currentTp, currentTn, currentFp, currentFn);
      } else {
	  double currentTp = 0;

	  int booleanVarNum = actual.getDimensionNumbers().length - 1;
	  Tensor zeroSelector = SparseTensor.vector(actual.getDimensionNumbers()[booleanVarNum], 
						    actual.getDimensionSizes()[booleanVarNum], new double[] { 1.0, 0.0 });
	  Tensor oneSelector = SparseTensor.vector(actual.getDimensionNumbers()[booleanVarNum], 
						   actual.getDimensionSizes()[booleanVarNum], new double[] { 0.0, 1.0 });

	  double currentTn = actual.elementwiseProduct(zeroSelector).sumOutDimensions(actual.getDimensionNumbers()).get(0);
	  double currentFn = actual.elementwiseProduct(oneSelector).sumOutDimensions(actual.getDimensionNumbers()).get(0);

	  booleanVarNum = predicted.getDimensionNumbers().length - 1;
	  zeroSelector = SparseTensor.vector(predicted.getDimensionNumbers()[booleanVarNum], 
						    predicted.getDimensionSizes()[booleanVarNum], new double[] { 1.0, 0.0 });
	  oneSelector = SparseTensor.vector(predicted.getDimensionNumbers()[booleanVarNum], 
						   predicted.getDimensionSizes()[booleanVarNum], new double[] { 0.0, 1.0 });
	  double currentFp = predicted.elementwiseProduct(oneSelector).sumOutDimensions(predicted.getDimensionNumbers()).get(0);
	  return new PrecisionRecall(currentTp, currentTn, currentFp, currentFn);
      }
  }

  private static class TreeValidityPredicate implements Predicate<ParseTree>, Serializable {
    private static final long serialVersionUID = 1L;

    @Override
    public boolean apply(ParseTree value) {
      SemanticParse semParse = SemanticParser.convertParseTreeToSemanticParse(value);
      return semParse != SemanticParse.EMPTY && !(semParse.getSyntacticType() instanceof DirectedFunctionType)
          && (semParse.getSyntacticType().getName().equals("N") || semParse.getSyntacticType().getName().equals("S"));
    }
  };

  public static class EvaluationScore {
    public final PrecisionRecall overallPr;
    public final PrecisionRecall overallCompletePr;

    public final int numExamplesAnsweredCorrectly;
    public final int numExamplesCompleteGroundingCorrect;
    public final int numExamplesParsed;
    public final int numExamplesCorrectlyParsed;
    public final int numExamples;

    public final double numCorrectRandomGuess;

    public final PrecisionRecall catGroundingPr;
    public final PrecisionRecall relGroundingPr;
    public final PrecisionRecall reweightedRelGroundingPr;
    public final int numCatGroundingExamples;
    public final int numRelGroundingExamples;

    public EvaluationScore(PrecisionRecall overallPr, PrecisionRecall overallCompletePr,
        int numExamplesAnsweredCorrectly, int numExamplesCompleteGroundingCorrect, int numExamplesParsed,
        int numExamplesCorrectlyParsed, int numExamples,
        double numCorrectRandomGuess,
			   PrecisionRecall catGroundingPr, PrecisionRecall relGroundingPr, PrecisionRecall reweightedRelGroundingPr,
        int numCatGroundingExamples, int numRelGroundingExamples) {
      this.overallPr = overallPr;
      this.overallCompletePr = overallCompletePr;

      this.numExamplesAnsweredCorrectly = numExamplesAnsweredCorrectly;
      this.numExamplesCompleteGroundingCorrect = numExamplesCompleteGroundingCorrect;
      this.numExamplesParsed = numExamplesParsed;
      this.numExamplesCorrectlyParsed = numExamplesCorrectlyParsed;
      this.numExamples = numExamples;

      this.numCorrectRandomGuess = numCorrectRandomGuess;

      this.catGroundingPr = catGroundingPr;
      this.relGroundingPr = relGroundingPr;
      this.reweightedRelGroundingPr = reweightedRelGroundingPr;
      this.numCatGroundingExamples = numCatGroundingExamples;
      this.numRelGroundingExamples = numRelGroundingExamples;
    }

    public static EvaluationScore zero() {
      return new EvaluationScore(PrecisionRecall.zero(), PrecisionRecall.zero(), 0, 0, 0, 0, 0, 0.0,
				 PrecisionRecall.zero(), PrecisionRecall.zero(), PrecisionRecall.zero(), 0, 0);
    }

    public double getParserPrecision() {
      return ((double) numExamplesCorrectlyParsed) / numExamplesParsed;
    }

    public double getParserRecall() {
      return ((double) numExamplesCorrectlyParsed) / numExamples;
    }

    public double getWholeExampleAccuracy() {
      return ((double) numExamplesAnsweredCorrectly) / numExamples;
    }
    
    public double getCompleteGroundingAccuracy() {
      return ((double) numExamplesCompleteGroundingCorrect) / numExamples;
    }

    public double getWholeExamplePrecision() {
      return ((double) numExamplesAnsweredCorrectly) / numExamplesParsed;
    }

    public double getRandomGuessWholeExampleAccuracy() {
      return numCorrectRandomGuess / numExamples;
    }

    public double getRandomGuessPrecision() {
      double expectedTp = overallPr.getPositiveLabelFraction() * 0.5;
      double expectedFp = (1.0 - overallPr.getPositiveLabelFraction()) * 0.5;

      return expectedTp / (expectedTp + expectedFp);
    }

    public double getRandomGuessRecall() {
      return 0.5;
    }

    public double getRandomGuessF1() {
      double precision = getRandomGuessPrecision();
      double recall = getRandomGuessRecall();
      return 2 * precision * recall / (precision + recall);
    }

    public EvaluationScore add(EvaluationScore other) {
      return new EvaluationScore(overallPr.add(other.overallPr),
          overallCompletePr.add(other.overallCompletePr),
          numExamplesAnsweredCorrectly + other.numExamplesAnsweredCorrectly,
          numExamplesCompleteGroundingCorrect + other.numExamplesCompleteGroundingCorrect,
          numExamplesParsed + other.numExamplesParsed,
          numExamplesCorrectlyParsed + other.numExamplesCorrectlyParsed,
          numExamples + other.numExamples, numCorrectRandomGuess + other.numCorrectRandomGuess,
          catGroundingPr.add(other.catGroundingPr),
          relGroundingPr.add(other.relGroundingPr), 
				 reweightedRelGroundingPr.add(other.reweightedRelGroundingPr),
				 numCatGroundingExamples + other.numCatGroundingExamples,
          numRelGroundingExamples + other.numRelGroundingExamples);
    }

    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("-- OVERALL --\n");
      sb.append("EXACT VALUE ACCURACY: " + getWholeExampleAccuracy() + " (" + numExamplesAnsweredCorrectly + "/" + numExamples + ")\n");
      sb.append(overallPr.toString());
      // sb.append("WHOLE EXAMPLE PRECISION: " + getWholeExamplePrecision() + " (" + numExamplesAnsweredCorrectly + "/" + numExamplesParsed + ")\n");
      sb.append("EXACT COMPLETE GROUNDING ACCURACY: " + getCompleteGroundingAccuracy() + " (" + numExamplesCompleteGroundingCorrect + "/" + numExamples + ")\n");
      sb.append(overallCompletePr.toString());
      // sb.append("WHOLE EXAMPLE PRECISION: " + getWholeExamplePrecision() + " (" + numExamplesAnsweredCorrectly + "/" + numExamplesParsed + ")\n");
      sb.append("LF PRECISION: " + getParserPrecision() + " RECALL: " + getParserRecall() + "\n");
      sb.append("RANDOM BASELINE: \n");
      sb.append("RANDOM GUESS ACCURACY: " + getRandomGuessWholeExampleAccuracy() + "\n");
      sb.append("RANDOM GUESS PRECISION: " + getRandomGuessPrecision() + "\n");
      sb.append("RANDOM GUESS RECALL: " + getRandomGuessRecall() + "\n");
      sb.append("RANDOM GUESS F1: " + getRandomGuessF1() + "\n");

      sb.append("-- CATEGORY GROUNDINGS --\n");
      sb.append(catGroundingPr.toString());

      sb.append("-- RELATION GROUNDINGS --\n");
      sb.append(relGroundingPr.toString());

      sb.append("-- REWEIGHTED RELATION GROUNDINGS --\n");
      sb.append(reweightedRelGroundingPr.toString());

      return sb.toString();
    }
  }

  public static class PrecisionRecall {
    public final double truePositives;
    public final double trueNegatives;
    public final double falsePositives;
    public final double falseNegatives;

    public PrecisionRecall(double truePositives, double trueNegatives, double falsePositives, double falseNegatives) {
      this.truePositives = truePositives;
      this.trueNegatives = trueNegatives;
      this.falsePositives = falsePositives;
      this.falseNegatives = falseNegatives;
    }

    public static PrecisionRecall zero() {
      return new PrecisionRecall(0, 0, 0, 0);
    }

    public PrecisionRecall add(PrecisionRecall other) {
      return new PrecisionRecall(truePositives + other.truePositives, trueNegatives + other.trueNegatives,
          falsePositives + other.falsePositives, falseNegatives + other.falseNegatives);
    }

    public double getTruePositives() {
      return truePositives;
    }

    public double getTrueNegatives() {
      return trueNegatives;
    }

    public double getFalsePositives() {
      return falsePositives;
    }

    public double getFalseNegatives() {
      return falseNegatives;
    }

    public double getPrecision() {
      return truePositives / (truePositives + falsePositives);
    }

    public double getRecall() {
      return truePositives / (truePositives + falseNegatives);
    }

    public double getAccuracy() {
      return (truePositives + trueNegatives) / (truePositives + trueNegatives + falsePositives + falseNegatives);
    }

    /**
     * Returns the fraction of labels in the annotated data which are
     * positive.
     * 
     * @return
     */
    public double getPositiveLabelFraction() {
      return (truePositives + falseNegatives) / (truePositives + trueNegatives + falsePositives + falseNegatives);
    }

    public double getF1() {
      double precision = getPrecision();
      double recall = getRecall();
      return 2 * precision * recall / (precision + recall);
    }

    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("PRECISION: " + getPrecision() + "\n");
      sb.append("RECALL: " + getRecall() + "\n");
      sb.append("F1:" + getF1() + "\n");
      sb.append("ACCURACY: " + getAccuracy() + "\n");
      sb.append("TP: " + getTruePositives() + " FP: " + getFalsePositives() + " FN: " 
          + getFalseNegatives()  + " TN: " + getTrueNegatives()  + "\n");
      sb.append("ACTUAL POSITIVE RATIO: " + getPositiveLabelFraction() + "\n");

      return sb.toString();
    }
  }
}