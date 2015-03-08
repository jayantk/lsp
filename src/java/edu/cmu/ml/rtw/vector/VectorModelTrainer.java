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

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Maps;
import com.jayantkrish.jklol.ccg.lambda.ApplicationExpression;
import com.jayantkrish.jklol.ccg.lambda.ConstantExpression;
import com.jayantkrish.jklol.ccg.lambda.Expression;
import com.jayantkrish.jklol.cli.AbstractCli;
import com.jayantkrish.jklol.cvsm.ConstantLrtFamily;
import com.jayantkrish.jklol.cvsm.Cvsm;
import com.jayantkrish.jklol.cvsm.CvsmExample;
import com.jayantkrish.jklol.cvsm.CvsmFamily;
import com.jayantkrish.jklol.cvsm.CvsmLoglikelihoodOracle;
import com.jayantkrish.jklol.cvsm.CvsmLoglikelihoodOracle.CvsmKlElementwiseLoss;
import com.jayantkrish.jklol.cvsm.CvsmLoglikelihoodOracle.CvsmLoss;
import com.jayantkrish.jklol.cvsm.LrtFamily;
import com.jayantkrish.jklol.cvsm.TensorLrtFamily;
import com.jayantkrish.jklol.cvsm.lrt.TensorLowRankTensor;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.tensor.DenseTensor;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.training.GradientOptimizer;
import com.jayantkrish.jklol.training.GradientOracle;
import com.jayantkrish.jklol.util.IndexedList;

import edu.cmu.ml.rtw.users.jayantk.grounding.Domain;
import edu.cmu.ml.rtw.users.jayantk.grounding.GroundingExample;
import edu.cmu.ml.rtw.users.jayantk.grounding.GroundingModelTrainer;

public class VectorModelTrainer extends AbstractCli {
  
  private OptionSpec<String> domainDir;
  private OptionSpec<String> trainingFilename;
  
  private OptionSpec<Double> gaussianVariance;

  public VectorModelTrainer() {
    super(CommonOptions.STOCHASTIC_GRADIENT, CommonOptions.MAP_REDUCE,
        CommonOptions.LBFGS);
  }

  @Override
  public void initializeOptions(OptionParser parser) {
    domainDir = parser.accepts("domainDir").withRequiredArg().ofType(String.class).required();
    trainingFilename = parser.accepts("trainingFilename").withOptionalArg().ofType(String.class).defaultsTo("training.txt");

    gaussianVariance = parser.accepts("gaussianVariance").withRequiredArg().ofType(Double.class).defaultsTo(0.0);
    
  }

  @Override
  public void run(OptionSet options) {
    List<Domain> domains = Domain.readDomainsFromDirectory(options.valueOf(domainDir), 
        options.valueOf(trainingFilename), null, Integer.MAX_VALUE,
        false, false, false);
    IndexedList<String> domainNames = IndexedList.create(extract(domains, on(Domain.class).getName()));

    VectorSpaceModelInterface vsmInterface = new SequenceRnnVectorSpaceModel();
    
    Multimap<String, GroundingExample> trainingFoldsOrig = GroundingModelTrainer.getCrossValidationFolds(domains);
    Multimap<String, CvsmExample> trainingFolds = HashMultimap.create();
    for (String key : trainingFoldsOrig.keySet()) {
      for (GroundingExample example : trainingFoldsOrig.get(key)) {
        trainingFolds.put(key, convertExample(example, domains, domainNames, vsmInterface));
      }
    }

    List<CvsmExample> allExamples = Lists.newArrayList();
    Multimap<String, CvsmExample> testFolds = HashMultimap.create();
    for (Domain domain : domains) {
      for (GroundingExample example : domain.getTrainingExamples()) {
        CvsmExample cvsmExample = convertExample(example, domains, domainNames, vsmInterface);
        allExamples.add(cvsmExample);
        testFolds.put(domain.getName(), cvsmExample);
      }
    }

    CvsmFamily family = buildFamily(allExamples, domains, domainNames);

    Map<String, SufficientStatistics> trainedParameters = Maps.newHashMap();
    for (String foldName : trainingFoldsOrig.keySet()) {
      SufficientStatistics parameters = train(family, trainingFolds.get(foldName), options.valueOf(gaussianVariance));
      trainedParameters.put(foldName, parameters);
    }

    int numCorrectTotal = 0;
    int numTotal = 0;
    for (String testFold : testFolds.keySet()) {
      Cvsm model = family.getModelFromParameters(trainedParameters.get(testFold));
      EvaluationResult result = evaluateCvsmModel(model, testFolds.get(testFold));
      numCorrectTotal += result.numCorrect;
      numTotal += result.total;
    }

    double accuracy = ((double) numCorrectTotal) / numTotal;
    System.out.println("Correct: " + numCorrectTotal + " / " + numTotal);
    System.out.println("Accuracy: " + accuracy);
  }
  
  public String getCategoryTensorName(String domainName) {
    return "domain:" + domainName + ":category";
  }
  
