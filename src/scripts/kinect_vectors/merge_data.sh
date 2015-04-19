#!/bin/bash -e

DIRS="data/cobot/set/kinect/0*"

for f in $DIRS
do
    IN="$f/training.annotated.txt"
    PARSED="$f/training.annotated.txt.parsed"
    CCG="$f/training.annotated.txt.ccg"
    MERGED="$f/training.annotated.txt.merged"

    TEMP="$f/training.temp.txt"
    TEMP2="$f/training.temp2.txt"

    grep -v '^\*' $IN > $TEMP
    cut -f2 $CCG > $TEMP2

    paste -d ';' $TEMP $PARSED $TEMP2 > $MERGED

    rm $TEMP
    rm $TEMP2

done