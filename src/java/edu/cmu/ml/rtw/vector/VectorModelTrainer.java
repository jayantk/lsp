package edu.cmu.ml.rtw.vector;

import static ch.lambdaj.Lambda.extract;
import static ch.lambdaj.Lambda.on;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.ccg.lambda.ApplicationExpression;
import com.jayantkrish.jklol.ccg.lambda.ConstantExpression;
import com.jayantkrish.jklol.ccg.lambda.Expression;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.cli.AbstractCli;
import com.jayantkrish.jklol.cvsm.ConstantLrtFamily;
import com.jayantkrish.jklol.cvsm.Cvsm;
import com.jayantkrish.jklol.cvsm.CvsmExample;
import com.jayantkrish.jklol.cvsm.CvsmFamily;
import com.jayantkrish.jklol.cvsm.CvsmLoglikelihoodOracle;
import com.jayantkrish.jklol.cvsm.CvsmLoglikelihoodOracle.CvsmHingeElementwiseLoss;
import com.jayantkrish.jklol.cvsm.CvsmLoglikelihoodOracle.CvsmKlElementwiseLoss;
import com.jayantkrish.jklol.cvsm.CvsmLoglikelihoodOracle.CvsmLoss;
import com.jayantkrish.jklol.cvsm.LrtFamily;
import com.jayantkrish.jklol.cvsm.OpLrtFamily;
import com.jayantkrish.jklol.cvsm.TensorLrtFamily;
import com.jayantkrish.jklol.cvsm.lrt.TensorLowRankTensor;
import com.jayantkrish.jklol.lisp.SExpression;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.Variable;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.VariableNumMap.VariableRelabeling;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.tensor.DenseTensor;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.training.GradientOptimizer;
import com.jayantkrish.jklol.training.GradientOracle;
import com.jayantkrish.jklol.util.IndexedList;

import edu.cmu.ml.rtw.time.utils.IoUtil;
import edu.cmu.ml.rtw.users.jayantk.grounding.Domain;
import edu.cmu.ml.rtw.users.jayantk.grounding.GroundingExample;
import edu.cmu.ml.rtw.users.jayantk.grounding.GroundingModelTrainer;
import edu.cmu.ml.rtw.users.jayantk.semparse.Lexicon;
import edu.cmu.ml.rtw.users.jayantk.semparse.Lexicon.LexicalCategory;

/**
 * Command line program for training a vector space-based
 * model for predicting denotations.
 * 
 * @author jayantk
 */
public class VectorModelTrainer extends AbstractCli {
  
  private OptionSpec<String> domainDir;
  private OptionSpec<String> trainingFilename;
  private OptionSpec<String> vectorModelName;
  
  private OptionSpec<Double> gaussianVariance;
  private OptionSpec<Integer> numFoldsToRun;
  private OptionSpec<Integer> dimension;
  
  private OptionSpec<Void> hingeLoss;
  private OptionSpec<String> lexiconExamples;

  public VectorModelTrainer() {
    super(CommonOptions.STOCHASTIC_GRADIENT, CommonOptions.MAP_REDUCE,
        CommonOptions.LBFGS);
  }

  @Override
  public void initializeOptions(OptionParser parser) {
    domainDir = parser.accepts("domainDir").withRequiredArg().ofType(String.class).required();
    trainingFilename = parser.accepts("trainingFilename").withOptionalArg().ofType(String.class).defaultsTo("training.annotated.txt");
    vectorModelName = parser.accepts("vectorModelName").withRequiredArg().ofType(String.class).required();
		dimension = parser.accepts("dim").withRequiredArg().ofType(Integer.class).required();

    gaussianVariance = parser.accepts("gaussianVariance").withRequiredArg().ofType(Double.class).defaultsTo(0.0);
    numFoldsToRun = parser.accepts("numFoldsToRun").withRequiredArg().ofType(Integer.class);
    hingeLoss = parser.accepts("hingeLoss");
    lexiconExamples = parser.accepts("lexiconExamples").withRequiredArg().ofType(String.class);
  }

