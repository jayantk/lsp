#!/bin/bash

# Generates a CCG lexicon from an input file containing raw sentences.
# This script wraps the Stanford NLP pipeline to POS-tag and lemmatize 
# the sentences, then runs the result through a heuristic POS-based
# lexicon generation script.

# The file containing raw sentences.
INPUT_FILE=$1
# Where the lexicon is saved.
OUTPUT_FILE=$2
BASELINE_OUTPUT_FILE=$3
USE_NNP=$4
OUT_XML_FILE=$(basename "$INPUT_FILE")".xml"
OUT_POS_FILE=$(basename "$INPUT_FILE")".pos"

java -cp lib/stanford-corenlp-2012-07-09.jar:lib/stanford-corenlp-2012-07-06-models.jar:lib/xom.jar:lib/joda-time.jar -Xmx3g edu.stanford.nlp.pipeline.StanfordCoreNLP -annotators tokenize,ssplit,pos,lemma -file $1 

echo "Cleaning XML and generating lexicon, USE_NNP=$USE_NNP"

./src/python/semparse/clean_stanford_xml.py $OUT_XML_FILE > $OUT_POS_FILE
./src/python/semparse/genlex.py $OUT_POS_FILE rel $USE_NNP > $2
./src/python/semparse/genlex.py $OUT_POS_FILE cat $USE_NNP > $3

#rm $OUT_XML_FILE $OUT_POS_FILE

echo "Done"