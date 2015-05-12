

# is + PP can be analyzed using the same category as the PP
# to the right of / to the left of need to be 1 relation.

#  --numFoldsToRun 1
./src/scripts/invoke.pl -Xmx2000M  edu.cmu.ml.rtw.vector.VectorModelTrainer --domainDir data/cobot/set/kinect/ --trainingFilename training.annotated.txt.merged -iterations 100 --batchSize 1 --l2Regularization 0.1 --initialStepSize 0.1 --logInterval 500 --gaussianVariance 0.01 --vectorModelName addition --adagrad --dim 100 --numFoldsToRun 1 --hingeLoss
