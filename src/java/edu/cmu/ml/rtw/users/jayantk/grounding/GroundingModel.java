package edu.cmu.ml.rtw.users.jayantk.grounding;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.ccg.lambda.ApplicationExpression;
import com.jayantkrish.jklol.ccg.lambda.CommutativeOperator;
import com.jayantkrish.jklol.ccg.lambda.ConstantExpression;
import com.jayantkrish.jklol.ccg.lambda.Expression;
import com.jayantkrish.jklol.ccg.lambda.LambdaExpression;
import com.jayantkrish.jklol.ccg.lambda.QuantifierExpression;
import com.jayantkrish.jklol.cfg.BeamSearchCfgFactor;
import com.jayantkrish.jklol.cfg.ParametricCfgFactor;
import com.jayantkrish.jklol.cfg.ParseTree;
import com.jayantkrish.jklol.inference.MarginalCalculator.ZeroProbabilityError;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteObjectFactor;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.VariableNumMap.VariableRelabeling;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.tensor.DenseTensor;
import com.jayantkrish.jklol.tensor.DenseTensorBuilder;
import com.jayantkrish.jklol.tensor.SparseTensor;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.util.AllAssignmentIterator;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.IndexedList;

import edu.cmu.ml.rtw.users.jayantk.semparse.RelationType;
import edu.cmu.ml.rtw.users.jayantk.semparse.SemanticParser;
import edu.cmu.ml.rtw.users.jayantk.semparse.SemanticPredicate;
import edu.cmu.ml.rtw.util.Pair;

public class GroundingModel implements Serializable, GroundingModelInterface {
  static final long serialVersionUID = 10275539472837495L;

  // groundingTypes are the predicates which are grounded out in terms of the physical world.
  // These predicates have parameters which must be learned during training.
  protected final IndexedList<RelationType> groundingTypes;
  protected final List<SufficientStatistics> groundingParameters;

  // knownPredicateTypes are predicates whose groundings are known a priori.
  // No parameters need to be estimated for these predicates.
  private final IndexedList<RelationType> knownPredicateTypes;

  private final ParametricCfgFactor parserCfgFactor;
  private final SufficientStatistics parserParameters;
  private final List<Domain> domains;

  public GroundingModel(IndexedList<RelationType> groundingTypes, IndexedList<RelationType> knownPredicateTypes, 
      List<SufficientStatistics> groundingParameters,
      SufficientStatistics parserParameters, ParametricCfgFactor parserCfgFactor, List<Domain> domains) {
    //this.domainNames = domainNames;
    this.domains = domains;

    //these are all indexed in the same way
    this.groundingTypes = Preconditions.checkNotNull(groundingTypes);
    this.groundingParameters = groundingParameters;

    this.knownPredicateTypes = knownPredicateTypes;

    /* parser parameters*/
    // These parameters should really be copied to avoid problems.
    this.parserParameters = parserParameters;
    this.parserCfgFactor = parserCfgFactor;
  }

  public static GroundingModel fromSerializedFile(String modelFilename) throws FileNotFoundException, IOException, ClassNotFoundException{
    //load the grounding model
    System.out.println("Loading file:" + modelFilename);
    ObjectInputStream in = new ObjectInputStream(new FileInputStream(modelFilename));
    GroundingModel trainedGroundingModel = (GroundingModel)in.readObject();
    in.close();

    return trainedGroundingModel;
  }

  public static void toSerializedFile(String modelFilename, GroundingModel trainedGroundingModel) throws FileNotFoundException, IOException, ClassNotFoundException{
    FileOutputStream fos = new FileOutputStream(modelFilename);
    ObjectOutputStream out = new ObjectOutputStream(fos);
    out.writeObject(trainedGroundingModel);
    out.close();  fos.close();
  }

  public BeamSearchCfgFactor getParser() {
    return parserCfgFactor.getModelFromParameters(parserParameters);
  }

  public IndexedList<RelationType> getPredicates() {
    return groundingTypes;
  }

  public List<SufficientStatistics> getPredicateParameters() {
    return groundingParameters;
  }

  public SufficientStatistics getParserParameters() {
    return parserParameters;
  }

  public List<Domain> getDomains() {
    return domains;
  }

  public static void transferParameters(GroundingModel fromModel, GroundingModel toModel) {
    for (int i = 0; i < fromModel.groundingTypes.size(); i++) {
      String fromName = fromModel.groundingTypes.get(i).getName();

      for (int j = 0; j < toModel.groundingTypes.size(); j++){
        String toName = toModel.groundingTypes.get(j).getName();

        if(fromName.equals(toName)){
          toModel.groundingParameters.get(j).transferParameters(fromModel.groundingParameters.get(i));
        }
      }
    }

    // Transfer the CCG parser parameters.
    toModel.parserParameters.transferParameters(fromModel.parserParameters);
  }

  public DiscreteVariable getCategoryFeatureVariable(){

    DiscreteVariable var = null;
    for(Domain d: domains){
      DiscreteVariable domainVar = d.getCategoryFeatureVariable();
      if (var == null) {
        var = domainVar;
      } else {
        Preconditions.checkState(var.getValues().equals(domainVar.getValues()));
      }
    }

    return var;
  }

  public DiscreteVariable getRelationFeatureVariable(){
    DiscreteVariable var = null;
    for(Domain d: domains){
      DiscreteVariable domainVar = d.getRelationFeatureVariable();
      if (var == null) {
        var = domainVar;
      } else {
        Preconditions.checkState(var.getValues().equals(domainVar.getValues()));
      }
    }
    return var;
  }

