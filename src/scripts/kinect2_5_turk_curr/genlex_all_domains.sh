#!/bin/bash

PATH_TO_DOMAIN_DIRS=data/cobot/set/kinect2_5_turk_curr/training/**/training.txt
LEXICON_OUTPUT=lexicon.txt
CAT_LEXICON_OUTPUT=lexicon.categories.txt

TEMP_FILE=`tempfile`
cat $PATH_TO_DOMAIN_DIRS | sed 's/;.*/ ./g' > $TEMP_FILE

./src/scripts/cobot/genlex.sh $TEMP_FILE $LEXICON_OUTPUT $CAT_LEXICON_OUTPUT nnp

rm $TEMP_FILE
