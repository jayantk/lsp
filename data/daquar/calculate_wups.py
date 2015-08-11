#!/usr/bin/env python

"""
Author: Mateusz Malinowski
Email: mmalinow@mpi-inf.mpg.de

it assumes there are two files
- first file with ground truth answers
- second file with predicted answers
both answers are line-aligned and at the same order 
(sorted with the same method).

Script assumes that answer items are comma separated.
For instance: chair,table,window.

WUPS is a set measure, so not exactly the same as accuracy 
even if dirac measure is used since {book,book}=={book}.
However, both ACC and WUPS with DIRAC should be 
very close to each other on DAQUAR.
"""

import sys

from numpy import prod
from nltk.corpus import wordnet as wn


def file2list(filepath):
    with open(filepath,'r') as f:
        lines =[k for k in 
            [k.strip() for k in f.readlines()] 
        if len(k) > 0]

    return lines


def list2file(filepath,mylist):
    mylist='\n'.join(mylist)
    with open(filepath,'w') as f:
        f.writelines(mylist)


def items2list(x):
    """
    x - string of comma-separated answer items
    """
    return x.split(',')


def fuzzy_set_membership_measure(x,A,m):
    """
    Set membership measure.
    x: element
    A: set of elements
    m: point-wise element-to-element measure m(a,b) ~ similarity(a,b)

    This function implments a fuzzy set membership measure:
        m(x \in A) = max_{a \in A} m(x,a)}
    """
    return 0 if A==[] else max(map(lambda a: m(x,a), A))


def score_it(A,T,m):
    """
    A: list of A items 
    T: list of T items
    m: set membership measure
        m(a \in A) gives a membership quality of a into A 

    This function implements a fuzzy accuracy score:
        score(A,T) = min{prod_{a \in A} m(a \in T), prod_{t \in T} m(a \in A)}
        where A and T are set representations of the answers
        and m is a measure
    """
    if A==[] and T==[]:
        return 1

    # print A,T

    score_left=0 if A==[] else prod(map(lambda a: m(a,T), A))
    score_right=0 if T==[] else prod(map(lambda t: m(t,A),T))
    return min(score_left,score_right) 


# implementations of different measure functions
def dirac_measure(a,b):
    """
    Returns 1 iff a=b and 0 otherwise.
    """
    if a==[] or b==[]:
        return 0.0
    return float(a==b)


def wup_measure(a,b,similarity_threshold=0.925):
    """
    Returns Wu-Palmer similarity score.
    More specifically, it computes:
        max_{x \in interp(a)} max_{y \in interp(b)} wup(x,y)
        where interp is a 'interpretation field'
    """
    def get_semantic_field(a):
        weight = 1.0
        semantic_field = wn.synsets(a,pos=wn.NOUN)
        return (semantic_field,weight)


    def get_stem_word(a):
        """
        Not used yet.
        """
        weight = 1.0
        return (a,weight)


    global_weight=1.0

    (a,global_weight_a)=get_stem_word(a)
    (b,global_weight_b)=get_stem_word(b)
    global_weight = min(global_weight_a,global_weight_b)

    if a==b:
        # they are the same
        return 1.0*global_weight

    if a==[] or b==[]:
        return 0


    interp_a,weight_a = get_semantic_field(a) 
    interp_b,weight_b = get_semantic_field(b)

    if interp_a == [] or interp_b == []:
        return 0

    # we take the most optimistic interpretation
    global_max=0.0
    for x in interp_a:
        for y in interp_b:
            local_score=x.wup_similarity(y)
            if local_score > global_max:
                global_max=local_score

    # we need to use the semantic fields and therefore we downweight
    # unless the score is high which indicates both are synonyms
    if global_max < similarity_threshold:
        interp_weight = 0.1
    else:
        interp_weight = 1.0

    final_score=global_max*weight_a*weight_b*interp_weight*global_weight
    return final_score 
###


if __name__ == '__main__':

    if len(sys.argv) < 4:
        print 'Usage: path to true answers, path to predicted answers, threshold'
        print 'If threshold is -1, then the standard Accuracy is used'
        sys.exit("3 arguments must be given")

    # folders
    gt_filepath=sys.argv[1]
    pred_filepath=sys.argv[2]

    input_gt=file2list(gt_filepath)
    input_pred=file2list(pred_filepath)

    thresh=float(sys.argv[3])
    if thresh == -1:
        our_element_membership=dirac_measure
    else:
        our_element_membership=lambda x,y: wup_measure(x,y,thresh)

    our_set_membership=\
            lambda x,A: fuzzy_set_membership_measure(x,A,our_element_membership)

    if thresh == -1:
        print 'standard Accuracy is used'
    else:
        print 'soft WUPS at %1.2f is used' % thresh
    score_list=[score_it(items2list(ta),items2list(pa),our_set_membership) 
            for (ta,pa) in zip(input_gt,input_pred)]
    print 'computing the final score'
    final_score=float(sum(score_list))/float(len(score_list))

    #print 'full score:', score_list
    print 'exact final score:', final_score
    print 'final score is %2.2f%%' % (final_score * 100.0)