  /**
   * Gets a description of the parameters in this model. If {@code
   * numFeatures >= 0}, prints the {@code numFeatures}
   * highest-weighted features of each parameteric factor. If {@code
   * numFeatures < 0}, prints the complete feature vector.
   */
  public String getParameterDescription(int numFeatures) {
    StringBuilder sb = new StringBuilder();
    sb.append(parserCfgFactor.getParameterDescription(parserParameters));

    for (int i = 0; i < groundingTypes.size(); i++) {
      // All families will print equivalent descriptions.
      GroundingFamily family = domains.get(0).getFamilyForRelation(groundingTypes.get(i));
      sb.append("<relation_name>"+groundingTypes.get(i).getName() + "</relation_name>\n");
      sb.append(family.getParameterDescription(groundingParameters.get(i), numFeatures));
    }
    return sb.toString();
  }

  public SemanticPredicate getSemanticParseFromParseTree(ParseTree parse) {
    // SemanticPredicate simplified = recursivelySimplifyParse(semanticParse);
    return SemanticParser.convertParseTreeToSemanticParse(parse).getSemanticPredicate();
  }

  public Expression getLogicalFormFromParseTree(ParseTree parse) {
    return getExpressionFromSemanticParse(getSemanticParseFromParseTree(parse));
  }

  public QueryTree getQueryFromParse(ParseTree parse, Domain domain, boolean addImplicitDeterminer) {
    SemanticPredicate semanticPredicate = getSemanticParseFromParseTree(parse);
    QueryTree query = getQueryFromSemanticParse(semanticPredicate, domain);
    if (addImplicitDeterminer) {
      query = new DeterminerQueryTree(query, true);
      System.out.println("adding determiner to: " + semanticPredicate);
    }
    return query;
  }

    public boolean hasRelationName(String relationName) {
	for (RelationType groundingType : groundingTypes) {
	    if (groundingType.getName().equals(relationName)) {
		return true;
	    }
	}
	return false;
    }

  public QueryTree getQueryForRelationName(String relationName, Domain domain) {
    for (RelationType groundingType : groundingTypes) {
      if (groundingType.getName().equals(relationName)) {
        return new PredicateQueryTree(groundingType, 
            getFactorForRelation(groundingType, domain), false);
      }
    }

    return null;
    //throw new IllegalArgumentException("Unknown relation name: " + relationName);
  }
  
  public World getWorldForDomain(Domain domain) {
    List<ParallelFactors> groundingFactors = Lists.newArrayList();
    IndexedList<String> relationNames = IndexedList.create();
    for (RelationType groundingType : groundingTypes) {
      relationNames.add(groundingType.getName());
      groundingFactors.add(getFactorForRelation(groundingType, domain));
    }
    
    for (String fixedRelationName : domain.getKnownRelationNames()) {
      relationNames.add(fixedRelationName);
      groundingFactors.add(domain.getGroundingForFixedRelation(fixedRelationName));
    }
    
    return new World(domain.getGroundingVariable1(), domain.getGroundingVariable2(),
        domain.getBooleanVariable(), relationNames, groundingFactors);
  }

  public GroundingPrediction getPredictionFromWords(List<String> input, Domain domain, boolean addImplicitDeterminer) {
    List<List<String>> wordList = Lists.newArrayList();
    wordList.add(input);
    double[] weights = new double[] {0};
    return getPrediction(wordList, weights, domain, addImplicitDeterminer);
  }

  public GroundingPrediction getPrediction(List<List<String>> inputCandidates, double[] inputWeights, Domain domain,
      boolean addImplicitDeterminer) {
    BeamSearchCfgFactor parser = parserCfgFactor.getModelFromParameters(parserParameters);

    double bestWeight = Double.NEGATIVE_INFINITY;
    GroundingPrediction bestPrediction = null;

    for (int i = 0; i < inputCandidates.size(); i++) {
      List<String> input = inputCandidates.get(i);
      double inputWeight = inputWeights[i];

      DiscreteObjectFactor parseFactor = parser.conditional(parser.getTerminalVariable().outcomeArrayToAssignment(input))
          .coerceToDiscreteObject();
      List<Assignment> bestAssignments = parseFactor.getMostLikelyAssignments(1);
      System.out.println("NUM PARSES: " + parseFactor.size());

      if (bestAssignments.size() > 0) {
        Assignment parseAssignment = bestAssignments.get(0);
        ParseTree tree = (ParseTree) parseAssignment.getOnlyValue();
        SemanticPredicate semParse = getSemanticParseFromParseTree(tree);
        QueryTree query = getQueryFromParse(tree, domain, addImplicitDeterminer);

        // Queries can be deterministically evaluated.
        // MultiTree<Tensor> assignment = query.evaluateQueryMap();
        try {
          MultiTree<Tensor> assignment = query.ilpInference(false, true);
          double parseWeight = parseFactor.getUnnormalizedLogProbability(parseAssignment);
          double groundingWeight = query.getWeight(assignment);
          if (parseWeight + inputWeight > bestWeight) {
            bestWeight = parseWeight + inputWeight;
            bestPrediction = new GroundingPrediction(query, assignment, semParse, parseWeight + inputWeight, groundingWeight);
          }
        } catch (ZeroProbabilityError e) {
          // There does not exist a satisfying assignment. Fail.
        }
      }
    }
    return bestPrediction;
  }

