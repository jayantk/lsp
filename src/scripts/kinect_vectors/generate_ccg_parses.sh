#!/bin/bash -e

DIRS="data/cobot/set/language_geography/small_domains/*"

PARSER="/home/jayantk/data/ccg_models/parser.ser"
SUPERTAGGER="/home/jayantk/data/ccg_models/supertagger.ser"
# Logical form templates.
LF_TEMPLATES="data/cobot/set/kinect/logic_templates2.txt"
# Vector space model templates
# LF_TEMPLATES="data/cobot/set/kinect/vsm_templates.txt"

for f in $DIRS
do
    IN="$f/training.txt.parsed"
    OUT="$f/training.txt.pos"
    # This is where the logical forms go.
    CCG_OUT="$f/training.txt.ccg"
    # This file contains direct conversions to vector space models.
    # CCG_OUT="$f/training.annotated.txt.vsm"

    if [ -e $IN ]
    then
	echo "POS $IN -> $OUT" 
	./src/scripts/kinect_vectors/extract_pos_tags.py $IN $OUT

	echo "CCG $OUT -> $CCG_OUT" 
	./src/scripts/invoke.pl com.jayantkrish.jklol.cvsm.ccg.ParseToLogicalForm --parser $PARSER --supertagger $SUPERTAGGER --multitagThreshold 0.01,0.0001 --inputFile $OUT --lfTemplates $LF_TEMPLATES --noPrintOptions > $CCG_OUT --useHackConstraints
    fi
done
