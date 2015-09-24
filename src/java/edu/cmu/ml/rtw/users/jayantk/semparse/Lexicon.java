package edu.cmu.ml.rtw.users.jayantk.semparse;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import com.google.common.collect.SortedSetMultimap;
import com.google.common.collect.TreeMultimap;

/**
 * Represents the lexicon of a semantic parser. The lexicon maps sequences of
 * words (trigger sequences) to semantic categories. A trigger sequence is a
 * contiguous sequence of words. A semantic category is a {@link RelationType},
 * representing an intensionally-defined set of tuples.
 * 
 * @author jayantk
 */
public class Lexicon<T> {

  private SortedSetMultimap<List<T>, LexicalCategory> lexicon;
  private Set<TypeRaisingRule> typeRaisingRules;

  /**
   * Creates a new, empty lexicon.
   */
  public Lexicon() {
    @SuppressWarnings("unchecked")
    Ordering<T> keyComparator = (Ordering<T>) Ordering.usingToString();
    this.lexicon = TreeMultimap.create(keyComparator.lexicographical(),
        Ordering.usingToString());
    this.typeRaisingRules = Sets.newHashSet();
  }
  
  /**
   * Reads in a CCG lexicon from a data file.
   *
   * @param filename
   * @return
   */
  public static Lexicon<String> fromFile(List<String> lines) {
    Lexicon<String> lexicon = new Lexicon<String>();
    Map<String, AtomicType> atomicTypeMap = Maps.newHashMap();
    Map<String, RelationType> predicateMap = Maps.newHashMap();
        
    AtomicType everything = new AtomicType("everything", Collections.<AtomicType>emptyList());
    
    for (String line : lines) {
      if (line.trim().length() == 0) {
        // Skip blank lines.
        continue;
      }

      String[] parts = line.split(",");
      Preconditions.checkArgument(parts.length >= 3);
      
      // First part of lexicon is space-separated list of words,
      // representing the phrase which maps to the specified syntactic and semantic type.
      List<String> words = Arrays.asList(parts[0].split(" "));
      
      // Parse syntactic type
      String syntaxString = parts[1];
      String[] syntaxParts = syntaxString.split("[/\\\\]");
      String[] slashes = syntaxString.split("[^/\\\\]+");

      // System.out.println(Arrays.toString(syntaxParts));
      // System.out.println(Arrays.toString(slashes));
            
      Type syntaxType = null;
      for (int i = 0; i < syntaxParts.length; i++) {
        if (!atomicTypeMap.containsKey(syntaxParts[i])) {
          atomicTypeMap.put(syntaxParts[i], new AtomicType(syntaxParts[i], Collections.<AtomicType>emptyList()));
        }
        
        if (i == 0) {
          syntaxType = atomicTypeMap.get(syntaxParts[i]);
        } else {
          AtomicType direction = slashes[i].equals("/") ? DirectedFunctionType.RIGHT : DirectedFunctionType.LEFT;   
          syntaxType = new DirectedFunctionType(atomicTypeMap.get(syntaxParts[i]), syntaxType, true, 
              direction, Collections.<String>emptyList());
        }
      }
      
      // Parse the semantic type.
      String[] semanticParts = parts[2].split(" "); 
      String predicate = semanticParts[0];
      int outputArgument = Integer.parseInt(semanticParts[1]);
      List<Integer> argumentOrder = Lists.newArrayList();
      for (int i = 2; i < semanticParts.length; i++) {
        argumentOrder.add(Integer.parseInt(semanticParts[i]));
      }

      int numArgs = Collections.max(argumentOrder) + 1;
      List<AtomicType> argumentTypes = Lists.newArrayList();
      for (int i = 0; i < numArgs; i++) {
        argumentTypes.add(everything);
      }
      
      if (!predicateMap.containsKey(predicate)) {
        predicateMap.put(predicate, RelationType.createWithNewAtomicType(predicate, argumentTypes));
        // System.out.println("Predicate: " + predicate + " : " + argumentTypes);
      }
      
      SemanticPredicate semanticPredicate = new SemanticPredicate(predicateMap.get(predicate), argumentOrder,
          outputArgument, Collections.<Integer, Object>emptyMap());

      lexicon.addLexicalEntry(words, LexicalCategory.create(syntaxType, semanticPredicate));
    }
    
    return lexicon;
  }

  public void addLexicalEntry(List<T> triggerSequence, LexicalCategory category) {
    lexicon.put(triggerSequence, category);
  }

  public void addTypeRaisingRule(TypeRaisingRule rule) {
    typeRaisingRules.add(rule);
  }

  public Set<TypeRaisingRule> getTypeRaisingRules() {
    return typeRaisingRules;
  }

  /**
   * Adds the lexical entries in {@code other} to {@code this}.
   * 
   * @param other
   */
  public void addAll(Lexicon<T> other) {
    for (List<T> key : other.lexicon.keySet()) {
      lexicon.putAll(key, other.lexicon.get(key));
    }

    typeRaisingRules.addAll(other.typeRaisingRules);
  }

  /**
   * Gets all sequences of words which trigger predicates.
   * 
   * @return
   */
  public Set<List<T>> getTriggerSequences() {
    return lexicon.keySet();
  }

  @SuppressWarnings("unchecked")
  public Set<List<T>> getAllRecognizedWordSequences() {
    Set<List<T>> words = Sets.newHashSet();
    words.addAll(lexicon.keySet());
    for (List<T> triggerSequence : getTriggerSequences()) {
      for (T word : triggerSequence) {
        words.add(Arrays.asList(word));
      }
    }
    return words;
  }