  public ParallelFactors getFactorForRelation(RelationType relation, Domain domain) {
    if (groundingTypes.contains(relation)) {
      GroundingFamily family = domain.getFamilyForRelation(relation);
      SufficientStatistics params = groundingParameters.get(groundingTypes.getIndex(relation));

      return family.getFactorFromParameters(params);
    } else {
      return domain.getGroundingForFixedRelation(relation);
    }
  }

  public ParallelFactors getFactorForRelation(String relationName, Domain domain) {
    for (RelationType groundingType : groundingTypes) {
      if (groundingType.getName().equals(relationName)) {
        return getFactorForRelation(groundingType, domain);
      }
    }

    return domain.getGroundingForFixedRelation(relationName);
  }

  @Deprecated
  public List<String> generateTextFromGroundingOld(Tensor groundingAssignment, Domain domain,
      UnigramLanguageModel languageModel) {
    double bestOverlap = -1;
    double bestProb = 0.0;
    List<String> bestText = null;

    for (String categoryPhrase : languageModel.getCategoryPhrases()) {
      double wordProb = languageModel.getProbability(categoryPhrase);
      GroundingPrediction prediction = getPredictionFromWords(Arrays.asList(categoryPhrase.split(" ")), domain,
          false);
      if (prediction != null) {
        Tensor predictedGrounding = prediction.getAssignment().getValue();
        if (Arrays.equals(predictedGrounding.getDimensionNumbers(), 
            groundingAssignment.getDimensionNumbers())) {
          double overlap = predictedGrounding.innerProduct(groundingAssignment).getByDimKey();
          if (overlap > bestOverlap || (overlap == bestOverlap && wordProb > bestProb)) {
            bestOverlap = overlap;
            bestProb = wordProb;
            bestText = Arrays.asList(categoryPhrase.split(" "));
          }
        }
      }
    }

    if (bestOverlap < groundingAssignment.getDimensionSizes()[0]) {
      String bestFirstCategoryPhrase = Joiner.on(" ").join(bestText);
      for (String relationPhrase : languageModel.getRelationPhrases()) {
        for (String categoryPhrase2 : languageModel.getCategoryPhrases()) {
          String wholePhrase = Joiner.on(" ").join(bestFirstCategoryPhrase, relationPhrase, categoryPhrase2);
          // System.out.println(wholePhrase);
          List<String> words = Arrays.asList(wholePhrase.split(" "));
          double wordProb = languageModel.getProbability(wholePhrase);

          GroundingPrediction prediction = getPredictionFromWords(words, domain, false);
          if (prediction != null) {
            Tensor predictedGrounding = prediction.getAssignment().getValue();
            /*
			System.out.println(Arrays.toString(predictedGrounding.getDimensionNumbers()) + " " + 
					   Arrays.toString(groundingAssignment.getDimensionNumbers()));
             */
            if (Arrays.equals(predictedGrounding.getDimensionNumbers(), 
                groundingAssignment.getDimensionNumbers())) {
              double overlap = predictedGrounding.innerProduct(groundingAssignment).getByDimKey();
              if (overlap > bestOverlap || (overlap == bestOverlap && wordProb > bestProb)) {
                bestOverlap = overlap;
                bestProb = wordProb;
                bestText = words;
              }
            }
          }
        }
      }
    }

    System.out.println("Best phrase: " + bestText + " Overlap: " + bestOverlap + " prob: " + bestProb);
    return bestText;
  }

