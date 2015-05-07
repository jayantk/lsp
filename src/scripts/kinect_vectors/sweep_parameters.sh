#!/bin/bash -e

# iterations 10 100
# l2Regularization 0.001 0.01 0.1 1
# initialStepSize 0.1
# gaussianVariance 0.1 0.01
# vectorModelName addition sequenceRnn Birnn logicalFormNn autogeneratedLogicalFormNn
# dimensionality 100 300

# ITERATIONS=(10 100)
# L2_REGULARIZATION=(0.001 0.01 0.1 1)
# INITIAL_STEP_SIZE=(0.1)
# GAUSSIAN_VARIANCE=(0.1 0.01)
# VECTOR_MODEL_NAME=(addition sequenceRnn Birnn logicalFormNn autogeneratedLogicalFormNn)
# DIMENSIONALITY=(100 300)

ITERATIONS=(10)
L2_REGULARIZATION=(0.001)
INITIAL_STEP_SIZE=(0.1)
GAUSSIAN_VARIANCE=(0.01)
VECTOR_MODEL_NAME=(addition sequenceRnn Birnn logicalFormNn autogeneratedLogicalFormNn)
DIMENSIONALITY=(100)

OUT_DIR=/home/jayantk/lsp/kinect_output/

for ITER in ${ITERATIONS[@]}; do
for L2 in ${L2_REGULARIZATION[@]}; do
for STEP in ${INITIAL_STEP_SIZE[@]}; do
for VAR in ${GAUSSIAN_VARIANCE[@]}; do
for NAME in ${VECTOR_MODEL_NAME[@]}; do
for DIM in ${DIMENSIONALITY[@]}; do

    f=$OUT_DIR/$NAME.$ITER.$L2.$STEP.$VAR.$DIM

    CMD="./singlenode.pl ./src/scripts/invoke.pl -Xmx10000M edu.cmu.ml.rtw.vector.VectorModelTrainer --domainDir data/cobot/set/kinect/ --trainingFilename training.annotated.txt.merged -iterations $ITER --batchSize 1 --l2Regularization $L2 --initialStepSize $STEP --logInterval 1000 --gaussianVariance $VAR --vectorModelName $NAME --dim $DIM --adagrad > $f"

    echo $CMD
    $CMD

done
done
done
done
done
done