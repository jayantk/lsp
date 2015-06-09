
import numpy as np
import theano
from theano import tensor as T


def standard_initialization(num_rows, num_cols=0):
	if num_cols > 0:
		rootn = np.sqrt(num_cols)
		return np.random.uniform(low=-rootn, high=rootn, size=(num_rows, num_cols))
	else:
		return np.random.uniform(low=-1., high=1., size=(num_rows))

class SeqRnn:
	def __init__(self, wordvec_dim, sent_dim, num_words, max_words, num_entities, learning_rate):

		# word vec dimension
		self.wordvec_dim = wordvec_dim

		# num words total in dataset
		self.num_words = num_words

		# sentence vec dimension
		self.sent_dim = sent_dim

		# max words in a sentence
		self.max_words = max_words

		# number of entities total in the dataset
		self.num_entities = num_entities
		self.learning_rate = learning_rate


		# matrix to convert dense one hot encoding to word embedding space
		self.Wemb = theano.shared(name='Wemb',
				value=standard_initialization(wordvec_dim, num_words))

		# matrix to convert word embedding to sentence embedding
		self.M_word_to_sent = theano.shared(name='M_word_to_sent',
				value=standard_initialization(wordvec_dim, sent_dim))

		# matrix to compute softmax on the final sentence embedding to get the classification
		self.M_softmax = theano.shared(name='M_softmax',
				value=standard_initialization(sent_dim, num_entities))

		self.params = [self.Wemb, self.M_word_to_sent]

		# rows are different sentences with each row being the one hot encoding of that sentence
		self.one_hot_sent_matrix = T.imatrix('one_hot_sent_matrix') 

		# rows are the different sentences with each row being the answer vector for that sentence
		self.answer_matrix = T.dmatrix('answer_matrix') 

	# compute log likelihood of a minibatch
	def log_likelihood(self):

		# sequence step for each sentence is word_vec*M + rest where M moves the word vector to sentence embedding space
		def seq_step(
				words_vectors,
				seq_sum_vectors):
			return T.dot(words_vectors, self.M_word_to_sent)+seq_sum_vectors

		# 3-Tensor for word embeddings for a minibatch. After dimshuffle, the first dimension is that of the word sequence for sentences. So we want to iterate over each of the sentences in the minibatch simultaneously
		word_emb_tensor = T.concatenate([T.dot(T.eye(self.max_words, self.num_words)[self.one_hot_sent_matrix[i],:], self.Wemb) for i in xrange(self.one_hot_sent_matrix.get_value(borrow=True, return_internal_type=True).shape[0])]).dimshuffle(2, 1, 0)

		# use scan to generate the sequence rnn
		sent_emb, _ = theano.scan(
				fn=seq_step,
				sequences=[word_emb_tensor],
				outputs_info=[np.zeros(self.sent_dim)])

		return T.mean(T.sum(T.log(np.ones(self.num_entities)-self.answer_matrix-T.nnet.softmax(T.dot(sent_emb[-1], self.M_softmax))), axis=1))

	# get objective function in cost and new parameters in updates
	def get_cost_updates(self):
		cost = self.doc_log_likelihood()

		gparams = T.grad(cost, self.params)
		updates = [(param, param-self.learning_rate*gparam)
				for param, gparam in zip(self.params, gparams)]

		return cost, updates

def train(sentence_list, word_to_index, answer_matrix, wordvec_dim, sent_dim, num_entities, minibatch_size, num_iter, learning_rate):

	# length of largest possible sentence
	max_words = max((len(sent) for sent in sentence_list))

	# compute sparse one hot encoding of sentences -- each sentence has one row.
	one_hot_sentences = [[word_to_index[word] for word in sentence] for sentence in sentence_list]
	
	# Create the model
	seqrnn = SeqRnn(wordvec_dim, sent_dim, len(word_to_index), max_words, num_entities, learning_rate)

	cost, updates = seqrnn.get_cost_updates()

	# theano variables to keep track of minibatches
	start_var = T.lscalar()
	end_var = T.lscalar()

	# training function
	train_model = theano.function(
		inputs=[start_var, end_var],
		outputs=cost,
		updates=updates,
		givens={
			seqrnn.one_hot_sent_matrix : one_hot_sentences[start_var:end_var],
			seqrnn.answer_matrix : answer_matrix[start_var:end_var]
		})

	# training
	num_examples = len(sentence_list)
	for j in xrange(num_iter):
		for i in xrange(num_examples/minibatch_size):
			# compute minibatch examples
			start = i*minibatch_size
			end = min((i+1)*minibatch_size, num_examples)

			# one step of gradient descent on those examples
			minibatch_avg_cost = train_model(start, end)
