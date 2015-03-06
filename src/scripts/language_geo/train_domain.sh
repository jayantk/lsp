# This trains the model using ILP inference
./src/scripts/invoke.pl -Xmx30000M -Djava.library.path=/usr/local/ilog/cplex100/bin/x86-64_rhel4.0_3.4/ edu.cmu.ml.rtw.users.jayantk.grounding.GroundingModelTrainer --domainDir data/cobot/set/language_geography/small_domains/ --lexicon $1 -iterations 10 -modelFilename $2 -batchSize 8 -useIlp --l2regularization 0.02 --initialStepSize 1.0 --noCurriculum $@

# This trains the model using dual decomposition inference.
# It's very slow, but doesn't require CPLEX.
# ./src/scripts/invoke.pl -Xmx30000M -Djava.library.path=/usr/local/ilog/cplex100/bin/x86-64_rhel4.0_3.4/ edu.cmu.ml.rtw.users.jayantk.grounding.GroundingModelTrainer --domainDir data/cobot/set/language_geography/small_domains/ --lexicon $1 -iterations 10 -modelFilename $2 -batchSize 8 --l2regularization 0.02 --initialStepSize 1.0 --noCurriculum $@
