#!/usr/bin/python

# Script for converting the output of AllPairsGrep into feature tensors
# suitable for use with the grounding model.

import sys
from collections import defaultdict

filename = sys.argv[1]
entity_filename = sys.argv[2]
num_feats = int(sys.argv[3])
min_total_occurrences = int(sys.argv[4])
lowercase = (len(sys.argv) >= 6 and sys.argv[5] == "lc")

def parse_line(line):
    portions = line.split("\t")
    np = portions[0]
    featvec = []
    for i in xrange(1, len(portions)):
        if (len(portions[i].strip()) != 0):
            chunks = portions[i].split(" -#- ")
            if chunks[0].islower():
                featvec.append((int(chunks[1]), chunks[0]))
    featvec.sort(reverse=True)
    return (np, featvec)

def print_feature(np, feature, value):
    print ",".join([np.replace(" || ", ","), "T", feature.replace(',','COMMA'), str(value)])
    # print ",".join([np.replace(" || ", ","), "F", feature.replace(',','COMMA'), str(-1.0 * value)])

# Read in all valid entities
entities = set()
with open(entity_filename) as f:
    for line in f:
        if lowercase:
            entities.add(line.strip().lower())
        else:
            entities.add(line.strip())

with open(filename) as f:
    context_counts = defaultdict(lambda: 0)
    for line in f:
        (np, featvec) = parse_line(line)

        for count,context in featvec:
            context_counts[context] += 1

    contexts = [(y, x) for (x, y) in context_counts.items()]
    contexts.sort(reverse = True)
    # print contexts

rel_entities = set()
with open(filename) as f:
    for line in f:
        (np, featvec) = parse_line(line)
        if lowercase:
            np = np.lower()

        # Make sure any referenced entities are still supposed to have features generated.
        if (' || ' in np):
            parts = np.split(' || ')
            if (parts[0].strip() not in entities or parts[1].strip() not in entities):
                continue
        elif (np not in entities):
            continue

        # print "\t".join(("%s -# %d" % (x[1], x[0]) for x in featvec))
        for i in xrange(min(num_feats, len(featvec))):
            # Print the selected features from the feature dictionary of this instance.
            if (context_counts[featvec[i][1]] >= min_total_occurrences):
                print_feature(np, featvec[i][1], 1)
        if (' || ' not in np):
            # Generate indicator features when np is a category instance.
            print_feature(np, np, 1)
        else:
            rel_entities = entities

# Generate per-entity features for relations.
for entity in rel_entities:
    # Generate a special equality feature for relations when both entities
    # are equal.
    print_feature("%s || %s" % (entity, entity), "entity1=entity2", 1)

if len(rel_entities) == 0:
    for entity in entities:
        # Generate a bias feature for each entity. (categories only)
        print_feature(entity, "bias", 1)
