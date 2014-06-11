package edu.cmu.ml.rtw.users.jayantk.semparse;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.lambda.ApplicationExpression;
import com.jayantkrish.jklol.ccg.lambda.CommutativeOperator;
import com.jayantkrish.jklol.ccg.lambda.ConstantExpression;
import com.jayantkrish.jklol.ccg.lambda.Expression;
import com.jayantkrish.jklol.ccg.lambda.LambdaExpression;
import com.jayantkrish.jklol.ccg.lambda.QuantifierExpression;
import com.jayantkrish.jklol.cfg.ParametricCfgFactor;
import com.jayantkrish.jklol.cfg.ParseTree;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.FunctionFactor;
import com.jayantkrish.jklol.models.TableFactorBuilder;
import com.jayantkrish.jklol.models.Variable;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.loglinear.IndicatorLogLinearFactor;
import com.jayantkrish.jklol.models.parametric.ParametricFactor;
import com.jayantkrish.jklol.tensor.SparseTensorBuilder;

import edu.cmu.ml.rtw.users.jayantk.semparse.Lexicon.LexicalCategory;
import edu.cmu.ml.rtw.users.jayantk.semparse.Lexicon.TypeRaisingRule;

/**
 * {@code SemanticParser} is an algorithm for extracting semantic
 * predicate-argument structure from sentences. The parser does not actually
 * parse sentences, but rather constructs a CFG encoding predicate-argument
 * structure. The CFG is parsed / trained using normal algorithms for CFGs.
 * Trees predicted by the grammar can be converted into semantic parses using
 * {@link #convertParseTreeToSemanticParse(ParseTree)}.
 * 
 * SemanticParser TODO 
 * -Implicit sets (democrats = {x : politicianParty(x, democrat)}) 
 * - Is-a constructions => generalizations?
 * - Conjunctions (and / or)
 * 
 * @author jayantk
 */
public class SemanticParser implements Serializable{

  /**
   * 
   */
  private static final long serialVersionUID = 1L;
  private Lexicon<String> lexicon;
  
  public static enum RuleType {
    NULL, APPLICATION, TYPE_RAISE 
  }

  public SemanticParser(Lexicon<String> lexicon) {
    this.lexicon = lexicon;
  }

  public ParametricCfgFactor toCfgFactor(VariableNumMap inputVar, VariableNumMap parseTreeVar, int beamSize,
      Predicate<? super ParseTree> validTreeFilter) {
    List<Nonterminal> nonterminals = getNonterminalsInLexicon(lexicon);
    List<Edge> edgeTypes = getEdgeTypesInLexicon(lexicon);
    Variable nonterminalVariable = new DiscreteVariable("nonterminal", nonterminals);
    Variable terminalVariable = new DiscreteVariable("terminal", lexicon.getAllRecognizedWordSequences());
    Variable edgeTypeVariable = new DiscreteVariable("edgeTypes", edgeTypes);

    // Instantiate the variables which define the two CFG factors.
    VariableNumMap leftVar = new VariableNumMap(Arrays.asList(0), Arrays.asList("left"), Arrays.asList(nonterminalVariable));
    VariableNumMap rightVar = new VariableNumMap(Arrays.asList(1), Arrays.asList("right"), Arrays.asList(nonterminalVariable));
    VariableNumMap parentVar = new VariableNumMap(Arrays.asList(3), Arrays.asList("parent"), Arrays.asList(nonterminalVariable));
    VariableNumMap terminalVar = new VariableNumMap(Arrays.asList(2), Arrays.asList("terminals"), Arrays.asList(terminalVariable));
    VariableNumMap ruleVar = new VariableNumMap(Arrays.asList(4), Arrays.asList("ruleTypes"), Arrays.asList(edgeTypeVariable));
    VariableNumMap nonterminalVars = VariableNumMap.unionAll(leftVar, rightVar, parentVar, ruleVar);
    VariableNumMap terminalVars = VariableNumMap.unionAll(terminalVar, parentVar, ruleVar);

    // Create the sparsity pattern of the returned factor such that predicates
    // only accept correctly-typed arguments.
    // TODO (jayantk): Maybe experiment with different features (e.g.,
    // incorporating the predicate hierarchy).
    System.out.println("Getting nonterminal combinations.");
    TableFactorBuilder sparsityPattern = getPossibleNonterminalCombinations(nonterminalVars, nonterminals, lexicon);
    ParametricFactor nonterminalFactor = new IndicatorLogLinearFactor(nonterminalVars, 
        sparsityPattern.build());

    // Create the sparsity pattern for the terminal symbols.
    System.out.println("Creating terminal combinations.");
    TableFactorBuilder terminalSparsityBuilder = getPossibleTerminalCombinations(terminalVars, lexicon);
    ParametricFactor terminalFactor = new IndicatorLogLinearFactor(terminalVars, 
        terminalSparsityBuilder.build()); 

    Function<Object, List<Object>> preprocessor = new SemanticParsePreprocessor(lexicon.getWords());
    return new ParametricCfgFactor(parentVar, leftVar, rightVar, terminalVar, ruleVar,
        parseTreeVar, inputVar, nonterminalFactor, terminalFactor, preprocessor, validTreeFilter, 
        beamSize, false);
  }

