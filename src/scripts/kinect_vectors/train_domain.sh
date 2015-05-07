
./src/scripts/invoke.pl -Xmx2000M  edu.cmu.ml.rtw.vector.VectorModelTrainer --domainDir data/cobot/set/kinect/ --trainingFilename training.annotated.txt.merged -iterations 10 --batchSize 1 --l2Regularization 2 --initialStepSize 0.1 --logInterval 500 --gaussianVariance 0.1 --vectorModelName sequenceRnn --dim 100 --numFoldsToRun 1 --adagrad
