from os import listdir
from os.path import *

import numpy

DEFAULT_PATH="data/cobot/set/kinect/"

DEFAULT_TRAINING_FILENAME="training.annotated.txt.merged"
CATEGORY_FEATURE_FILE = "osm_kb.domain.entities";
RELATION_FEATURE_FILE = "osm_kb.domain.relations";

def read_domains_from_directory(directory, training_filename):
    files = listdir(directory)

    # Read in the features of each domain as a dictionary
    category_feature_list = []
    relation_feature_list = []
    training_data_list = []
    for f in files:
        path = join(directory, f)
        if isdir(path):
            print path

            category_feature_file = join(path, CATEGORY_FEATURE_FILE)
            relation_feature_file = join(path, RELATION_FEATURE_FILE)
            training_data_file = join(path, training_filename)

            (category_features, relation_features, training_data_lines) = read_domain(category_feature_file, relation_feature_file, training_data_file)

            category_feature_list.append(category_features)
            relation_feature_list.append(relation_features)
            training_data_list.append(training_data_lines)

    # Map each feature to a common feature vector index across domains
    category_feature_names = set()
    relation_feature_names = set()
    for i in xrange(len(category_feature_list)):
        category_feature_names.update( (x[1] for x in category_feature_list[i].iterkeys()) )
        relation_feature_names.update( (x[2] for x in relation_feature_list[i].iterkeys()) )

    category_feature_name_list = sorted(category_feature_names)
    relation_feature_name_list = sorted(relation_feature_names)
    category_feature_name_index = dict( [(y, x) for (x, y) in enumerate(category_feature_name_list)] )
    relation_feature_name_index = dict( [(y, x) for (x, y) in enumerate(relation_feature_name_list)] )

    domains = []
    for i in xrange(len(category_feature_list)):
        entity_names = sorted(set( (x[0] for x in category_feature_list[i].iterkeys()) ))
        entity_name_index = dict( [(y, x) for (x, y) in enumerate(entity_names)] )
        
        category_feature_matrix = dict_to_numpy_array(category_feature_list[i], [entity_name_index, category_feature_name_index])
        relation_feature_tensor = dict_to_numpy_array(relation_feature_list[i], [entity_name_index, entity_name_index, relation_feature_name_index])

        training_examples = parse_training_examples(training_data_list[i], entity_name_index)

        domains.append( Domain(entity_names, category_feature_name_list, relation_feature_name_list,
                               category_feature_matrix, relation_feature_tensor, training_examples) )

    return domains

def read_domain(category_feature_file, relation_feature_file, training_data_file):
    print "   ", category_feature_file
    print "   ", relation_feature_file
    print "   ", training_data_file

    category_features = {}
    with open(category_feature_file, 'r') as f:
        for line in f:
            parts = line.split(",")
            entity_name = parts[0]
            feature_name = parts[2]
            value = float(parts[3])

            category_features[(entity_name, feature_name)] = value

    relation_features = {}
    with open(relation_feature_file, 'r') as f:
        for line in f:
            parts = line.split(",")
            entity1_name = parts[0]
            entity2_name = parts[1]
            feature_name = parts[3]
            value = float(parts[4])

            relation_features[(entity1_name, entity2_name, feature_name)] = value

    training_data_lines = []
    with open(training_data_file, 'r') as f:
        for line in f:
            training_data_lines.append(line)

    return (category_features, relation_features, training_data_lines)

def parse_training_examples(training_example_lines, entity_name_index):
    examples = []
    for line in training_example_lines:
        if line.startswith("*"):
            print "WARNING: data contains annotated predicates"
            continue

        parts = line.split(";")

        tokens = parts[0].split(" ")
        entity_answers = parts[1].split(",")
        difficulty = int(parts[2])

        # TODO: parse these with an S-expression parser.
        logical_form_string = parts[3]
        cfg_parse_string = parts[4]
        autogenerated_logical_form_string = parts[5]

        answer_vector = numpy.zeros( len(entity_name_index) )
        for entity in entity_answers:
            answer_vector[ entity_name_index[entity] ]  = 1.0

        examples.append( GroundingExample(tokens, entity_answers, answer_vector, difficulty) )

    return examples

'''
Converts a dictionary with named tuple keys into a tensor
by mapping each named key to a common index. The key -> index mapping
for each dimension is provided by the list of dictionaries index_list.
'''
def dict_to_numpy_array(dictionary, index_list):
    shape = [len(index) for index in index_list]
    tensor = numpy.zeros(shape)

    for k,v in dictionary.iteritems():
        ind = tuple([ index_list[i][k[i]] for i in xrange(len(index_list)) ])

        tensor[ind] = v

    return tensor

class Domain:
    def __init__(self, entity_names, category_feature_names, relation_feature_names,
                 category_feature_matrix, relation_feature_tensor, training_examples):
        self.entity_names = entity_names
        self.entity_name_index = dict([ (entity_names[i], i) for i in xrange(len(entity_names)) ])
        self.category_feature_names = category_feature_names
        self.relation_feature_names = relation_feature_names

        self.category_feature_matrix = category_feature_matrix
        self.relation_feature_tensor = relation_feature_tensor

        self.training_examples = training_examples

    def get_entity_category_feature_dict(self, entity_name):
        ind = self.entity_name_index[entity_name]
        vec = self.category_feature_matrix[ind, :]
        return dict( [(self.category_feature_names[i], vec[i]) for i in xrange(0, len(self.category_feature_names))] )

class GroundingExample:
    def __init__(self, tokens, entity_answers, answer_vector, difficulty):
        self.tokens = tokens
        self.entity_answers = entity_answers
        self.answer_vector = answer_vector
        self.difficulty = difficulty

# Code to read in the training data:
# domains = read_domains_from_directory(DEFAULT_PATH, DEFAULT_TRAINING_FILENAME)


