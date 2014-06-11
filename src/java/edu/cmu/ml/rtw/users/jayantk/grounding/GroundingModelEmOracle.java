package edu.cmu.ml.rtw.users.jayantk.grounding;

import java.io.Serializable;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.cfg.ParametricCfgFactor;
import com.jayantkrish.jklol.cfg.ParseTree;
import com.jayantkrish.jklol.evaluation.Example;
import com.jayantkrish.jklol.inference.JunctionTree;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.dynamic.DynamicFactorGraph;
import com.jayantkrish.jklol.models.dynamic.WrapperVariablePattern;
import com.jayantkrish.jklol.models.parametric.ListSufficientStatistics;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraphBuilder;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.models.parametric.TensorSufficientStatistics;
import com.jayantkrish.jklol.parallel.MapReduceConfiguration;
import com.jayantkrish.jklol.parallel.MapReduceExecutor;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.training.EmOracle;
import com.jayantkrish.jklol.training.GradientOracle;
import com.jayantkrish.jklol.training.LogFunction;
import com.jayantkrish.jklol.training.LoglikelihoodOracle;
import com.jayantkrish.jklol.training.NullLogFunction;
import com.jayantkrish.jklol.training.OracleAdapter;
import com.jayantkrish.jklol.training.StochasticGradientTrainer;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.IndexedList;

import edu.cmu.ml.rtw.users.jayantk.grounding.GroundingModelEmOracle.GroundingExpectation;
import edu.cmu.ml.rtw.users.jayantk.grounding.GroundingModelInference.GroundingInference;
import edu.cmu.ml.rtw.users.jayantk.grounding.GroundingModelMStep.GroundingModelReducer;
import edu.cmu.ml.rtw.users.jayantk.semparse.RelationType;

/**
 * Oracle for running hard EM, maximizing the generative version of 
 * the grounding model family.
 * 
 * Expects every domain to contain a {@code GaussianGroundingFamily}.
 * 
 * @author jayantk
 */
public class GroundingModelEmOracle implements EmOracle<GroundingModel, GroundingExample, GroundingExpectation>, Serializable {
  
  private final GroundingModelFamily family;
  private final GroundingModelInference inferenceAlgorithm;
  private final List<Domain> domains;
  private final IndexedList<String> domainNames;
  
  // Smoothing parameters for the grounding parameters.
  private final double varianceSmoothing;
  private final double positivePriorSmoothing;
  private final double negativePriorSmoothing;
  
  // Heuristic for improved cluster initialization: only
  // sum up grounding elements which differ from the prediction.
  private final boolean onlyUseDeltas;

  public GroundingModelEmOracle(GroundingModelFamily family, 
      GroundingModelInference inferenceAlgorithm, List<Domain> domains, 
      IndexedList<String> domainNames, double varianceSmoothing, double positivePriorSmoothing,
      double negativePriorSmoothing, boolean onlyUseDeltas) {
    this.family = Preconditions.checkNotNull(family);
    this.inferenceAlgorithm = Preconditions.checkNotNull(inferenceAlgorithm);
    this.domains = Preconditions.checkNotNull(domains);
    this.domainNames = Preconditions.checkNotNull(domainNames);
    
    this.varianceSmoothing = varianceSmoothing;
    this.positivePriorSmoothing = positivePriorSmoothing;
    this.negativePriorSmoothing = negativePriorSmoothing;
    
    this.onlyUseDeltas = onlyUseDeltas;
  }

  @Override
  public GroundingModel instantiateModel(SufficientStatistics parameters) {
    return family.instantiateModel(parameters);
  }