  public List<String> generateCategoryTextFromGrounding(Tensor groundingAssignment, Domain domain,
      UnigramLanguageModel languageModel) {

    Multimap<RelationType, String> adjectiveMap = languageModel.getAdjectiveCategories();
    Multimap<RelationType, String> nounMap = languageModel.getNounCategories();
    Multimap<RelationType, String> prepMap = languageModel.getPrepositionCategories();

    Set<RelationType> possibleNouns = Sets.newHashSet();
    int nounOverlap = findCategoriesForGrounding(groundingAssignment, nounMap.keySet(), domain, possibleNouns, false);

    Set<RelationType> possibleAdjectives = Sets.newHashSet();
    int adjectiveOverlap = findCategoriesForGrounding(groundingAssignment, adjectiveMap.keySet(), domain, possibleAdjectives, false);

    System.out.println(possibleNouns);
    System.out.println(possibleAdjectives);

    double bestOverlap = -1;
    double bestProb = Double.NEGATIVE_INFINITY;
    List<String> bestText = null;

    List<String> articles = Arrays.asList("a ", "the ", "");
    for (String article : articles) {
      for (RelationType possibleNounCategory : possibleNouns) {
        for (String categoryPhrase : nounMap.get(possibleNounCategory)) {
          String phrase = article + categoryPhrase;
          List<String> wordSequence = Arrays.asList(phrase.split(" "));
          double wordProb = languageModel.getProbability(phrase);
          GroundingPrediction prediction = getPredictionFromWords(wordSequence, domain, false);
          if (prediction != null) {
            // double parseProb = Math.exp(prediction.getParseWeight());
            double parseProb = 1.0;
            double combinedProb = Math.log(parseProb * wordProb) / wordSequence.size();
            Tensor predictedGrounding = prediction.getAssignment().getValue();
            if (Arrays.equals(predictedGrounding.getDimensionNumbers(), 
                groundingAssignment.getDimensionNumbers())) {
              double overlap = predictedGrounding.innerProduct(groundingAssignment).getByDimKey();
              System.out.println(combinedProb + " " + overlap + " " + phrase);

              if (overlap > bestOverlap || (overlap == bestOverlap && combinedProb > bestProb)) {
                bestOverlap = overlap;
                bestProb = combinedProb;
                bestText = wordSequence;
              }
            }
          }
        }
      }
    }

    for (String article : articles) {
      for (RelationType possibleAdjectiveCategory : possibleAdjectives) {
        for (RelationType possibleNounCategory : possibleNouns) {
          for (String adjectivePhrase : adjectiveMap.get(possibleAdjectiveCategory)) {
            for (String categoryPhrase : nounMap.get(possibleNounCategory)) {
              String phrase = article + adjectivePhrase + " " + categoryPhrase;
              List<String> wordSequence = Arrays.asList(phrase.split(" "));
              double wordProb = languageModel.getProbability(phrase);
              GroundingPrediction prediction = getPredictionFromWords(wordSequence, domain, false);
              if (prediction != null) {
                // double parseProb = Math.exp(prediction.getParseWeight());
                double parseProb = 1.0;
                double combinedProb = Math.log(parseProb * wordProb) / wordSequence.size();
                Tensor predictedGrounding = prediction.getAssignment().getValue();
                if (Arrays.equals(predictedGrounding.getDimensionNumbers(), 
                    groundingAssignment.getDimensionNumbers())) {
                  double overlap = predictedGrounding.innerProduct(groundingAssignment).getByDimKey();
                  System.out.println(combinedProb + " " + overlap + " " + phrase);

                  if (overlap > bestOverlap || (overlap == bestOverlap && combinedProb > bestProb)) {
                    bestOverlap = overlap;
                    bestProb = combinedProb;
                    bestText = wordSequence;
                  }
                }
              }
            }
          }
        }
      }
    }

    System.out.println("BEST OVERLAP: " + bestOverlap);
    System.out.println("BEST PROB: " + bestOverlap);
    return bestText;
  }

  public List<String> generateTextFromGrounding(Tensor groundingAssignment, Domain domain,
      UnigramLanguageModel languageModel, boolean forceRelation) {

    List<String> bestText = generateCategoryTextFromGrounding(groundingAssignment, domain, languageModel);
    if (!forceRelation) {
      return bestText;
    }

    if (bestText == null) {
      return null;
    }
    double bestRelationOverlap = 0.0;
    double bestRelationProb = Double.NEGATIVE_INFINITY;
    List<String> bestRelationText = null;

    String bestTextString = Joiner.on(" ").join(bestText);
    Set<Pair<String, String>> relationPhrases = findRelationsForGrounding(groundingAssignment,
        domain, languageModel);
    for (Pair<String, String> relationPhrase : relationPhrases) {
      String prepPhrase = relationPhrase.getLeft();
      String nounPhrase = relationPhrase.getRight();
      String phrase = bestTextString + " " + prepPhrase + " " + nounPhrase;
      List<String> wordSequence = Arrays.asList(phrase.split(" "));
      // System.out.println(wordSequence);
      double wordProb = languageModel.getProbability(phrase);
      GroundingPrediction prediction = getPredictionFromWords(wordSequence, domain, false);
      if (prediction != null) {
        //double parseProb = Math.exp(prediction.getParseWeight());
        double parseProb = 1.0;
        double combinedProb = Math.log(parseProb * wordProb) / wordSequence.size();
        Tensor predictedGrounding = prediction.getAssignment().getValue();
        if (Arrays.equals(predictedGrounding.getDimensionNumbers(), 
            groundingAssignment.getDimensionNumbers())) {
          double overlap = predictedGrounding.innerProduct(groundingAssignment).getByDimKey();
          System.out.println(combinedProb + " " + overlap + " " + phrase);
          if (overlap > bestRelationOverlap || (overlap == bestRelationOverlap && combinedProb > bestRelationProb)) {
            bestRelationOverlap = overlap;
            bestRelationProb = combinedProb;
            bestRelationText = wordSequence;
          }
        }
      }
    }
    return bestRelationText;
  }

  public int findCategoriesForGrounding(Tensor groundingAssignment, 
      Set<RelationType> categories, Domain domain, Set<RelationType> candidateAccumulator,
      boolean exactMatch) {
    int numTrue = -1;
    Tensor trueTensor = null;

    for (RelationType category : categories) {
      ParallelFactors factor = getFactorForRelation(category, domain);
      if (numTrue == -1) {
        TableFactor trueFactor = TableFactor.pointDistribution(factor.getValueVariables(),
            factor.getValueVariables().outcomeArrayToAssignment("T"));
        trueTensor = trueFactor.getWeights();
        numTrue = (int) groundingAssignment.elementwiseProduct(trueTensor).innerProduct(groundingAssignment).getByDimKey();
        System.out.println("NUM TRUE: " + numTrue);
      }

      Tensor bestAssignment = factor.getBestAssignments();
      int overlap = (int) bestAssignment.elementwiseProduct(trueTensor).innerProduct(groundingAssignment).getByDimKey();

      if (overlap == numTrue || (overlap > numTrue && !exactMatch)) {
        candidateAccumulator.add(category);
      }
    }
    return numTrue;
  }

