#!/usr/bin/python

import sys

filename = sys.argv[1]

with open(filename) as f:
    for line in f:
        np = line.split(',')[0].strip()
        print 'kb-true,%s,T,1' % np
        print 'kb-%s,%s,T,1' % (np.replace(' ', '_'), np)