  @Override
  public void run(OptionSet options) {
    // Read training data from disk and partition
    // into leave-one-environment-out cross validation folds.
    List<Domain> domains = Domain.readDomainsFromDirectory(options.valueOf(domainDir), 
        options.valueOf(trainingFilename), null, Integer.MAX_VALUE,
        false, false, false);
    IndexedList<String> domainNames = IndexedList.create(extract(domains, on(Domain.class).getName()));
    Multimap<String, GroundingExample> trainingFoldsOrig = GroundingModelTrainer.getCrossValidationFolds(domains);

    // Instantiate the vector space model for this experiment.
    VectorSpaceModelInterface vsmInterface = null;
    String modelName = options.valueOf(vectorModelName);
		int dim = options.valueOf(dimension);
    if (modelName.equals("addition")) {
      vsmInterface = new AdditionVectorSpaceModel();
    } else if (modelName.equals("sequenceRnn")) {
      vsmInterface = new SequenceRnnVectorSpaceModel(dim, "op:tanh");
    } else if (modelName.equals("logicalFormNn")) {
      vsmInterface = new LogicalFormVectorSpaceModel(false);
    } else if (modelName.equals("autogeneratedLogicalFormNn")) {
      vsmInterface = new LogicalFormVectorSpaceModel(true);
    } else if (modelName.equals("Birnn")) {
      vsmInterface = new BirnnVsm(dim);
    }
    
    Preconditions.checkState(vsmInterface != null);
    
    // For the geography domain, generate training examples for each entity name.
    List<CvsmExample> allFoldExamples = Lists.newArrayList();
    if (options.has(lexiconExamples)) {
      Lexicon<String> lexicon = Lexicon.fromFile(IoUtil.LoadFile(options.valueOf(lexiconExamples)));
      Multimap<String, List<String>> predicateNames = HashMultimap.create();
      for (List<String> words : lexicon.getTriggerSequences()) {
        for (LexicalCategory cat : lexicon.getCategories(words)) {
          String predName = cat.getSemanticType().getRelation().getName();
          if (predName.startsWith(Domain.KB_PREFIX) && !predName.startsWith(Domain.KB_IGNORE_PREFIX) &&
              !predName.startsWith(Domain.KB_IGNORE_EQUAL_PREFIX) && 
              !predName.startsWith(Domain.KB_EQUAL_PREFIX)) {
            predicateNames.put(predName, words);
          }
        }
      }
      
      ExpressionParser<Expression> parser = ExpressionParser.lambdaCalculus();
      for (Domain domain : domains) {
        if (domain.getTrainingExamples().size() > 0) {
          for (String predName : domain.getKnownRelationNames()) {
            for (List<String> words : predicateNames.get(predName)) {
              List<List<String>> inputCandidates = Lists.newArrayList();
              inputCandidates.add(words);
              double[] inputScores = new double[] {0};
              Tensor outputTensor = domain.getGroundingForFixedRelation(predName).getBestAssignments();
              Expression logicalForm = parser.parseSingleExpression("(lambda $x (" + predName + " $x))");

              SExpression cfgParse = null;

              List<String> wordParts = Lists.newArrayList();
              for (String word : words) {
                wordParts.add("(" + word.toLowerCase() + " $x)");
              }
              Expression autogeneratedLogicalForm = parser.parseSingleExpression("(lambda $x (and " + Joiner.on(" ").join(wordParts) + "))");

              GroundingExample ex = new GroundingExample(inputCandidates, inputScores, outputTensor,
                  domain.getName(), 0, logicalForm, cfgParse, autogeneratedLogicalForm);
              System.out.println(ex.getWords().get(0) + " " + ex.getLogicalForm() + " " + ex.getAutogeneratedLogicalForm());
              System.out.println(domain.getGroundingForFixedRelation(predName).getTensorAssignmentString(ex.getGrounding()));
              allFoldExamples.add(convertExample(ex, domains, domainNames, vsmInterface));
            }
          }
        }
      }
    }

    // Reformat the training / test data to be suitable for the vector space model. 
    Multimap<String, CvsmExample> trainingFolds = ArrayListMultimap.create();
    for (String key : trainingFoldsOrig.keySet()) {
      for (GroundingExample example : trainingFoldsOrig.get(key)) {
        if (!example.hasObservedRelation()) {
          trainingFolds.put(key, convertExample(example, domains, domainNames, vsmInterface));
        }
      }
      
      // Add in the training examples shared by all folds.
      trainingFolds.putAll(key, allFoldExamples);
      /*
      for (int i = 0; i < options.valueOf(generateEntityExamples); i++) {
        trainingFolds.putAll(key, allFoldExamples);
      }
      */
    }

    List<CvsmExample> allExamples = Lists.newArrayList();
    Multimap<String, CvsmExample> testFolds = HashMultimap.create();
    for (Domain domain : domains) {
      if (domain.getTrainingExamples().size() > 0) {
        for (GroundingExample example : domain.getTrainingExamples()) {
          if (!example.hasObservedRelation()) {
            CvsmExample cvsmExample = convertExample(example, domains, domainNames, vsmInterface);
            allExamples.add(cvsmExample);
            testFolds.put(domain.getName(), cvsmExample);
          }
        }
      }
    }
    allExamples.addAll(allFoldExamples);

    // Construct a parametric family of compositional vector space models
    // given the training data. This method figures out the dimensionality
    // of all declared vector parameters, etc.
    CvsmFamily family = buildFamily(allExamples, domains, domainNames);

    
    int foldsToRun = trainingFoldsOrig.keySet().size();
    if (options.has(numFoldsToRun)) {
      foldsToRun = options.valueOf(numFoldsToRun);
    }

    List<String> foldNames = Lists.newArrayList(trainingFoldsOrig.keySet());
    // Train a model for each fold.
    Map<String, SufficientStatistics> trainedParameters = Maps.newHashMap();
    for (int i = 0; i < foldsToRun; i++) {
      String foldName = foldNames.get(i);
			/*
			for (CvsmExample c : trainingFolds.get(foldName)) {
				String t = "";
				String s = "";
				String source = c.getLogicalForm() + "";
				for (int j=0; j<source.length(); ++j) {
					char ch = source.charAt(j);
					if (ch == '(') {
						s += "\n" + t + "(";
						t += "  ";
					}
					else if (ch == ')') {
						t = t.substring(0, t.length()-2);
						s += "\n" + t + ")";
					}
					else {
						s += ch;
					}
				}
				System.out.println("Fold:\n" + s);
			}
			*/
      SufficientStatistics parameters = train(family, trainingFolds.get(foldName),
          options.valueOf(gaussianVariance), options.has(hingeLoss));
      // System.out.println(family.getParameterDescription(parameters));
      
      trainedParameters.put(foldName, parameters);
      
      Cvsm model = family.getModelFromParameters(parameters);
      System.out.println("TRAINING ERROR");
      evaluateCvsmModel(model, trainingFolds.get(foldName));
      EvaluationResult result = evaluateCvsmModel(model, testFolds.get(foldName));
      int numCorrectTotal = result.numCorrect;
      int numTotal = result.total;
			double accuracy = ((double) numCorrectTotal) / numTotal;
			System.out.println("Correct: " + numCorrectTotal + " / " + numTotal);
			System.out.println("Accuracy: " + accuracy);
    }

    // Evaluate on each fold.
    int numCorrectTotal = 0;
    int numTotal = 0;
    for (String testFold : trainedParameters.keySet()) {
      Cvsm model = family.getModelFromParameters(trainedParameters.get(testFold));
      EvaluationResult result = evaluateCvsmModel(model, testFolds.get(testFold));
      numCorrectTotal += result.numCorrect;
      numTotal += result.total;
    }

    double accuracy = ((double) numCorrectTotal) / numTotal;
    System.out.println("Correct: " + numCorrectTotal + " / " + numTotal);
    System.out.println("Accuracy: " + accuracy);
  }
  
