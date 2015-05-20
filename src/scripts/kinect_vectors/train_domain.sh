
#  --numFoldsToRun 1
# sequenceRnn.100.0.01.0.01.0.01.100
./src/scripts/invoke.pl -Xmx2000M  edu.cmu.ml.rtw.vector.VectorModelTrainer --domainDir data/cobot/set/kinect/ --trainingFilename training.annotated.txt.merged -iterations 100 --batchSize 4 --l2Regularization 0.01 --initialStepSize 0.01 --logInterval 500 --gaussianVariance 0.01 --vectorModelName sequenceRnn --adagrad --dim 100

# addition.100.0.1.0.1.0.01.100
#./src/scripts/invoke.pl -Xmx2000M  edu.cmu.ml.rtw.vector.VectorModelTrainer --domainDir data/cobot/set/kinect/ --trainingFilename training.annotated.txt.merged -iterations 10 --batchSize 4 --l2Regularization 0.1 --initialStepSize 0.1 --logInterval 500 --gaussianVariance 0.01 --vectorModelName addition --adagrad --dim 100
