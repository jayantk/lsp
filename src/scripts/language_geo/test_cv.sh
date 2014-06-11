#!/bin/bash -e

MODEL=cgg/language_cv_goldlex2.ser
TEST_LOG=cgg/language_cv_goldlex2_test2.txt

./src/scripts/invoke.pl -Djava.library.path=/usr/local/ilog/cplex100/bin/x86-64_rhel4.0_3.4/ GroundingModelEvaluator --domainDir data/cobot/set/language_geography/small_domains/ --crossValidation --crossValidationTrainingFile=training_observed_baseline.txt --modelFilename $MODEL --goldKbFile=training.fullylabeled.txt > $TEST_LOG

# (exists (?a . ?x . ?b) (and ?u . (kb-equal ?x ?y) . ?v))
# -> (exists (?a ?b) (and ?u/?x/?y ?v/?x/?y))

# (and ?u . (exists ?x ?y) . ?v)
# -> (exists ?x (and ?u ?v ?y/fresh))