#!/bin/bash -e

MODEL_OUTPUT=cgg/language_cv_baseline_goldlex3.ser
MODEL_LOG=cgg/language_cv_baseline_goldlex3_log.txt

./src/scripts/cobot/language_geography2/train_domain.sh data/cobot/set/language_geography/small_domains/baseline_filtered_lexicon2.txt $MODEL_OUTPUT --crossValidation --skipUnparseable $@ > $MODEL_LOG