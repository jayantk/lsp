#!/bin/bash -e

MODEL_OUTPUT=cgg/language_cv_goldkb1.ser
MODEL_LOG=cgg/language_cv_goldkb1_log.txt

./src/scripts/cobot/language_geography2/train_domain.sh data/cobot/set/language_geography/small_domains/lexicon.txt $MODEL_OUTPUT --crossValidation --skipUnparseable --goldKbFile training.fullylabeled.txt $@ > $MODEL_LOG