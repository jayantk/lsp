#!/bin/bash

./src/scripts/cobot/language_geography/train_observed_cv.sh --skipUnparseable > observed-out6.txt &
./src/scripts/cobot/language_geography/train_cv.sh --skipUnparseable > small-out62.txt &
./src/scripts/cobot/language_geography/train_baseline_cv.sh --skipUnparseable > baseline-out8.txt &
