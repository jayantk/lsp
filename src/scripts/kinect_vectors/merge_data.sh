#!/bin/bash -e

# DIRS="data/cobot/set/kinect/0*"
DIRS="data/cobot/set/language_geography/small_domains/*"

for f in $DIRS
do
    IN="$f/training.txt"
    PARSED="$f/training.txt.parsed"
    CCG="$f/training.txt.ccg"
    MERGED="$f/training.txt.merged"

    TEMP="$f/training.temp.txt"
    TEMP2="$f/training.temp2.txt"

    if [ -e $IN ]
    then
	echo $IN

	grep -v '^\*' $IN | grep -v '^$' | sed 's/ +$//' | sed 's/;$//' | sed 's/\([^)]\)$/\1;/g' > $TEMP
	cut -f2 $CCG > $TEMP2

	paste -d ';' $TEMP $PARSED $TEMP2 > $MERGED

	rm $TEMP
	rm $TEMP2
    fi
done
