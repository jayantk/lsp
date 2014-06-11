package edu.cmu.ml.rtw.users.jayantk.grounding;

import static ch.lambdaj.Lambda.extract;
import static ch.lambdaj.Lambda.on;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.cfg.ParametricCfgFactor;
import com.jayantkrish.jklol.cfg.ParseTree;
import com.jayantkrish.jklol.models.parametric.ListSufficientStatistics;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.IndexedList;

import edu.cmu.ml.rtw.users.jayantk.semparse.RelationType;

/**
 * The graphical model family containing the semantic parser and grounding
 * factor graph. This class is similar to a {@code ParametricFactorGraph}.
 * 
 * @author jayantk
 */
public class GroundingModelFamily implements Serializable{

  // Relation types whose values are classifiers that must be learned.
  private final IndexedList<RelationType> groundedRelationTypes;
  // Relation types whose values are known a priori (e.g., from a KB).
  private final IndexedList<RelationType> knownRelationTypes;

  // The various domains in which this model family can be instantiated.
  // Each domain consists of a separate set of possibly-related entities.
  private final List<Domain> domains;
  private final IndexedList<String> domainNames;

  // The parameterized semantic parser.
  private final ParametricCfgFactor parserCfgFactor;

  // The variables that index the names of the outputs and the value for each output.
  // private final VariableNumMap relationOutputVar;
  // private final VariableNumMap relationBooleanVar;

  private static final int PARSER_PARAMETER_INDEX = 0;
  private static final int GROUNDING_PARAMETER_INDEX = 1;

  public GroundingModelFamily(ParametricCfgFactor parserCfgFactor, IndexedList<RelationType> groundedRelationTypes, 
      IndexedList<RelationType> knownRelationTypes, List<Domain> domains) {
    this.parserCfgFactor = Preconditions.checkNotNull(parserCfgFactor);
    this.groundedRelationTypes = Preconditions.checkNotNull(groundedRelationTypes);
    this.knownRelationTypes = Preconditions.checkNotNull(knownRelationTypes);
    this.domains = Lists.newArrayList(domains);

    domainNames = IndexedList.create(extract(domains, on(Domain.class).getName()));
  }

  public void addDomain(Domain domain) {
    Preconditions.checkArgument(!domainNames.contains(domain.getName()));
    domains.add(domain);
    domainNames.add(domain.getName());
  }

  public List<Domain> getDomains() {
    return domains;
  }

  public IndexedList<String> getDomainNames() {
    return domainNames;
  }
  
  public IndexedList<RelationType> getGroundedRelationTypes() {
    return groundedRelationTypes;
  }
  
  public ParametricCfgFactor getCfgFamily() {
    return parserCfgFactor;
  }

  public SufficientStatistics transferParameters(GroundingModel oldModel) {
    IndexedList<RelationType> oldPredicateTypes = oldModel.getPredicates();
    IndexedList<String> oldPredicateNames = IndexedList.create();
    for (RelationType predicate : oldPredicateTypes.items()) {
      oldPredicateNames.add(predicate.getName());
    }
    List<SufficientStatistics> oldPredicateParameters = oldModel.getPredicateParameters();

    List<String> relationNames = Lists.newArrayList();
    List<SufficientStatistics> relationParameters = Lists.newArrayList();
    for (int i = 0; i < groundedRelationTypes.size(); i++) {
      String curName = groundedRelationTypes.get(i).getName();
      relationNames.add(curName);

      SufficientStatistics newParameters = domains.get(0).getFamilyForRelation(groundedRelationTypes.get(i)).getNewSufficientStatistics();

      if (oldPredicateNames.contains(curName)) {
        newParameters.transferParameters(oldPredicateParameters.get(oldPredicateNames.getIndex(curName)));
      } 
      relationParameters.add(newParameters);
    }
    SufficientStatistics relationParameterList = new ListSufficientStatistics(relationNames, relationParameters);

    SufficientStatistics parserParameters = parserCfgFactor.getNewSufficientStatistics();
    parserParameters.transferParameters(oldModel.getParserParameters());

    return new ListSufficientStatistics(Arrays.asList("cfg", "relations"), 
        Arrays.asList(parserParameters, relationParameterList));
  }

  public SufficientStatistics getNewSufficientStatistics() {
    List<SufficientStatistics> relationParameters = Lists.newArrayList();
    List<String> relationNames = Lists.newArrayList();
    for (int i = 0; i < groundedRelationTypes.size(); i++) {
      // All domains have the same features and feature indexes.
      relationNames.add(groundedRelationTypes.get(i).getName());
      relationParameters.add(domains.get(0).getFamilyForRelation(groundedRelationTypes.get(i)).getNewSufficientStatistics());
    }
    SufficientStatistics relationParameterList = new ListSufficientStatistics(relationNames, relationParameters);

    return new ListSufficientStatistics(Arrays.asList("cfg", "relations"), 
        Arrays.asList(parserCfgFactor.getNewSufficientStatistics(), relationParameterList));
  }