  public Set<T> getWords() {
    Set<T> words = Sets.newHashSet();
    for (List<T> triggerSequence : getTriggerSequences()) {
      for (T word : triggerSequence) {
        words.add(word);
      }
    }
    return words;
  }

  /**
   * Gets all predicates (n-ary relation types) used in the CCG lexicon.
   * 
   * @return
   */
  public Set<RelationType> getPredicatesInGrammar() {
    Set<RelationType> relationSet = Sets.newHashSet();
    for (SemanticPredicate predicate : getSemanticPredicates()) {
      relationSet.add(predicate.getRelation());
    }
    return relationSet;
  }

  public Set<SemanticPredicate> getSemanticPredicates() {
    Set<SemanticPredicate> predicates = Sets.newHashSet();
    for (LexicalCategory category : getLexicalCategories()) {
      predicates.add(category.getSemanticType());
    }

    for (TypeRaisingRule typeRaisingRule : typeRaisingRules) {
      predicates.add(typeRaisingRule.getSemanticType());
    }

    return predicates;
  }

  public Collection<LexicalCategory> getLexicalCategories() {
    return lexicon.values();
  }

  public SortedSet<LexicalCategory> getCategories(List<T> words) {
    return lexicon.get(words);
  }

  @Override
  public String toString() {
    return lexicon.toString();
  }

  public static class LexicalCategory {
    private static Map<LexicalCategory, LexicalCategory> categoryCache =
        Collections.synchronizedMap(Maps.<LexicalCategory, LexicalCategory> newHashMap());

    // The type of the lexical category which becomes a nonterminal in the
    // grammar.
    private final Type baseType;
    private final SemanticPredicate semanticType;

    private final Map<Integer, Object> fixedValues;

    /**
     * Use {@link #create}.
     * 
     * @param baseType
     */
    private LexicalCategory(Type baseType, SemanticPredicate semanticType,
        Map<Integer, Object> fixedValues) {
      this.baseType = baseType;
      this.semanticType = semanticType;
      // this.argumentDirections = argumentDirections;
      this.fixedValues = fixedValues;
    }

    public static final LexicalCategory create(Type baseType, SemanticPredicate semanticType) {
      return createWithHiddenArguments(baseType, semanticType, Collections.<Integer, Object> emptyMap());
    }

    public static final LexicalCategory createWithHiddenArguments(Type baseType, SemanticPredicate semanticType,
        Map<Integer, Object> arguments) {
      // Note that this isn't totally concurrency safe, as we might create
      // duplicate
      // lexical categories representing the same data type. However, such
      // duplication isn't a huge problem.
      LexicalCategory newCategory = new LexicalCategory(baseType, semanticType,
          arguments);

      if (categoryCache.containsKey(newCategory)) {
        return categoryCache.get(newCategory);
      } else {
        categoryCache.put(newCategory, newCategory);
        return newCategory;
      }
    }

    public Type getType() {
      return baseType;
    }

    public SemanticPredicate getSemanticType() {
      return semanticType;
    }

    public Map<Integer, Object> getFixedValues() {
      return fixedValues;
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("LexicalCategory.");
      if (baseType != null) {
        sb.append(baseType.toString());
      } else {
        sb.append("null");
      }
      sb.append(".");
      if (semanticType != null) {
        sb.append(semanticType.toString());
      } else {
        sb.append("null");
      }
      sb.append(".");
      if (fixedValues != null) {
        sb.append(fixedValues.toString());
      }
      return sb.toString();
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((baseType == null) ? 0 : baseType.hashCode());
      result = prime * result + ((fixedValues == null) ? 0 : fixedValues.hashCode());
      result = prime * result + ((semanticType == null) ? 0 : semanticType.hashCode());
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
      LexicalCategory other = (LexicalCategory) obj;
      if (baseType == null) {
        if (other.baseType != null)
          return false;
      } else if (!baseType.equals(other.baseType))
        return false;
      if (fixedValues == null) {
        if (other.fixedValues != null)
          return false;
      } else if (!fixedValues.equals(other.fixedValues))
        return false;
      if (semanticType == null) {
        if (other.semanticType != null)
          return false;
      } else if (!semanticType.equals(other.semanticType))
        return false;
      return true;
    }
  }

  public static class TypeRaisingRule {
    private final Type inputType;
    private final Type outputType;

    // Output semantic type is equal to semanticType applied to
    // the input semantic type.
    private final SemanticPredicate semanticType;

    public TypeRaisingRule(Type inputType, Type outputType, SemanticPredicate semanticType) {
      this.inputType = inputType;
      this.outputType = outputType;
      this.semanticType = semanticType;
    }

    public Type getInputType() {
      return inputType;
    }

    public Type getOutputType() {
      return outputType;
    }

    public SemanticPredicate getSemanticType() {
      return semanticType;
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((inputType == null) ? 0 : inputType.hashCode());
      result = prime * result + ((outputType == null) ? 0 : outputType.hashCode());
      result = prime * result + ((semanticType == null) ? 0 : semanticType.hashCode());
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
      TypeRaisingRule other = (TypeRaisingRule) obj;
      if (inputType == null) {
        if (other.inputType != null)
          return false;
      } else if (!inputType.equals(other.inputType))
        return false;
      if (outputType == null) {
        if (other.outputType != null)
          return false;
      } else if (!outputType.equals(other.outputType))
        return false;
      if (semanticType == null) {
        if (other.semanticType != null)
          return false;
      } else if (!semanticType.equals(other.semanticType))
        return false;
      return true;
    }

    @Override
    public String toString() {
      return inputType + "-raise->" + outputType + "(" + semanticType + ")";
    }
  }
}