  public static String getCategoryTensorName(String domainName) { 
    return "domain:" + domainName + ":category";
  }
  
  public static String getRelationTensorName(String domainName) { 
    return "domain:" + domainName + ":relation";
  }
  
  public static String getPredicateTensorName(String domainName, String predicateName) {
    return "domain:" + domainName + ":" + predicateName;
  }
  
  // Figure out what tensors are referenced in the training data
  // and their dimensionality. 
  private void extractTensorNamesFromExpression(Expression expression, IndexedList<String> tensorNames,
      List<LrtFamily> parameters, Map<String, DiscreteVariable> generatedVectorSizes) {
    if (expression instanceof ConstantExpression) {
      String name = ((ConstantExpression) expression).getName();

      if (name.startsWith("t:") && !tensorNames.contains(name)) {
        // Parse the tensor name to figure out the dimensionality
        // of this tensor.
        String[] parts = name.split(":");
        Preconditions.checkArgument(parts.length >= 2,
          "Invalid expression: %r. Tensor parameters are specified using the notation t:<dims1>;<dims2>;...:<parameter name>",
          name);

        // Dimensions can be specified using named variables
        // (which must be given as part of generatedVectorSizes),
        // or using numbers, which are parsed out and variables
        // are generated for them.
        String[] dimParts = parts[1].split(";");
        Variable[] vars = new Variable[dimParts.length];
        int[] varNums = new int[dimParts.length]; 

        for (int i = 0; i < dimParts.length; i++) {
          if (generatedVectorSizes.containsKey(dimParts[i])) {
            vars[i] = generatedVectorSizes.get(dimParts[i]);
          } else {
            int size = Integer.parseInt(dimParts[i]);
            DiscreteVariable var = DiscreteVariable.sequence(dimParts[i], size);
            generatedVectorSizes.put(dimParts[i], var);
            vars[i] = var;
          }
          
          // Give the rightmost dimension the lowest variable number,
          // which makes it the first dimension eliminated by 
          // multiplication.
          varNums[i] = dimParts.length - (i + 1);
        }

        VariableNumMap varNumMap = new VariableNumMap(Ints.asList(varNums), Arrays.asList(dimParts), Arrays.asList(vars));
        tensorNames.add(name);
        parameters.add(new TensorLrtFamily(varNumMap));
      }
			else if (name.startsWith("tlr:") && !tensorNames.contains(name)) {
        // Parse the tensor name to figure out the dimensionality
        // of this tensor.
        String[] parts = name.split(":");
        Preconditions.checkArgument(parts.length >= 2,
          "Invalid expression: %r. Tensor parameters are specified using the notation t:<dims1>;<dims2>;...:<parameter name>",
          name);

        // Dimensions can be specified using named variables
        // (which must be given as part of generatedVectorSizes),
        // or using numbers, which are parsed out and variables
        // are generated for them.
				int lowRank = Integer.parseInt(parts[1]);
        String[] dimParts = parts[2].split(";");
        Variable[] vars = new Variable[dimParts.length];
        int[] varNums = new int[dimParts.length]; 

        for (int i = 0; i < dimParts.length; i++) {
          if (generatedVectorSizes.containsKey(dimParts[i])) {
            vars[i] = generatedVectorSizes.get(dimParts[i]);
          } else {
            int size = Integer.parseInt(dimParts[i]);
            DiscreteVariable var = DiscreteVariable.sequence(dimParts[i], size);
            generatedVectorSizes.put(dimParts[i], var);
            vars[i] = var;
          }
          
          // Give the rightmost dimension the lowest variable number,
          // which makes it the first dimension eliminated by 
          // multiplication.
          varNums[i] = dimParts.length - (i + 1);
        }

        VariableNumMap varNumMap = new VariableNumMap(Ints.asList(varNums), Arrays.asList(dimParts), Arrays.asList(vars));
        tensorNames.add(name);
        parameters.add(new OpLrtFamily(varNumMap, lowRank));
      }
    }
		else if (expression instanceof ApplicationExpression) {
      List<Expression> subexpressions = ((ApplicationExpression) expression).getSubexpressions();
      for (Expression subexpression : subexpressions) {
        extractTensorNamesFromExpression(subexpression, tensorNames,
            parameters, generatedVectorSizes);
      }
    }
  }

