#!/bin/bash -e

MODEL_OUTPUT=output/language_cv_goldlex2.ser
MODEL_LOG=output/language_cv_goldlex2_log.txt

./src/scripts/language_geo/train_domain.sh data/cobot/set/language_geography/small_domains/filtered_lexicon.txt $MODEL_OUTPUT --crossValidation --skipUnparseable $@ > $MODEL_LOG