  public GroundingModel instantiateModel(SufficientStatistics parameters) {
    SufficientStatistics parserParameters = getParserParameters(parameters);    
    List<SufficientStatistics> groundingParameters = getGroundingParameters(parameters);

    return new GroundingModel(groundedRelationTypes, knownRelationTypes, 
        groundingParameters, parserParameters, parserCfgFactor, domains);
  }

  public void incrementParserParameters(SufficientStatistics gradient,
                                        SufficientStatistics parameters,
      List<String> input, ParseTree tree, double multiplier) {
    // Returns a reference, so updating parserParameters updates parameters.
    SufficientStatistics parserGradient = getParserParameters(gradient);
    SufficientStatistics parserParameters = getParserParameters(parameters);

    Assignment assignment = parserCfgFactor.getInputVar().outcomeArrayToAssignment(input)
        .union(parserCfgFactor.getTreeVar().outcomeArrayToAssignment(tree));    
    parserCfgFactor.incrementSufficientStatisticsFromAssignment(parserGradient,
                                                                parserParameters, assignment, multiplier);
  }
  
  public void incrementParserParameters(SufficientStatistics parameters, 
      SufficientStatistics parserParameterIncrement, double multiplier) {
    getParserParameters(parameters).increment(parserParameterIncrement, multiplier);
  }

  /*
  public void incrementGroundingRelationParameters(RelationType relation, Domain domain, 
      ParallelFactors probabilities) {
    int index = groundedRelationTypes.getIndex(relation);
    RelationGroundingFamily relationFamily = domain.getFamilyForRelation(relation);

    relationFamily.incrementSufficientStatistics(
          parameters.get(index), probabilities, 1.0);
  }
   */

  public void incrementGroundingParameters(String domainName, SufficientStatistics parameters,
      QueryTree query, MultiTree<Tensor> assignment, double multiplier) {
    Preconditions.checkArgument(domainNames.contains(domainName));
    Domain domain = domains.get(domainNames.getIndex(domainName));

    List<SufficientStatistics> groundingParameters = getGroundingParameters(parameters);
    recursivelyUpdateGroundingParameters(domain, query, assignment, groundingParameters, multiplier);
  }

  public void incrementGroundingParameters(String domainName, SufficientStatistics parameters,
      RelationType relation, ParallelFactors marginal, double multiplier) {
    if (groundedRelationTypes.contains(relation)) {
      List<SufficientStatistics> groundingParameters = getGroundingParameters(parameters);
      Domain domain = domains.get(domainNames.getIndex(domainName));

      int index = groundedRelationTypes.getIndex(relation);
      GroundingFamily relationFamily = domain.getFamilyForRelation(relation);
      relationFamily.incrementSufficientStatistics(groundingParameters.get(index), groundingParameters.get(index),
          marginal.getTensor(), multiplier);
    }
  }

  private void recursivelyUpdateGroundingParameters(Domain domain, QueryTree query, 
      MultiTree<Tensor> assignment, List<SufficientStatistics> parameters, double multiplier) {
    if (query.hasPredicate() && groundedRelationTypes.contains(query.getPredicate())) {
      // Parameters only need to be updated for relation types that are grounded.
      // (No learning is required for relations where the values are already known.)

      RelationType relation = query.getPredicate();
      //System.out.println("updating: " + relation);

      int index = groundedRelationTypes.getIndex(relation);
      GroundingFamily relationFamily = domain.getFamilyForRelation(relation);

      Tensor bestAssignment = assignment.getValue();
      relationFamily.incrementSufficientStatistics(parameters.get(index), 
          parameters.get(index), bestAssignment, multiplier);

      System.out.println(relation + ": " + parameters.get(index));
    }

    List<QueryTree> subtrees = query.getSubtrees();
    List<MultiTree<Tensor>> childTrees = assignment.getChildren();
    Preconditions.checkState(subtrees.size() == childTrees.size());
    for (int i = 0; i < subtrees.size(); i++) {
      recursivelyUpdateGroundingParameters(domain, subtrees.get(i), childTrees.get(i), parameters, multiplier);
    }
  }
  

  private SufficientStatistics getParserParameters(SufficientStatistics parameters) {
    return parameters.coerceToList().getStatistics().get(PARSER_PARAMETER_INDEX);
  }

  public List<SufficientStatistics> getGroundingParameters(SufficientStatistics parameters) {
    return parameters.coerceToList().getStatistics().get(GROUNDING_PARAMETER_INDEX).coerceToList().getStatistics();
  }
}