  private CvsmExample convertExample(GroundingExample example, List<Domain> domains,
      IndexedList<String> domainNames, VectorSpaceModelInterface vectorSpaceModel) {
    // convert to cvsm example using CCG parse and templates
    Expression cvsmFormula = vectorSpaceModel.getFormula(example);
    
    Domain domain = domains.get(domainNames.getIndex(example.getDomainName()));
    VariableNumMap vars = domain.getCategoryFamily().getFeatureVectors().getVars();
    VariableNumMap featureVar = vars.getVariablesByName("catFeatures");
    VariableNumMap truthVar = vars.getVariablesByName("truthVal");
    vars = vars.removeAll(featureVar);
    Tensor grounding = new TableFactor(vars, example.getGrounding()).conditional(truthVar.outcomeArrayToAssignment("T")).getWeights();
    grounding = DenseTensor.copyOf(grounding);
    
    return new CvsmExample(cvsmFormula, grounding, null);
  }

  private CvsmFamily buildFamily(Collection<CvsmExample> examples, List<Domain> domains, IndexedList<String> domainNames) {

    IndexedList<String> tensorNames = IndexedList.create();
    List<LrtFamily> tensorParameters = Lists.newArrayList();
    
    DiscreteVariable featureVarType = null;
    DiscreteVariable relFeatureVarType = null;
    
    for (Domain domain : domains) {
      // Get the object features of each object in the domain.
      // The features are for the assignment (entity name, T), and we just want features per entity,
      // so condition on the "T" value.
      DiscreteFactor categoryFeatures = domain.getCategoryFamily().getFeatureVectors();
      VariableNumMap truthVar = categoryFeatures.getVars().getVariablesByName("truthVal");
      categoryFeatures = categoryFeatures.conditional(truthVar.outcomeArrayToAssignment("T"));

      VariableNumMap entityVar = categoryFeatures.getVars().getVariablesByName("grounding0");
      VariableNumMap featureVar = categoryFeatures.getVars().getVariablesByName("catFeatures");

      if (featureVarType == null) {
        featureVarType = (DiscreteVariable) featureVar.getOnlyVariable();
      }

      VariableRelabeling entityRelabeling = VariableRelabeling.createFromVariables(entityVar,
          entityVar.relabelVariableNums(new int[] {1}));
      VariableRelabeling featureRelabeling = VariableRelabeling.createFromVariables(featureVar,
          featureVar.relabelVariableNums(new int[] {0}));
      categoryFeatures = (DiscreteFactor) categoryFeatures.relabelVariables(
          entityRelabeling.union(featureRelabeling));

      tensorNames.add(getCategoryTensorName(domain.getName()));
      tensorParameters.add(new ConstantLrtFamily(categoryFeatures.getVars(),
          new TensorLowRankTensor(categoryFeatures.getWeights())));

      DiscreteFactor relationFeatures = domain.getRelationFamily().getFeatureVectors();
      truthVar = relationFeatures.getVars().getVariablesByName("truthVal");
      relationFeatures = relationFeatures.conditional(truthVar.outcomeArrayToAssignment("T"));

      VariableNumMap entity0Var = relationFeatures.getVars().getVariablesByName("grounding0");
      VariableNumMap entity1Var = relationFeatures.getVars().getVariablesByName("grounding1");
      VariableNumMap relFeatureVar = relationFeatures.getVars().getVariablesByName("relFeatures");

      if (relFeatureVarType == null) {
        relFeatureVarType = (DiscreteVariable) relFeatureVar.getOnlyVariable();
      }

      VariableRelabeling entity0Relabeling = VariableRelabeling.createFromVariables(entity0Var,
          entity0Var.relabelVariableNums(new int[] {1}));
      VariableRelabeling entity1Relabeling = VariableRelabeling.createFromVariables(entity1Var,
          entity1Var.relabelVariableNums(new int[] {2}));
      VariableRelabeling relFeatureRelabeling = VariableRelabeling.createFromVariables(relFeatureVar,
          relFeatureVar.relabelVariableNums(new int[] {0}));
      relationFeatures = (DiscreteFactor) relationFeatures.relabelVariables(
          entity0Relabeling.union(relFeatureRelabeling).union(entity1Relabeling));

      tensorNames.add(getRelationTensorName(domain.getName()));
      tensorParameters.add(new ConstantLrtFamily(relationFeatures.getVars(),
          new TensorLowRankTensor(relationFeatures.getWeights())));
      
      // Generate a tensor for each predicate whose value is known in the domain.
      for (String relName : domain.getKnownRelationNames()) {
        DiscreteFactor relGrounding = domain.getGroundingForFixedRelation(relName).getFactor();
        
        truthVar = relGrounding.getVars().getVariablesByName("truthVal");
        Tensor tensor = relGrounding.conditional(truthVar.outcomeArrayToAssignment("T")).getWeights()
            .elementwiseAddition(relGrounding.conditional(truthVar.outcomeArrayToAssignment("F")).product(-1.0).getWeights());
        
        // Make each entry of the tensor 1000 * this number. It's 1000 by default, for whatever reason.
        tensor = tensor.elementwiseProduct(0.001);
        
        tensorNames.add(getPredicateTensorName(domain.getName(), relName));
        tensorParameters.add(new ConstantLrtFamily(relGrounding.getVars().removeAll(truthVar), 
            new TensorLowRankTensor(DenseTensor.copyOf(tensor))));
      }
    }

    Map<String, DiscreteVariable> generatedVectorDims = Maps.newHashMap();
    generatedVectorDims.put("catFeatures", featureVarType);
    generatedVectorDims.put("relFeatures", relFeatureVarType);
    for (CvsmExample example : examples) {
      // Initialize tensors for any variables referenced in this formula.
      extractTensorNamesFromExpression(example.getLogicalForm(), tensorNames, tensorParameters, generatedVectorDims);
    }

    // Read in the set of vectors, etc. from the training examples and
    // instantiate vectors to create the family.
    return new CvsmFamily(tensorNames, tensorParameters);
  }

