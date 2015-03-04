#!/bin/bash -e

MODEL_OUTPUT=cgg/language_cv_fullsup_goldlex5.ser
MODEL_LOG=cgg/language_cv_fullsup_goldlex5_log.txt

./src/scripts/cobot/language_geography2/train_domain.sh data/cobot/set/language_geography/small_domains/filtered_lexicon.txt $MODEL_OUTPUT --crossValidation --skipUnparseable --trainingFilename=training_observed_baseline.txt --fullSupervision --maxParses 100 $@ > $MODEL_LOG