  public Set<Pair<String, String>> findRelationsForGrounding(Tensor groundingAssignment,
      Domain domain, UnigramLanguageModel languageModel) {

    // Find the best category reference for every entity on its own.
    VariableNumMap booleanVar = domain.getBooleanVariable();
    VariableNumMap entityVar = domain.getGroundingVariable1();
    VariableNumMap groundingVars = entityVar.union(booleanVar);
    Iterator<Assignment> entityIter = new AllAssignmentIterator(entityVar);
    Assignment falseAssignment = booleanVar.outcomeArrayToAssignment("F");
    Assignment trueAssignment = booleanVar.outcomeArrayToAssignment("T");
    DiscreteFactor allFalse = TableFactor.unity(groundingVars).product(TableFactor.pointDistribution(booleanVar, falseAssignment));

    // Find out the best string to refer to each entity in the environment.
    Map<Object, String> entityCategoryMap = Maps.newHashMap();
    while (entityIter.hasNext()) {
      Assignment entityAssignment = entityIter.next();
      TableFactor entityTrue = TableFactor.pointDistribution(groundingVars,
          entityAssignment.union(trueAssignment));
      TableFactor entityFalse = TableFactor.pointDistribution(groundingVars,
          entityAssignment.union(falseAssignment));

      DiscreteFactor groundingFactor = allFalse.add(entityTrue).add(entityFalse.product(-1.0));
      DenseTensor groundingTensor = DenseTensorBuilder.copyOf(groundingFactor.getWeights()).build();
      DenseTensor denseGroundingAssignment = DenseTensorBuilder.copyOf(groundingAssignment).build();

      if (denseGroundingAssignment.equals(groundingTensor)) {
        continue;
      }

      List<String> entityName = generateCategoryTextFromGrounding(groundingTensor, domain, languageModel);
      entityCategoryMap.put(entityAssignment.getOnlyValue(), Joiner.on(" ").join(entityName));
    }
    System.out.println(entityCategoryMap);

    // Find the best string that refers to each noun-mediated relation.
    Multimap<RelationType, String> nounRelationMap = languageModel.getNounRelationCategories();
    Set<String> prepositions = Sets.newHashSet(languageModel.getPrepositionCategories().values());
    prepositions.add("to");
    Multimap<RelationType, String> canonicalRelationForm = HashMultimap.create();
    List<String> articles = Arrays.asList("a ", "the ", "");
    for (RelationType nounRelation : nounRelationMap.keySet()) {
      double bestProb = 0;
      String bestPhrase = null;
      String bestRelationWord = null;

      for (String prep1 : prepositions) {
        for (String article : articles) {
          for (String relationWord : nounRelationMap.get(nounRelation)) {
            String phrase = prep1 + " " + article + relationWord;
            double phraseProb = languageModel.getProbability(phrase);
            if (phraseProb > bestProb) {
              bestProb = phraseProb;
              bestPhrase = phrase;
              bestRelationWord = relationWord;
            }
          }
        }
      }

      if (bestPhrase == null) { 
        continue;
      }

      double bestPrepProb = 0;
      String bestPrep = null;
      for (String prep2 : prepositions) {
        double prepProb = languageModel.getTransitionProbability(bestRelationWord, prep2);
        System.out.println(prepProb + " " + bestRelationWord + " " + prep2);
        if (prepProb > bestPrepProb) {
          bestPrep = prep2;
          bestPrepProb = prepProb;
        }
      }

      bestPhrase = bestPhrase + " " + bestPrep;
      canonicalRelationForm.put(nounRelation, bestPhrase);
    }

    double bestOverlap = -1;
    Set<Pair<String, String>> candidates = Sets.newHashSet();
    canonicalRelationForm.putAll(languageModel.getPrepositionCategories());

    Set<RelationType> relations = canonicalRelationForm.keySet();
    System.out.println(relations);
    for (RelationType relation : relations) {
      ParallelFactors factor = getFactorForRelation(relation, domain);
      DiscreteFactor bestAssignment = new TableFactor(factor.getAllVariables(), factor.getBestAssignments());

      DiscreteFactor trueBestAssignments = bestAssignment.conditional(trueAssignment);
      DiscreteFactor groundingAssignmentsAsFactor = new TableFactor(factor.getAllVariables()
          .intersection(groundingAssignment.getDimensionNumbers()), groundingAssignment)
      .conditional(trueAssignment);

      DiscreteFactor overlapCount = trueBestAssignments.innerProduct(groundingAssignmentsAsFactor);
      double numEntitiesInGrounding = groundingAssignmentsAsFactor.innerProduct(groundingAssignmentsAsFactor)
          .getUnnormalizedProbability();
      for (Assignment targetEntity : overlapCount.getNonzeroAssignments()) {
        double newCount = overlapCount.getUnnormalizedProbability(targetEntity);
        System.out.println("overlap: " + newCount + " " + numEntitiesInGrounding);
        if (newCount >= numEntitiesInGrounding) {
          String entityName = entityCategoryMap.get(targetEntity.getOnlyValue());
          if (entityName != null) {
            for (String prep : canonicalRelationForm.get(relation)) {
              candidates.add(new Pair<String, String>(prep, entityName));
            }
          }
        }
      }
    }

    System.out.println(candidates);
    return candidates;
  }

  public Expression getExpressionFromSemanticParse(SemanticPredicate semanticParse) {
    Expression expression = SemanticParser.getLogicalFormFromSemanticParse(semanticParse).simplify();

    return eliminateEquality(expression);
  }

