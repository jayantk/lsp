./src/scripts/invoke.pl -Xmx30000M -Djava.library.path=/usr/local/ilog/cplex100/bin/x86-64_rhel4.0_3.4/ GroundingModelEmTrainer --domainDir data/cobot/set/language_geography/small_domains/ --lexicon $1 -iterations 10 -modelFilename $2 --noCurriculum $@