  public static FunctionFactor cfgParseToSemanticParseFactor(VariableNumMap inputVar, VariableNumMap outputVar) {
    Function<Object, Object> function = new Function<Object, Object>() {
      @Override
      public SemanticParse apply(Object parseObject) {
        Preconditions.checkArgument(parseObject instanceof ParseTree);
        return convertParseTreeToSemanticParse((ParseTree) parseObject);
      }
    };
    return new FunctionFactor(inputVar, outputVar, function,
        null, null);
  }
  
  private static void addAllApplicationResults(Nonterminal current, Set<Nonterminal> addTo) {
    addTo.add(current);
    if (current.isFunctional()) {
      for (Nonterminal applicationResult : current.getApplicationResults()) {
        addAllApplicationResults(applicationResult, addTo);
      }
    }
  }

  private static List<Nonterminal> getNonterminalsInLexicon(Lexicon<String> lexicon) {
    // Compute all of the nonterminals in the grammar.
    Set<Nonterminal> predicateNonterminals = Sets.newHashSet();
    for (LexicalCategory category : lexicon.getLexicalCategories()) {
      Nonterminal current = Nonterminal.createFromPredicate(category.getType(), category.getSemanticType());
      addAllApplicationResults(current, predicateNonterminals);
    }

    // Consider the possible outputs of the type raising rules.
    for (TypeRaisingRule rule : lexicon.getTypeRaisingRules()) {
      Nonterminal typeRaisingResult = Nonterminal.createFromPredicate(
          ((DirectedFunctionType) rule.getOutputType()).getReturnType(),
          rule.getSemanticType());
      predicateNonterminals.add(typeRaisingResult);
    }

    List<Nonterminal> nonterminals = Lists.newArrayList(predicateNonterminals);
    System.out.println(nonterminals.size() + " nonterminals in grammar");

    return nonterminals;
  }
  
  private static Set<TypeRaisingRule> getTypeRaisingRules(Type typeToRaise, Lexicon<String> lexicon) {
    Set<TypeRaisingRule> rules = Sets.newHashSet();
    for (TypeRaisingRule candidateRule : lexicon.getTypeRaisingRules()) {
      if (typeToRaise.hasAncestor(candidateRule.getInputType())) {
        rules.add(candidateRule);
        // System.out.println("can raise: " + typeToRaise + " to " + candidateRule.getOutputType() + " / " + candidateRule.getSemanticType());
      }
    }
    return rules;
  }

  private static List<Edge> getEdgeTypesInLexicon(Lexicon<String> lexicon) {
    Set<Edge> edgeTypes = Sets.newHashSet();
    edgeTypes.add(Edge.LEFT_APPLICATION);
    edgeTypes.add(Edge.RIGHT_APPLICATION);

    // Type-raising rules get hidden in the edge types.
    for (Edge.Direction direction : Arrays.asList(Edge.Direction.LEFT, Edge.Direction.RIGHT)) {
      List<RuleType> ruleTypes = Lists.newArrayList(RuleType.values());
      ruleTypes.remove(RuleType.NULL);
      ruleTypes.remove(RuleType.TYPE_RAISE);
      for (RuleType ruleType : ruleTypes) {
        for (TypeRaisingRule rule : lexicon.getTypeRaisingRules()) {
          edgeTypes.add(Edge.create(direction, ruleType, rule));
        }
      }
    }
    
    // Terminal rules may hide values in edges.
    for (LexicalCategory category : lexicon.getLexicalCategories()) {
      edgeTypes.add(Edge.createTerminal(category.getFixedValues()));
    }
    
    System.out.println(edgeTypes.size() + " edge types in parser");

    return Lists.newArrayList(edgeTypes);
  }