  private Expression eliminateEquality(Expression expression) {
    expression = expression.simplify();
    System.out.println(expression);

    Expression returnExpression = expression;
    if (expression instanceof LambdaExpression) {
      LambdaExpression lambdaExpression = (LambdaExpression) expression;
      expression = lambdaExpression.getBody();
    }

    Map<ConstantExpression, ConstantExpression> previouslyRelabeled = Maps.newHashMap();
    Set<ConstantExpression> quantifiedVarsToRemove = Sets.newHashSet();
    if (expression instanceof QuantifierExpression) {
      QuantifierExpression quant = (QuantifierExpression) expression;
      Expression quantBody = quant.getBody();
      Set<ConstantExpression> quantifiedVars = Sets.newHashSet(quant.getBoundVariables()); 

      if (quant.getBody() instanceof CommutativeOperator) {
        CommutativeOperator op = (CommutativeOperator) quant.getBody();
        List<Expression> expressions = op.getArguments();
        List<Expression> newExpressions = Lists.newArrayList();
        for (Expression subexpression : expressions) {

          boolean retainExpression = true;
          if (subexpression instanceof ApplicationExpression) {
            ApplicationExpression app = (ApplicationExpression) subexpression;
            if (app.getFunction() instanceof ConstantExpression) {
              ConstantExpression constantExpression = (ConstantExpression) app.getFunction();
              String name = constantExpression.getName();

              if (name.startsWith(Domain.KB_IGNORE_EQUAL_PREFIX) || name.startsWith("kb-equal") ) {
                List<ConstantExpression> arguments = Lists.newArrayList();
                for (Expression appArg : app.getArguments()) {
                  arguments.add((ConstantExpression) appArg);
                }

                Set<ConstantExpression> quantifiedArguments = Sets.newHashSet(quantifiedVars);
                quantifiedArguments.retainAll(arguments);

                if (quantifiedArguments.size() >= arguments.size() - 1) {
                  retainExpression = false;
                  ConstantExpression target = ConstantExpression.generateUniqueVariable();
                  for (ConstantExpression appArg : arguments) {
		      ConstantExpression relabeled = appArg;
		      if (previouslyRelabeled.containsKey(relabeled)) {
			  relabeled = previouslyRelabeled.get(relabeled);
		      }
		      returnExpression = returnExpression.renameVariable(relabeled, target);
		      previouslyRelabeled.put(appArg, target);
                  }
                  if (quantifiedArguments.size() == arguments.size() - 1) {
                    quantifiedVarsToRemove.add(target);
                  }
                }
              } else if (name.startsWith(Domain.KB_IGNORE_PREFIX)) {
                retainExpression = false;
              }
            }
          }

          if (retainExpression) {
            newExpressions.add(subexpression);
          }
        }
      }
    }

    // Strip out the variables to eliminate.
    List<ConstantExpression> lambdaArgs = null;
    Expression toFilter = returnExpression;
    if (returnExpression instanceof LambdaExpression) {
      LambdaExpression lambdaExpression = (LambdaExpression) returnExpression;
      lambdaArgs = lambdaExpression.getLocallyBoundVariables();
      toFilter = lambdaExpression.getBody();
    }

    if (toFilter instanceof QuantifierExpression) {
      QuantifierExpression quant = ((QuantifierExpression) toFilter);
      Set<ConstantExpression> quantifiedVars = Sets.newHashSet(quant.getLocallyBoundVariables());
      quantifiedVars.removeAll(quantifiedVarsToRemove);
      quantifiedVars.removeAll(lambdaArgs);

      if (quantifiedVars.size() > 0) {
        toFilter = new QuantifierExpression(quant.getQuantifierName(), Lists.newArrayList(quantifiedVars),
            quant.getBody());
      } else {
        toFilter = quant.getBody();
      }
    }

    if (lambdaArgs != null) {
      toFilter = new LambdaExpression(lambdaArgs, toFilter);
    }

    return eliminateTruePredicates(toFilter).simplify();
  }

  private Expression eliminateTruePredicates(Expression expression) {

    List<ConstantExpression> lambdaArgs = null;
    if (expression instanceof LambdaExpression) {
      LambdaExpression lambdaExpression = (LambdaExpression) expression;
      lambdaArgs = lambdaExpression.getLocallyBoundVariables();
      expression = lambdaExpression.getBody();
    }

    String quantifierName = null;
    List<ConstantExpression> quantArgs = null;
    if (expression instanceof QuantifierExpression) {
      QuantifierExpression quantExpression = (QuantifierExpression) expression;
      quantArgs = quantExpression.getLocallyBoundVariables();
      quantifierName = quantExpression.getQuantifierName();
      expression = quantExpression.getBody();
    }

    if (expression instanceof CommutativeOperator) {
      CommutativeOperator op = (CommutativeOperator) expression;
      List<Expression> expressions = op.getArguments();
      List<Expression> newExpressions = Lists.newArrayList();
      for (Expression subexpression : expressions) {
        boolean retainExpression = true;
        if (subexpression instanceof ApplicationExpression) {
          ApplicationExpression app = (ApplicationExpression) subexpression;
          if (app.getFunction() instanceof ConstantExpression) {
            ConstantExpression constantExpression = (ConstantExpression) app.getFunction();
            String name = constantExpression.getName();

            if (name.startsWith(Domain.KB_IGNORE_EQUAL_PREFIX) || name.startsWith("kb-equal")) {
              Set<Expression> distinctArguments = Sets.newHashSet(app.getArguments());
              if (distinctArguments.size() == 1) {
                retainExpression = false;
              }
            } else if (name.startsWith(Domain.KB_IGNORE_PREFIX)) {
              retainExpression = false;
            }
          }

          if (retainExpression) {
            newExpressions.add(subexpression);
          }
        }
      }

      expression = new CommutativeOperator(op.getOperatorName(), newExpressions);
    }

    if (quantArgs != null) {
      expression = new QuantifierExpression(quantifierName, quantArgs, expression);
    }

    if (lambdaArgs != null) {
      expression = new LambdaExpression(lambdaArgs, expression);
    }

    return expression;
  }

