#!/bin/bash -e

DIRS="data/cobot/set/kinect/0*"

PARSER="/home/jayantk/data/ccg_models/parser.ser"
SUPERTAGGER="/home/jayantk/data/ccg_models/supertagger.ser"
LF_TEMPLATES="data/cobot/set/kinect/logic_templates2.txt"

for f in $DIRS
do
    IN="$f/training.annotated.txt.parsed"
    OUT="$f/training.annotated.txt.pos"
    CCG_OUT="$f/training.annotated.txt.ccg"

    echo "POS $IN -> $OUT" 

    ./src/scripts/kinect_vectors/extract_pos_tags.py $IN $OUT

    echo "CCG $OUT -> $CCG_OUT" 
    ./src/scripts/invoke.pl com.jayantkrish.jklol.cvsm.ccg.ParseToLogicalForm --parser $PARSER --supertagger $SUPERTAGGER --multitagThreshold 0.01,0.001 --inputFile $OUT --lfTemplates $LF_TEMPLATES --noPrintOptions > $CCG_OUT
done