  @Override
    public GroundingExpectation computeExpectations(GroundingModel model, SufficientStatistics currentParameters, GroundingExample example, LogFunction log) {
    Domain domain = domains.get(domainNames.getIndex(example.getDomainName()));
    
    if (!example.hasObservedRelation()) {
      List<String> parserInput = Iterables.getOnlyElement(example.getWords());
      // These weights enforce that the output must agree with the given grounding
      // by assigning -infty weight to all other assignments.
      Tensor expectedGroundingWeights = example.getGrounding().elementwiseLog();
      GroundingInference inferenceResult = inferenceAlgorithm.getConditionalPrediction(
          model, domain, parserInput, expectedGroundingWeights);
      
      Tensor predictedAssignment = inferenceResult.getBestAssignment().getValue();
      Tensor trueAssignment = example.getGrounding();
      QueryTree query = inferenceResult.getBestQuery();
      ParallelFactors queryFactor = inferenceResult.getBestQuery().getOutputLocalWeights();
      System.out.println("computing expectations: " + parserInput + " prediction: " + 
          queryFactor.getTensorAssignmentString(predictedAssignment) + " actual: " + 
          queryFactor.getTensorAssignmentString(trueAssignment) + "\n" + 
			 inferenceResult.getBestParse() + "\n" +
			 query.getAssignmentString(inferenceResult.getBestAssignment()) + "\n");
      
      // Compute unconditional marginals for all predicates.
      IndexedList<RelationType> relations = family.getGroundedRelationTypes();
      List<ParallelFactors> unconditionalMarginals = Lists.newArrayList();
      for (RelationType relation : relations.items()) {
        ParallelFactors relationFactor = model.getFactorForRelation(relation, domain);
        
        int[] valueDimensions = relationFactor.getValueVariables().getVariableNumsArray();
        Tensor unnormalizedLogMarginals = relationFactor.getTensor();
        Tensor maxLogMarginals = unnormalizedLogMarginals.maxOutDimensions(valueDimensions);
        unnormalizedLogMarginals = unnormalizedLogMarginals.elementwiseAddition(maxLogMarginals.elementwiseProduct(-1.0));

        Tensor unnormalizedMarginals = unnormalizedLogMarginals.elementwiseExp();
        Tensor marginals = unnormalizedMarginals.elementwiseProduct(
            unnormalizedMarginals.sumOutDimensions(valueDimensions).elementwiseInverse());

        unconditionalMarginals.add(new ParallelFactors(marginals, relationFactor.getIndexVariables(),
            relationFactor.getValueVariables()));
      }

      return new GroundingExpectation(parserInput, domain, inferenceResult.getBestParse(), 
          inferenceResult.getBestQuery(), inferenceResult.getBestAssignment(),
          inferenceResult.getBestUnconditionalAssignment(), relations, unconditionalMarginals);
    } else {
      throw new UnsupportedOperationException("Not yet implemented");
    }
  }
  
  public SufficientStatistics smoothParameters(SufficientStatistics parameters) {

    // initialize the parameters for each relation; 
    List<SufficientStatistics> groundingParameters = family.getGroundingParameters(parameters);
    for (SufficientStatistics relationParameters : groundingParameters) {
      ListSufficientStatistics relationParameterList = relationParameters.coerceToList();

      TensorSufficientStatistics priorStats = (TensorSufficientStatistics) relationParameterList
          .getStatisticByName(GaussianGroundingFamily.PRIOR_PARAMETERS);
      priorStats.incrementFeature(priorStats.getStatisticNames().outcomeArrayToAssignment("T"), positivePriorSmoothing);
      priorStats.incrementFeature(priorStats.getStatisticNames().outcomeArrayToAssignment("F"), negativePriorSmoothing);
      
      relationParameterList.getStatisticByName(GaussianGroundingFamily.GLOBAL_VARIANCE_PARAMETERS).increment(varianceSmoothing);
    }

    return parameters;
  }

  @Override
  public SufficientStatistics maximizeParameters(List<GroundingExpectation> expectations, 
      SufficientStatistics currentParameters, LogFunction log) {
    // M-step factors into loglikelihood maximization of parser 
    // and grounding functions independently.
    SufficientStatistics newParameters = smoothParameters(family.getNewSufficientStatistics());
    family.incrementParserParameters(newParameters, 
        maximizeParserParameters(family.getCfgFamily(), expectations), 1.0);
    
    // Use mapreduce to quickly run the M-step in parallel.
    MapReduceExecutor executor = MapReduceConfiguration.getMapReduceExecutor();
    SufficientStatistics groundingParameters = executor.mapReduce(expectations, 
        new GroundingModelMStep(family, onlyUseDeltas), new GroundingModelReducer(family));
    newParameters.increment(groundingParameters, 1.0);
    
    GroundingModel model = family.instantiateModel(newParameters);
    System.out.println(model.getParameterDescription(10));
    return newParameters;
  }