  private QueryTree getQueryFromSemanticParse(SemanticPredicate predicate, Domain domain) {
    RelationType relation = predicate.getRelation();

    if (!relation.getName().startsWith("special-")) {
      // This node in the parse tree refers to some predicate from a KB.
      QueryTree current = new PredicateQueryTree(relation, 
          getFactorForRelation(relation, domain), 
          !groundingTypes.contains(relation));

      int outputVariable = predicate.getOutputArgument();

      // For each argument to this, create a conjunction node.
      Map<Integer, SemanticPredicate> arguments = predicate.getArguments();
      for (Integer argumentNumber : arguments.keySet()) {
        QueryTree subtree = getQueryFromSemanticParse(arguments.get(argumentNumber), domain);
        current = ConjunctionQueryTree.createConjunction(domain.getAndTruthTable(), current, subtree, argumentNumber);
      }

      // Finally, existentially-quantify out any extraneous variables which
      // are not part of the output.
      if (current.getOutputLocalWeights().getIndexVariables().size() > 1) {
        VariableNumMap curVars = current.getOutputLocalWeights().getIndexVariables();
        VariableNumMap toEliminate = curVars.removeAll(Ints.asList(outputVariable));

        current = ExistentialQueryTree.eliminateVariables(current, toEliminate.getVariableNums());
      }
      return current;
    } else {
      Map<Integer, SemanticPredicate> arguments = predicate.getArguments();
      // This node refers to a special operation of some sort (e.g., "the").
      String name = relation.getName();
      if (name.equals("special-the")) {
        Preconditions.checkState(arguments.size() == 1);
        QueryTree subtree = getQueryFromSemanticParse(Iterables.getOnlyElement(arguments.values()), domain);
        return new DeterminerQueryTree(subtree, true);
      } else if (name.equals("special-a")) {
        Preconditions.checkState(arguments.size() == 1);
        QueryTree subtree = getQueryFromSemanticParse(Iterables.getOnlyElement(arguments.values()), domain);
        return new DeterminerQueryTree(subtree, false);
      } else {
        throw new IllegalArgumentException("Invalid special relation: " + name);
      }
    }
  }

