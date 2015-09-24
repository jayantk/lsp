
# -modelFilename $2

# 
./src/scripts/invoke.pl -Xmx2000M  edu.cmu.ml.rtw.vector.VectorModelTrainer --domainDir data/cobot/set/language_geography/small_domains/ --trainingFilename training.txt.merged --lexiconExamples data/cobot/set/language_geography/small_domains/filtered_lexicon.txt -iterations 100 --batchSize 1 --l2Regularization 0.01 --initialStepSize 1.0 --logInterval 1000 --gaussianVariance 0.01 --vectorModelName logicalFormNn --dim 100 --adagrad --numFoldsToRun 1 --hingeLoss