  private static TableFactorBuilder getPossibleNonterminalCombinations(VariableNumMap nonterminalVars,
      List<Nonterminal> nonterminals, Lexicon<String> lexicon) {
    TableFactorBuilder sparsityPattern = new TableFactorBuilder(nonterminalVars, 
        SparseTensorBuilder.getFactory());

    // For each nonterminal, identify which argument types it can fill. typeNonterminal map
    // includes all nonterminals which are ancestors of a type 
    SetMultimap<Type, Nonterminal> typeNonterminalMap = HashMultimap.create();
    // nonterminal which have the same type as type.
    SetMultimap<Type, Nonterminal> typeNonterminalEqualMap = HashMultimap.create(); 
    for (Nonterminal nonterminal : nonterminals) {
      Type nonterminalType = nonterminal.getType();
      if (nonterminalType instanceof DirectedFunctionType) {
        continue;
      }
      typeNonterminalEqualMap.put(nonterminalType, nonterminal);
      
      Iterator<? extends Type> ancestorIterator = nonterminalType.getAncestorIterator();
      while (ancestorIterator.hasNext()) {
        typeNonterminalMap.put(ancestorIterator.next(), nonterminal);
      }
    }

    for (Nonterminal nonterminal : nonterminals) {
      Type nonterminalType = nonterminal.getType();
      addTypeCombinations(nonterminalType, nonterminal, null, sparsityPattern, 
          typeNonterminalMap, typeNonterminalEqualMap);

      // Try raising the current type.
      for (TypeRaisingRule leftRaiseRule : getTypeRaisingRules(nonterminalType, lexicon)) {
        Type leftRaisedType = leftRaiseRule.getOutputType();
        addTypeCombinations(leftRaisedType, nonterminal, leftRaiseRule, sparsityPattern, 
            typeNonterminalMap, typeNonterminalEqualMap);
      }
    }

    System.out.println("Nonterminal sparsity pattern: " + sparsityPattern.size());
    return sparsityPattern;
  }

  private static void addTypeCombinations(Type parentType, Nonterminal parentNonterminal, 
      TypeRaisingRule rule, TableFactorBuilder sparsityPattern, 
      SetMultimap<Type, Nonterminal> typeArgumentMap,
      SetMultimap<Type, Nonterminal> typeArgumentEqualMap) {

    // Determine whether nonterminal can participate in each of the coded CCG rules.
    if (parentType instanceof DirectedFunctionType) {
      // Can apply function application rule.
      DirectedFunctionType parentAsFunction = (DirectedFunctionType) parentType;
      Type expectedArgumentType = parentAsFunction.getArgumentType();

      Set<Nonterminal> applicationResults;
      if (rule != null) {
        applicationResults = Collections.singleton(new Nonterminal(
            parentAsFunction.getReturnType(), rule.getSemanticType()));
      } else {
        applicationResults = parentNonterminal.getApplicationResults();
      }

      for (Nonterminal applicationResult : applicationResults) {
        Set<Nonterminal> possibleArgumentNonterminals = null;
        if (parentAsFunction.acceptsDescendantTypesAsArgument()) {
          possibleArgumentNonterminals = typeArgumentMap.get(expectedArgumentType);
        } else {
          possibleArgumentNonterminals = typeArgumentEqualMap.get(expectedArgumentType);
        }
        for (Nonterminal argument : possibleArgumentNonterminals) {
          if (parentAsFunction.takesArgumentOnRight()) {
            sparsityPattern.setWeight(1.0, parentNonterminal, argument, applicationResult,
                Edge.create(Edge.Direction.LEFT, RuleType.APPLICATION, rule));
          }
          if (parentAsFunction.takesArgumentOnLeft()) {
            sparsityPattern.setWeight(1.0, argument, parentNonterminal, applicationResult,
                Edge.create(Edge.Direction.RIGHT, RuleType.APPLICATION, rule));
          }
        }
      }
    }
  }
  
  private static TableFactorBuilder getPossibleTerminalCombinations(VariableNumMap terminalVars,
      Lexicon<String> lexicon) {
    // Create the terminal -> nonterminal factor.
    TableFactorBuilder terminalSparsityBuilder = new TableFactorBuilder(terminalVars, 
        SparseTensorBuilder.getFactory());
    for (List<String> terminalSequence : lexicon.getTriggerSequences()) {
      for (LexicalCategory category : lexicon.getCategories(terminalSequence)) {
        Edge edge = Edge.createTerminal(category.getFixedValues());
        terminalSparsityBuilder.setWeight(1.0, terminalSequence,
            Nonterminal.createFromPredicate(category.getType(), category.getSemanticType()), edge);
      }
    }
    System.out.println("Terminal sparsity pattern: " + terminalSparsityBuilder.size());
    return terminalSparsityBuilder;
  }