  private void extractTensorNamesFromExpression(Expression expression, IndexedList<String> tensorNames,
      List<LrtFamily> parameters, VariableNumMap featureVar) {
    if (expression instanceof ConstantExpression) {
      String name = ((ConstantExpression) expression).getName();
      if (!name.startsWith("op:") && !tensorNames.contains(name)) {
        tensorNames.add(name);
        parameters.add(new TensorLrtFamily(featureVar));
      }
    } else if (expression instanceof ApplicationExpression) {
      List<Expression> subexpressions = ((ApplicationExpression) expression).getSubexpressions();
      for (Expression subexpression : subexpressions) {
        extractTensorNamesFromExpression(subexpression, tensorNames, parameters, featureVar);
      }
    }
  }

  private CvsmExample convertExample(GroundingExample example, List<Domain> domains,
      IndexedList<String> domainNames, VectorSpaceModelInterface vectorSpaceModel) {
    // convert to cvsm example using CCG parse and templates
    Expression cvsmFormula = vectorSpaceModel.getFormula(example);
        
    String domainCategoryFeaturesName = getCategoryTensorName(example.getDomainName());
    Expression newExpression = new ApplicationExpression(Lists.newArrayList(new ConstantExpression("op:logistic"),
            new ApplicationExpression(Lists.newArrayList(new ConstantExpression("op:matvecmul"), new ConstantExpression(domainCategoryFeaturesName), cvsmFormula))));
    
    System.out.println(newExpression.toString());
    
    Domain domain = domains.get(domainNames.getIndex(example.getDomainName()));
    VariableNumMap vars = domain.getCategoryFamily().getFeatureVectors().getVars();
    VariableNumMap featureVar = vars.getVariablesByName("catFeatures");
    VariableNumMap truthVar = vars.getVariablesByName("truthVal");
    vars = vars.removeAll(featureVar);
    Tensor grounding = new TableFactor(vars, example.getGrounding()).conditional(truthVar.outcomeArrayToAssignment("T")).getWeights();
    grounding = DenseTensor.copyOf(grounding);
    System.out.println(Arrays.toString(grounding.getDimensionNumbers()));
    
    return new CvsmExample(newExpression, grounding, null);
  }

  private CvsmFamily buildFamily(Collection<CvsmExample> examples, List<Domain> domains, IndexedList<String> domainNames) {

    IndexedList<String> tensorNames = IndexedList.create();
    List<LrtFamily> tensorParameters = Lists.newArrayList();
    
    VariableNumMap featureVar = null;
    VariableNumMap truthVar = null;
    
    for (Domain domain : domains) {
      // Get the object features of each object in the domain.
      // The features are for the assignment (entity name, T), and we just want features per entity,
      // so condition on the "T" value.
      DiscreteFactor categoryFeatures = domain.getCategoryFamily().getFeatureVectors();
      truthVar = categoryFeatures.getVars().getVariablesByName("truthVal");
      categoryFeatures = categoryFeatures.conditional(truthVar.outcomeArrayToAssignment("T"));
      
      System.out.println(categoryFeatures.getVars());
      
      if (featureVar == null) {
        featureVar = categoryFeatures.getVars().getVariablesByName("catFeatures");
      }

      tensorNames.add(getCategoryTensorName(domain.getName()));
      tensorParameters.add(new ConstantLrtFamily(categoryFeatures.getVars(),
          new TensorLowRankTensor(categoryFeatures.getWeights())));
    }

    List<CvsmExample> cvsmExamples = Lists.newArrayList();
    for (CvsmExample example : examples) {
      // Initialize tensors for any variables referenced in this formula.  
      extractTensorNamesFromExpression(example.getLogicalForm(), tensorNames, tensorParameters, featureVar);
    }

    // Read in the set of vectors, etc. from the training examples and
    // instantiate vectors to create the family.
    return new CvsmFamily(tensorNames, tensorParameters);
  }

  private SufficientStatistics train(CvsmFamily family, Collection<CvsmExample> examples, double gaussianVariance) {
    // An elementwise log-loss for binary elements.
    CvsmLoss loss = new CvsmKlElementwiseLoss();

    // TODO: this can also be a max-margin loss
    GradientOracle<Cvsm, CvsmExample> oracle = new CvsmLoglikelihoodOracle(family, loss);
    
    SufficientStatistics initialParameters = family.getNewSufficientStatistics();
    if (gaussianVariance > 0.0) {
      initialParameters.perturb(gaussianVariance);
    }

    GradientOptimizer trainer = createGradientOptimizer(examples.size());
    SufficientStatistics trainedParameters = trainer.train(oracle, initialParameters, examples);
    
    return trainedParameters;
  }
  
  private EvaluationResult evaluateCvsmModel(Cvsm model, Collection<CvsmExample> examples) {
    int numCorrect = 0;
    int total = 0;
    for (CvsmExample example : examples) {
      System.out.println(example.getLogicalForm());
      Tensor predictionProbabilities = model.getInterpretationTree(example.getLogicalForm()).getValue().getTensor();
      System.out.println(Arrays.toString(predictionProbabilities.getValues()));
      Tensor predictions = DenseTensor.copyOf(predictionProbabilities.findKeysLargerThan(0.5));
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
