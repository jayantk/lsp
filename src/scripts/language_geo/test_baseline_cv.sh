#!/bin/bash -e

MODEL=cgg/language_cv_baseline_goldlex3.ser
TEST_LOG=cgg/language_cv_baseline_goldlex3_test2.txt

./src/scripts/invoke.pl -Djava.library.path=/usr/local/ilog/cplex100/bin/x86-64_rhel4.0_3.4/ GroundingModelEvaluator --domainDir data/cobot/set/language_geography/small_domains/ --crossValidation --crossValidationTrainingFile=training_observed_baseline.txt --modelFilename $MODEL --goldKbFile=training.fullylabeled.txt > $TEST_LOG
