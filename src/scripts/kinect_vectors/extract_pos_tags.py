#!/usr/bin/python

import sys
import re

input_file = sys.argv[1]
output_file = sys.argv[2]

with open(input_file, 'r') as f:
    with open(output_file, 'w') as f2:
        for line in f:
            if line.startswith("*"):
                continue
            else:
                matches = re.findall('\([^\(\)]*\)', line.strip())
                out_tags = []
                for match in matches:
                    parts = match.strip('()').lstrip('()').split(" ")
                    out_tags.append( (parts[1], parts[0]) )
                
                print >> f2, " ".join(["%s/%s" % tag for tag in out_tags])
