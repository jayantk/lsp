package edu.cmu.ml.rtw.users.jayantk.grounding;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.util.PairCountAccumulator;

import edu.cmu.ml.rtw.users.jayantk.semparse.RelationType;

// This is actually a *bigram* language model.
public class UnigramLanguageModel implements Serializable {
  // Phrases which may map to a category predicate in the semantic parser
  private final List<String> categoryPhrases;

  // Phrases which may map to a relation predicate in the semantic parser
  private final List<String> relationPhrases;

  // Probabilities of generating individual words. A unigram language model.
  private final PairCountAccumulator<String, String> transitionModel;
  private final double smoothing;
  private final double vocabularySize;

  private final Multimap<RelationType, String> adjectiveCategories;
  private final Multimap<RelationType, String> nounCategories;
  private final Multimap<RelationType, String> nounRelationCategories;
  private final Multimap<RelationType, String> prepositionCategories;

  private static final String FIRST_WORD = "**INIT**";
  private static final String LAST_WORD = "**END**";

  public UnigramLanguageModel(Collection<String> categoryPhrases, Collection<String> relationPhrases,
      PairCountAccumulator<String, String> transitionModel,
      double smoothing, Multimap<RelationType, String> adjectiveCategories,
      Multimap<RelationType, String> nounCategories, Multimap<RelationType, String> nounRelationCategories, 
      Multimap<RelationType, String> prepositionCategories) {
    this.categoryPhrases = ImmutableList.copyOf(categoryPhrases);
    this.relationPhrases = ImmutableList.copyOf(relationPhrases);
    this.transitionModel = Preconditions.checkNotNull(transitionModel);

    this.smoothing = smoothing;

    Set<String> firstWords = transitionModel.keySet();
    Set<String> allWords = Sets.newHashSet(firstWords);
    for (String firstWord : firstWords) {
      allWords.addAll(transitionModel.getValues(firstWord));
    }
    this.vocabularySize = allWords.size();
    System.out.println("vocabulary size: " + vocabularySize);

    this.adjectiveCategories = adjectiveCategories;
    this.nounCategories = nounCategories;
    this.nounRelationCategories = nounRelationCategories;
    this.prepositionCategories = prepositionCategories;
  }

  public List<String> getCategoryPhrases() {
    return categoryPhrases;
  }

  public Multimap<RelationType, String> getAdjectiveCategories() {
    return adjectiveCategories;
  }

  public Multimap<RelationType, String> getNounCategories() {
    return nounCategories;
  }
  
  public Multimap<RelationType, String> getNounRelationCategories() {
    return nounRelationCategories;
  }

  public Multimap<RelationType, String> getPrepositionCategories() {
    return prepositionCategories;
  }

  public List<String> getRelationPhrases() {
    return relationPhrases;
  }
  
  public double getTransitionProbability(String firstWord, String secondWord) {
    double count = transitionModel.getCount(firstWord, secondWord);
    double totalCount = transitionModel.getTotalCount(firstWord);
    return (count + smoothing) / (totalCount + (vocabularySize * smoothing));
  }

  public double getProbability(String phrase) {
    String[] words = phrase.split(" ");
    return getProbability(words);
  }

  public double getProbability(String... words) {
    double prob = 1.0;
    String prevWord = FIRST_WORD;
    String[] allWords = Arrays.copyOf(words, words.length + 1);
    allWords[allWords.length - 1] = LAST_WORD;
    for (int i = 0; i < allWords.length; i++) {
      double count = transitionModel.getCount(prevWord, allWords[i]);
      double totalCount = transitionModel.getTotalCount(prevWord);
      double transProb = (count + smoothing) / (totalCount + (vocabularySize * smoothing));
      prob *= transProb;
      // System.out.println(prevWord + " -> " + allWords[i] + " " + count + " " + transProb);
      prevWord = allWords[i];
    }
    return prob;
  }
  
  public double getProbabilityNoStartEnd(String phrase) {
    String[] words = phrase.split(" ");
    return getProbability(words);
  }
  
  public double getProbabilityNoStartEnd(String ... words) {
    double prob = 1.0;
    for (int i = 1; i < words.length; i++) {
      double count = transitionModel.getCount(words[i - 1], words[i]);
      double totalCount = transitionModel.getTotalCount(words[i - 1]);
      double transProb = (count + smoothing) / (totalCount + (vocabularySize * smoothing));
      prob *= transProb;
      // System.out.println(prevWord + " -> " + allWords[i] + " " + count + " " + transProb);
    }
    return prob;
  }


  public static PairCountAccumulator<String, String> estimate(Collection<List<String>> phrases) {
    PairCountAccumulator<String, String> transitions = PairCountAccumulator.create();
    for (List<String> words : phrases) {
      String prevWord = FIRST_WORD;
      for (String word : words) {
        transitions.incrementOutcome(prevWord, word, 1.0);
        prevWord = word;
      }

      transitions.incrementOutcome(prevWord, LAST_WORD, 1.0);
    }
    return transitions;
  }
}