  /**
   * Converts a {@code ParseTree} in the context-free grammar into a
   * {@code SemanticParse}. This process strips out much of the unnecessary
   * information contained in {@code tree} (e.g., which words were ignored),
   * returning only the predicate-argument structure it encodes.
   * 
   * @param tree
   * @return
   */
  public static SemanticParse convertParseTreeToSemanticParse(ParseTree tree) {
      if (tree == null || tree == ParseTree.EMPTY) {
      return SemanticParse.EMPTY;
    }

    Nonterminal root = (Nonterminal) tree.getRoot();
    Edge edge = (Edge) tree.getRuleType();
    if (tree.isTerminal()) {
      return SemanticParse.createFromType(root.getType(), root.getSemanticType()
          .addFixedArguments(edge.getFixedArguments()),   
          tree.getTerminalProductions()); 
    } else {
      SemanticParse leftParse = convertParseTreeToSemanticParse(tree.getLeft());
      SemanticParse rightParse = convertParseTreeToSemanticParse(tree.getRight());

      // There is some sort of predicate-argument structure at this production
      // rule.
      SemanticParse parent = null;
      SemanticParse child = null;
      if (edge.getDirection() == Edge.Direction.LEFT) {
        parent = leftParse;
        child = rightParse;
      } else {
        parent = rightParse;
        child = leftParse;
      }

      if (edge.getType() == RuleType.APPLICATION) {
        if (edge.getParentTypeRaising() != null) {
          // Type raise the parent semantic parse.
          return parent.typeRaiseAndApply(edge.getParentTypeRaising(), child); 
        } else {
          return parent.apply(child);
        }
      } else {
        throw new RuntimeException("Invalid edge type for binary rules: " + edge);
      }
    }
  }
  
  public static LambdaExpression getLogicalFormFromSemanticParse(SemanticPredicate parse) {
    String relationName = parse.getRelation().getName();
    int outputArgumentIndex = parse.getOutputArgument();
    List<ConstantExpression> arguments = ConstantExpression.generateUniqueVariables(parse.getRelation().getNumArguments());

    List<Expression> appList = Lists.newArrayList();
    appList.add(new ConstantExpression(relationName));
    appList.addAll(arguments);
    Expression expression = new ApplicationExpression(appList);
            
    // If this parse has any arguments, and them with this one.
    List<Expression> argumentExpressions = Lists.newArrayList(expression);
    Map<Integer, SemanticPredicate> parseArguments = parse.getArguments();
    for (Integer argNum : parseArguments.keySet()) {
      SemanticPredicate parseArg = parseArguments.get(argNum);
      LambdaExpression argExpression = getLogicalFormFromSemanticParse(parseArg);
      
      Preconditions.checkState(argExpression.getLocallyBoundVariables().size() == 1);
      List<Expression> argApplicationArgs = Lists.<Expression>newArrayList(argExpression, arguments.get(argNum));

      argumentExpressions.add(new ApplicationExpression(argApplicationArgs));
    }

    // if (argumentExpressions.size() > 1) {
      expression = new CommutativeOperator(new ConstantExpression("and"), argumentExpressions);
      // }
    
    if (arguments.size() > 1) {
      List<ConstantExpression> quantified = Lists.newArrayList();
      quantified.addAll(arguments.subList(0, outputArgumentIndex));
      quantified.addAll(arguments.subList(outputArgumentIndex + 1, arguments.size()));

      expression = new QuantifierExpression("exists", quantified, expression);
    }

    List<ConstantExpression> lambdaArgs = Lists.newArrayList(arguments.get(outputArgumentIndex));
    return new LambdaExpression(lambdaArgs, expression);
  }

  /**
   * An edge relates the arguments of two predicates in a semantic parse. Edges
   * may also contain hidden trace predicates mediating the connection. Hence,
   * every edge behaves like two edges: one connecting the left argument to an
   * argument of the trace predicate, and one connecting the trace predicate to
   * the right argument. In the case where the trace predicate is equality, this
   * reduces to simply tying the two arguments together.
   * 
   * @author jayantk
   */
  private static class Edge implements Serializable {
    // Directionality of the edge, which determines the head word of the
    // resulting phrase.
    private final Direction direction;

    // Type of the edge, determining what (if any) semantic information is
    // encoded
    // in this connection.
    private final RuleType type;
    
    // If non-null, the child nonterminal was type-raised.  
    private final TypeRaisingRule parentTypeRaising;
    
    private final Map<Integer, Object> fixedArguments;

    public static enum Direction {
      LEFT, RIGHT, TERMINAL,
    };

