
# -modelFilename $2

./src/scripts/invoke.pl -Xmx2000M  edu.cmu.ml.rtw.vector.VectorModelTrainer --domainDir data/cobot/set/language_geography/small_domains/ -iterations 100 --l2Regularization 0.02 --initialStepSize 1.0 --logInterval 100 --gaussianVariance 1