  private SufficientStatistics maximizeParserParameters(ParametricCfgFactor parserFamily, 
      List<GroundingExpectation> examples) {
    ParametricFactorGraphBuilder builder = new ParametricFactorGraphBuilder();
    builder.addVariables(parserFamily.getVars());

    builder.addFactor("cfg", parserFamily, new WrapperVariablePattern(parserFamily.getVars()));
    ParametricFactorGraph cfgModel = builder.build();
    
    // Reformat the training data into assignments.
    VariableNumMap inputVar = parserFamily.getInputVar();
    VariableNumMap outputVar = parserFamily.getTreeVar();
    List<Example<Assignment, Assignment>> trainingData = Lists.newArrayList();
    for (int i = 0; i < examples.size(); i++) {
      ParseTree outputTree = examples.get(i).getParse();
      List<Object> terminals = outputTree.getTerminalProductions();

      Assignment input = inputVar.outcomeArrayToAssignment(terminals);
      Assignment output = outputVar.outcomeArrayToAssignment(outputTree);
      trainingData.add(Example.create(input, output));
    }
    
    // Optimize the loglikelihood of the observed parses.
    LoglikelihoodOracle oracle = new LoglikelihoodOracle(cfgModel, new JunctionTree());
    GradientOracle<DynamicFactorGraph, Example<Assignment, Assignment>> adaptedOracle = 
        OracleAdapter.createAssignmentAdapter(oracle);

    StochasticGradientTrainer trainer = StochasticGradientTrainer.createWithL2Regularization(
        5 * trainingData.size(), 1, 1.0, true, false, 0.001, new NullLogFunction());

    SufficientStatistics trainedParameters = trainer.train(adaptedOracle,
        adaptedOracle.initializeGradient(), trainingData);

    return trainedParameters.coerceToList().getStatisticByName("cfg");
  }
  
  public static class GroundingExpectation {
    private final List<String> words;
    private final Domain domain;
    private final ParseTree parse;
    private final QueryTree query;
    private final MultiTree<Tensor> assignment;
    private final MultiTree<Tensor> unconditionalAssignment;
    
    private final IndexedList<RelationType> allRelations;
    private final List<ParallelFactors> unconditionalRelationMarginal;
    
    public GroundingExpectation(List<String> words, Domain domain, 
        ParseTree parse, QueryTree query, MultiTree<Tensor> assignment,
        MultiTree<Tensor> unconditionalAssignment, IndexedList<RelationType> allRelations,
        List<ParallelFactors> unconditionalRelationMarginal) {
      this.words = words;
      this.domain = domain;
      this.parse = parse;
      this.query = query;
      this.assignment = assignment;
      this.unconditionalAssignment = unconditionalAssignment;
      
      this.allRelations = allRelations;
      this.unconditionalRelationMarginal = unconditionalRelationMarginal;
    }

    public List<String> getWords() {
      return words;
    }

    public Domain getDomain() {
      return domain;
    }

    public ParseTree getParse() {
      return parse;
    }

    public QueryTree getQuery() {
      return query;
    }
    
    public List<RelationType> getPredicates() {
      return allRelations.items();
    }
    
    public ParallelFactors getUnconditionalMarginalForPredicate(RelationType relation) {
      return unconditionalRelationMarginal.get(allRelations.getIndex(relation));
    }

    public MultiTree<Tensor> getAssignment() {
      return assignment;
    }
    
    public MultiTree<Tensor> getUnconditionalAssignment() {
      return unconditionalAssignment;
    }
  }

  public static class VectorExample {
    private final Tensor assignment;
    private final DiscreteFactor domainVectors;
    
    public VectorExample(Tensor assignment, DiscreteFactor domainVectors) {
      this.assignment = assignment;
      this.domainVectors = domainVectors;
    }
    
    public Tensor getAssignment() {
      return assignment;
    }
    public DiscreteFactor getDomainVectors() {
      return domainVectors;
    }
  }
}