  public ParallelFactors getCompleteGrounding(Expression logicalForm, World world) {
    Expression expression = eliminateEquality(logicalForm.simplify());
    if (expression instanceof LambdaExpression) {
      expression = ((LambdaExpression) expression).getBody();
    }

    if (expression instanceof QuantifierExpression) {
      expression = ((QuantifierExpression) expression).getBody();
    }

    IndexedList<String> argNames = IndexedList.create();
    SetMultimap<Integer, String> categories = HashMultimap.create();
    Multimap<String, int[]> relationMap = HashMultimap.create();
    if (expression instanceof CommutativeOperator) {
      CommutativeOperator op = ((CommutativeOperator) expression);
      List<Expression> args = op.getArguments();

      for (Expression arg : args) {
        if (arg instanceof ApplicationExpression) {
          ApplicationExpression app = ((ApplicationExpression) arg);
          String functionName = ((ConstantExpression) app.getFunction()).getName();

	  if (app.getArguments().size() == 1) {
	      int[] argNums = new int[app.getArguments().size()];
	      for (int i = 0; i < app.getArguments().size(); i++) {
		  Expression appArg = app.getArguments().get(i);
		  String name = ((ConstantExpression) appArg).getName();
		  argNames.add(name);
		  argNums[i] = argNames.getIndex(name);
	      }
	      categories.put(argNums[0], functionName);
	  }
	}
      }

      for (Expression arg : args) {
	  if (arg instanceof ApplicationExpression) {
	      ApplicationExpression app = ((ApplicationExpression) arg);
	      String functionName = ((ConstantExpression) app.getFunction()).getName();

	      if (app.getArguments().size() != 1) {
		  int[] argNums = new int[app.getArguments().size()];
		  for (int i = 0; i < app.getArguments().size(); i++) {
		      Expression appArg = app.getArguments().get(i);
		      String name = ((ConstantExpression) appArg).getName();
		      argNames.add(name);
		      argNums[i] = argNames.getIndex(name);
		  }

		  relationMap.put(functionName, argNums);
	      }
	  }
      }
    }

    // Generate the space of possible complete groundings to the categories.
    DiscreteVariable groundingType = world.getGroundingVariable1().getDiscreteVariables().get(0);
    VariableNumMap vars = VariableNumMap.EMPTY;
    VariableNumMap booleanVar = world.getBooleanVariable();
    Assignment trueAssignment = booleanVar.outcomeArrayToAssignment("T");
    DiscreteFactor result = new TableFactor(VariableNumMap.EMPTY, SparseTensor.getScalarConstant(1.0));
    for (int i = 0; i < argNames.size(); i++) {
      VariableNumMap curVar = VariableNumMap.singleton(i, "grounding-" + i, groundingType);
      vars = vars.union(curVar);
      Set<String> categoryPreds = categories.get(i);
      DiscreteFactor factor = new TableFactor(curVar,
          DenseTensor.constant(curVar.getVariableNumsArray(), curVar.getVariableSizes(), 1.0));
      for (String pred : categoryPreds) {
        ParallelFactors relationFactor = world.getFactorForRelation(pred);
        DiscreteFactor discreteFactor = relationFactor.getBestAssignmentsFactor().conditional(trueAssignment);

        factor = factor.product(discreteFactor.relabelVariables(
            VariableRelabeling.createFromVariables(discreteFactor.getVars(), factor.getVars())));
      }
      result = result.outerProduct(factor);
    }

    // Intersect the category space with the relation space.
    for (String relationName : relationMap.keySet()) {
      for (int[] varNums : relationMap.get(relationName)) {
        ParallelFactors relationFactor = world.getFactorForRelation(relationName);
        DiscreteFactor discreteFactor = relationFactor.getBestAssignmentsFactor().conditional(trueAssignment);
        VariableNumMap factorVars = discreteFactor.getVars();
        List<Integer> factorVarNums = factorVars.getVariableNums();
        VariableRelabeling relabeling = VariableRelabeling.EMPTY;
        for (int i = 0; i < varNums.length; i++) {
          relabeling = relabeling.union(VariableRelabeling.createFromVariables(factorVars.intersection(factorVarNums.get(i)),
              vars.intersection(varNums[i])));
        }
        result = result.product(discreteFactor.relabelVariables(relabeling));
      }
    }

    // result contains the true assignments.
    booleanVar = booleanVar.relabelVariableNums(new int[] {argNames.size() + 1});
    TableFactor trueFactor = TableFactor.pointDistribution(booleanVar, booleanVar.outcomeArrayToAssignment("T"));
    TableFactor falseFactor = TableFactor.pointDistribution(booleanVar, booleanVar.outcomeArrayToAssignment("F"));
    DiscreteFactor trueOutcomes = result.outerProduct(trueFactor);
    DiscreteFactor falseOutcomes = result.add(TableFactor.unity(result.getVars()).product(-1.0)).product(-1.0)
        .outerProduct(falseFactor);
    DiscreteFactor resultOutcomes = trueOutcomes.add(falseOutcomes);

    return new ParallelFactors(resultOutcomes.getWeights(), result.getVars(), booleanVar);
  }

  private SemanticPredicate recursivelySimplifyParse(SemanticPredicate predicate) {
    RelationType relation = predicate.getRelation();
    // This relation is always true.
    if (relation.getName().startsWith(Domain.KB_IGNORE_PREFIX)) {
      Map<Integer, SemanticPredicate> arguments = predicate.getArguments();
      if (arguments.size() == 0) {
        return null;
      } else {
        Preconditions.checkState(arguments.size() == 1);
        return recursivelySimplifyParse(arguments.get(0));
      }
    }

    Map<Integer, SemanticPredicate> simplifiedArguments = Maps.newHashMap();
    Map<Integer, SemanticPredicate> arguments = predicate.getArguments();
    for (Integer argumentNumber : arguments.keySet()) {
      SemanticPredicate simplifiedArgument = recursivelySimplifyParse(arguments.get(argumentNumber));
      if (simplifiedArgument != null) {
        simplifiedArguments.put(argumentNumber, simplifiedArgument);
      }
    }

    // This relation becomes a conjunction.
    if (relation.getName().startsWith(Domain.KB_IGNORE_EQUAL_PREFIX)) {
      if (simplifiedArguments.size() == 0) {
        // This really shouldn't happen, but it means both arguments were KB_IGNORE.
        return null;
      } else if (simplifiedArguments.size() == 1) {
        return Iterables.getOnlyElement(simplifiedArguments.values());
      } 
      /*
	else {
	    SemanticPredicate arg0 = simplifiedArguments.get(0);
	    // This is a hack to make inference faster:
	    arg0.getArguments().put(0, simplifiedArguments.get(1));
	    return arg0;
	}
       */
    }


    return new SemanticPredicate(relation, Collections.<Integer>emptyList(), predicate.getOutputArgument(), 
        predicate.getFixedArguments(), simplifiedArguments);
  }

  public static class GroundingPrediction implements Serializable {
    static final long serialVersionUID = 10125539472837495L;
    private final QueryTree queryTree;
    private final MultiTree<Tensor> assignment;
    private final SemanticPredicate semParse;

    private final double parseWeight;
    private final double groundingWeight;

    public GroundingPrediction(QueryTree queryTree, MultiTree<Tensor> assignment, 
        SemanticPredicate semParse, double parseWeight, double groundingWeight) {
      this.queryTree = queryTree;
      this.assignment = assignment;
      this.semParse = semParse;

      this.parseWeight = parseWeight;
      this.groundingWeight = groundingWeight;
    }

    public SemanticPredicate getSemanticParse() {
      return semParse;
    }

    public QueryTree getQueryTree() {
      return queryTree;
    }

    public MultiTree<Tensor> getAssignment() {
      return assignment;
    }

    public double getParseWeight() {
      return parseWeight;
    }

    public double getGroundingWeight() {
      return groundingWeight;
    }
  }
}
