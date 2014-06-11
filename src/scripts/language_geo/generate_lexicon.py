#!/usr/bin/python

import sys

filename = sys.argv[1]
generate_category_baseline = (len(sys.argv) >= 3 and sys.argv[2] == 'cat')
lowercase = (len(sys.argv) >= 4 and sys.argv[3] == "lc")

with open(filename) as f:
    for line in f:
        np = line.split(';')[0].strip()
        if lowercase:
            np = np.lower()
        if (len(np) > 0):
            print "%s,N,kb-%s 0 0" % (np, np.replace(' ', '_'))
            print "%s,N/N,kb-%s 0 0" % (np, np.replace(' ', '_'))
            if generate_category_baseline:
                print "%s,N,kb-ignore 0 0" % (np)
                print "%s,N/N,kb-ignore 0 0" % (np)