  private SufficientStatistics train(CvsmFamily family, Collection<CvsmExample> examples,
      double gaussianVariance, boolean useHingeLoss) {
    // An elementwise log-loss for binary elements.
    CvsmLoss loss = null;
    if (useHingeLoss) {
      loss = new CvsmHingeElementwiseLoss();
    } else {
      loss = new CvsmKlElementwiseLoss();
    }

    // TODO: this can also be a max-margin loss
    GradientOracle<Cvsm, CvsmExample> oracle = new CvsmLoglikelihoodOracle(family, loss);
    
    SufficientStatistics initialParameters = family.getNewSufficientStatistics();
    family.initializeParametersToIdentity(initialParameters);
    if (gaussianVariance > 0.0) {
      initialParameters.perturb(gaussianVariance);
    }
		//System.err.println("init\n"+family.getParameterDescription(initialParameters)); // TODO 

    GradientOptimizer trainer = createGradientOptimizer(examples.size());
    SufficientStatistics trainedParameters = trainer.train(oracle, initialParameters, examples);
		//System.err.println("trained\n"+family.getParameterDescription(trainedParameters)); // TODO 
    
    return trainedParameters;
  }
  
  private EvaluationResult evaluateCvsmModel(Cvsm model, Collection<CvsmExample> examples) {
    int numCorrect = 0;
    int total = 0;
    for (CvsmExample example : examples) {
      System.out.println(example.getLogicalForm());
      Tensor predictionLogProbabilities = model.getInterpretationTree(example.getLogicalForm()).getValue().getTensor();
      System.out.println(Arrays.toString(predictionLogProbabilities.getValues()));
      Tensor predictions = DenseTensor.copyOf(predictionLogProbabilities.findKeysLargerThan(0));
      System.out.println(Arrays.toString(predictions.getValues()));
      System.out.println(Arrays.toString(example.getTargets().getValues()));
      
      if (Arrays.equals(predictions.getValues(), example.getTargets().getValues())) {
        numCorrect += 1;
      }
      total += 1;
    }

    double accuracy = ((double) numCorrect) / total;
    System.out.println("Correct: " + numCorrect + " / " + total);
    System.out.println("Accuracy: " + accuracy);

    return new EvaluationResult(numCorrect, total);
  }

  public static void main(String[] args) throws Exception {
    (new VectorModelTrainer()).run(args);
  }


  private class EvaluationResult {
    public final int numCorrect;
    public final int total;

    public EvaluationResult(int numCorrect, int total) {
      this.numCorrect = numCorrect;
      this.total = total;
    }
  }
}
