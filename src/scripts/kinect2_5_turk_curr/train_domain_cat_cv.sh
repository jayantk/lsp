./src/scripts/invoke.pl -Xmx10000M -Djava.library.path=/usr/local/ilog/cplex100/bin/x86-64_rhel4.0_3.4/ GroundingModelTrainer --domainDir data/cobot/set/kinect2_5_turk_curr/training/ --trainingFilename training.annotated.txt -lexicon data/cobot/set/kinect2_5_turk_curr/training/lexicon.category.filtered.txt -iterations 100 -modelFilename image_grounding_model_cat.ser -batchSize 5 -useIlp -maxTrainingExamples 200000 -l2regularization 0.03 -skipUnparseable --crossValidation