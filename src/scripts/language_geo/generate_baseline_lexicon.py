#!/usr/bin/python

import sys

lex_filename = sys.argv[1]

with open(lex_filename) as f:
    for line in f:
        parts = line.strip().split(",")
        predparts = parts[2].split(" ")
        predname = predparts[0]

        if not predname.startswith("kb-") and "1" in predparts:
            predname = 'kb-ignore-all'

        predparts[0] = predname
        predstr = " ".join(predparts)
        print ",".join([parts[0], parts[1], predstr])