    // The direction in the names of these two things is the functional category
    // which takes an argument during application.
    public static final Edge LEFT_APPLICATION = create(Direction.LEFT, RuleType.APPLICATION, null);
    public static final Edge RIGHT_APPLICATION = create(Direction.RIGHT, RuleType.APPLICATION, null);
    
    private Edge(Direction direction, RuleType type, TypeRaisingRule parentTypeRaising,
        Map<Integer, Object> fixedArguments) {
      this.direction = direction;
      this.type = type;
      this.parentTypeRaising = parentTypeRaising;
      this.fixedArguments = fixedArguments;
    }

    /**
     * Creates an edge that does not contain any fixed arguments.
     * 
     * @param leftArgument
     * @param rightArgument
     * @param direction
     * @return
     */
    public static Edge create(Direction direction, RuleType type, TypeRaisingRule parentTypeRaisingRule) {
      return new Edge(direction, type, parentTypeRaisingRule, null);
    }
    
    public static Edge createTerminal(Map<Integer, Object> fixedArguments) {
      return new Edge(Direction.TERMINAL, RuleType.NULL, null, fixedArguments);
    }

    public Direction getDirection() {
      return direction;
    }

    public RuleType getType() {
      return type;
    }
    
    public Map<Integer, Object> getFixedArguments() {
      return fixedArguments;
    }
    
    public TypeRaisingRule getParentTypeRaising() {
      return parentTypeRaising;
    }

    @Override
    public String toString() {
      return "Edge." + direction + "." + type + (fixedArguments != null ? fixedArguments.toString() : "");
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((direction == null) ? 0 : direction.hashCode());
      result = prime * result + ((fixedArguments == null) ? 0 : fixedArguments.hashCode());
      result = prime * result + ((parentTypeRaising == null) ? 0 : parentTypeRaising.hashCode());
      result = prime * result + ((type == null) ? 0 : type.hashCode());
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj)
        return true;
      if (obj == null)
        return false;
      if (getClass() != obj.getClass())
        return false;
      Edge other = (Edge) obj;
      if (direction != other.direction)
        return false;
      if (fixedArguments == null) {
        if (other.fixedArguments != null)
          return false;
      } else if (!fixedArguments.equals(other.fixedArguments))
        return false;
      if (parentTypeRaising == null) {
        if (other.parentTypeRaising != null)
          return false;
      } else if (!parentTypeRaising.equals(other.parentTypeRaising))
        return false;
      if (type != other.type)
        return false;
      return true;
    }
  }

  public static class Nonterminal implements Serializable{
    private final Type type;
    private final SemanticPredicate semanticType;

    private Nonterminal(Type type, SemanticPredicate semanticType) {
      this.type = type;
      this.semanticType = semanticType;
    }

    public static Nonterminal createFromPredicate(Type type, SemanticPredicate semanticType) {
      return new Nonterminal(Preconditions.checkNotNull(type), Preconditions.checkNotNull(semanticType));
    }

    public Type getType() {
      return type;
    }
    
    public SemanticPredicate getSemanticType() {
      return semanticType;
    }
    
    public boolean isFunctional() {
      return type instanceof DirectedFunctionType;
    }
    
    public Set<Nonterminal> getApplicationResults() {
      DirectedFunctionType typeAsFunction = (DirectedFunctionType) type;
      // Semantic types don't change as we parse.
      return Collections.singleton(
          Nonterminal.createFromPredicate(typeAsFunction.getReturnType(), semanticType)); 
    }

    @Override
    public String toString() {
      return "Nonterminal." + (type == null ? "null" : type.toString()) + "." + semanticType.toShortString(); 
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((semanticType == null) ? 0 : semanticType.hashCode());
      result = prime * result + ((type == null) ? 0 : type.hashCode());
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj)
        return true;
      if (obj == null)
        return false;
      if (getClass() != obj.getClass())
        return false;
      Nonterminal other = (Nonterminal) obj;
      if (semanticType == null) {
        if (other.semanticType != null)
          return false;
      } else if (!semanticType.equals(other.semanticType))
        return false;
      if (type == null) {
        if (other.type != null)
          return false;
      } else if (!type.equals(other.type))
        return false;
      return true;
    }    
  }

  private static class SemanticParsePreprocessor implements Function<Object, List<Object>>, Serializable {
    private final Set<Object> words;

    public SemanticParsePreprocessor(Set<? extends Object> words) {
      this.words = Sets.newHashSet(words);
    }

    @Override
    public List<Object> apply(Object input) {
      List<Object> copy = Lists.newArrayList();
      for (Object inputObject : (List<?>) input) {
	  if (words.contains(inputObject)) {
          copy.add(inputObject);
        } 
      }
      return copy;
    }
  }
}
