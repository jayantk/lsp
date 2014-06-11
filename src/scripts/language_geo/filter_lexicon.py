#!/usr/bin/python

import sys

lex_filename = sys.argv[1]
predicate_filename = sys.argv[2]

predicates = set()
with open(predicate_filename) as f:
    for line in f:
        if len(line.strip()) > 0:
            predicates.add(line.strip())

with open(lex_filename) as f:
    for line in f:
        parts = line.strip().split(",")
        predparts = parts[2].split(" ")
        predname = predparts[0]

        if predname not in predicates and not predname.startswith("kb-"):
            if "1" in predparts:
                predname = 'kb-ignore-equal'
            else:
                predname = 'kb-ignore'

        predparts[0] = predname
        predstr = " ".join(predparts)
        print ",".join([parts[0], parts[1], predstr])


