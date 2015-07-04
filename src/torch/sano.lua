-- forward declarations
local archive -- table to store texts of all files in the archive
local onLoad  -- optionally defineable function for
local autoloader = {}
-- main code for this package

--[[
	Sano Library, 1/14/2006 Release

	Copyright 2005-2006 Paul Chiusano
	Copyright 1994-2005 Lua.org, PUC-Rio.

	Permission is hereby granted, free of charge, to any person obtaining a
	copy of this software and associated documentation files (the "Software"),
	to deal in the Software without restriction, including without limitation
	the rights to use, copy, modify, merge, publish, distribute, sublicense,
	and/or sell copies of the Software, and to permit persons to whom the
	Software is furnished to do so, subject to the following conditions:

	The above copyright notice and this permission notice shall be included in
	all copies or substantial portions of the Software.

	THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
	IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
	FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
	AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
	LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
	FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
	DEALINGS IN THE SOFTWARE.

]]

local documentation = {}
local autoloader = {}

local makersMap = {
	heap = "PairingHeap",
	queue = "QueueVector",
	list = "SkipVector",
	skipvector = "SkipVector",
	llist = "LinkedList",
	map = "HashMap",
	hmap= "HashMap",
	hashmap = "HashMap",
	omap = "SkipMap",
	orderedmap = "SkipMap",
	lmap = "LinkedMap",
	set = "HashSet",
	oset = "SkipSet",
	orderedset = "SkipSet",
	mset = "Multiset",
	mmap = "Multimap",
	lset = "LinkedSet",
	linkedlist = "LinkedList",
	linkedmap = "LinkedMap",
	linkedset = "LinkedSet",
	__index = function(t,k) return string.upper(string.sub(k,1,1))..string.lower(string.sub(k,2,-1)) end
}
setmetatable(makersMap, makersMap)

local makers = {
	__index = function(t,k)
		--print(makersMap[k])
		local class = autoloader[makersMap[k]]
		assert(class, "No class by the name "..k)
		t[k] = function(...) return class:make(unpack(arg)) end
		local name = makersMap[k]
		documentation[t[k] ] = "\tmakers."..k.."(...) - Returns "..name..":make(...).\n"
		return t[k]
	end
}
setmetatable(makers, makers)

onLoad = function(name, module)
	documentation[module] = module.documentation and module.documentation.general
	if module.documentation then
		for k,v in pairs(module.documentation) do
			if k=="make" then 
				makers[k] = function(...) return module:make(unpack(arg)) end
			end
			if not documentation[k] then 
				documentation[k] = v
			end
		end
	end
end

autoloader.makers = makers

autoloader.documentation = {}
local apidoc = autoloader.documentation

documentation[autoloader] = [[
	sano - Sano is a library of Lua data structures and algorithms. 
	
	Among the highlights are: Several sequence types, including the humble
	Tuple (a lightweight, hashable table wrapper), Vector (slightly more 
	industrial strength table wrapper, including negative indexing),
	QueueVector (random access + efficient inserts at head OR tail), 
	a basic LinkedList, and SkipVector (a skip-list based random access 
	sequence with efficient inserts or removals at ANY position).
	
	The library also includes several map implementations: among them the 
	basic HashMap (a table-based map with plenty of convenience methods 
	and support for user-defined hashing functions), an ordered, 
	mergeable, spliceable SkipMap, and a LinkedMap (iteration order same as 
	insertion order). For each of these map types, Sano also has
	the parallel set type - HashSet, SkipSet, LinkedSet.
	
	In addition, the library contains a PairingHeap (constant-time 
	mergeable heap), Multimap (implements one to many mappings), Multiset 
	(ordered or unordered), and numerous algorithms and utility functions 
	for working with all the data structures in the library.
	
	To get help on any object, function, or module, 
	<b><code>sano.summary(obj)</code></b> prints a brief one sentance 
	description of obj, and, if obj is an object or module, a brief one 
	sentance description of each of the methods of the object or functions
	of the module. <b><code>sano.doc(obj)</code></b> prints full 
	documentation for obj.

]]
apidoc.general = documentation[autoloader]

documentation[makers] = [[
	sano.makers - Table of convenience functions for accessing the value 
	  constructors of Sano data structures.
	
	These function exist only to make creating data structures less
	verbose. Each function in sano.makers returns class:make(...)
	for some class.
	
	The mapping between function name and class is as follows:
	
	vector             = Vector,
	list, skipvector   = SkipVector,
	queue, queuevector = QueueVector,
	llist, linkedlist  = LinkedList,
	heap, pairingheap  = PairingHeap,
	set, hset, hashset = HashSet,
	oset, orderedset   = SkipSet,
	lset, linkedset    = LinkedSet,
	map, hmap, hashmap = HashMap,
	omap, orderedmap   = SkipMap,
	lmap, linkedmap    = LinkedMap,
	phonebook          = Phonebook,
	multiset, mset     = Multiset,
	multimap, mmap     = Multimap,
	tuple              = Tuple
	
	Examples:
	v = vector(1,2,3,4) <-->   v = Vector:make(1,2,3,4)
	sl = list(1,2,3,4)  <-->   sl = SkipVector:make(1,2,3,4)
	q = queue(1,2,3,4)  <-->   q = QueueVector:make(1,2,3,4)
	ll = llist(1,2,3,4) <-->   ll = LinkedList:make(1,2,3,4)
	s = set(1,2,3,4)    <-->   s = HashSet:make(1,2,3,4)
	
]]

-- TODO: this documentation parsing code is an ugly hack; clean up

local function getFirstSentance(str)
	if not str then return '\t'..default end
	return string.gfind(str, "(.-%.)%s")() or ""
end

local function getShortDescription(obj, default, localDoc)
	return getFirstSentance(autoloader.description(obj,localDoc))
end

local function prepSummary(obj, skipGeneral) -- ugly hack alert
	local ordering = autoloader.ordering
	local methodPriorities = ordering.ranked{{""},{"new","make"}}
	local name = string.gfind(obj.documentation.general, '%a+')()
	local origObj = obj
	local localDoc = origObj.documentation or {}
	local localMethodsFirst = function(x,y)
		local objX, objY = origObj[x], origObj[y]
		local parentX = string.gfind(string.sub(localDoc[objX] or documentation[origObj[x] ] or "", 1, 30) or "", '(%a+):')()
		local parentY = string.gfind(string.sub(localDoc[objY] or documentation[origObj[y] ] or "", 1, 30) or "", '(%a+):')()
		--printCall(parentX, parentY)
		if parentX == name and parentY ~= name then return x
		elseif parentX ~= name and parentY == name then return y
		else return x end
	end 
	
	local order = ordering.chained(methodPriorities, localMethodsFirst, ordering.INCREASING)
	local nameToObj = autoloader.SkipMap:new(order)
	if not skipGeneral then nameToObj:add("", obj) end
	while obj do
		for k,v in pairs(obj) do
			if k~="documentation" and k~="__index" and k~="source" and k~="loadAll" then
			nameToObj:add(k,v) end
		end
		obj = getmetatable(obj) ~= obj and getmetatable(obj)
	end
	return nameToObj
end

local function summary(map, localDoc)
	local buf = autoloader.makers.vector()
	for name, obj in map:iter() do 
		buf:add(getShortDescription(obj, name, localDoc))
		buf:add("\n\n")
	end
	return string.gsub(buf:asString(),"\n\n\n","\n")
end

--print(string.gfind(str,'(%a+):')())
local nilMsg = [[
	Cannot print documentation for nil object!

]]

local function missingMsg(obj)
	return "No documentation found for: "..tostring(obj)
end

function autoloader.doc(obj, printCall)
	printCall = printCall or print
	if obj == nil then
		printCall(nilMsg)
		return nilMsg
	end
	local doc = documentation[obj]
	if not string.find(doc,"%-") then doc = doc.." - ... " end
	if obj==makers then
		printCall(doc); return doc
	elseif type(obj)=="table" then
		local buf = autoloader.makers.vector()
		buf:add(doc)
		buf:add("\n\nSummary \n")
		buf:add(autoloader.summary(obj, function() end, true))
		printCall(buf:asString())
		return buf:asString()
	elseif type(obj)=="function" then 
		printCall(doc); return doc
	else
		printCall(missingMsg(obj)); return missingMsg(obj)
	end
end
documentation[autoloader.doc] = [[
	sano.doc(obj) - Prints the full documentation for obj.

]]

function autoloader.description(obj, localDoc)
	local doc =  localDoc and localDoc[obj] or documentation[obj]
	if not doc then return "" end
	if not string.find(doc,"%-") then doc = doc.."\t - ... " end
	return doc
end

--local function 
function autoloader.summary(obj, printCall, skipGeneral) -- to disable printing to console
	printCall = printCall or print
	if obj == nil then
		printCall(nilMsg)
		return t
	end
	if type(obj) ~= "table" then
		local t = getShortDescription(obj, missingMsg(obj))
		printCall(t)
		return t
	elseif obj == autoloader then
		autoloader.loadAll()
		local t = summary(prepSummary(obj, true), obj.documentation)
		printCall(t)
		return t
	elseif obj == autoloader.makers then
		local t = getShortDescription(obj, missingMsg(obj))
		printCall(t)
		return t
	end
	local t = summary(prepSummary(obj, skipGeneral), obj.documentation)
	printCall(t)
	return t
end
documentation[autoloader.summary] = [[
	sano.summary(obj) - Prints a brief, one sentance description of obj,
	  and, if obj is an object or module, a brief one sentance description
	  of each of the methods of the object or functions of the module.

]]

local modules = {'Vector','QueueVector','LinkedList','Tuple',
                 'SkipVector','SkipSet','SkipMap','Phonebook',
                 'HashMap','HashSet','LinkedSet','LinkedMap',
                 'Multiset','Multimap','PairingHeap',
                 'oop','ordering','iter','collections',
                 'sequences','sets','maps','heaps','utils'}

function autoloader.testAll()
	print("Testing module set:\n"..tostring(autoloader.makers.vector(modules)))
	for _,moduleName in ipairs(modules) do
		local m = autoloader[moduleName]
		if m.test then
			io.write("Testing "..moduleName.."... ")
			io.write(m:test() and "passed\n" or "passed\n")
		end
	end
	print("... all tests passed.")
end
documentation[autoloader.testAll] = [[
	sano.testAll() - Runs unit tests on all modules in the Sano library.

]]


-- File text archive table 
archive = {collections=[[
local sano = require 'sano'
local utils = sano.utils
local iter = sano.iter
local oop = sano.oop


--- Collections

local collections = {}
collections.documentation = {}
local apidoc = collections.documentation
local doc

apidoc.general = beginlongstringliteral
	collections - Module containing methods shared by multiple collection
	  implementations.

endlongstringliteral

collections.mixins = {}

doc = beginlongstringliteral
	collections.make(self, ...) - Creates and returns a new instance of self
	  containing the elements passed in as vararg params.
	
	If #args is 1, the argument is assumed to be iterable and its elements
	are added to the Collection.
	
	Example, assuming Collection is a sequence type:
	> print( Collection:make(1,2,3,4) )
	[1, 2, 3, 4]
	> print( Collection:make(iter.count(4) )
	[1, 2, 3, 4]
	> v = Collection:make(1,2,3,4); print( Collection:make(v) )
	[1, 2, 3, 4]
	
	This method is used as the make method for LinkedList, SkipVector, 
	HashSet, SkipSet, QueueVector, and PairingHeap. It assumes that self
	has new() and addAll() methods. 

endlongstringliteral
function collections.make(self, ...)
	local obj = self:new()
	obj:addAll(table.getn(arg)==1 and arg[1] or arg)
	return obj
end
apidoc[collections.make] = doc

doc = beginlongstringliteral
	collections.addAll(collection, iterable) - Calls collection:add(e) for
	  each e in iter(iterable).

endlongstringliteral
function collections.addAll(collection, elements)
	for i in iter.from(elements) do collection:add(i) end
	return collection
end
apidoc[collections.addAll] = doc

doc = beginlongstringliteral
	collections.contains(collection, element) - Returns true if element
	  exists in collection.

	This involves iterating through the entire collection and takes
	expected linear time.

endlongstringliteral
function collections.contains(collection, element)
	for e in collection:iter() do
		if e == element then return true end
	end
	return false
end
apidoc[collections.contains] = doc

doc = beginlongstringliteral
	collections.containsAll(collection, elements) - Returns true iff 
	  collection:contains(e) for e in iter(iterable).

endlongstringliteral
function collections.containsAll(collection, iterable)
	for i in iter.from(iterable) do
		if not collection:contains(i) then return false end
	end
	return true
end
apidoc[collections.containsAll] = doc

doc = beginlongstringliteral
	collections.containsAny(collection, iterable) - Returns true if for 
	  collection:contains(e) for some e in iter(iterable).

endlongstringliteral
function collections.containsAny(collection, elements)
	for i in iter.from(elements) do
		if collection:contains(i) then return true end
	end
	return false
end
apidoc[collections.containsAny] = doc

collections.mixins.plauralizeHelper = {
	addAll = collections.addAll,
	containsAll = collections.containsAll,
	containsAny = collections.containsAny
}

collections.mixins.convenienceMethods = {
	contains = collections.contains,
	enum = iter.enum
}

return collections
]],
LinkedSet=[[
local sano = require 'sano'
local LinkedMap = sano.LinkedMap
local iter = sano.iter

local LinkedSet = LinkedMap:new()
sano.maps.makeSet(LinkedSet)

local apidoc = {}
local doc
LinkedSet.documentation = apidoc

apidoc.general = beginlongstringliteral
	LinkedSet - Set implementation in which iteration order is same as
	  insertion order.
	
	The LinkedSet class was created by a call to 
	sano.maps.makeSet(LinkedMap:new()). The set is implemented
	by storing the elements of the set as the keys in LinkedMap, where all
	keys simply map to the value 'true'.
	
	Example usage:
	> linkedset = function(...) return LinkedSet:make(unpack(arg)) end
	> -- above is equivalent to: LinkedSet = sano.makers.linkedset
	> s = linkedset(1,2,3,3,4); print(s)
	{1, 2, 3, 4}
	> print(s == linkedset(4,3,2,1))
	true
	> print(s == linkedset(1,3,5,7))
	false
	> print(s:remove(1))
	1
	> s:addAll(iter.count(10,15)); print(s)
	{2, 3, 4, 10, 11, 12, 13, 14, 15}
	> s:removeAll(iter.count(2,4)); print(s)
	{10, 11, 12, 13, 14, 15}
	> print(s:size())
	6
	

endlongstringliteral

doc = beginlongstringliteral
	LinkedSet:test() - Unit test.

endlongstringliteral
function LinkedSet:test()
	local linkedset = function(...) return self:make(unpack(arg)) end
	
	local s = linkedset(1,2,3,3,4)
	assert(s == linkedset(4,3,2,1))
	assert(s:size()==4)
	assert(s:remove(3)==3)
	assert(s:remove(7)==nil)
	assert(s:contains(2))
	assert(s:containsAll(sano.iter.count(2)))
end
apidoc[LinkedSet.test] = doc


return LinkedSet
]],
sequences=[[local sano = require 'sano'
local collections = sano.collections
local oop = sano.oop
local iter = sano.iter

local apidoc = {}
local doc
local sequences = {documentation=apidoc}
sequences.mixins = {}

apidoc.general = beginlongstringliteral
	sequences - Module containing methods shared across sequence
	  implementations.

endlongstringliteral

doc = beginlongstringliteral
	sequences.removeElement(sequence, element) - Removes the first instance
	  of element from sequence and returns it, or if not found, nil.

endlongstringliteral
function sequences.removeElement(seq, element)
	local toRemove = nil
	for ind,v in iter.enum(seq:iter(),seq:size()) do
		if v == element then toRemove = ind end
	end
	if toRemove ~= nil then return seq:remove(toRemove) end
	return nil
end
apidoc[sequences.removeElement] = doc

doc = beginlongstringliteral
	sequences.removeAll(sequence, elements) - Calls
	  sequence:removeElement(e) for e in iter(elements). 

endlongstringliteral
function sequences.removeAll(collection, elements)
	for i in iter.from(elements) do collection:removeElement(i) end
end
apidoc[sequences.removeAll] = doc

doc = beginlongstringliteral
	sequences.first(sequence) - Returns the first element of the sequence,
	  or nil if the sequence is empty.

endlongstringliteral
function sequences.first(seq) return seq:size()>0 and seq:get(1) end
apidoc[sequences.first] = doc

doc = beginlongstringliteral
	sequences.last(sequence) - Returns the last element of the sequence, or
	  nil if the sequence is emtpty.

endlongstringliteral
function sequences.last(seq) return seq:size()>0 and seq:get(seq:size()) end
apidoc[sequences.last] = doc

doc = beginlongstringliteral
	sequences.toString(sequence) - Returns a string representation of
	  sequence.
	
	This method is used for the __tostring metamethod of Vector,
	SkipVector, QueueVector, and LinkedList. Example:
	
	> print(SkipVector:make(1,2,3,4,5))
	[1, 2, 3, 4, 5]	

endlongstringliteral
function sequences.toString(seq) return "["..iter.toString(seq:iter()).."]" end
apidoc[sequences.toString] = doc

doc = beginlongstringliteral
	sequences.swap(sequence, index1, index2) - Exchanges the elements
	  stored in sequence at the two supplied indices.

endlongstringliteral
function sequences.swap(seq, index1, index2)
	local t = seq:get(index1)
	seq:set(index1, seq:get(index2))
	seq:set(index2, t)
end
apidoc[sequences.swap] = doc

doc = beginlongstringliteral
	sequences.shuffle(sequence [,rng]) - Permutes the input sequence in
	  place, using the optionally supplied rng (by default math.random).
	
	rng can be any function which can takes two integer arguments a,b and
	returns a random integer in the range [a..b] (inclusive on both sides).
	
	The algorithm runs in linear time and generates all permutations with
	equal probability, assuming the rng is perfectly uniform. It is
	implemented using an algorithm proposed by Knuth, see
	http://en.wikipedia.org/wiki/Shuffle#Shuffling_algorithms

endlongstringliteral
function sequences.shuffle(seq, rng)
	rng = rng or math.random
	local n = seq:size()
	for i=n,2,-1 do
		local rand = rng(1,i)
		seq:swap(i, rand)
	end
	return seq
end
apidoc[sequences.shuffle] = doc

doc = beginlongstringliteral
	Sequence:sort([orderFn]) - Sorts the sequence in place using the
	  built-in table.sort function and the ordering specified (default
	  is increasing order).
	
	If seq is a Vector or Tuple, no auxilliary table is created for the
	sort. If seq is any other collection, a copy of seq is created, 
	sorted, and the results copied back to seq.

endlongstringliteral
function sequences.sort(seq, orderFn)
	local ordering = sano.ordering
	orderFn = orderFn or ordering.INCREASING
	local lt = ordering.lessThan
	if oop.isInstance(seq, sano.Vector) or oop.isInstance(seq, sano.Tuple) then
		local order = function(x,y) return lt(x,y,orderFn) end
		table.sort(seq, order)
	else
		for ind,v in ordering.sorted(seq,orderFn):enum() do
			seq:set(ind,v)
		end
	end
	return seq
end
apidoc[sequences.sort] = doc

sequences.mixins.plauralizeHelper = {
	addAll = collections.addAll, 
	containsAll = collections.containsAll,
	containsAny = collections.containsAny,
	removeAll = sequences.removeAll
}

sequences.mixins.convenienceMethods = {
	enum = iter.enum,
	make = collections.make,
	removeElement = sequences.removeElement,
	contains = collections.contains,
	first = sequences.first,
	last = sequences.last,
	__tostring = sequences.toString
}

sequences.mixins.randomAccessHelper = {
	swap = sequences.swap,
	shuffle = sequences.shuffle,
	sort = sequences.sort
}

return sequences]],
heaps=[[local sano = require 'sano'
local collections = sano.collections
local iter = sano.iter

local apidoc = {}
local heaps = {documentation = apidoc}
heaps.mixins = {}

local doc

apidoc.general = beginlongstringliteral
	heaps - Module containing methods shared across heap implementations.
	
	Currently, the Sano library contains only one heap implementation 
	(PairingHeap).

endlongstringliteral

doc = beginlongstringliteral
	heaps.toString(heap) - Returns a string representation of heap.
	
	The order in which elements are displayed is unspecified, except that 
	the top element of the heap will always appear first.
	
	Example:
	> h = heap(1,32,2,5,2,0)
	> print(h) -- 0 is top of heap, appears first in string repr.
	/0, 1, 2, 5, 2, 32\

endlongstringliteral
function heaps.toString(heap)
	return "/"..iter.toString(heap:iter()).."\\"
end
apidoc[heaps.toString] = doc

doc = beginlongstringliteral
	heaps.extractFirstK(heap [, k]) - Returns an iterator which 
	  destructively removes and returns the first k elements from this 
	  heap.
	
	If k is unspecified, the entire heap is drained by the returned 
	iterator. If k is greater than heap:size(), an error is thrown.
	
	The typical use for this method would be in a heapsort or a partial
	sort, for example:
	
	> v = vector(iter.count(15)); v:shuffle(); print(v)
	[4, 9, 3, 8, 12, 2, 14, 1, 6, 11, 13, 10, 7, 15, 5]
	> h = heap(v); print(h)
	/1, 5, 15, 7, 10, 13, 11, 6, 2, 14, 3, 12, 8, 4, 9\
	> print(vector(h:extractFirstK(10)))
	[1, 2, 3, 4, 5, 6, 7, 8, 9, 10]

endlongstringliteral
function heaps.extractFirstK(heap, k)
	local c = 0
	k = k or heap:size()
	assert(k <= heap:size(), "k is greater than heap size.")
	return function()
		if c < k then 
			c = c + 1; return heap:remove() 
		end
	end
end
apidoc[heaps.extractFirstK] = doc

heaps.mixins.convenienceMethods = {
	make = collections.make,
	addAll = collections.addAll,
	__tostring = heaps.toString,
	extractFirstK = heaps.extractFirstK
}

return heaps]],
ordering=[[
local sano = require("sano")
local utils = sano.utils
local iter = sano.iter

local ordering = {documentation={}}

local doc
local apidoc = ordering.documentation

apidoc.general = beginlongstringliteral
	ordering - A collection of utility functions and algorithms for working
	  with ordered data.
	
	This module contains several utilities for working with ordering
	functions. An ordering function is a binary function which returns the
	first argument if it is ordered before the second argument or if the
	two arguments are equal.
	
	For example, the natural increasing order, ordering.INCREASING, is 
	defined as:
	
		function ordering.INCREASING(x,y)
		   return x <= y and x or y
		end
	
	Instances of ordered collections in the Sano library (SkipMap, 
	SkipSet, PairingHeap) can be constructed using any user-defined
	ordering function (by default they use ordering.INCREASING). For each 
	of these collections, the ordering function is interpreted in the 
	obvious way: the 'retrieval' order of the collection will be consistent 
	with the order in which elements are returned by the ordering function:
	
	> p = PairingHeap:new(ordering.DECREASING)
	> p:addAll{3,2,1,6,5}; print(p:get())
	6
	> s = SkipSet:new(ordering.DECREASING)
	> s:addAll{1,2,3,4,5,6}; print(s)
	{6, 5, 4, 3, 2, 1}
	
	ordering.DECREASING returns larger elements first, so the largest
	element is at the top of the heap, and at the start of the iteration
	order of the SkipSet (an ordered set implementation).
	
	In addition, this module contains several algorithms for operating on
	ordered data. See for example: ordering.first, ordering.kth, 
	ordering.sorted, and ordering.lexicographical

endlongstringliteral

doc = beginlongstringliteral
	ordering.INCREASING(x,y) - Returns x if x <= y and y otherwise.
	
	This ordering is the default ordering typically used in functions
	where an (optional) ordering function is left unspecified.

endlongstringliteral
function ordering.INCREASING(x,y)
	return x <= y and x or y
end
apidoc[ordering.INCREASING] = doc

doc = beginlongstringliteral
	ordering.reverse(orderFn) - Returns a new ordering which is the reverse
	  of orderFn.

endlongstringliteral
function ordering.reverse(orderFn)
	return function(x,y)
		local first = orderFn(x,y)
		return rawequal(first, x) and y or x
	end
end
apidoc[ordering.reverse] = doc

doc = beginlongstringliteral
	ordering.DECREASING(x,y) - Returns x if x >= y and y otherwise.

endlongstringliteral
function ordering.DECREASING(x,y)
 	return x >= y and x or y
end
apidoc[ordering.DECREASING] = doc

doc = beginlongstringliteral
	ordering.byKey(key [,orderFn]) - Returns a new ordering, obk, in which
	  obk(x,y) == x iff x[key] <= y[key].
	
	For example, to sort a vector of tables, t, using the second element of
	each table as the sort criteria:
	> v = vector({1,1},{1,2},{1,1.3},{1,1.4})
	> sorted = ordering.sorted(v, ordering.byKey(2))
	> for t in sorted:iter() do print(t[1],t[2]) end
	1       1
	1       1.3
	1       1.4
	1       2

endlongstringliteral
function ordering.byKey(key, orderFn)
	local natural = orderFn or ordering.INCREASING
	return function(x,y)
		local vx, vy = x[key], y[key]
		local first = natural(vx, vy)
		return rawequal(first, vx) and x or y
	end
end
apidoc[ordering.byKey] = doc

doc = beginlongstringliteral
	ordering.byTransform(transform) - Returns a new ordering obt, in which
	  obt(x,y) == x iff transform(x) <= transform(y).

endlongstringliteral
function ordering.byTransform(transformer, rev)
	local natural = not rev and ordering.INCREASING or ordering.DECREASING
	return function(x,y)
		local tx, ty = transformer(x), transformer(y)
		local first = natural(tx, ty)
		return rawequal(first, tx) and x or y
	end
end
apidoc[ordering.byTransform] = doc

doc = beginlongstringliteral
	ordering.equals(x,y,orderFn)

endlongstringliteral
function ordering.equals(x,y,orderFn)
	return rawequal(orderFn(x,y), x) and rawequal(orderFn(y,x), y)
end
apidoc[ordering.equals] = doc

local eq = ordering.equals

doc = beginlongstringliteral
	ordering.chained(...) - Returns a new ordering based on a chain of
	  ordering functions.
	
	If the first ordering function in the chain determines the two input
	are equal, the second ordering function is consulted, and so on, until
	one of the orderings reveals a preference or the chain is exhausted.
	In effect, orderings later in the chain are used to 'break ties'
	between orderings earlier in the chain.
	
	This is useful for sorting some data based on multiple criteria. For
	example, we might sort a list of Tuple objects storing names in the
	form (first, last) using the following code:
	
	> -- names = [('J','Jones'),('A','Jones'),('A','Smith'),('B','Smith')]
	> -- sort by last name, then use first name to break ties
	> names:sort(ordering.chained(ordering.byKey(2), ordering.byKey(1)))
	> print(names)
	[("A", "Jones"), ("J", "Jones"), ("A", "Smith"), ("B", "Smith")]

endlongstringliteral
function ordering.chained(...)
	return function(x,y)
		for orderFn in iter(arg) do
			if not eq(x,y,orderFn) then
				return orderFn(x,y)
			end
		end
		return arg[table.getn(arg)](x,y)
	end
end
apidoc[ordering.chained] = doc

doc = beginlongstringliteral
	ordering.ranked(rankGroups)

endlongstringliteral
function ordering.ranked(rankGroups)
	local rankMap = {}
	local rank = 1
	for group in iter(rankGroups) do
		for e in iter(group) do
			rankMap[e]=rank
		end
		rank = rank+1
	end
	return function(x,y)
		--for k,v in pairs(rankMap) do print(k,v) end
		local rankX, rankY = rankMap[x] or rank, rankMap[y] or rank
		--print(rankX, rankY)
		return rankY < rankX and y or x
	end
end
apidoc[ordering.ranked] = doc

doc = beginlongstringliteral
	ordering.lessThan(x,y,orderFn)

endlongstringliteral
function ordering.lessThan(x,y,orderFn)
	return rawequal(orderFn(x,y), x) and not rawequal(orderFn(y,x), y)
end
apidoc[ordering.lessThan] = doc

doc = beginlongstringliteral
	ordering.lessThanOrEq(x,y,orderFn)

endlongstringliteral
function ordering.lessThanOrEq(x,y,orderFn)
	return rawequal(orderFn(x,y), x)
end
apidoc[ordering.lessThanOrEq] = doc

doc = beginlongstringliteral
	ordering.greaterThan(x,y,orderFn)

endlongstringliteral
function ordering.greaterThan(x,y,orderFn)
	return rawequal(orderFn(y,x), y) and not rawequal(orderFn(x,y),x)
end
apidoc[ordering.greaterThan] = doc

doc = beginlongstringliteral
	ordering.greaterThanOrEq(x,y,orderFn)

endlongstringliteral
function ordering.greaterThanOrEq(x,y,orderFn)
	return rawequal(orderFn(y,x), y)
end
apidoc[ordering.greaterThanOrEq] = doc

doc = beginlongstringliteral
	ordering.isSorted(iterable [,orderFn]) - Testing function which returns
	  true if the iterable is sorted according to the specified ordering
	  function.

endlongstringliteral
function ordering.isSorted(iterable, orderFn)
	orderFn = orderFn or ordering.INCREASING
	for i,j in iter.windows(iter(iterable), 2) do
		if orderFn(i,j) ~= i then return false end
	end
	return true
end
apidoc[ordering.isSorted] = doc

local lt = ordering.lessThan

doc = beginlongstringliteral
	ordering.sorted(iterable [,orderFn]) - Returns a Sano Vector sorted
	  using the built in Lua sort function and the ordering specified
	  (default ordering.INCREASING).

endlongstringliteral
function ordering.sorted(iterable, orderFn)
	orderFn = orderFn or ordering.INCREASING
	local order = function(x,y) return lt(x,y,orderFn) end
	local tab = iter.toList(iter.from(iterable))
	table.sort(tab, order)
	setmetatable(tab, sano.Vector)
	tab.mSize = table.getn(tab)
	return tab
end
apidoc[ordering.sorted] = doc

doc = beginlongstringliteral
	ordering.rsorted(iterable [,orderFn]) - Returns a Sano Vector reverse
	  sorted using the built-in Lua sort function and the ordering function
	  specified (default ordering.INCREASING).
	
	If orderFn is left unspecified, the order of the returned Vector will
	be max first.

endlongstringliteral
function ordering.rsorted(iterable, orderFn)
	orderFn = ordering.reverse(orderFn or ordering.INCREASING)
	return ordering.sorted(iterable, orderFn)
end
apidoc[ordering.rsorted] = doc

doc = beginlongstringliteral
	ordering.first(iterable [,orderFn]) - Returns the first element that
	  would appear if the elements of iterable were sorted by orderFn,
	  which defaults to ordering.INCREASING.

endlongstringliteral
function ordering.first(iterable, orderFn)
	return iter.fold(iterable, orderFn or ordering.INCREASING)
end
apidoc[ordering.first] = doc

doc = beginlongstringliteral
	ordering.first(iterable [,orderFn]) - Returns the last element that
	  would appear if the elements of iterable were sorted by orderFn,
	  which defaults to ordering.INCREASING.

endlongstringliteral
function ordering.last(iterable, orderFn)
	return iter.fold(iterable, ordering.reverse(orderFn or ordering.INCREASING))
end
apidoc[ordering.last] = doc


local le = ordering.lessThanOrEq

local function partition(a, pivotInd, left, right, orderFn)
	local pivot = a:get(pivotInd)
	local storeInd = left
	a:swap(pivotInd, right)
	for i=left,right-1 do
		if le(a[i], pivot, orderFn) then 
			a:swap(storeInd, i)
			storeInd = storeInd+1
		end
	end
	a:swap(right, storeInd)
	return storeInd
end

local function kth(a, k, left, right, orderFn)
	local pivotInd = math.random(left,right)
	local newPivotInd = partition(a, pivotInd, left, right, orderFn)
	if newPivotInd == k then
		return a:get(k), a
	elseif k < newPivotInd then
		return kth(a,k,left,newPivotInd-1,orderFn)
	else
		return kth(a,k,newPivotInd+1,right,orderFn)
	end
end

doc = beginlongstringliteral
	ordering.kth(iterable, k, orderFn) - Returns the element that would 
	  appear at the kth index if the elements of iterable were sorted.
	
	This method also returns as a second value a sano.Vector, v in which:
	
	+) v[k] == v:sort()[k], 
	+) set(v:iter(1,k)) == set(v:sort():iter(1,k)),
	+) set(v:iter(k+1,-1)) == set(v:sort():iter(k+1,-1))
	
	For example:
	
	> v = vector(iter.count(1,10)); v:shuffle(); print(v)
	[4, 3, 9, 2, 6, 8, 5, 1, 10, 7]
	> print(ordering.kth(v,6))
	6       [1, 2, 3, 5, 4, 6, 7, 9, 10, 8]
	
	The element 6 is in the 6th position and is the first value returned.
	The second value returned is the Vector used for partitioning. Notice
	that the set of elements which appear before 6 in the Vector are the
	same that would appear if the entire Vector were sorted; likewise for
	the set of elements which appear after 6.
	
	The time complexity is expected O(size(iterable)). See
	http://en.wikipedia.org/wiki/Median_algorithm for more information on
	the algorithm used.
	
	If iterable is a Vector, the operation is done IN PLACE; otherwise, the
	elements of iterable are added to a new Vector. In either case, the
	Vector (new or otherwise) is returned as the second value.
	
	param k: must be positive and <= the number of elements in iterable

endlongstringliteral
function ordering.kth(iterable, k, orderFn)
	local oop = sano.oop
	local Vector = sano.Vector
	if not oop.isInstance(iterable, Vector) then
		iterable = Vector:make(iterable)
		assert(k <= iterable:size(), "k must be less than the number of elements in iterable")
	end
	local size = iterable:size()
	return kth(iterable, k>=1 and k or math.ceil(k*size), 1, size, orderFn or ordering.INCREASING)
end
apidoc[ordering.kth] = doc

doc = beginlongstringliteral
	ordering.keyOrdered(mapIterable, orderFn) - Returns an iterator over
	  the key-value pairs of mapIterable, ordered by key.
	
	mapIterable may be a Lua table or any two element iterable. See
	iter.mapIter for more information on the map-iterable contract.
	
	Examples:
		> keys, vals = "edcba", iter.count(5,1,-1)
		> for k,v in ordering.keyOrdered(iter.zip(keys,vals)) do print(k,v) end
		a       1
		b       2
		c       3
		d       4
		e       5
		> -- equivalently:
		> m = {a=1,b=2,c=3,d=4,e=5}
		> for k,v in ordering.keyOrdered(m) do print(k,v)
		a       1
		b       2
		c       3
		d       4
		e       5		

endlongstringliteral
function ordering.keyOrdered(mapIter, orderFn)
	local orderedKeys = sano.SkipMap:new(orderFn)
	orderedKeys:addMappings(mapIter)
	return orderedKeys:iter()
end
apidoc[ordering.keyOrdered] = doc

doc = beginlongstringliteral
	ordering.heapsorted(iterable, orderFn) - Sorts the elements of iterable
	  using a heap along with the specified ordering function.

endlongstringliteral
function ordering.heapsorted(iterable, orderFn)
	local h = sano.PairingHeap:new(orderFn)
	h:addAll(iterable)
	return h:extractFirstK()
end
apidoc[ordering.heapsorted] = doc

local function lexicographical(i1, i2, orderFn)
	local gt = ordering.greaterThan
	while true do
		local a, b = i1(), i2()
		if a~=nil and b~=nil then
			if gt(a,b,orderFn) then return 1
			elseif gt(b,a,orderFn) then return -1
			--else return lexicographical(i1, i2, orderFn)
			end
		elseif a==nil then
			if b == nil then return 0 -- they're equal
			elseif b ~= nil then return -1 -- i2 has more elements and is therefore greater
			end
		elseif a~=nil and b==nil then -- i1 has more elements and is greater
			return 1
		end
	end
end

doc = beginlongstringliteral
	ordering.lexicographical(iterable1, iterable2 [,orderFn]) - Returns 
	  -1, 0, or 1 indicating whether iterable1 is ordered lexicographically
	  before, is equal to, or is ordered after iterable2.
	
	The ordering on strings is a lexicographical ordering based on the
	ordering of individual letters - if the two strings have a different
	first letter, the string which starts with the alphabetically first
	letter is ordered first. If the two strings have the same first letter,
	then second letter is examined, and so on, until one or both of the
	strings run out of letters or pairwise comparisons reveal an ordering.
	
	More generally, in a lexicographical ordering:
	   
	   If a = a1, a2, ..., aN and b = b1, b2, ..., bN, then a < b iff:
		b1 > a1
		OR
		b1 == a1, b2 == a2, ..., bK == aK, bK+1 > aK+1 (b is greater than a)
	
	If a and b have different sizes, the smaller will be ordered before the
	larger if pairwise comparisons do not reveal an ordering.
	
	Examples: (1,1) <= (1,1), () <= (1), (1,2,1) <= (1,3)
	
	The evaluation is 'short-circuit' and halts as soon as there is enough
	information to determine the ordering.
	
	returns -1 if iterable1 is ordered before iterable2, 
	         0 if the two iterables are equal,
	         1 if iterable2 is ordered after iterable2

endlongstringliteral
function ordering.lexicographical(iterable1, iterable2, orderFn)
	orderFn = orderFn or ordering.INCREASING
	local result = lexicographical(iter(iterable1), iter(iterable2), orderFn)
	if result <= 0 then
		return iterable1, iterable2, result==0, result
	else
		return iterable1, iterable2, nil, result
	end
end
apidoc[ordering.lexicographical] = doc

doc = beginlongstringliteral
	ordering.test - unit test

endlongstringliteral
function ordering.test()
	assert( ordering.equals(1,1,ordering.INCREASING) )
	assert( ordering.equals(1,1,ordering.DECREASING) )
	assert( not ordering.equals(1,2,ordering.INCREASING) )
	assert( ordering.lessThanOrEq(1,1,ordering.INCREASING) )
	assert( ordering.lessThanOrEq(1,6,ordering.INCREASING) )
	assert( ordering.greaterThanOrEq(1,1,ordering.DECREASING) )
	assert( ordering.greaterThanOrEq(6,1,ordering.INCREASING) )
	assert( ordering.lessThan(1,6,ordering.INCREASING) )
	assert( ordering.greaterThan(6,1,ordering.INCREASING) )
	
	assert( ordering.first(iter.count(25,1,-1)) == 1 )
	assert( ordering.first(iter.count(25,1,-1), ordering.DECREASING) == 25 )
	assert( ordering.last(iter.count(25,1,-1)) == 25 )
	
	local vector = sano.makers.vector
	local N = 100
	local v = vector(iter.count(N))
	for i=1,10 do
		v:shuffle()
		assert( ordering.kth(v, 10) == 10 )
		assert( ordering.kth(v, 1) == 1 )
		assert( ordering.kth(v, .9999) == N )
	end
	
	assert(ordering.isSorted{1,2,3,4,5,6})
	assert(not ordering.isSorted{1,2,1,4,24})
	
	v:shuffle()
	--print(vector(ordering.sorted(v)))
	assert( ordering.isSorted(ordering.sorted(v)) )
	assert( ordering.isSorted(ordering.rsorted(v, ordering.DECREASING)) )
	--assert( ordering.isSorted(ordering.heapsorted(v)) )
	
end
apidoc[ordering.test] = doc

ordering.test()
return ordering
]],
SkipMap=[[

local sano = require 'sano'
local iter = sano.iter
local oop = sano.oop
local ordering = sano.ordering
local maps = sano.maps
local utils = sano.utils

local function SNode(key,val,numSkips)
	return {key=key, val=val, numSkips=numSkips, skips={}}
end

local function default0(t,ind) return 0 end

local function SINode(val,numSkips)
	local t = {val=val, numSkips=numSkips, skips={}, distance={__index=default0}}
	setmetatable(t.distance,t.distance); return t
end

local E = 2.718
local MAX_LEVELS = 24

local function exponential(survivalp, intmax)
	local toReturn = 1
	while math.random() > survivalp and toReturn <= intmax do
		toReturn = toReturn + 1
	end
	return toReturn
end

local SkipMap = {documentation={},cDefaultOrdering=ordering.INCREASING}
local apidoc = SkipMap.documentation
local doc

apidoc.general = beginlongstringliteral
	SkipMap - An ordered map implementation with performance characteristics
	  similar to a balanced binary tree.
	
	The name 'SkipMap' comes from the implementation, which is based on 
	skip-lists.
	
	Example of usage:
	> local s1 = SkipMap:make{a=1,b=2,c=3,d=4,e=5}
	> assert( s1:size()==5 )
	> assert( ordering.isSorted(s1) )
	> assert( s1:get("a")==1 )
	> assert( s1:get("e")==5 )
	> assert( s1:first()=="a" )
	> assert( s1:last()=="e" )
	> assert( s1:remove("b")==2 ) -- can also remove a key range
	> -- iterate just from keys=="c" or after
	> for k,v in s1:iter("c") do print(k,v) end
	c	3
	d	4
	e	5

	SkipSet is just a SkipMap where the keys map to true for all elements
	that exist in the set. Phonebook is a SkipMap which allows duplicate
	key-value pairs.

endlongstringliteral

doc = beginlongstringliteral
	SkipMap:new([orderFn]) - Returns a newly constructed SkipMap using the
	  specified ordering function, or self.cDefaultOrdering (initially
	  natural increasing order) if none is specified.
	
	The newly created map can be used to create other maps with the same
	default ordering, for instance:
	> MaxFirstSkipMap = SkipMap:new(ordering.DECREASING)
	> maxFirstMap = MaxFirstSkipMap:make{a=1,b=2,c=3,d=4}
	> print(maxFirstMap:first())
	d

endlongstringliteral
function SkipMap:new(orderFn)
	local obj = {}
	setmetatable(obj, self)
	self.__index = oop.loadOnFirstUse(self)
	obj.mOrderFn = orderFn or self.cDefaultOrdering
	obj.cDefaultOrdering = obj.mOrderFn
	obj.mHead = SNode(nil, nil, MAX_LEVELS)
	obj.mMaxLevel = 1
	obj.mFingers = {}
	obj.mSize = 0
	return obj
end
 apidoc[SkipMap.new] = doc

-- private methods: search, fingerSearch
local function search(node, key)
	for i=maxLevel,1,-1 do
		local t = node.skips[i]
		while t and t.key < key do node = t; t = t.skips[i] end
	end
	local prev,atOrGt = node, node.skips[1]
	if atOrGt and atOrGt.key == key then return atOrGt
	else return prev, atOrGt end -- bracket where it would be
end

local lessThan = ordering.lessThan
local greaterThanOrEq = ordering.greaterThanOrEq
local lessThanOrEq = ordering.lessThanOrEq
local equals = ordering.equals

--beginlongstringliteral same as search, but store largestNode at each level traversed 
 also updates fingers endlongstringliteral
local function trackedSearch(self, node, key, startingLevel, largestPerLevel)
	local mOrderFn = self.mOrderFn
	for i=startingLevel,1,-1 do
		local t = node.skips[i]
		while t and lessThan(t.key, key, mOrderFn) do node = t; t = t.skips[i] end
		largestPerLevel[i] = node
	end
	self.mFingers = largestPerLevel
	local prev,atOrGt = node, node.skips[1]
	if atOrGt and equals(atOrGt.key,key,mOrderFn) then return atOrGt
	else return prev, atOrGt end -- bracket where it would be
end

local function fingerSearch(self, key)
	if table.getn(self.mFingers)==0 then 
		return trackedSearch(self, self.mHead, key, self.mMaxLevel, self.mFingers) 
	end
	local fingers, mMaxLevel, mOrderFn = self.mFingers, self.mMaxLevel, self.mOrderFn
	local v, startNode = 2, nil
	if fingers[1].key and lessThan(fingers[1].key, key, mOrderFn) then -- move forward on highest level possible
		while v <= mMaxLevel and fingers[v].skips[v] and lessThan(fingers[v].skips[v].key, key, mOrderFn) 
			do v = v + 1 end
		v = v - 1
		startNode = fingers[v]
	else -- move backward
		while v <= mMaxLevel and fingers[v].key and greaterThanOrEq(fingers[v].key, key, mOrderFn) 
			do v = v + 1 end
		if v > mMaxLevel then v = mMaxLevel; startNode = self.mHead -- back to start
		else startNode = fingers[v] end
	end
	return trackedSearch(self, startNode, key, v, fingers)
end

local function insert(self, startNode, key, val, allowDups)
	local maxLevel = self.mMaxLevel; local update = {}; local node = startNode
	local mOrderFn = self.mOrderFn
	if not allowDups then
		local at = trackedSearch(self, startNode, key, maxLevel, update)
		if at.key and equals(at.key, key, mOrderFn) then -- already exists
			local old = at.val; at.val = val; return old
		end
	else
		for i=maxLevel,1,-1 do
			local t = node.skips[i]
			while t and lessThanOrEq(t.key,key,mOrderFn) do node = t; t = t.skips[i] end
			update[i] = node
		end
	end
	local newLevel = exponential(1/E, MAX_LEVELS)
	if newLevel > maxLevel then
		maxLevel, newLevel = maxLevel+1, maxLevel+1
		update[maxLevel] = self.mHead
		--for i=maxLevel+1,newLevel do update[i] = mHead end
		--maxLevel = newLevel -- technically correct
	end
	local newNode = SNode(key, val, newLevel)
	for i=1,newLevel do
		newNode.skips[i] = update[i].skips[i]
		update[i].skips[i] = newNode
	end
	self.mFingers = {}
	self.mMaxLevel = maxLevel
	self.mSize = self.mSize + 1
end

local function delete(self, node, key)
	local update = {}
	local maxLevel, mHead = self.mMaxLevel, self.mHead
	local toRemove = trackedSearch(self, node, key, maxLevel, update)
	if toRemove.key ~= key then return nil end
	for i=1,maxLevel do
		if update[i].skips[i] == toRemove then
			update[i].skips[i] = toRemove.skips[i] 
		end
	end
	while maxLevel > 1 and not mHead.skips[maxLevel] do
		maxLevel = maxLevel - 1
	end
	self.mSize = self.mSize - 1
	self.mMaxLevel = maxLevel
	return toRemove
end

doc = beginlongstringliteral
	SkipMap:first() - Returns the first key-value pair in this map, or nil
	  if the map is empty.
	
	time complexity: O(1)

endlongstringliteral
function SkipMap:first()
	local n = self.mHead.skips[1]
	if n then return n.key, n.val end
end
 apidoc[SkipMap.first] = doc


doc = beginlongstringliteral
	SkipMap:last() - Returns the last key-value pair in this map, or nil if
	  the map is empty.
	
	time complexity: O(lg(size))

endlongstringliteral
function SkipMap:last()
	local n = self.mHead
	for i=self.mMaxLevel,1,-1 do while n.skips[i] do n = n.skips[i] end end
	return n.key,n.val
end
 apidoc[SkipMap.last] = doc


doc = beginlongstringliteral
	SkipMap:split(splitKey) - Removes all elements ordered at or after 
	  splitKey and returns them in a new SkipMap.
	
	Example, given the map m = {a=1,b=2,c=3,d=4}, m:split("b") would
	return {b=2,c=3,d=4}.
	
	The same result can be acheived by calling SkipMap:splice(splitKey). 
	splice() is a more general method in that it also allows an end key.
	
	time complexity: O(lg(size))

endlongstringliteral
function SkipMap:split(splitKey)
	local mOrderFn = self.mOrderFn
	local toReturn = getmetatable(self):new()
	toReturn.mMaxLevel = self.mMaxLevel
	local mMaxLevel, smMaxLevel = self.mMaxLevel, toReturn.mMaxLevel
	local node, shead = self.mHead, toReturn.mHead
	for i=mMaxLevel,1,-1 do
		while node.skips[i] and lessThan(node.skips[i].key, splitKey, mOrderFn)
			do node = node.skips[i] end
		shead.skips[i] = node.skips[i]
		node.skips[i] = nil
	end
	local mHead = self.mHead
	while mHead.skips[mMaxLevel] == nil and mMaxLevel > 1 do
		mMaxLevel = mMaxLevel - 1 end
	self.mMaxLevel = mMaxLevel
	while shead.skips[smMaxLevel] == nil and smMaxLevel > 1 do
		smMaxLevel = smMaxLevel - 1 end
	local smSize = -1; while shead do smSize=smSize+1; shead=shead.skips[1] end 
	toReturn.mMaxLevel = smMaxLevel
	toReturn.mSize = smSize; self.mSize = self.mSize - smSize
	return toReturn
end
 apidoc[SkipMap.split] = doc


doc = beginlongstringliteral
	SkipMap:concatenate(skipmap) - Concatenates skipmap onto the end of this
	  SkipMap.
	
	This method throws an error if the two maps overlap. If the two maps
	are expected to have overlapping keysets according to the ordering, then
	use SkipMap:merge().
	
	skiplist is destroyed as a result of this call.
	
	time complexity: O(lg(size))

endlongstringliteral
function SkipMap:concatenate(skiplist)
	if self:size()>0 and skiplist:size()>0 then
		assert(lessThan(self:last(), skiplist:first(), self.mOrderFn), 
		"Cannot concatenate overlapping list; use merge() instead.")
	end
	self.mMaxLevel = math.max(self.mMaxLevel, skiplist.mMaxLevel)
	local node = self.mHead
	for i=self.mMaxLevel,1,-1 do
		while node.skips[i] do node = node.skips[i] end
		if i <= skiplist.mMaxLevel then node.skips[i] = skiplist.mHead.skips[i] end
	end
	self.mSize = self.mSize + skiplist.mSize
	oop.invalidate(skiplist, SkipMap, "SkipMap has been concatenated onto another.")
	return self
end
 apidoc[SkipMap.concatenate] = doc

doc = beginlongstringliteral
	SkipMap:splice(start [, stop]) - Removes all elements ordered between
	  start and stop (both inclusive) and returns them as a new SkipMap.

	param stop: the last key (inclusive) to remove, default SkipMap:last()
	
	time complexity: O(lg(size) + |stop-start|) (Unfortunately, the linear
	term is needed to recompute sizes - todo is to recompute these lazily)

endlongstringliteral
function SkipMap:splice(start, stop)
	assert(start)
	stop = stop or self:last()
	local startToEnd = self:split(start)
	--if startToEnd:size()==0 then return 
	local stopToEnd = startToEnd:split(stop)
	local last = stopToEnd:first()
	if last then startToEnd:add(last, stopToEnd:remove(last)) end
	self:concatenate(stopToEnd)
	return startToEnd
end
 apidoc[SkipMap.splice] = doc
	
doc = beginlongstringliteral
	SkipMap:merge(skipmap) - Merges another SkipMap into this SkipMap, 
	  ensuring that the merged map remains ordered.
	
	The other SkipMap is destroyed as result of this call.
	
	The time complexity of this operation depends on the amount of overlap.
	The worst case, when entries need to be perfectly interlaced, takes
	O(size(self)+size(skipmap)). The best case, when entries are completely
	disjoint, takes only O(lg(size)).

endlongstringliteral
function SkipMap:merge(slist2, saveDuplicates)
	local update, unflipped = {}, true
	local dupCount = 0
	local merged = self:new()
	local mOrderFn = self.mOrderFn
	merged.mMaxLevel = math.max(self.mMaxLevel, slist2.mMaxLevel)
	local mergedMaxLevel= merged.mMaxLevel
	for i=1,mergedMaxLevel do update[i] = merged.mHead end
	local head1,head2 = self.mHead,slist2.mHead
	
	while head1.skips[1] and head2.skips[1] do
		local key1,key2 = head1.skips[1].key, head2.skips[1].key
		if lessThan(key2, key1, mOrderFn) then 
			  head1,head2,key1,key2,unflipped 
			= head2,head1,key2,key1,not unflipped
		end
		local v = 1
		repeat update[v].skips[v] = head1.skips[v]; v = v+1
			until v > mergedMaxLevel or not head1.skips[v] or lessThan(key2, head1.skips[v].key, mOrderFn)
		v = v - 1
		local node = head1.skips[v]
		for i=v,1,-1 do
			while node.skips[i] and lessThanOrEq(node.skips[i].key, key2, mOrderFn) 
				do node = node.skips[i] end
			update[i] = node
			head1.skips[i] = node.skips[i]
		end
		if not saveDuplicates and equals(key2,node.key,mOrderFn) then
			dupCount = dupCount + 1
			if unflipped then node.val = head2.skips[1].val end
			local node2 = head2.skips[1]
			for i=1,node2.numSkips do head2.skips[i] = node2.skips[i] end
			-- node2 is deleted
		end
	end -- one or both lists have been exhausted
	
	local leftOver = (not head2.skips[1]) and head1 or head2
	local leftOverML = unflipped and self.mMaxLevel or slist2.mMaxLevel
	for i=1,leftOverML do update[i].skips[i] = leftOver.skips[i] end
	
	-- we may have deleted some elements, readjust
	if not saveDuplicates then
		for i=leftOverML+1,mergedMaxLevel do update[i].skips[i] = nil end
		while merged.mHead.skips[merged.mMaxLevel] == nil and merged.mMaxLevel > 1
			do merged.mMaxLevel = merged.mMaxLevel - 1 end
	end
	self.mMaxLevel = merged.mMaxLevel
	self.mHead = merged.mHead
	self.mSize = self.mSize + slist2.mSize + (saveDuplicates and 0 or -dupCount)
	self.mFingers = {}
	return self
end
 apidoc[SkipMap.merge] = doc

doc = beginlongstringliteral
	SkipMap:add(key, val) - Adds a new key-value pair to this SkipMap and
	  returns the old value stored against key (or nil if the pair is new).

endlongstringliteral
function SkipMap:add(key, val, allowDuplicates)
	return insert(self, self.mHead, key, val, allowDuplicates)
end
 apidoc[SkipMap.add] = doc


doc = beginlongstringliteral
	SkipMap:get(key) - Returns the value associated with the supplied key,
	  or nil if none exists.
	
	synonyms: map:get(key) == map(key)

endlongstringliteral
function SkipMap:get(key)
	local node = fingerSearch(self, key)
	return (node.key and equals(node.key, key, self.mOrderFn)) and node.val or nil
end
 apidoc[SkipMap.get] = doc

SkipMap.__call = SkipMap.get

doc = beginlongstringliteral
	SkipMap:contains(key) - Returns true if this SkipMap contains a value
	  for the supplied key.

endlongstringliteral
function SkipMap:contains(key)
	return self:get(key) ~= nil
end
 apidoc[SkipMap.contains] = doc


doc = beginlongstringliteral
	SkipMap:remove(key [, last]) - Removes the mapping associated with the
	  supplied key and returns the value stored against key.
	
	Supplying a second parameter makes this equalivalent to calling
	SkipMap:splice().
	
	Examples: 	
	{a=1,b=2,c=3}:remove("a") == 1
	{a=1,b=2,c=3}:remove("a","b") == {a=1,b=2}

endlongstringliteral
function SkipMap:remove(key, last)
	if last then return self:splice(key, last) end
	local node = delete(self, self.mHead, key)
	return node and node.val or nil
end	
 apidoc[SkipMap.remove] = doc

doc = beginlongstringliteral
	SkipMap:iter([start [,stop] ])  - Returns an iterator over all key-value
	  pairs in this map, or optionally over the range [start..stop]
	  (inclusive both ends).
	
	If stop is unspecified, the iteration is from start to the last mapping
	(inclusive).
	
	Each step in the iteration returns two elements, k,v where k is a key
	in the key sequence and v is the value associated with k.

endlongstringliteral
function SkipMap:iter(start, stop)
	if self.mSize == 0 then return iter.EMPTY end
	
	local mOrderFn = self.mOrderFn
	start = start ~= nil and start or self:first()
	stop = stop ~= nil and stop or self:last()
	local startNode, failed = fingerSearch(self, start)
	if failed then 
		startNode = failed 
	end -- just past where start would be
	return coroutine.wrap(function()
		local curNode = startNode
		while curNode and lessThanOrEq(curNode.key, stop, mOrderFn) do
			coroutine.yield(curNode.key, curNode.val)
			curNode = curNode.skips[1]
		end
	end)
end

doc = beginlongstringliteral
	SkipMap:size() - Returns the number of mappings in this SkipMap.

endlongstringliteral
function SkipMap:size() return self.mSize end
 apidoc[SkipMap.size] = doc


doc = beginlongstringliteral
	SkipMap:__eq(other) - Returns true if both SkipMaps have the same size 
	  and contain the same elements in the same order.

endlongstringliteral
function SkipMap:__eq(other)
	return self:size()==other:size() and iter.multiEquals(self:iter(), other:iter())
end
 apidoc[SkipMap.__eq] = doc

function SkipMap:debug()
	local curNode = self.mHead
	local function strNode(node)
		local k,v = node.key and node.key or "nil", node.val and node.val or "nil"
		local links = ""
		for _,l in pairs(node.skips) do links = links..l.key..", " end
		return k.."="..v.."->"..links
	end
	print("maxLevel="..self.mMaxLevel, "size="..self.mSize)
	while curNode do 
		print (strNode(curNode))
		curNode = curNode.skips[1] 
	end
end

oop.mixin(SkipMap, maps.mixins.plauralizeHelper)
oop.mixin(SkipMap, maps.mixins.convenienceMethods)

doc = beginlongstringliteral
	SkipMap:test() - Unit test. 

endlongstringliteral
function SkipMap:test()
	local skipmap = function(map) return self:make(map) end
	
	-- exercise basic functionality
	local s1 = skipmap{a=1,b=2,c=3,d=4,e=5}
	--print(s1, s1:size())
	--s1:add("b",2)
	--print(s1)
	assert(s1:size()==5)
	assert(ordering.isSorted(s1))
	assert(s1:get("a")==1)
	assert(s1:get("e")==5)
	assert(s1:first()=="a")
	assert(s1:last()=="e")
	assert(s1:remove("b")==2)

	local s2 = skipmap{d=4,c=3,a=1,e=5}
	assert(s1==s2)
	
	local s3 = skipmap{e=5,f=6,g=7,h=8}
	s1:merge(s3)
	assert(ordering.isSorted(s1))
	assert(s1:size()==7, "Duplicate elements were retained, size should be 7, was: "..s1:size())
	
	local empty = s1:splice("z")
	assert(empty:size()==0, tostring(empty))
	assert(empty:first()==nil)
	assert(empty:last()==nil)
	s1:merge(empty)
	
	local s1Copy = skipmap(s1)
	local nonempty = s1:split("b")
	s1:concatenate(nonempty)
	assert(s1==s1Copy)
	
	local s1Copy2 = skipmap(s1)
	assert(s1Copy2:splice('b','d') == skipmap(s1:iter('b','d')))
	
	local queue = sano.makers.queue()
	local N=500
	for i=1,N do 
		queue:add(skipmap{[math.random()]=math.random()})
	end
	while queue:size()>1 do
		local slist1 = queue:remove()
		local slist2 = queue:remove()
		queue:add(slist1:merge(slist2))
	end
	local mergesorted = queue:remove()
	assert(mergesorted:size()==N)
	assert(ordering.isSorted(mergesorted))
end
apidoc[SkipMap.test] = doc


return SkipMap
]],
Vector=[[
local sano = require 'sano'
local sequences = sano.sequences
local oop = sano.oop
local iter = sano.iter
local utils = sano.utils


local Vector = {}

Vector.documentation = {}

local apidoc = Vector.documentation

apidoc.general = beginlongstringliteral
 	Vector - Table implementation of a general purpose vector. Provides 
	constant time random access and worst-case linear time insertions and 
	deletions in the middle of the list.
	
	Indices in the vector start at 1 go to size(), and can be negative. 
	Negative indices count backward from the end of the vector: get(-1) 
	returns the last element, get(-2) returns the second to last element, 
	get(-size()) returns the first element, etc. Any method that accepts 
	index arguments can use negative indexing. Indexing modes can be mixed 
	in the same call, i.e. iter(1,-2) will iterate from the first element to 
	the second to last element.
	
	nil values can be stored in a Vector, although nil values will cause
	the iterators returned by this class to halt prematurely, since Lua 
	interprets nil being returned as signaling the end of the iteration.
	
	Note that the elements of a Vector are stored directly in the Vector 
	object itself, so a Vector can be treated like a vanilla Lua table.
	For example, the following code is all valid:
	
	> v = Vector:make(1,2,3,4,5,6); v:shuffle(); print(v)
	[1, 2, 6, 4, 3, 5]
	> table.sort(v); print(v)
	[1, 2, 3, 4, 5, 6]
	> for _,e in ipairs(v) do print(e) end                                    
	1
	2
	3
	4
	5
	6
	> -- raw indexing - no range checking or negative index translation
	> print(v[1], v[-1])
	1       nil

endlongstringliteral

local doc -- tmp var to store documentation strings

doc = beginlongstringliteral
	Vector:new(array) - Creates a new Vector from array or returns an empty
	  Vector if array is nil.

endlongstringliteral
function Vector:new(obj)
	obj = obj or {}
	obj.mSize = table.getn(obj) or 0
	if obj.mSize == 0 then for i in ipairs(obj) do obj.mSize = obj.mSize + 1 end end
	setmetatable(obj, self)
	self.__index = oop.loadOnFirstUse(self)
	return obj
end
apidoc[Vector.new] = doc

doc = beginlongstringliteral
	Vector:make(...) - Creates and returns a new Vector containing the
	  elements passed as varargs to this method.

	If only one argument is specified, it is assumed to be iterable and the
	elements of the iteration are added to a newly constructed Vector. 
	Otherwise, if there is more than one argument, each argument is added
	to a newly constructed Vector.
	
	Thus, Vector:make(1,2,3) == Vector:make{1,2,3}.

endlongstringliteral
function Vector:make(...)	
	if table.getn(arg) == 1 then
		local v = self:new(); v:addAll(arg[1]); return v
	else
		return self:new(arg)
	end
end
apidoc[Vector.make] = doc

local function checkRange(self, ind)
	if not ind then error("Index is null",3) end
	if ind < 1 or ind > self.mSize then error("Index: "..ind.." out of range,"
	  .." list size is: "..self.mSize,3) end
end

local function translate(self, index)
	assert(self.mSize)
	index = index or self.mSize
	if index < 0 then return self.mSize+1+index else return index end
end

doc = beginlongstringliteral
	Vector:get(index) - Returns the element at the 1-based position index in 
	  the Vector.
	
	Indices may be negative, with -1 retrieving the last element, -2 the
	second-to-last element, and -size() retrieving the first element.
	param index: in +/-[1..size()]
	error: if index not in valid range

endlongstringliteral
function Vector:get(ind)
	ind = translate(self,ind);
	return self[ind]
end
apidoc[Vector.get] = doc

doc = beginlongstringliteral
	Vector:set(index, value) - Sets the element at the 1-based position 
	  index equal to value and returns the previous value stored there.
	  
	Indices may be negative, with -1 setting the last element, -2 the
	second-to-last element, and -size() the first element.
	param index: in +/-[1..size()]
	
	Example:
	> v = Vector:make(1,2,3,4)
	> print(v:set(2, "hello world!"))
	2
	> print(v)
	[1, hello world!, 3, 4]

endlongstringliteral
function Vector:set(ind, val)
	ind = translate(self, ind); checkRange(self, ind)
	local old = self[ind]; self[ind] = val; return old
end
apidoc[Vector.set] = doc

doc = beginlongstringliteral
	Vector:add(value, [index]) - Inserts value AFTER position index, or
	  appends it onto the end of the Vector if index is not specified.
	
	param index: in +/-[0..size()]
	
	Example:
	> v = Vector:make(1,2,3,4)
	> v:add(-1, 0); v:add(5, -1); print(v)
	[-1, 1, 2, 3, 4, 5]
	> v:add(2.5, 3); print(v)
	[-1, 1, 2, 2.5, 3, 4, 5]

endlongstringliteral
function Vector:add(val, ind)
	ind = translate(self,ind)
	if ind < 0 or ind > self.mSize then error("Index: "..ind.." out of range,"
	  .." list size is: "..self.mSize,2) end
	self.mSize = self.mSize + 1
	table.insert(self, ind+1, val)
	return true
end
apidoc[Vector.add] = doc

doc = beginlongstringliteral
	Vector:remove([index]) - Removes and returns the element at index or 
	  the last element of the Vector if no index is specified.

endlongstringliteral
function Vector:remove(ind)
	ind = translate(self,ind); checkRange(self,ind)
	self.mSize = self.mSize - 1
	return table.remove(self, ind)
end
apidoc[Vector.remove] = doc

doc = beginlongstringliteral
	Vector:iter([start, [stop, [step] ]) - Returns an iterator over the
	  range [start..stop] (inclusive on both ends) with the step size
	  defaulting to +1.
	
	This method uses iter.count(start, stop, step) to count off the indices
	being iterated over. Thus:
	
	If no parameters are specified, the iteration will be from 1..size(),+1
	If one parameter, p, is specified, the iteration will be from 1..p,+1
	If two parameters p1 and p2 are specified, the iteration will be over
	  p1..p2,+1
	If three parameters, p1, p2, p3 are specified, the iteration will be
	  over p1..p2,+p3
	
	Examples:
	> v = Vector:make(1,2,3,4,5)
	> print( Vector:make(v:iter()) )
	[1, 2, 3, 4, 5]
	> print( Vector:make(v:iter(-1,1,-1)) )
	[5, 4, 3, 2, 1]
	> print( Vector:make(v:iter(2,4) )
	[2, 3, 4]
	> print( Vector:make(v:iter(-1, 2, -2) )
	[5, 3]
	> print( Vector:make(v:iter(4, 3)) ) -- empty ranges are allowed
	[]

endlongstringliteral
function Vector:iter(start, stop, step)
	if self:size() == 0 then return iter.EMPTY end
	if not stop then stop = translate(self,start); start = 1
	else start = translate(self,start); stop = translate(self,stop) end
	checkRange(self,start); checkRange(self,stop)
	return coroutine.wrap(function()
		for ind in iter.count(start, stop, step) do
			coroutine.yield(self[ind])
		end
	end)
end
apidoc[Vector.iter] = doc

doc = beginlongstringliteral
	Vector:asString(separator) - Concatenates all elements of the Vector
	  into a string and returns it.
	
	Vectors can thus be used as string buffers. This method just wraps
	table.concat().

endlongstringliteral
function Vector:asString()
	return table.concat(self, separator)
end
apidoc[Vector.asString] = doc

doc = beginlongstringliteral
	Vector:unpack() - Returns all of the elements of this Vector.

	This method just wraps the built-in function unpack.

endlongstringliteral
function Vector:unpack() return unpack(self) end
apidoc[Vector.unpack] = doc

doc = beginlongstringliteral
	Vector:size() - Returns the number of elements in this Vector.
	
	This is also the largest valid index for calls to get, set.

endlongstringliteral
function Vector:size() return self.mSize end
apidoc[Vector.size] = doc

doc = beginlongstringliteral
	Vector:__eq(other) - Returns true if self and other are the same size
	  and both contain the same elements in the same order.

endlongstringliteral
function Vector:__eq(other) 
	return self:size() == other:size() and iter.equals(self:iter(),other:iter())
end
apidoc[Vector.__eq] = doc

Vector.__call = Vector.get

doc = beginlongstringliteral
	Vector:test() - Unit test.

endlongstringliteral
function Vector:test()
	local vector = oop.methodCaller(Vector, "make")
	local N = 10
	local v = vector(iter.count(N))
	assert( v:size() == N )
	assert( v(1) == 1 )
	assert( v(-1) == N )
	assert( v:remove() == N )
	assert( v:set(1,0) == 1 )
	assert( v:get(1) == 0 )
	assert( v:size() == N-1 )
	assert( v:remove(1) == 0 )
	assert( v:remove(1) == 2 )
	assert( v:size() == N-3 )
	assert( v:add(2,0) )
	assert( v:add(1,0) )
	assert( v:add(N) )
	assert( v:size() == N )
	--print(v)
	
	assert( v == vector(v:iter(1,-1)))
	assert( v ~= vector(v:iter(1,-2)))
	assert( vector(v:iter(1,3)) == vector(v:iter(1,3)))
	--print(vector(v:iter(-1,1,-1)))
end
apidoc[Vector.test] = doc
-- add some convenience methods

oop.fallback(Vector, sequences.mixins.plauralizeHelper)
oop.fallback(Vector, sequences.mixins.convenienceMethods)
oop.fallback(Vector, sequences.mixins.randomAccessHelper)


return Vector
]],
iter=[[local sano = require 'sano'
local utils = sano.utils
local oop = sano.oop
---

local iter = {}
setmetatable(iter,iter)

local doc
iter.documentation = {}
local apidoc = iter.documentation

apidoc.general = beginlongstringliteral
	iter - A collection of useful functions for working with iterators and
	  iterable objects.
	
	There are two contracts which are used extensively throughout the Sano
	library: the *iterable* contract and the *map-iterable* contract. 
	
	An object, a, satisfies the *iterable* contract if:
	1) It is an iterator function, for instance: a = iter.count(10)
	2) It is an object with an 'iter' method: a = Vector:make{1,2,3,4,5} 
	3) It is a vanilla Lua table, for example: a = {1,2,3,4,5}
	4) It is a string, i.e. a = "hello world!"
	
	Nearly all library functions which operate on sequences of elements 
	will work for any object which satisfies the iterable contract. For
	example, the function ordering.first takes as input an iterable object
	and returns the element that would appear first if the iterable object
	were to be sorted:
	
	> a1 = iter.count(5,1,-1)
	> a2 = {4,3,5,2,1}
	> a3 = Vector:make(4,3,5,2,1)
	> a4 = "fhazqe"
	> print(ordering.first(a1), ordering.first(a2), 
	>>      ordering.first(a3), ordering.first(a4))
	1       1       1       a
	
	As another example, the SkipSet value constructor can take any iterable
	as an argument:	
	> a = SkipSet:make(iter.count(5,1,-1))
	> print(a)
	{1, 2, 3, 4, 5}
	> print(SkipSet:make(a))
	{1, 2, 3, 4, 5}
	> print(SkipSet:make{1,2,3,4,5})
	{1, 2, 3, 4, 5}
	> print(SkipSet:make("fhaayejvbad"))
	{"a", "b", "d", "e", "f", "h", "j", "v", "y"}
	
	An object, a, satisfies the *map-iterable* contract if:
	1) It is an iterator function which returns two values per 
	   step: iter.zip("abc",{1,2,3})
	2) It is an object whose 'iter' method returns a function satisfying 1.
	3) It is a vanilla Lua table, for instance a = {a=1,b=2,c=3}

	Any function which expects a map-iterable object will work for any
	object which satisfies the map-iterable contract. For example, the
	HashMap value constructor can take any map-iterable as an argument:
	
	> a = HashMap:make {a=1,b=2,c=3}; print(a)
	{a=1, c=3, b=2}
	> print( HashMap:make(a) ) -- make a copy of a
	{a=1, c=3, b=2}
	> print( HashMap:make(iter.zip("abc",{1,2,3})) )
	{a=1, c=3, b=2}
	

endlongstringliteral

doc = beginlongstringliteral
	iter(obj) - Returns an iterator extracted from the supplied object. 
	
	There are four cases, each of which is handled differently:
	1) If obj is a function, this function assumes it is an iterator 
	   and returns it. 
	2) If obj is a string, this function returns an
	   iterator over the characters of the string. 
	3) If obj is a table with an 'iter' field, this function returns
	   obj:iter().
	4) If obj is a table without an 'iter' field, this function returns an
	   iterator over the values of the table, using ipairs()
	
	So, the loop, for e in iter(a) do ... end, works so long as a is one
	of the types handled by the above cases.
	
	synonyms: iter.from

endlongstringliteral
function iter.from(obj)
	if type(obj) == "function" then return obj
	elseif type(obj) == "table" then
		if obj.iter then return obj:iter()
		else return utils.tableVals(obj) end
	elseif type(obj) == "string" then
		return utils.charIter(obj)
	else
		error("Unrecognized type: "..type(obj)..", cannot extract iterator")
	end
end

function iter.__call(_, obj) return iter.from(obj) end
apidoc[iter.__call] = doc
apidoc[iter.from] = doc

doc = beginlongstringliteral
	iter.mapIter(obj) - Extracts a 2-value iterator from obj.
	
	obj may be:
	
	1) a function (iter.zip("abc",{123}), in which case obj is returned
	   directly
	2) an object with an 'iter' method, in which case obj:iter() is
	   returned
	3) a Lua table {a=1, b=2, c=3}, in which case an iteration over the
	   key-value pairs of the table is returned.
	

endlongstringliteral
function iter.mapIter(obj)
	if type(obj) == "function" then return obj 
	elseif obj.iter then return obj:iter() 
	else return coroutine.wrap(function()
		for k,v in pairs(obj) do coroutine.yield(k,v) end
	end)
	end
end
apidoc[iter.mapIter] = doc

doc = beginlongstringliteral
	iter.make(func) - Creates an iterable from the supplied function, or
	  simply returns func if it is already a object with an iter method.

endlongstringliteral
function iter.make(func)
	if type(func)=="table" and func.iter then return func
	else return {iter=function() return func() end} end
end
apidoc[iter.make] = doc

doc = beginlongstringliteral
	iter.EMPTY - An empty iteration.

endlongstringliteral
iter.EMPTY = function() return nil end
apidoc[iter.EMPTY] = doc

doc = beginlongstringliteral
	iter.resume(iterator) 

endlongstringliteral
function iter.resume(iterator)
	local first
	if pcall(function() for i in iterator do first = i; break; end end) then
		if first ~= nil then return iter.chain({first}, iter)
		else return iter.EMPTY end
	else
		return iter.EMPTY
	end
end
apidoc[iter.resume] = doc

doc = beginlongstringliteral
	iter.noCycle(iterator)

endlongstringliteral
function iter.noCycle(iterator)
	local done = false
	return function()
		if not done then
			local e = iterator()
			done = e == nil
			return e
		end
	end
end
apidoc[iter.noCycle] = doc

doc = beginlongstringliteral
	iter.chain(...) - Returns an iterator that is the chaining together of 
	  each iterable passed as an argument to this function.
	
	If only one argument is passed to this function, it is assumed to be an
	iterable over iterables.
	
	Examples: 
		chain{ {1,1,1},{2},{3} } --> 1, 1, 1, 2, 3
		chain( {1,1,1}, {2}, {3} ) --> 1, 1, 1, 2, 3
		chain(iter.count(1,3), iter.count(3,6)) --> 1, 2, 3, 3, 4, 5, 6
		

endlongstringliteral
function iter.chain(...)
	local iterables = table.getn(arg)==1 and iter(arg[1]) or iter(arg)
	return coroutine.wrap(function()
		for i in iterables do
			for e in iter(i) do coroutine.yield(e) end
		end
	end)
end
apidoc[iter.chain] = doc

-- iter.flatten

doc = beginlongstringliteral
	iter.unary(element) - Returns an 'iterator' over a single element.
	
	The returned function will, when first called, return the element, and
	on subsequent calls return nil.

endlongstringliteral
function iter.unary(element)
	local returned = false
	return function()
		if not returned then
			returned = true; return element
		end
	end
end
apidoc[iter.unary] = doc

doc = beginlongstringliteral
	iter.map(iterable, transform) - Returns an iteration in which each 
	  element e, in iterable, is replaced by transform(e).
	
	The mapping is 'lazy', so the transform of an element is not applied
	until that element is requested by the iteration.
	
	Example:
	iter.map({1,2,3,4}, math.sqrt) --> sqrt(1), sqrt(2), sqrt(3), sqrt(4)
	

endlongstringliteral
function iter.map(iterable, transformer)
	local i = iter.from(iterable)
	return function()
		local e = i()
		if e ~= nil then return transformer(e) end
	end
end
apidoc[iter.map] = doc

doc = beginlongstringliteral
	iter.mapN(n, transform) - Returns the result of 
	  iter.map(iter.count(n), transform).
	
	Example:
	iter.mapN(5, function(x) return x*x end) --> 1, 4, 9, 16, 25
	

endlongstringliteral
function iter.mapN(n, transformer)
	return iter.map(iter.count(n), transformer)
end
apidoc[iter.mapN] = doc

doc = beginlongstringliteral
 	iter.filter(iterable, filter [,removed]) - Returns an iteration
	  containing only the elements of iterable for which filter returns 
	  true.
	
	For each element, e which is removed from the returned iteration, 
	removed(e) is called if removed is specified. The filtering is 'lazy',
	so the filtered iteration is not determined until the returned
	iteration is exhausted.
	
	Example:
	function isEven(n) return math.mod(n,2)==0 end
	iter.filter(count(10), isEven) --> 2, 4, 6, 8, 10
	

endlongstringliteral
function iter.filter(iterable, filter, removed)
	local i = iter.from(iterable)
	return coroutine.wrap(function()
		for e in i do
			if e == nil or filter(e) then coroutine.yield(e)
			elseif removed then removed.add(e) end
		end
	end)
end
apidoc[iter.filter] = doc

doc = beginlongstringliteral
	iter.openFilter(filter)

endlongstringliteral
function iter.openFilter(filter)
	return function(iterable)
		return iter.filter(iterable, filter)
	end
end
apidoc[iter.openFilter] = doc

doc = beginlongstringliteral
	iter.mapFilter(iterable, transform)

endlongstringliteral
function iter.mapFilter(iterable, map)
	local i = iter(iterable)
	return coroutine.wrap(function()
		for e in i do
			local t = map(e)
			if t == true then coroutine.yield(e)
			elseif t then coroutine.yield(t) end
		end
	end)
end
apidoc[iter.mapFilter] = doc

doc = beginlongstringliteral
	iter.pipe(...)

endlongstringliteral
function iter.pipe(...)
	-- beautiful!
	return function(source)
		return iter.fold(arg, iter.mapFilter, source) 
	end
end
apidoc[iter.pipe] = doc

doc = beginlongstringliteral
	iter.pipeIter(source, ...)

endlongstringliteral
function iter.pipeIter(source, ...)
	return iter.pipe(arg)(source)
end
apidoc[iter.pipeIter] = doc

doc = beginlongstringliteral
 	iter.fold(iterable, fn [, seed, thirdArg]) - Also sometimes called 
	  'reduce' in functional programming contexts. 
	
	fn is a binary function. Label the elements of iterable i1, i2...iN.
	fold calls fn(i1, i2) and passes the result to fn(_, i3), then passes
	the result of that to fn(_, i4) and so on. The result of the last call
	to fn(_, iN) is this function's return value.
	
	Some examples:
	> function sum(t) return iter.fold(t, function(a,b) return a+b end) end
	> print(sum{1,2,3,4,5})
	15
	>  -- compute the smallest element in an iterable
	>  function first(iterable, orderFn)
	>>   return iter.fold(iterable, orderFn or ordering.INCREASING)
	>> end
	> v = vector(iter.count(100)):shuffle()
	> print(first(v), first(v,ordering.DECREASING))
	1       100
	
	param func: a binary function
	param seed: the first element supplied to the folding process; if nil
	  defaults to the first element in iterable
	param thirdArg: passed as the third argument to func at each stage of 
	  the fold

endlongstringliteral
function iter.fold(iterable, func, seed, thirdArg)
	iterable = iter(iterable)
	seed = seed or iterable()
	for i in iterable do
		seed = func(seed, i, thirdArg)
	end
	return seed
end
apidoc[iter.fold] = doc

doc = beginlongstringliteral
	iter.foldIter(iterable, func [, seed, thirdArg]) - Same as fold, except
	  that intermediate values are returned as an iterator.
	
	Example:
		add = function(a,b) return a+b end
		sums = iter.toList(iter.foldSeq({1,2,3,4,5}, add)) --> [3,6,10,15]

endlongstringliteral
function iter.foldIter(iterable, func, seed, thirdArg)
	iterable = iter(iterable)
	seed = seed or iterable()
	return coroutine.wrap(function()
		for i in iterable do seed = func(seed, i, thirdArg); coroutine.yield(seed) end
	end)
end
apidoc[iter.foldIter] = doc

doc = beginlongstringliteral
	iter.autofolding(func, seed, thirdArg) - Turn any binary function into 
	  a function which operates on a variable number of arguments, by 
	  cascading the results.
	
	Example: 
	  function add(x,y) return x + y end
	  sum = autofolding(add)
	  print(sum(1,2,3,4)) --> 10
	  print(sum(iter.count(4)) --> 10
	

endlongstringliteral
function iter.autofolding(func, seed, thirdArg)
	return function(...)
		return iter.fold((table.getn(arg)>1 and arg or arg[1]), func, seed, thirdArg)
	end
end
apidoc[iter.autofolding] = doc

doc = beginlongstringliteral
	iter.groupEq(iterable, equalsFn) - Returns an iterator over lists of 
	   adjacent elements that are equal according to the supplied equals 
	   function (default uses '=='). 
	
	Example:
	iter.groupEq({1,1,2,2,3,3,3,4,5}) --> {1,1}, {2,2}, {3,3,3}, {4}, {5}
	

endlongstringliteral
function iter.groupEq(iterable, equalsFn)
	local i = iter.from(iterable)
	local equals = equalsFn or function(x,y) return x==y end
	if type(equals) == "number" then equals = negation(everyNth(equals)) end
	return coroutine.wrap(function()
		local buf = {}
		for e in i do
			local size = table.getn(buf)
			if size == 0 or equals(e, buf[size]) then
				table.insert(buf, e)
			else
				coroutine.yield(buf)
				buf = {e,n=1}
			end
		end
		-- flush anything left in the buffer
		if table.getn(buf) ~= 0 then coroutine.yield(buf) end
	end)
end
apidoc[iter.groupEq] = doc

doc = beginlongstringliteral
 	iter.collapseEq(iterable, equalsFn) - Returns an iterator in which 
	  adjacent duplicate elements are removed.

endlongstringliteral
function iter.collapseEq(iterable, equalsFn)
	return iter.map(iter.groupEq(iterable, equalsFn), function(t) return t[1] end)
end
apidoc[iter.collapseEq] = doc

doc = beginlongstringliteral
	iter.interleave(iter1, iter2) - Returns an iteration which alternates 
	   between returning elements from iter1 and iter2. 
	
	The iteration halts when either iterator runs out of elements.
	
	example: interleave({'a','b','c'}, {1,2,3}) --> a, 1, b, 2, c, 3
	

endlongstringliteral
function iter.interleave(iter1, iter2)
	return coroutine.wrap(function()
		local z = iter.zip(iter1, iter2)
		for i,j in z do
			coroutine.yield(i); coroutine.yield(j)
		end
	end)
end
apidoc[iter.interleave] = doc


doc = beginlongstringliteral
	iter.loop(iterable [,times]) - Returns an iterator which cycles the 
	  iterable through times repetitions, or forever, if times is nil.

endlongstringliteral
function iter.loop(iterable, times)
	return coroutine.wrap(function()
		local elements = {}
		for i in iter.from(iterable) do
			coroutine.yield(i); table.insert(elements,i)
		end
		if times then
			for k=1,times-1 do 
				for i in iter.from(elements) do coroutine.yield(i) end 
			end
		else
			while true do 
				for i in iter.from(elements) do coroutine.yield(i) end
			end
		end
	end)
end
apidoc[iter.loop] = doc

doc = beginlongstringliteral
	iter.repeatCall(n, fn, ...) - Returns an iterator which calls the
	  function fn n times with the arguments (...).
	
	Example:
	> -- generate a vector of random ints, all in [1..5]
	> print( vector(iter.repeatCall(10, math.random, 1, 5)) )
	[3, 3, 1, 3, 5, 3, 4, 1, 4, 3]
	

endlongstringliteral
function iter.repeatCall(n, fn, ...)
	local s = 0
	return function()
		if s < n then
			s = s+1
			return fn(unpack(arg))
		end
	end
end
apidoc[iter.repeatCall] = doc		

doc = beginlongstringliteral
	iter.restartable(iterable)

endlongstringliteral
function iter.restartable(iterable)
	return coroutine.wrap(function()
		local elements = {}
		for i in iter(iterable) do
			coroutine.yield(i); table.insert(elements,i)
		end
		repeat 
			coroutine.yield(nil)
			for _,i in ipairs(elements) do coroutine.yield(i) end 
		until false
	end)
end
apidoc[iter.restartable] = doc

doc = beginlongstringliteral
	iter.reverse(iterable) - Returns an iterator that yields elements in 
	  the reverse order of iterable.
	
	This requires allocating a stack to store all the elements in the
	iteration.

endlongstringliteral
function iter.reverse(iterable)
	local t = {}
	for i in iter.from(iterable) do table.insert(t,i) end
	return function() if table.getn(t) > 0 then return table.remove(t) end end
end
apidoc[iter.reverse] = doc

doc = beginlongstringliteral
	iter.buffer()

endlongstringliteral
function iter.buffer()
	local buf, first, size = {}, 1, 0
	function buf.__call()
		if size > 0 then
			local t = buf[first]; first = first + 1; size = size - 1; return t
		end
	end
	function buf.iter() return function() return buf() end end
	function buf.add(element) table.insert(buf, element); size = size + 1 end
	function buf.addAll(elements) for i in iter.from(elements) do buf.add(i) end end
	function buf.size() return size end
	function buf.unpack() 
		local t = {}; for i=first,first+size-1 do table.insert(t,buf[i]) end
		return unpack(t)
	end
	function buf.clear() while size > 0 do buf() end end
	setmetatable(buf, buf)
	return buf
end
apidoc[iter.buffer] = doc

doc = beginlongstringliteral
	iter.partialWindows(iterable, size)

endlongstringliteral
local function partialWindows(iterable, size)
	return coroutine.wrap(function()
		local buf = iter.buffer()
		for i in iter.from(iterable) do
			buf.add(i)
			if buf.size() > size then buf() end 
			coroutine.yield(buf.unpack())
		end
	end)
end

doc = beginlongstringliteral
	iter.windows(iterable, size) - Returns an iteration in which each step
	  returns size elements in a 'moving window' of the iteration.

	Example:
	> for lead,follow in iter.windows(vector(1,2,3,4,5), 2) do 
	>>  print(lead, follow) end
	1       2
	2       3
	3       4
	4       5
	

endlongstringliteral
function iter.windows(iterable, size)
	return iter.trim(partialWindows(iterable, size), size-1)
end
apidoc[iter.windows] = doc

doc = beginlongstringliteral
	iter.everyNth(iterable, n)

endlongstringliteral
function iter.everyNth(iterable, n)
	return coroutine.wrap(function()
		for ind, e in iter.enum(iterable) do
			if math.mod(ind-1, n)==0 then
				coroutine.yield(e)
			end
		end
	end)
end
apidoc[iter.everyNth] = doc

doc = beginlongstringliteral
	iter.cross(...)

endlongstringliteral
function iter.cross(iterable1, iterable2)
	iterable1 = type(iterable1)=="number" and iter.count(iterable1) or iterable1
	iterable2 = type(iterable2)=="number" and iter.count(iterable2) or iterable2
	local iter2 = iter.restartable(iterable2)
	return coroutine.wrap(function()
		while true do
			for i in iter.pack(iter(iterable1)) do
				for j in iter2 do
					local c = {}
					utils.tableInsertAll(c, i)
					table.insert(c,j)
					coroutine.yield(unpack(c))
				end
			end
			coroutine.yield(nil)
		end
	end)		
end

iter.cross = iter.autofolding(iter.cross)
apidoc[iter.cross] = doc

doc = beginlongstringliteral
	iter.pairs(iterable)

endlongstringliteral
function iter.pairs(iterable)
	local elements = {}
	for e in iter(iterable) do table.insert(elements,e) end
	local count = table.getn(elements)
	return coroutine.wrap(function()
		for i=1,count do
			for j=i+1,count do
				coroutine.yield({elements[i], elements[j]})
			end
		end
	end)
end
apidoc[iter.pairs] = doc

--function iter.pairs(iterable) return iter.subsets(iterable, 2) end
--function iter.trios(iterable) return iter.subsets(iterable, 3) end

doc = beginlongstringliteral
	iter.preorder(roots, nodeExtractor, filter, queue)

endlongstringliteral
function iter.preorder(roots, nodeExtractor, filter, queue)
	filter = filter or function(unused) return true end
	if not queue then
		queue = require("sano").Vector:new()
	end
	return coroutine.wrap(function()
		local cur 
		for n in iter(roots) do queue:add(n) end
		while queue:size() > 0 do
			cur = queue:remove()
			if filter(cur) then 
				coroutine.yield(cur)
				for c in iter(nodeExtractor(cur)) do queue:add(c) end
			end
		end
	end)
end
apidoc[iter.preorder] = doc

doc = beginlongstringliteral
	iter.trim(iterable, count) - Returns an iterator which ignores the 
	  first count elements of iterable.

endlongstringliteral
function iter.trim(iterable, headJunk)
	iterable = iter.from(iterable)
	for i=1,headJunk do iterable() end
	return iterable
end
apidoc[iter.trim] = doc

doc = beginlongstringliteral
	iter.last(iterable) - Returns the last element in the iteration over 
	  iterable.
	
	The iterator is exhausted by this call.

endlongstringliteral
function iter.last(iterable)
	local e
	for i in iter.from(iterable) do e = i end
	return e
end
apidoc[iter.last] = doc

local function firstK(iterable, k)
	local i = iter(iterable)
	return coroutine.wrap(function()
		for i=1,k do coroutine.yield(i()) end
	end)
end

doc = beginlongstringliteral
	iter.first(iterable [,k]) - Returns the first element of iterable if k
	  is unspecified, or an iterator over the first k elements.

endlongstringliteral
function iter.first(iterable, k)
	return not k and iter.from(iterable)() or firstK(iterable, k)
end
apidoc[iter.first] = doc

doc = beginlongstringliteral
	iter.equals(iter1, iter2, equalsFn) - Returns true if both iterations 
	  return the same number of elements and each pair of corresponding 
	  elements are equal according to equalsFn (default '==').

endlongstringliteral
function iter.equals(iter1, iter2, equalityFn)
	equalityFn = equalityFn or function(x,y) return x==y end
	local iter1, iter2 = iter.from(iter1), iter.from(iter2)
	for i,j in iter.zip(iter1, iter2) do
		if not equalityFn(i,j) then return false end
	end
	-- now make sure that both iterators have been exhausted
	local i1, i2 = iter.resume(iter1), iter.resume(iter2) 
	local a,b = i1(), i2()
	if a==nil and b==nil then return true
	elseif not equalityFn(a, b) then return false
	else return true end
end
apidoc[iter.equals] = doc

doc = beginlongstringliteral
	iter.multiEquals(iter1, iter2) - Compares two iterators that return 
	  multiple values for equality.
	  
	The two iterators are equal if both halt after the same number of steps
	and if, for each iteration step, both return the same values in the same 
	order.

endlongstringliteral
function iter.multiEquals(iter1, iter2)
	return iter.equals(iter.pack(iter1), iter.pack(iter2), utils.tableEquals)
end
apidoc[iter.multiEquals] = doc

doc = beginlongstringliteral
	iter.toList(iterable [, collector]) - Converts an iterable to a Lua
	  table.
	
	If collector is specified, collector:add(i) is called for each i in
	iter(iterable) and collector is returned.

endlongstringliteral
function iter.toList(iterator, collector)
	if collector then
		for i in iter(iterator) do collector:add(i) end
		return collector
	else
		local t = {}
		for i in iter(iterator) do table.insert(t,i) end
		return t
	end
end
apidoc[iter.toList] = doc

doc = beginlongstringliteral
	iter.zip(...) - 'Zips' multiple iterations up into one, similar to the 
	  built-in python 'zip' function. 
	
	When any one of the iterations returns nil, that iteration is removed 
	from the zip and the zip halts. If resumed, the zip then returns 
	elements from the remaining live iterators.
	
	Example:
	  a,b = count(5), count(10)
	  z = zip(a,b)
	  for i,j in z do print(i,j) end --> 1,1  2,2  3,3  4,4  5,5
	  for i in z do print(i) end  --> 6  7  8  9  10

endlongstringliteral
function iter.zip(...)
	if table.getn(arg) == 1 then
		arg = iter.toList(arg[1])
	end
	for k,v in ipairs(arg) do arg[k] = iter.from(v) end
	local n = table.getn(arg)
	return coroutine.wrap(function()
		while true do
			local tuple = {}
			for i=1,n do
				if arg[i] then -- iterable will be nil if it has been exhausted
					local e = arg[i]() -- generate next element
					if e == nil then coroutine.yield(nil, arg[i]); arg[i] = nil; 
					else table.insert(tuple, e) end
				end
			end
			coroutine.yield(unpack(tuple))
		end
	end)
end
apidoc[iter.zip] = doc

doc = beginlongstringliteral
	iter.pack(iterable) - Returns an iterator which packs the result(s) of
	  each iteration step of iter(iterable) into a table.
	
	Example:
	iter.pack(iter.mapIter{a=1,b=2,c=3}) will yield {a,1}, {b,2}, {c,3}

endlongstringliteral
function iter.pack(iter)
	return function()
		local e = {iter()}
		if e[1] ~= nil then return e end
	end
end
apidoc[iter.pack] = doc

doc = beginlongstringliteral
	iter.unpack(iterable) - Equivalent to iter.map(iterable, unpack)

endlongstringliteral
function iter.unpack(iter)
	return iter.map(iter, unpack)
end
apidoc[iter.unpack] = doc

doc = beginlongstringliteral
	iter.count(start, stop, step) - Returns an iterator which counts off
	  successive integers in the range [start..stop] with stepsize step. 
	
	This function has several defaults:
	
	+) If no parameters are provided, this iteration will return 1,2,3,etc up 
	   to infinity and beyond.
	+) If one param p is provided, start defaults to 1 and stop becomes p,
	   and the iteration returned is [1..p],+1. 
	   (The notation [a..b],+1 indicates the iteration a, a+1, a+2,..., b.)
	+) If two params p1, p2 are provided, start becomes p1, stop becomes p2,
	   and the iteration returned is [p1..p2],+1.
	+) If three params p1, p2, p3 are provided, the iteration returned is
	   [p1..p2],+p3. (p3 may be negative)
	
	If start > stop and step is positive, the iteration returned will be 
	EMPTY. Likewise if start < stop and step is negative. Thus there is no
	way to accidentally construct a count iterator which does not 
	terminate (these semantics are much like the Lua numeric for loop).
	
	Examples:
	  count(4) --> 1  2  3  4
	  count(-4) --> empty
	  count(1,-4) --> empty
	  count(2,4) --> 2  3  4
	  count(4,2) --> empty 
	  count(1,10,2) --> 1  3  5  7  9
	  count(10,1,-2) --> 10  8  6  4  2

endlongstringliteral
function iter.count(start, stop, step)
	if not start then -- count from 1 to infinity
		local i = 0; return function() i = i+1; return i end
	elseif not stop then -- count from 1 to first param
		start, stop, step = 1, start, 1
	else start, step = start or 1, step or 1 end -- count from start to stop
	local delta = math.abs(stop-start)
	if step > 0 and stop < start or
	   step < 0 and stop > start then 
		return iter.EMPTY 
	end
	return coroutine.wrap(function()
		while math.abs(start-stop+step) < delta do
			coroutine.yield(start)
			start = start + step; 
			delta = math.abs(stop-start);
		end
		coroutine.yield(start)
	end)
end
apidoc[iter.count] = doc

doc = beginlongstringliteral
	iter.enum(iterable [, size, stop, step]) - Decorates another iteration 
	  to return the index of each element returned from the decorated 
	  iteration.
	
	If iterable is an object with a size() method, or if size is explicitly
	specified, the iteration will halt after iterable:size() steps.
	Otherwise, the iteration halts as soon as iter(iterable) halts (this
	may be undesireable if the iteration is expected to contain nil values)
	
	If 2 args are given the indices will range from 1 to arg[2]. 
	If 3 args are given the second and third arguments are interpreted as a
	counting range for the enumeration; 
	If a 4th argument is specified, args 2 and 3 specify 
	the counting range, and arg 4 specifies the step increment.
	
	Examples: 
	> for ind,v in vector("abcd"):enum() do print(ind,v) end
	1       a
	2       b
	3       c
	4       d
	> vec = vector("abc"); vec:add(nil)
	> for ind,v in vec:enum() do print(ind, v) end
	1       a
	2       b
	3       c
	4       nil

endlongstringliteral
function iter.enum(iteration, size, stop, step)
	if type(iteration)=="table" and iteration.size then 
		size = iteration:size()
	end
	if not size then return iter.zip(iter.count(),iter.from(iteration)) end
	iteration = iter.from(iteration)
	local counter
	if not stop then counter = iter.count(size) 
	else counter = iter.count(size,stop,step) end
	return function()
		local ind = counter()
		return ind, ind and iteration() -- don't destroy an element from iteration
		                                -- if indexing has been exhausted
	end
end
apidoc[iter.enum] = doc

doc = beginlongstringliteral
	iter.exchange(iterable) - Swaps the order that multiple values are 
	  returned by two-element iterator.
	
	Example: 
	-- i = 1,a  2,b  3,c 
	iter.exchange(i) --> a,1  b,2  c,3
	

endlongstringliteral
function iter.exchange(iteration)
	return function() local i,j = iteration(); return j,i end
end
apidoc[iter.exchange] = doc

doc = beginlongstringliteral
	iter.find(iterable, predicate) - Returns the first object, e from 
	  iterable for which predicate(e) returns true, or nil if there are no 
	  matches.

endlongstringliteral
function iter.find(iterable, predicate)
	for i in iter.from(iterable) do
		if predicate(i) then return i end
	end
end	
apidoc[iter.find] = doc

doc = beginlongstringliteral
	iter.toString(iterable [,delimiter]) - Returns a comma+space-delimited 
	  string of the elements in iterable, or uses the delimiter if one is
	  provided.
	
	Example:
	> print(iter.toString(iter.count(3)))
	1, 2, 3

endlongstringliteral
function iter.toString(iterable, delimiter)
	local tab = {}
	for i in iter.from(iterable) do 
		if type(i)=="string" then
			i = '\"'..i..'\"'
		end
		table.insert(tab, tostring(i)) 
	end
	return table.concat(tab, delimiter or ", ")
end
apidoc[iter.toString] = doc

doc = beginlongstringliteral
	iter.serialize(iterable, deserializeName)

endlongstringliteral
function iter.serialize(iterable, deserializeName)
	return deserializeName..'('..iter.toString(iterable)..')'
end
apidoc[iter.serialize] = doc

doc = beginlongstringliteral
	iter.transpose(iterable)

endlongstringliteral
function iter.transpose(iterable)
	local rows = {}
	local cols
	for r in iter(iterable) do
		table.insert(rows, r)
		cols = r:size()
	end
	return coroutine.wrap(function()
		for i=1,cols do
			coroutine.yield(coroutine.wrap(function()
				for r in iter(rows) do
					coroutine.yield(r:get(i))
				end
			end))
		end
	end)
end
apidoc[iter.transpose] = doc

doc = beginlongstringliteral
	iter.pairsToString(iterable) - Returns a comma-delimted string of the 
	  key-value pairs in iterable; each pair of items (a,b) returned by 
	  iterable will be denoted by a=b in the returned string.
	
	Example:
	> print(iter.pairsToString(iter.zip("abc",{1,2,3})))
	a=1, b=2, c=3
	

endlongstringliteral
function iter.pairsToString(iterable)
	local tab = {}
	for a,b in iterable do
		if type(b)=="string" then
			b = '\"'..b..'\"'
		end
		table.insert(tab, tostring(a).."="..tostring(b))
	end
	return table.concat(tab, ", ")
end
apidoc[iter.pairsToString] = doc

return iter
]],
Multiset=[[
local sano = require 'sano'
local sets = sano.sets
local iter = sano.iter
local collections = sano.collections
local oop = sano.oop
local SkipMap = sano.SkipMap

local Multiset = {documentation={}}
local apidoc = Multiset.documentation
local doc

apidoc.general = beginlongstringliteral
	Multiset - Collection in which duplicate elements are not explicitly
	  stored.
	
	A Multiset is implemented as a map in which the keys are the elements
	of the Multiset and the value stored against key is a number denoting
	the quantity (or multiplicity) of that element. When an element is
	added to a multiset, its quantity is incremented by 1.
	
	Multisets are handy for efficiently processing collections of objects
	which are expected to contain a large number of duplicates. For
	instance, to sort a list of one million integers in the range 1..5:
	
	> m = Multiset:new(SkipMap) 
	> m:addAll(iter.repeatCall(1e6, math.random, 1, 5))
	> print(m)
	{1^199981, 2^199613, 3^200520, 4^200379, 5^199507}
	> sorted = vector(m:iter())
	
	The constructor takes an optional map implementation, which defaults to
	SkipMap (thus it wasn't neccessary to supply SkipMap to the constructor
	above).
	
	Computing the sum of the numbers in a Multiset is also efficient:
	
	> sum = 0
	>  for e, quantity in m:uniqueIter() do
	>>   sum = sum + (e*quantity)
	>> end
	
	Other operations, such as union and intersection, can be computed more
	efficiently by Multisets by taking advantage of implicit storage of 
	duplicates.

endlongstringliteral

doc = beginlongstringliteral
	Multiset.cDefaultMapType - The default backing map for this Multiset,
	  initially SkipMap.

endlongstringliteral
Multiset.cDefaultMapType = SkipMap
apidoc[Multiset.cDefaultMapType] = doc
 
doc = beginlongstringliteral
	Multiset:new([map]) - Returns a new Multiset, using map as the backing
	  map, or Multiset.cDefaultMapType (initially SkipMap) if map is 
	  unspecified.
	
	There are not separate 'classes' for each different backing map type;
	instead, the newly returned multiset can be used to create other 
	multisets with the same default backing map, for instance:
	> HashMultiset = Multiset:new(HashMap)
	> multi = HashMultiset:make(1,2,2,3,4)

endlongstringliteral
function Multiset:new(map)
	local obj = {}
	map = map or self.cDefaultMapType
	obj.cDefaultMapType = map
	setmetatable(obj, self)
	self.__index = oop.loadOnFirstUse(self)
	obj.mQuantities = map:new()
	obj.mSize = 0
	return obj
end
apidoc[Multiset.new] = doc

doc = beginlongstringliteral
 	Multiset:__tostring() - Returns a string representation of this 
	  Multiset. 
	
	{a^3, b^1, e^12} signifies a multiset with 3 copies of element a, 1 copy 
	of element b, and 12 copies of element e.

endlongstringliteral
function Multiset:__tostring()
	local buf = sano.makers.vector()
	for e, quantity in self:uniqueIter() do
		e = type(e)==string and '"'..e..'"' or tostring(e)
		buf:add(e); buf:add("^"..quantity); buf:add(", ")
	end
	if buf:size() > 0 then buf:remove() end -- remove dangling comma
	return "{"..table.concat(buf).."}"
end
apidoc[Multiset.__tostring] = doc

doc = beginlongstringliteral
	Multiset:add(element [,quantity]) - Adds quantity copies of element to
	  this Multiset, default 1.

	param element: the element to add
	param quantity: the number of copies to add, defaults to 1
	returns: the updated total number of copies of element in this multiset

endlongstringliteral
function Multiset:add(element, quantity)
	quantity = quantity or 1
	if quantity < 0 then return self:remove(element, -quantity) end
	local newQuantity = (self.mQuantities:get(element) or 0) + quantity
	self.mQuantities:add(element, newQuantity)
	self.mSize = self.mSize + quantity
	return newQuantity
end
apidoc[Multiset.add] = doc

doc = beginlongstringliteral
 	Multiset:addAll(iterable) - Adds all elements in collection to this 
	  Multiset. 
	
	If iterable is also a Multiset, this takes time O(u), where u is the 
	number of unique elements in iterable. Otherwise, this takes O(n), 
	where n is the total number of elements in iterable.

endlongstringliteral
function Multiset:addAll(iterable)
	if oop.isInstance(iterable, Multiset) then
		for e, quantity in iterable:uniqueIter() do
			self:add(e, quantity)
		end
	else
		collections.addAll(self, iterable)
	end
	return self
end
apidoc[Multiset.addAll] = doc

doc = beginlongstringliteral
 	Multiset:remove(element, quantity) - Remove quantity copies of element 
	  from this Multiset, default 1.
	
	param element: the element to remove
	param quantity: the number of copies to remove, defaults to 1
	returns: element and the actual number of copies that were removed
	example: {a^3, b^6}:remove(b, 32) will return b, 6

endlongstringliteral
function Multiset:remove(element, quantity)
	local curQuantity = (self.mQuantities:get(element) or 0)
	local numRemoved = math.min(quantity or 1, curQuantity)
	if numRemoved ~= curQuantity then
		self.mQuantities:add(element, curQuantity - numRemoved)
	else
		self.mQuantities:remove(element)
	end
	self.mSize = self.mSize - numRemoved
	return element, numRemoved
end
apidoc[Multiset.remove] = doc


doc = beginlongstringliteral
 	Multiset:removeEntirely(element) - Removes all copies of element from 
	  this Multiset.
	
	This function is equivalent to calling 
	remove(element, getQuantity(element))

endlongstringliteral
function Multiset:removeEntirely(element)
	return self:remove(element, self:getQuantity(element))
end

doc = beginlongstringliteral
 	Multiset:removeAll(iterable) - Removes all elements in iterable from 
	this Multiset, respecting quantity.
	
	Thus, if self={a^4, b^5, e^11} and iterable={a^2, b^3}, after a call to
	self:removeAll(iterable), self would be {a^2, b^2, e^11}. To remove all
	elements entirely, ignoring multiplicity, use removeAllEntirely().
	
	If collection is also a Multiset, this runs in O(u), where u is the 
	number of unique elements in iterable. Otherwise, this takes time O(n), 
	where n is the total number of elements in iterable.

endlongstringliteral
function Multiset:removeAll(iterable)
	if oop.isInstance(iterable, Multiset) then
		for e, quantity in iterable:uniqueIter() do
			self:remove(e, quantity)
		end
	else
		sets.removeAll(self, iterable)
	end
end
apidoc[Multiset.removeAll] = doc

doc = beginlongstringliteral
 	Multiset:removeAllEntirely(iterable) - Remove all copies of elements in 
	iterable that exist in this Multiset.

endlongstringliteral
function Multiset:removeAllEntirely(iterable)
	if oop.isInstance(iterable, Multiset) then
		for e in iterable:uniqueIter() do self:removeEntirely(e) end
	else
		for e in iter(iterable) do self:removeEntirely(e) end
	end
end
apidoc[Multiset.removeAllEntirely] = doc

doc = beginlongstringliteral
 	Multiset:retainOnly(iterable) - Modifies this Multiset so that it is 
	  the intersection (respecting quantity) of itself and iterable. 
	  
	If iterable is also a Multiset, this takes time O(u*c), where u is the
	number of unique items in this Multiset and c is the time for a lookup
	in the backing map. If not, then it takes O(n + u*c), where n is the 
	number of elements in iterable.

endlongstringliteral
function Multiset:retainOnly(iterable)
	if oop.isInstance(iterable, Multiset) then
		for e, quantity in self:uniqueIter() do
			self:setQuantity(e, math.min(quantity, iterable:getQuantity(e)))
		end
	else
		iterable = Multiset:make(iterable)
		self:retainOnly(iterable)
	end
end
apidoc[Multiset.retainOnly] = doc

doc = beginlongstringliteral
	Multiset:intersectionCount(otherMultiset) - Efficiently computes the
	  number of overlapping elements between this Multiset and 
	  otherMultiset.

endlongstringliteral
function Multiset:intersectionCount(otherMultiset)
	local total = 0
	for e, quantity in self:uniqueIter() do 
		total = total + math.min(quantity, otherMultiset:getQuantity(e))
	end
	return total
end
apidoc[Multiset.intersectionCount] = doc


doc = beginlongstringliteral
	Multiset:xorCount(other) - Returns the total number of unshared elements
	  between this Multiset and otherMultiset.

endlongstringliteral
function Multiset:xorCount(other)
	return self:size()+other:size()-self:intersectionCount(other)
end
apidoc[Multiset.xorCount] = doc


doc = beginlongstringliteral
 	Multiset:setQuantity(element, quantity) - Sets the number of copies of 
	  element that are stored in this Multiset and returns the old quantity.

endlongstringliteral
function Multiset:setQuantity(element, quantity)
	local oldQuantity = self:getQuantity(element)
	self.mSize = self.mSize + (quantity-oldQuantity)
	if quantity == 0 then self.mQuantities:remove(element)
	else self.mQuantities:add(element, quantity) end
	return oldQuantity
end
apidoc[Multiset.setQuantity] = doc

doc = beginlongstringliteral
 	Multiset:getQuantity(element) - Retrieves the number of copies of 
	  element in this Multiset, or 0 if the element does not exist.

endlongstringliteral
function Multiset:getQuantity(element)
	return self.mQuantities:get(element) or 0
end
apidoc[Multiset.getQuantity] = doc


doc = beginlongstringliteral
 	Multiset:contains(element) - Returns whether there are > 0 copies of 
	  element in this Multiset.

endlongstringliteral
function Multiset:contains(element)
	return self.mQuantities:get(element) ~= nil
end
apidoc[Multiset.contains] = doc


doc = beginlongstringliteral
	Multiset:size() - Returns the total number of elements in this Multiset.
	
	This is also the total number of elements that would be returned by
	a call to iter().
	
	Example: {a^3, b^7, c^8} has size 3+7+8=18 and uniqueSize=3 

endlongstringliteral
function Multiset:size() 
	return self.mSize 
end
apidoc[Multiset.size] = doc

doc = beginlongstringliteral
	Multiset:uniqueSize() - Returns the total number of unique elements in
	  this Multiset.
	  
	This is also the total number of elements that would be returned by a 
	call to uniqueIter().
	
	Example: {a^3, b^7, c^8} has uniqueSize of 3 (and size of 3+7+8=18)

endlongstringliteral
function Multiset:uniqueSize()
	return self.mQuantities:size()
end
apidoc[Multiset.uniqueSize] = doc

doc = beginlongstringliteral
 	Multiset:uniqueIter([start, stop]) - Returns an iterator over the unique 
	  set of elements in this Multiset. 
	
	The iteration order is determined by the iteration order of the map used 
	to implement this Multiset. Each call to the iterator return a pair 
	e,quantity, where e is a unique element in this Multiset, and quantity 
	is the number of copies of e in the Multiset.
	
	param start, stop: these are passed as params to the iterator of the
	  underlying map
	endlongstringliteral
function Multiset:uniqueIter(start, stop)
	return self.mQuantities:iter(start, stop)
end
apidoc[Multiset.uniqueIter] = doc

doc = beginlongstringliteral
 	Multiset:iter([start, stop]) - Returns an 'uncompressed' iterator over 
	  the elements in this Multiset, taking quanitity into account.
	
	The optional start and stop parameters are passed to the backing map.

endlongstringliteral
function Multiset:iter(start, stop)
	return coroutine.wrap(function()
		for element, n in self.mQuantities:iter(start, stop) do
			for i=1,n do coroutine.yield(element) end
		end
	end)
end
apidoc[Multiset.iter] = doc


doc = beginlongstringliteral
	Multiset:__eq(other) - Returns true if both Multisets have the same
	  unique set of elements and the same quantities for each of these
	  elements.

endlongstringliteral
function Multiset:__eq(other)
	return self:size()==other:size() and self.mQuantities == other.mQuantities
end
apidoc[Multiset.__eq] = doc

oop.fallback(Multiset, sets.mixins.convenienceMethods)
oop.fallback(Multiset, sets.mixins.plauralizeHelper)

doc = beginlongstringliteral
	Multiset:test() - Unit test.

endlongstringliteral
function Multiset:test()
	local multiset = function(...) return self:make(arg) end
	
	local m1 = multiset(5,4,3,2,1)
	assert(m1==multiset(1,2,3,4,5))
	assert(m1:getQuantity(5)==1)
	assert(m1:size()==5)
	assert(m1:remove(1)==1)
	assert(m1:size()==4)
	m1:add(1,100)
	assert(m1:size()==104)
	local _, onesQuantity = m1:remove(1, 1000)
	assert(onesQuantity==100)
	
	local m2 = multiset()
	m2:addAll(iter.repeatCall(500, math.random, 1, 10))
	m2:removeAll(iter.repeatCall(100, math.random, 1, 10))
	m2:retainOnly(iter.repeatCall(100, math.random, 1, 10))
	
end
apidoc[Multiset.test] = doc


return Multiset
]],
utils=[[
local apidoc = {}
local utils = {documentation=apidoc}
local doc 

apidoc.general = beginlongstringliteral
	utils - A collection of miscellaneous utility functions.

endlongstringliteral

doc = beginlongstringliteral
	utils.key(obj) - Extracts a hash key from obj.

	If obj is not a table, it is simply returned.
	If obj is a table whose metatable, mt, has a __hash metamethod, 
	mt.__hash(obj) is returned.
	
	This is method is used to generate hash keys for HashMap and HashSet.

endlongstringliteral-- return the key to be used for this type of object in a table
function utils.key(obj)
	if type(obj)~="table" then
		return obj
	else
		local t = getmetatable(obj)
		if t and t.__hash then return t.__hash(obj) end
		return obj
	end
end
apidoc[utils.key] = doc

doc = beginlongstringliteral
	utils.hash(obj) - Returns a numeric hash of obj.
	
	If obj is a number, it is simply returned.
	
	If obj is a table with a __hash metamethod, that method is called with
	obj as an argument and the result is returned.
	
	If obj is a table with no __hash metamethod, this function returns
	utils.hashString(tostring(obj)).
	
	For all other cases, this function returns 
	utils.hashString(tostring(obj)).

endlongstringliteral
function utils.hash(obj)
	if type(obj)=="number" then 
		return obj
	elseif type(obj)=="table" then
		local t = getmetatable(obj)
		if t and t.__hash then return t.__hash(obj)
		else return utils.hashString(tostring(obj)) end
	else
		return utils.hashString(tostring(obj))
	end
end
apidoc[utils.hash] = doc

doc = beginlongstringliteral
	utils.hashString(str) - Returns a numeric hash of the input string.
	
	This method accumulates the hash value by iterating, for each index 
	[1..string.len(str)], hash = hash*prime + string.byte(str, i).
	
	If hash exceeds some large prime, it is reduced to 
	math.mod(hash, largePrime) and accumulation continues.

endlongstringliteral
function utils.hashString(str)
	local hash = string.len(str)
	-- these primes are arbitrary
	local prime, largePrime = 7, 8000000269
	for i=1,string.len(str) do
		hash = hash*prime + string.byte(str, i)
		hash = hash < largePrime and hash or math.mod(hash, largePrime)
	end
	return hash
end
apidoc[utils.hashString] = doc

doc = beginlongstringliteral
	utils.hashIterable(iterable) - Returns a numeric hash of the input
	  iterable.
	
	This method accumulates the hash value by iterating, for each
	e in iter(iterable), hash = hash*prime + utils.hash(e).
	
	If hash exceeds some large prime, it is reduced to
	math.mod(hash, largePrime) and accumulation continues.
	
	The primes chosen for this function were selected arbitrarily.

endlongstringliteral
function utils.hashIterable(iterable)
	local prime, largePrime = 7, 8000000269
	local hash, hashFn = 0, utils.hash
	local iter = require('sano').iter
	for e in iter(iterable) do
		hash = hash*prime + hashFn(e)
		hash = hash < largePrime and hash or math.mod(hash, largePrime)
	end
	return hash
end
apidoc[utils.hashIterable] = doc

utils.Nil = {}
local Nil = utils.Nil

function utils.size(obj)
	if type(obj) == "table" then
		if obj.size then return obj:size()
		else return table.getn(obj) end
	elseif type(obj) == "string" then
		return string.len(obj)
	else
		error("Cannot extract size from unrecognized type: "..tostring(obj)
		..".\nThis function only works for tables, collections, and strings")
	end
end

function utils.tableVals(atable)
	return coroutine.wrap(function()
		while true do
			for _,v in ipairs(atable) do coroutine.yield(v,_) end
			coroutine.yield(nil) -- allows restarting
		end
	end)
end

function utils.tableEquals(t1,t2)
	for k,v in pairs(t1) do
		if t2[k] ~= v then return false end
	end
	for k,v in pairs(t2) do
		if t1[k] ~= v then return false end
	end
	return true
end

function utils.tableCopy(t)
	local copy = {}
	for k,v in pairs(t) do copy[k] = v end
	if getmetatable(t) then setmetatable(copy,getmetatable(t)) end
	return copy
end

function utils.tableInsertAll(t,t2)
	for e in utils.tableVals(t2) do table.insert(t,e) end
end

local function maskNil(maybeNil) return maybeNil == nil and Nil or maybeNil end

local function unmaskNil(maybeNil) return maybeNil ~= Nil and maybeNil or nil end

utils.maskNil = maskNil
utils.unmaskNil = unmaskNil

-- returns a comma-delimited string of the elements in iterable
local function iterToString(iterable)
	local tab = {}
	for i in iterable do table.insert(tab, tostring(i)) end
	return table.concat(tab, ", ")
end

local function memoizedKnownArgs(func, numArgs)
	local results = {}
	if numArgs == 1 then
		return function(...)
			local t = results[arg[1endlongstringliteral
			if t == nil then
				t = func(arg[1])
				results[arg[1endlongstringliteral = t
			end
			return t
		end
	else
		error("only works for memoizing 1 param functions")
	end
end

-- returns a memoized version of the function; the memoized values are 
-- contained in the upvalues of the returned function
function utils.memoized(func, numArgs)
	if numArgs then return memoizedKnownArgs(func, numArgs) end
	local results = {}
	setmetatable(results, {__mode="v"}) -- weak values
	return function(...)
		local s,t = iterToString(utils.tableVals(arg))
		t = results[s]
		if t == nil then 
			t = func(unpack(arg))
			results[s] = t
		end
		return t
	end
end

function utils.charIter(str)
	local pos = 0
	return function()
		pos = pos + 1
		if pos <= string.len(str) then
			return string.sub(str,pos,pos)
		end
	end
end

return utils
]],
LinkedList=[[
local sano = require 'sano'
local oop = sano.oop
local sequences = sano.sequences
local iter = sano.iter
----

local apidoc = {}
local doc
local LinkedList = {documentation=apidoc}
local function LNode(val, prev, next)
	return {val=val, prev=prev, next=next}
end

apidoc.general = beginlongstringliteral
	LinkedList - Basic doubly linked list implementation.
	
	LinkedList can be used as a FIFO queue: add() by default appends to the 
	end of the list (enqueue) and remove() removes by default from the 
	front of the list (dequeue). (get() by default returns the element 
	that would be removed next via a call to remove() and operates like a 
	'peek' method)
	
	The nodes of the list are tables with fields "val", "next", and "prev".
	
	It IS safe to modify a LinkedList while iterating, for example:
	
	> a = LinkedList:make(iter.count(8))
	> for node in a:nodeIter() do -- filter out even integers
	>>  if math.mod(node.val, 2)==0 then 
	>>    a:remove(node) 
	>>  end
	>> end
	> print(a)
	[1, 3, 5, 7]
	
	Elements that are added while iterating are never seen by the iteration,
	even when they are added after the current node via a call to addAfter,
	for example:
	
	> a = LinkedList:make(iter.count(5))
	> for n in a:nodeIter() do
	>>  print(n.val)
	>>  a:addAfter("between", n)
	>> end
	1
	2
	3
	4
	5
	> print(a)
	[1, between, 2, between, 3, between, 4, between, 5, between]

	Also see QueueVector, SkipVector.

endlongstringliteral

doc = beginlongstringliteral
	LinkedList:new() - Returns a new empty LinkedList.

endlongstringliteral
function LinkedList:new()
	local obj = {}
	setmetatable(obj, self)
	obj.mHead = LNode()
	obj.mTail = LNode()
	obj.mHead.next, obj.mTail.prev = obj.mTail, obj.mHead
	assert(obj.mTail.prev)
	obj.mSize = 0
	self.__index = oop.loadOnFirstUse(self)
	return obj
end
apidoc[LinkedList.new] = doc

local function computeSize(self)
	local count = 0
	for e in self:nodeIter() do count = count + 1 end
	self.mSize = count
	return count
end	

local function insertAfter(before, at)
	local after = before.next
	before.next, at.next, at.prev, after.prev = at, after, before, at
	return at
end

local function insertBefore(after, at)
	assert(after.prev)
	return insertAfter(after.prev, at)
end

local function remove(at)
	local before, after = at.prev, at.next
	before.next, after.prev = after, before
	return at
end

doc = beginlongstringliteral
	LinkedList:add(val [,beforeNode]) - Adds val before the supplied node,
	  or at the end of the LinkedList if no second parameter is specified.
	
	returns: the added node, which can be passed to a call to remove
	
	synonyms: LinkedList.addBefore

endlongstringliteral
function LinkedList:add(val, beforeNode)
	self.mSize = self.mSize + 1
	return insertBefore(beforeNode or self.mTail, LNode(val))
end
apidoc[LinkedList.add] = doc

doc = beginlongstringliteral
	LinkedList:addAfter(val [,afterNode]) - Adds val AFTER the given node,
	  or at the end of the LinkedList if no second parameter is specified.

endlongstringliteral
function LinkedList:addAfter(val, afterNode)
	self.mSize = self.mSize + 1
	return insertAfter(afterNode or self.mTail.prev, LNode(val))
end
apidoc[LinkedList.addAfter] = doc

LinkedList.addBefore = LinkedList.add

doc = beginlongstringliteral
	LinkedList:remove(node) - Removes node from this LinkedList.
	
	Nodes can be accessed via a call to nodeIter, firstNode,
	  lastNode, or add:
	> -- assuming a == [1,2,3,4,5,6,7,8]
	> for node in a:nodeIter() do -- filter out even ints
	>>  if math.mod(node.val, 2) == 0 then a:remove(node) end
	>> end
	> print(a)
	[1, 3, 5, 7]

endlongstringliteral
function LinkedList:remove(node)
	local t
	if self.mSize > 0 then 
		t = remove(node or self.mHead.next)
		self.mSize = self.mSize - 1
		return t.val
	end
end
apidoc[LinkedList.remove] = doc

doc = beginlongstringliteral
	LinkedList:removeElement(val) - Removes the first-encountered instance
	  of val from this LinkedList.
	
	Returns val if it was removed successfully, otherwise nil.

endlongstringliteral
function LinkedList:removeElement(val)
	for n in self:nodeIter() do
		if n.val == val then return self:remove(n) end
	end
end
apidoc[LinkedList.removeElement] = doc


function LinkedList:prepend(val) return self:addAfter(val, self.mHead) end
function LinkedList:append(val) return self:add(val) end

doc = beginlongstringliteral
	LinkedList:removeFirst() - Removes the first element of this list and
	  returns it.
	
	If this list is empty then an error is thrown.

endlongstringliteral
function LinkedList:removeFirst()
	return self:size() > 0 and self:remove(self.mHead.next) or error("Cannot remove from empty LinkedList")
end
apidoc[LinkedList.removeFirst] = doc

doc = beginlongstringliteral
	LinkedList:removeLast() - Removes the last element  of this list and
	  returns it.
	
	If this list is empty then this method throws an error.

endlongstringliteral
function LinkedList:removeLast() 
	return self:size() > 0 and self:remove(self.mTail.prev) or error("Cannot remove from empty LinkedList")
end
apidoc[LinkedList.removeLast] = doc

doc = beginlongstringliteral
	LinkedList:get([node]) - Gets the value being stored at node, or the
	  first element in this LinkedList.

endlongstringliteral
function LinkedList:get(node)	return (node or self.mHead.next).val end
apidoc[LinkedList.get] = doc


function LinkedList:firstNode() if self.mSize > 0 then return self.mHead.next end end
function LinkedList:lastNode() if self.mSize > 0 then return self.mTail.prev end end

doc = beginlongstringliteral
	LinkedList:first() - Returns the first element in this LinkedList.
	
	If this list is empty, this method returns nil.
	
	Example:
	> a = LinkedList:make(4,3,2,1); print(a:first())
	4

endlongstringliteral
function LinkedList:first() if self.mSize > 0 then return self.mHead.next.val end end
apidoc[LinkedList.first] = doc

doc = beginlongstringliteral
	LinkedList:last() - Returns the last element of this LinkedList.
	
	If this list is empty, this method returns nil.
	
	Example:
	> a = LinkedList:make(4,3,2,1); print(a:last())
	1

endlongstringliteral
function LinkedList:last() if self.mSize > 0 then return self.mTail.prev.val end end
apidoc[LinkedList.last] = doc

doc = beginlongstringliteral
	LinkedList:set(node, val) - Equivalent to performing node.val = val.
	
	val is returned by this call.

endlongstringliteral
function LinkedList:set(node, val) node.val = val; return val end
apidoc[LinkedList.set] = doc

doc = beginlongstringliteral
	LinkedList:size() - Returns the number of elements in this LinkedList.
	
	The first size()-call after a call to LinkedList:splice() will take 
	O(size()) to compute. In all other cases it takes constant time.

endlongstringliteral
function LinkedList:size() return self.mSize or computeSize(self) end
apidoc[LinkedList.size] = doc

doc = beginlongstringliteral
	LinkedList:iter() - Returns an iterator over the elements of this
	  LinkedList.
	
	The number of elements in the iteration will be equal to self:size().

endlongstringliteral
function LinkedList:iter()
	local n = self.mHead
	return function() 
		n = n.next
		return n and n.val or nil 
	end
end
apidoc[LinkedList.iter] = doc

doc = beginlongstringliteral
	LinkedList:reverseIter() - Returns an iterator over the elements of this
	  LinkedList in reverse order.

endlongstringliteral
function LinkedList:reverseIter()
	local n = self.mTail
	return function() 
		n = n.prev
		return n and n.val
	end
end
apidoc[LinkedList.reverseIter] = doc

doc = beginlongstringliteral
	LinkedList:nodeIter([start [, stop] ]) - Returns an iterator over the
	  nodes of this LinkedList.
	  
	The nodes returned by this iteration can be passed to a call to remove()
	addBefore(), or addAfter().

endlongstringliteral
function LinkedList:nodeIter(start, stop)
	start, stop = start or self.mHead.next, stop or self.mTail
	return coroutine.wrap(function() 
		repeat 
			local t = start.next
			coroutine.yield(start)
			start = t
		until start == stop
	end)
end
apidoc[LinkedList.nodeIter] = doc

doc = beginlongstringliteral
	LinkedList:splice(startNode, endNode) - Removes the nodeRange
	  [startNode..endNode] (inclusive both sides) and returns it as a list.
	
	The returned list will have as its first element startNode.val and as
	its last element endNode.val.

endlongstringliteral
function LinkedList:splice(startNode, endNode)
	local before, after = startNode.prev, endNode.next
	before.next, after.prev = after, before
	local l = LinkedList:new()
	l.mHead.next, startNode.prev = startNode, l.mHead
	l.mTail.prev, endNode.next = endNode, l.mTail
	self.mSize = nil
	l.mSize = nil
	return l
end
apidoc[LinkedList.splice] = doc

doc = beginlongstringliteral
	LinkedList:join(otherList [,afterNode]) - Inserts the LinkedList
	  otherList after the supplied node or concatenates it onto the end if
	  no insert position is specified.
	
	otherList is invalidated as a result of this call.
	returns: self (to allow chaining -- a:join(b):join(c):join(d))

endlongstringliteral
function LinkedList:join(otherList, afterNode)
	afterNode = afterNode or self.mTail.prev
	local before = afterNode
	local after = afterNode.next
	local newsize = self:size()+otherList:size()
	local jfirst, jlast = otherList.mHead.next, otherList.mTail.prev
	before.next, after.prev, jfirst.prev, jlast.next = 
	  otherList.mHead.next, otherList.mTail.prev, before, after
	self.mSize = newsize
	oop.invalidate(otherList, LinkedList)
	return self
end
apidoc[LinkedList.join] = doc

doc = beginlongstringliteral
	LinkedList:__eq(other) - Returns true if both LinkedLists have the same
	  size and contain the same elements in the same order.

endlongstringliteral
function LinkedList:__eq(other)
	return self:size()==other:size() and iter.equals(self, other)
end
apidoc[LinkedList.__eq] = doc

doc = beginlongstringliteral
	LinkedList:test() - Unit test.

endlongstringliteral
function LinkedList:test()
	local link = function(...) return self:make(unpack(arg)) end
	local a = link(1,2,3,4)
	local b = link(5,6,7,8)
	assert(a:join(b) == link(iter.count(8))); assert(a:size()==8)
	assert(a:remove()==1); assert(a:removeLast()==8)
	assert(a:size()==6)
	assert(a:prepend(1)); assert(a:append(8))
	assert(a:size()==8)
	local c = link(2.1,2.2,2.3,2.4)
	a:join(c, a.mHead.next.next)
	assert(a == link(1, 2, 2.1, 2.2, 2.3, 2.4, 3, 4, 5, 6, 7, 8))
	local innerList = a:splice(a.mHead.next.next, a.mTail.prev.prev)
	assert(innerList == link(2,2.1,2.2,2.3,2.4,3,4,5,6,7))
	assert(a:size()==2); assert(a:first()==1); assert(a:last()==8)
end
apidoc[LinkedList.test] = doc

oop.fallback(LinkedList, sequences.mixins.convenienceMethods)
oop.fallback(LinkedList, sequences.mixins.plauralizeHelper)


return LinkedList
]],
HashMap=[[
local sano = require "sano"
local utils = sano.utils
local oop = sano.oop
local maps = sano.maps
local ordering = sano.ordering
local iter = sano.iter
local Vector = sano.Vector
local key = utils.key


local HashMap = {documentation = {}}
local apidoc = HashMap.documentation
local doc

apidoc.general = beginlongstringliteral
	HashMap - Table-based implementation of a map with support for
	  user-defined hash functions and nil values.
	
	On a call to HashMap:add(key,val), HashMap checks for the existence of
	getmetatable(key).__hash. If it exists, key:__hash() is called and the
	result is used to select a bucket for the entry (key=val). If it does
	not exist, the default Lua hashing function is used to determine the
	bucket for the entry.
	
	For instance, The Tuple class overrides __hash and __eq, so HashMap can 
	be used as a multi-key map by making the keys tuples:
	> h = HashMap:new()
	> tuple = sano.makers.tuple
	> h:add(tuple(1,1,2), tuple(3,4))
	...
	> print( h:get(tuple(1,1,2)) )
	(3, 4)
	
	If h were a regular Lua table, the two instances of tuple(1,1,2) would
	not be recognized as being equivalent:
	> h = {}
	> h[tuple(1,1,2)] = tuple(3,4)
	...
	> print( h[tuple(1,1,2)] )
	nil
	
	If the __hash metamethod hashes distinct keys to the same bucket, then 
	all keys which are different according to '==' are retained in the 
	HashMap. If Tuple used a bad hash function and by chance tuple(1,2,3) 
	and tuple(3,2,1) hashed to the same value, both would still be retained 
	as keys since tuple(1,2,3) ~= tuple(3,2,1).
	
	Mutable objects should not be changed when they are being used as keys 
	in a HashMap, as HashMap has no way of knowing when keys change and will
	not know to rehash the changed key.
	
	More examples of usage:
	> h = HashMap:new(); h:add("Jones","Phil"); print(h)
	{Jones="Phil"}
	> print(h:get("Jones"))
	Phil
	> h:add("Smith","Andrea"); h:add("Doe","John")
	> for k,v in h:iter() do print(k,v) end
	Smith   Andrea
	Jones   Phil
	Doe     John
	> for k,v in h:orderedIter() do print(k,v) end -- entries sorted by key
	Doe     John
	Jones   Phil
	Smith   Andrea
	> print(h:add("Jones","Frankie")) -- override previous value
	Phil
	> print(h)
	{Smith="Andrea", Jones="Frankie", Doe="John"}
	> print(h:size()) -- prints the number of entries in the map
	3

endlongstringliteral

local emptyVector = Vector:new()
local mt = {__index = function(t,k) return emptyVector end}

doc = beginlongstringliteral
	HashMap:new() - Creates and returns a new, empty HashMap.

endlongstringliteral
function HashMap:new()
	obj = {}
	obj.mMap = {}
	obj.mNativeMap = {}
	setmetatable(obj, self)
	setmetatable(obj.mMap, mt)
	self.__index = oop.loadOnFirstUse(self)
	obj.mSize = 0
	return obj
end
apidoc[HashMap.new] = doc

local function isNative(obj)
	local mt = getmetatable(obj)
	return not mt or not mt.__hash
end

local Nil = {}
local function maskNil(obj) return obj~=nil and obj or Nil end
local function unmaskNil(obj) return obj~=Nil and obj or nil end

local function get(self, keyobj)
	if isNative(keyobj) then
		return self.mNativeMap[keyobj]
	else
		local k = key(keyobj)
		for e in self.mMap[k]:iter() do
			if e[1] == keyobj then return e end
		end
	end
end

doc = beginlongstringliteral
	HashMap:add(key,value) - Adds the key-value pair to this HashMap and
	  returns the previous value stored against key (possibly nil)
	
	value may be nil, but key must be non-nil.
	
	Example:
	> h = hashmap {a=1,b=2}; print(h:add("a",19))
	1

endlongstringliteral
function HashMap:add(keyobj, value)
	assert(key~=nil, "HashMap cannot use nil keys (nil values are okay)")
	--print(keyobj,value,isNative(keyobj),getmetatable(keyobj) and getmetatable(keyobj).__hash)
	if isNative(keyobj) then
		local nativemap = self.mNativeMap
		local old = nativemap[keyobj]
		nativemap[keyobj] = maskNil(value)
		if old==nil then self.mSize = self.mSize+1 end
		return old
	else
		local entry = get(self, keyobj)
		local old
		if entry then 
			old, entry[2] = entry[2], value
		else
			local k = key(keyobj)
			if rawequal(self.mMap[k],emptyVector) then
				self.mMap[k] = Vector:new{{keyobj,value}}
			else
				self.mMap[k]:add({keyobj,value})
			end
		end
		if not entry then 
			self.mSize = self.mSize + 1 
		end
		return old
	end
end
apidoc[HashMap.add] = doc

doc = beginlongstringliteral
	HashMap:get(key) - Returns the value stored against key, or nil if no
	  no such value exists.
	
	If the value of nil is explicitly stored against key, then get(key) will
	return nil and contains(key) will return true.
	
	synonym: __call 
	So, m:get("h") == m("h")

endlongstringliteral
function HashMap:get(keyobj)
	if isNative(keyobj) then return unmaskNil(get(self, keyobj))
	else
		local entry = get(self, keyobj)
		return entry and entry[2] or nil
	end
end
apidoc[HashMap.get] = doc

HashMap.__call = HashMap.get

doc = beginlongstringliteral
	HashMap:contains(key) - Returns true if this map contains a mapping 
	  (possibly nil) for the given key.

endlongstringliteral
function HashMap:contains(keyobj) 
	return get(self, keyobj) ~= nil
end
apidoc[HashMap.contains] = doc

doc = beginlongstringliteral
	HashMap:remove(key) - Removes the entry with key == key from this
	  HashMap and returns the value associated with key.
	
	Example:
	> h = HashMap:make {a=1,b=2}; print(h:remove("a"))
	1
	> print(h)
	{b=2}

endlongstringliteral
function HashMap:remove(keyobj)
	if isNative(keyobj) then
		local old = self.mNativeMap[keyobj]
		if old~=nil then self.mSize = self.mSize-1 end
		self.mNativeMap[keyobj] = nil
		return unmaskNil(old)
	else
		local k = key(keyobj)
		local vec = self.mMap[k]
		local toremove
		for i=1,vec:size() do 
			if vec[i][1]==keyobj then 
				toremove=i; break 
			end 
		end
		if toremove then 
			self.mSize = self.mSize - 1
			return vec:remove(toremove)[2] 
		end
	end
end
apidoc[HashMap.remove] = doc

doc = beginlongstringliteral
	HashMap:iter() - Returns an iteration over all key-value pairs in this
	  map.
	
	Each iteration step returns two values; the first is the key for the 
	current entry, the second is the value for the entry.

endlongstringliteral
function HashMap:iter() 
	return coroutine.wrap(function()
		for key,val in pairs(self.mNativeMap) do
			coroutine.yield(key,unmaskNil(val))
		end
		for key,val in pairs(self.mMap) do
			for j in val:iter() do
				coroutine.yield(j[1],j[2]) 
			end 
		end
	end)
end
apidoc[HashMap.iter] = doc


doc = beginlongstringliteral
	HashMap:orderedIter(orderFn) - Returns an iteration over the entries in 
	  this HashMap, ordered by key.

	orderFn determines how the keys are ordered; default is natural
	increasing order. This method internally makes a copy of the key set
	of this map and sorts it.

endlongstringliteral
function HashMap:orderedIter(orderFn)
	local sortedKeys = ordering.sorted(self:iter(), orderFn or ordering.INCREASING)
	return coroutine.wrap(function()
		for k in iter.from(sortedKeys) do
			coroutine.yield(k, self:get(k))
		end
	end)
end
apidoc[HashMap.orderedIter] = doc

doc = beginlongstringliteral
	HashMap:size() - Returns the number of key-value pairs in this HashMap.
	
	key-value pairs with a nil value are included in the count.

endlongstringliteral
function HashMap:size() return self.mSize end
apidoc[HashMap.size] = doc

doc = beginlongstringliteral
	HashMap:__eq(other) - Returns true if both maps have the same size and
	  contain the same mappings.

endlongstringliteral
function HashMap:__eq(tab2)
	if self:size() ~= tab2:size() then return false
	else
		for k,v in self:iter() do
			if tab2:get(k) ~= v then return false end
		end
	end
	return true
end
apidoc[HashMap.__eq] = doc

oop.fallback(HashMap, maps.mixins.convenienceMethods)
oop.fallback(HashMap, maps.mixins.plauralizeHelper)

doc = beginlongstringliteral
	HashMap:test() - Unit test.

endlongstringliteral
function HashMap:test()
	local hashmap = function(map) return self:make(map) end
	local h1 = hashmap{a=1,b=2,c=3,d=4}
	assert(h1 == hashmap{a=1,b=2,c=3,d=4})
	assert(h1:size()==4)
	assert(h1:get('b'))
	assert(h1:get("b")==2, h1:get('b'))
	assert(h1:add("b",22)==2)
	assert(h1:remove("a")==1)
	assert(iter.equals(h1:orderedIter(), {'b','c','d'}))
	assert(h1:size()==3)
	assert(not h1:add("e",nil))
	assert(h1:size()==4)
	assert(h1:contains("e"))
	assert(h1:get("e")==nil)
	assert(h1:remove("e")==nil)
	-- h1 == {b=22,c=3,d=4}
	
	local tuple = sano.makers.tuple
	local tup = tuple("John","Doe")
	h1:add(tup,12345)
	assert(h1:get(tuple("John","Doe"))==12345)
	assert(h1:remove(tup)==12345)
	assert(not h1:add(tup,nil))
	assert(h1:contains(tup)) -- nil value still counts for contains
	assert(h1:get(tup)==nil)
	assert(h1:size()==4, h1:size())
	assert(h1:remove(tup)==nil)
	assert(h1:size()==3)
end
apidoc[HashMap.test] = doc

return HashMap
]],
LinkedMap=[[
local sano = require 'sano'
local HashMap = sano.HashMap
local LinkedList = sano.LinkedList
local oop = sano.oop
local maps = sano.maps
local iter = sano.iter
----- 
local apidoc = {}
local doc
local LinkedMap = {documentation=apidoc, cDefaultMap=HashMap}

apidoc.general = beginlongstringliteral
	LinkedMap - Map implementation in which iteration order is same as
	  insertion order.
	
	In a regular HashMap (or a plain Lua table), the iteration order is
	dependent on the hash function and can be quite random. This is
	sometimes undesireable. In a LinkedMap, the iteration order of entries
	is the same as the order in which the entries are added to the map.
	
	For example:
	> a = HashMap:make("edcba", iter.count(5,1,-1)); print(a)
	{a=1, c=3, b=2, e=5, d=4}
	> a = LinkedMap:make("edcba", iter.count(5,1,-1)); print(a)
	{e=5, d=4, c=3, b=2, a=1}
	
	Note that the iteration order is determined only by the first point
	when an entry is added. If an entry is subsequently modified, its
	position in the iteration is unchanged unless it is removed then
	reinserted.

endlongstringliteral

doc = beginlongstringliteral
	LinkedMap:new(mapImpl) - Returns a new empty LinkedList.

endlongstringliteral
function LinkedMap:new(mapImpl)
	local obj = {}
	setmetatable(obj, self)
	self.__index = oop.loadOnFirstUse(self)
	obj.mMap = (mapImpl or self.cDefaultMap):new()
	obj.mList = LinkedList:new()
	return obj
end
apidoc[LinkedMap.new] = doc

doc = beginlongstringliteral
	LinkedMap.cDefaultMap - The default backing map for this LinkedMap
	  (initially HashMap).

endlongstringliteral
apidoc[LinkedMap.cDefaultMap] = doc

doc = beginlongstringliteral
	LinkedMap:add(key, val) - Adds the key-value pair to this map and 
	  returns the previous value stored against key (possibly nil). 
	
	If there is already a value stored against key, this function
	is equivalent to a 'set' operation, and has no effect on the iteration
	order. 

endlongstringliteral
function LinkedMap:add(key, val)
	local entry = self.mMap:get(key)
	if entry then 
		local old = entry.val[2]
		entry.val[2] = val
		return old
	else
		self.mMap:add(key, self.mList:add{key,val})
		return nil
	end
end
apidoc[LinkedMap.add] = doc

doc = string.gsub(HashMap.documentation[HashMap.remove], 'HashMap', 'LinkedMap')
function LinkedMap:remove(key)
	local entry = self.mMap:remove(key)
	if entry then
		return self.mList:remove(entry)[2]
	end
end
apidoc[LinkedMap.remove] = doc

doc = string.gsub(HashMap.documentation[HashMap.get], 'HashMap', 'LinkedMap')
function LinkedMap:get(key)
	local t = self.mMap:get(key)
	return t and t.val[2]
end
apidoc[LinkedMap.get] = doc

LinkedMap.__call = LinkedMap.get

doc = string.gsub(HashMap.documentation[HashMap.contains], 'HashMap', 'LinkedMap')
function LinkedMap:contains(key)
	return self.mMap:get(key) ~= nil
end
apidoc[LinkedMap.contains] = doc


doc = beginlongstringliteral
	LinkedMap:iter() - Returns an iterator over the key-value pairs in this
	  map in the same order in which they were inserted.
	
	Each step in the iteration returns two values.

endlongstringliteral
function LinkedMap:iter()
	return coroutine.wrap(function()
		for e in self.mList:iter() do
			coroutine.yield(e[1], e[2])
		end
	end)
end
apidoc[LinkedMap.iter] = doc


doc = string.gsub(HashMap.documentation[HashMap.size], 'HashMap', 'LinkedMap')
function LinkedMap:size()
	return self.mList:size()
end
apidoc[LinkedMap.size] = doc

doc = beginlongstringliteral
	LinkedMap:__eq(other) - Returns true iff maps.orderedEquals(self, other).

endlongstringliteral
function LinkedMap:__eq(other)
	return maps.orderedEquals(self, other)
end
apidoc[LinkedMap.__eq] = doc

doc = beginlongstringliteral
	LinkedMap:test() - Unit test.

endlongstringliteral
function LinkedMap:test()
	local lmap = function(map, vals) return self:make(map, vals) end
	local h1 = lmap("abcd", iter.count())
	assert(h1 == lmap("abcd", iter.count()))
	assert(h1:size()==4)
	assert(h1:get("b")==2, h1:get('b'))
	assert(h1:add("b",22)==2)
	assert(h1:remove("a")==1)
	assert(h1:size()==3)
	assert(not h1:add("e",nil))
	assert(h1:size()==4)
	assert(h1:contains("e"))
	assert(h1:get("e")==nil)
	assert(h1:remove("e")==nil)
	-- h1 == {b=22,c=3,d=4}
	
	local tuple = sano.makers.tuple
	local tup = tuple("John","Doe")
	h1:add(tup,12345)
	assert(h1:get(tuple("John","Doe"))==12345)
	assert(h1:remove(tup)==12345)
	assert(not h1:add(tup,nil))
	assert(h1:contains(tup)) -- nil value still counts for contains
	assert(h1:get(tup)==nil)
	assert(h1:size()==4, h1:size())
	assert(h1:remove(tup)==nil)
	assert(h1:size()==3)
end
apidoc[LinkedMap.test] = doc

oop.fallback(LinkedMap, maps.mixins.convenienceMethods)
oop.fallback(LinkedMap, maps.mixins.plauralizeHelper)


return LinkedMap]],
HashSet=[[
local sano = require "sano"
local collections = sano.collections
local sets = sano.sets
local oop = sano.oop
local Vector = sano.Vector
local iter = sano.iter
local utils = sano.utils
local key = utils.key
local maskNil = utils.maskNil
local unmaskNil = utils.unmaskNil

local apidoc = {}
local doc
local HashSet = {documentation = apidoc}

apidoc.general = beginlongstringliteral
	HashSet - Table-based set implementation with support for user defined
	  hash functions and nil elements. 
		
	Examples:
	> set = oop.methodCaller(HashSet, "make")
	> s = set(iter.count(10))
	> s2 = set(iter.count(5,15))
	> print(s + s2) -- synonym for s:union(s2)
	{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15}
	> print(s - s2)
	{1, 2, 3, 4}
	> print(s:intersection(s2))
	{5, 6, 7, 8, 9, 10}
	> print(s:size())
	10
	> print(s:contains(3), s(3)) -- __call is synonym for contains
	true  true
	> print(set(1,2,3) == set(1,2,3))
	true
	
	HashSets are themselves hashable, so they can be stored in HashSets or
	used as keys in HashMaps, for example:
	
	> s = set(set(1,2), set(2,1), set(3,4))
	> print(s)
	{{1, 2}, {3, 4}}
	
	HashSet can also store nil as an element, although when iterating over
	a HashSet, the nil element will cause the iteration to halt, as Lua uses
	nil to signal the end of an iteration. To work around this, use the enum
	method:
	
	> s = set(1,2); s:add(nil)
	> for _,e in s:enum() do print(e) end
	1
	2
	nil
	
	See HashMap for more information on how the Sano library handles 
	hashing of user defined objects.

endlongstringliteral

local emptyVector = Vector:new()
local mt = {__index = function(t,k) return emptyVector end}

doc = beginlongstringliteral
	HashSet:new() - Creates and returns a new, empty HashSet

endlongstringliteral
function HashSet:new()
	local obj = {}
	obj.mSize = 0
	obj.mSet = {}
	setmetatable(obj.mSet, mt)
	setmetatable(obj, self)
	self.__index = oop.loadOnFirstUse(self)
	return obj
end
apidoc[HashSet.new] = doc

doc = beginlongstringliteral
	HashSet:contains(element) - Returns true if element exists in this set.
	
	Since HashSet can be used to store nil elements, this method returns
	true if element is nil and there has been a prior call to add(nil)
	
	The __call metamethod is a synonym for contains.

endlongstringliteral
function HashSet:contains(element)
	local e = maskNil(element)
	return self.mSet[key(e)]:contains(e)
end
apidoc[HashSet.contains] = doc

HashSet.__call = HashSet.contains

doc = beginlongstringliteral
	HashSet:add(element) - Adds element to this HashSet and returns true
	  if it did not already exist in the set.
	
	nil may be added to a HashSet.

endlongstringliteral
function HashSet:add(element) 
	if self:contains(element) then return false
	else
		local e = maskNil(element)
		local h = key(e)
		if rawequal(self.mSet[h], emptyVector) then
			self.mSet[h] = Vector:new{e}
		else
			self.mSet[h]:add(e)
		end
		self.mSize = self.mSize + 1
		return true 
	end
end
apidoc[HashSet.add] = doc

doc = beginlongstringliteral
	HashSet:remove(element) - Removes and returns the element if it existed
	  in this HashSet; otherwise returns nil.

endlongstringliteral
function HashSet:remove(element)
	if not self:contains(element) then return nil
	else
		local e = maskNil(element)
		self.mSet[key(e)]:removeElement(e)
		self.mSize = self.mSize - 1 
		return element 
	end
end
apidoc[HashSet.remove] = doc

doc = beginlongstringliteral
	HashSet:iter() - Returns an iterator over the elements of this HashSet.

endlongstringliteral
function HashSet:iter()
	return coroutine.wrap(function()
		for key,val in pairs(self.mSet) do 
			for j in val:iter() do
				coroutine.yield(unmaskNil(j)) end end
	end)
end
apidoc[HashSet.iter] = doc

doc = beginlongstringliteral
	HashSet:size() - Returns the number of elements in this HashSet.

endlongstringliteral
function HashSet:size() return self.mSize end
apidoc[HashSet.size] = doc

doc = beginlongstringliteral
	HashSet:__eq(set2) - Returns true if both sets have the same size and
	  both contain the same elements.

endlongstringliteral
function HashSet:__eq(set2)
	if self:size() ~= set2:size() then 
		return false
	else 
		for i in self:iter() do
			if not set2:contains(i) then return false end
		end
	end
	return true
end
apidoc[HashSet.__eq] = doc

oop.fallback(HashSet, sets.mixins.plauralizeHelper)
oop.fallback(HashSet, sets.mixins.convenienceMethods)

doc = beginlongstringliteral
	HashSet:test() - Unit test.

endlongstringliteral
function HashSet:test()
	local set = oop.methodCaller(self, "make")
	local N = 10
	local s1 = set(iter.count(N))
	assert(s1:contains(N-1))
	assert(not s1:add(N-1))
	assert(s1:size() == N)
	assert(s1 == set(iter.count(N)))
	assert(s1:addAll(iter.count(N)):size() == N)
	local diff = s1 - set(iter.count(N/2+1,N)) 
	local diffC = set(iter.count(N/2))
	assert(diff == diffC)
	
	local tuple = sano.makers.tuple
	
	local s2 = set(tuple(1,2), tuple(3,4), tuple(5,6))
	assert(s2:contains(tuple(1,2)))
	assert(s2(tuple(1,2)))

	local s3 = set(set(1,2), set(2,1), set(3,4))
	assert(s3:size()==2)
end
apidoc[HashSet.test] = doc


return HashSet
]],
maps=[[local sano = require 'sano'
local collections = sano.collections
local sets = sano.sets
local oop = sano.oop
local iter = sano.iter

local apidoc = {}
local doc
local maps = {documentation = apidoc}
maps.mixins = {}

apidoc.general = beginlongstringliteral
	maps - Module containing methods shared across map implementations. 

endlongstringliteral

doc = beginlongstringliteral
	maps.toString(map) - Returns a string representation of map.

	Example:
	> x = HashMap:make{a=1,b=2,c=3,d=4}
	> print(x)
	{a=1, d=4, c=3, b=2}

endlongstringliteral
function maps.toString(map) 
	return "{"..iter.pairsToString(map:iter()).."}"
end
apidoc[maps.toString] = doc

local inputTypes = beginlongstringliteral
	input can be one of several types:
	1) A single Lua table {a=1,b=2,c=3,d=4,e=5} 
	2) A single iteration which returns two values per iteration step:
	   iter.zip("abcde",iter.count())
	3) Two iterables which both return one value per iteration step - this
	   is a shorthand for 2.
	4) Any table with an iter method which, when called, returns an
	   iteration satisfying 2.

	So, the following are all equivalent:
	     Map:make{a=1,b=2,c=3,d=4,e=5} 
	  == Map:make(iter.zip("abcde",iter.count()))
	  == Map:make("abcde",iter.count())
	  == Map:make(Map:make{a=1,b=2,c=3,d=4,e=5})

endlongstringliteral

doc = beginlongstringliteral
	Map:make(mappings [, vals]) - Creates and returns a new Map, 
	  containing the mappings supplied.
	

endlongstringliteral
function maps.make(self, mappings, vals)
	local h = self:new()
	return h:addMappings(mappings, vals)
end
apidoc[maps.make] = doc..inputTypes

doc = beginlongstringliteral
	Map:addMappings(mappings, vals) - Adds mappings to this Map and returns
	  Map to allow chaining.
	

endlongstringliteral
function maps.addMappings(map, toAdd, vals)
	if vals then
		toAdd = iter.zip(toAdd, vals)
	end
	for k,v in iter.mapIter(toAdd or iter.EMPTY) do
		map:add(k,v)
	end
	return map
end
apidoc[maps.addMappings] = doc..string.gsub(inputTypes, "make", "addMappings")

doc = beginlongstringliteral
	Map:valIter() - Equivalent to Map:iter() except that iteration steps 
	  return elements in the order val,key instead of key,val.

endlongstringliteral
function maps.valIter(self) return iter.exchange(self:iter()) end	
apidoc[maps.valIter] = doc

doc = beginlongstringliteral
	maps.orderedEquals(map1, map2) - Returns true iff both maps contain the
	  same mappings AND the same iteration order.
	
	This is typically not the function that would be used for equality
	comparisons of map. See maps.unorderedEquals.

endlongstringliteral
function maps.orderedEquals(map1, map2)
	 return map1:size() == map2:size() and 
	   iter.multiEquals(map1:iter(), map2:iter())
end
apidoc[maps.orderedEquals] = doc

doc = beginlongstringliteral
	maps.unorderedEquals(map1, map2) - Returns true if both maps have the
	  same size and contain the same mappings. 

endlongstringliteral
function maps.unorderedEquals(map1, map2)
	if map1:size() ~= map2:size() then return false
	else
		for k,v in map1:iter() do
			if map2:get(k) ~= v then 
				return false
			end
		end
		return true
	end
end
apidoc[maps.unorderedEquals] = doc
	 
doc = beginlongstringliteral
	maps.makeSet(map) - Modifies the methods of the supplied map in place
	  to create an object following the set contract.
	
	This function is used to create the SkipSet and LinkedSet 'classes': 
		SkipSet = maps.makeSet(SkipMap:new())
		LinkedSet = maps.makeSet(LinkedMap:new())
		
endlongstringliteral
function maps.makeSet(map)
	local mapadd = map.add
	local maprem = map.remove
	oop.loadAll(map, getmetatable(map))
	map.add = function(self, val)
		return mapadd(self, val, true) == nil
	end
	map.documentation[map.add] = beginlongstringliteral
	Set:add(element) - Adds element to this set and returns true on
	  success, false if element already existed in this set.
endlongstringliteral
	map.remove = function(self, val)
		return maprem(self, val) and val or nil
	end
	map.documentation[map.remove] = beginlongstringliteral
	Set:remove(element) - Removes and returns element from this set,
	  or returns nil if element was not a member of this set.
endlongstringliteral
	setmetatable(map, nil)
	map.__index = oop.loadOnFirstUse(map)
	map.addMappings = nil
	map.__tostring = sets.toString
	map.__eq = function(set1, set2) return sets.equals(set1, set2) end
	map.__call = map.contains
	oop.fallback(map, sets.mixins.convenienceMethods)
	oop.fallback(map, sets.mixins.plauralizeHelper)
	map.make = collections.make
	return map
end
apidoc[maps.makeSet] = doc

maps.mixins.plauralizeHelper = {
	containsAll = collections.containsAll,
	removeAll = sets.removeAll,
	addMappings = maps.addMappings
}

maps.mixins.convenienceMethods = {
	__tostring = maps.toString,
	valIter = maps.valIter,
	make = maps.make
}

return maps]],
sets=[[local sano = require 'sano'
local collections = sano.collections
local utils = sano.utils
local iter = sano.iter

---
local apidoc = {}
local doc
local sets = {documentation = apidoc}
sets.mixins = {}

apidoc.general = beginlongstringliteral
	sets - Module containing methods shared across set implementations.

endlongstringliteral

doc = beginlongstringliteral
	sets.removeElement(set, element) - Equivalent to set:remove(element).
	
	In a sequence, the remove() method takes as input an index while the
	removeElement method takes as input the element to be removed. This
	method has the same semantics across both set and sequence
	implementations.

endlongstringliteral
function sets.removeElement(set, element)
	return set:remove(element)
end
apidoc[sets.removeElement] = doc

doc = beginlongstringliteral
	sets.removeAll(set, elements) - Equivalent to calling set:remove(e)
	  for e in iter(elements).

endlongstringliteral
function sets.removeAll(collection, elements)
	for i in iter.from(elements) do collection:remove(i) end
end
apidoc[sets.removeAll] = doc

doc = beginlongstringliteral
	sets.retainOnly(set, elements) - Removes all elements from set which
	  do not also appear in elements.
	
	If elements is also a set, s, set is modified to be the intersection of
	set and s.

endlongstringliteral
function sets.retainOnly(set, elements)
	for e in set:iter() do
		if not elements:contains(e) then set:remove(e) end
	end
end
apidoc[sets.retainOnly] = doc

doc = beginlongstringliteral
	sets.toString(set) - Returns a string representation of set.

	The order in which elements appear in the representation is the same as
	the iteration order of elements of the set.
	
	Example:
	> print(HashSet:make(1,1,3,2,5,6,4))
	{1, 2, 3, 4, 5, 6}

endlongstringliteral
function sets.toString(set)
	return "{"..iter.toString(set:iter()).."}"
end
apidoc[sets.toString] = doc

doc = beginlongstringliteral
	sets.intersection(set1, set2 [, setType]) - Computes and returns the
	  intersection of set1 and set2.
	
	The returned set is created by a call to: 
	(setType or getmetatable(set1)):new()

endlongstringliteral
function sets.intersection(set1, set2, returnSet)
	local toReturn = (returnSet or getmetatable(set1)):new()
	if set2:size() < set1:size() then
		set1, set2 = set2, set1 -- more efficient to compute in this direction
	end
	toReturn:addAll(set1); 
	assert(toReturn.retainOnly, "setType must have a retainOnly method")
	toReturn:retainOnly(set2)
	return toReturn
end
apidoc[sets.intersection] = doc

doc = beginlongstringliteral
	sets.union(set1, set2 [, setType]) - Returns the union of set1 and set2
	  as a new set.
	
	The returned set is created by a call to: 
	(setType or getmetatable(set1)):make(...)
	
	synonyms: __add

endlongstringliteral
function sets.union(set1, set2, setType)
	return (setType or getmetatable(set1)):make(iter.chain(set1:iter(), set2:iter()))
end
apidoc[sets.union] = doc
 
--function sets.unionCount(set1, set2)
	
doc = beginlongstringliteral
	sets.difference(set1, set2 [,setType]) - Returns the set that would be 
	  created by removing from set1 all elements in 
	  intersection(set1,set2).
		
	The returned set is created by a call to: 
	(setType or getmetatable(set1)):new()

	synonyms: __sub, minus

endlongstringliteral
function sets.difference(set1, set2, setType)
	local s = (setType or getmetatable(set1)):new(); s:addAll(set1); s:removeAll(set2)
	return s
end
apidoc[sets.difference] = doc

sets.minus = sets.difference

doc = beginlongstringliteral
	sets.xor(set1, set2 [,setType]) - Returns the set of elements which 
	  appear in either set1 or set2, but not in both.
	
	This operation is also often called the symmetric set difference.

endlongstringliteral
function sets.xor(set1, set2, setType)
	return sets.difference(set1:union(set2), set1:intersection(set2), setType)
end
apidoc[sets.xor] = doc

doc = beginlongstringliteral
	sets.equals(set1, set2) - Returns true if both sets have the same size
	  and contain the same elements.
	
	This function does not care whether both sets are the same type - a
	HashSet and SkipSet may be equal according to this function.

endlongstringliteral
function sets.equals(set1, set2)
	return set1:size()==set2:size() and set1:containsAll(set2)
end
apidoc[sets.equals] = doc

doc = beginlongstringliteral
	sets.hash(set) - Returns the sum of utils.hash(i) for i in set.
	
	This is the hash function used by all set implementations. 

endlongstringliteral
function sets.hash(self)
	local total = 0
	for i in self:iter() do total = total + utils.hash(i) end
	return total
end
apidoc[sets.hash] = doc

doc = beginlongstringliteral
	sets.uniqueFilter(set) - Create and return a uniqueness filter based 
	  on set.
	
	The filter is a function which returns true only for objects it has
	not yet seen. This can be used in conjuction with iter.filter.
	For example:
	
	> v = vector(1,1,1,2,3,3,4,1,5,5)
	> print(vector(iter.filter(v, HashSet:uniqueFilter())))
	[1, 2, 3, 4, 5]

endlongstringliteral
function sets.uniqueFilter(set)
	local seen = set:new()
	return function(x)
		return seen:add(x)
	end
end
apidoc[sets.uniqueFilter] = doc

sets.mixins.convenienceMethods = {
	enum = iter.enum,
	make = collections.make,
	intersection = sets.intersection,
	union = sets.union,
	difference = sets.difference,
	__sub = sets.difference,
	minus = sets.difference,
	__add = sets.union,
	xor = sets.xor,
	__tostring = sets.toString,
	__hash = sets.hash,
	retainOnly = sets.retainOnly,
	uniqueFilter = sets.uniqueFilter,
	removeElement = sets.removeElement
}

sets.mixins.plauralizeHelper = {
	removeAll = sets.removeAll,
	addAll = collections.addAll,
	containsAll = collections.containsAll,
	containsAny = collections.containsAny
}

return sets]],
Tuple=[[local sano = require 'sano'
local utils = sano.utils
local ordering = sano.ordering
local iter = sano.iter

local Tuple = {documentation={}}	
Tuple.__index = Tuple

local apidoc = Tuple.documentation
local doc

apidoc.general = beginlongstringliteral
	Tuple - A lightweight sequence type mainly for use as keys in a map or
	  as elements of a set.
	
	Examples of usage:
	> tuple = sano.makers.tuple
	> t = tuple(1,2,3); print(t)
	(1, 2, 3)
	> print(t == tuple(1, 2, 3))
	true
	> print(t == tuple(1, 2))
	false
	> for e in t:iter() do print(e) end
	1
	2
	3
	> a, b, c = t:unpack() -- built-in unpack(t) has same effect
	> print(a, b, c)
	1	2	3
	> hashset = sano.makers.hashset
	> tuples = hashset(t, tuple(8,7,6))
	> print(tuples:contains(tuple(8, 7, 6)))
	true
	
	It's possible to give any table tuple-like powers simply by setting
	its metatable to be Tuple, for instance:
	> t = {1,2,3,4}
	> print(t)
	table: 0x82cb810
	> setmetatable(t, Tuple)
	> print(t)
	(1, 2, 3, 4)
	> h = hashset{tuple(1,2,3,4)}
	> print(h:contains(t))
	true
	> setmetatable(t, nil) -- restore back to normal
	> print(h:contains(t))
	false
	
	Tuples are ordered lexicographically in the obvious way, for example:
	(1,1) <= (1,1), (1,2,2) < (1,2,3), () < (1), (1,2,1) < (1,3), etc.
	
	More precisely, if a = (a1, a2, ..., aN) and b = (b1, b2, ..., bN), 
	then a < b if:
		b1 > a1
		OR
		b1 == a1, b2 == a2, ..., bK == aK, bK+1 > aK+1 (b is greater than a)
	
	If a and b have different sizes, the smaller Tuple will be ordered
	before the larger if pairwise comparisons do not reveal an ordering.

endlongstringliteral

doc = beginlongstringliteral
	Tuple:new(...) - Returns a newly constructed Tuple containing all
	  elements passed as arguments to this method.

	It is perfectly fine to pass zero arguments (this creates an empty
	tuple) or one argument (this creates a tuple containing just that 
	argument)
	
	Examples:
	> print( Tuple:new(1,2,3) )
	(1, 2, 3)
	> print( Tuple:new(1) ) 
	(1)
	> print( Tuple:new() )
	()

endlongstringliteral
function Tuple:new(...)
	local obj = arg
	setmetatable(obj, self)
	return obj
end
apidoc[Tuple.new] = doc

doc = beginlongstringliteral
	Tuple:make(...) - Identical behavior to Tuple:new(), except that if just
	  one argument is passed, it is assumed to be *iterable* and the
	  elements of the iteration are added to the Tuple.
	
	Example: Tuple:make(iter.count(3)) == Tuple:make{1,2,3} 
	  == Tuple:make(1,2,3)

endlongstringliteral
function Tuple:make(...)
	if table.getn(arg) == 1 then 
		local t = self:new()
		for e in iter(arg[1]) do table.insert(t, e) end
		return t
	else
		return self:new(unpack(arg))
	end
end
apidoc[Tuple.make] = doc


doc = beginlongstringliteral
	Tuple:unpack() - Wrapper around built-in unpack method.
	
	Given a Tuple, t, t:unpack() and unpack(t) are equivalent, since
	t is just a table with a few convenience methods.

endlongstringliteral
function Tuple:unpack() 
	return unpack(self) 
end
apidoc[Tuple.unpack] = doc


doc = beginlongstringliteral
	Tuple:iter() - Returns an iterator over the elements in this Tuple.

endlongstringliteral
function Tuple:iter()
	local pos = 0
	return function()
		pos = pos + 1
		return self[pos]
	end
end
apidoc[Tuple.iter] = doc


doc = beginlongstringliteral
	Tuple:size() - Returns the number of elements in this tuple.
	
	If the tuple, t, has no holes, then #t will return the same result as
	t:size().

endlongstringliteral
function Tuple:size() 
	return table.getn(self) 
end
apidoc[Tuple.size] = doc


doc = beginlongstringliteral
	Tuple:__eq(other) - Returns true if both Tuples have the same size and
	  contain the same elements in the same order.

endlongstringliteral
function Tuple:__eq(other) 
	return self:size() == other:size() and iter.equals(self:iter(), other:iter()) 
end
apidoc[Tuple.__eq] = doc


doc = beginlongstringliteral
	Tuple:__hash() - Returns a number that will be used as the hash code 
	  when this Tuple is stored in a HashMap or HashSet.
	
	The value is cached in the field self.hash. Tuples are intended to be
	used as immutable objects (otherwise, use Vector). Although there is
	nothing stopping a determined soul from modifying a Tuple, if it is 
	being used as a key in a map, this will result in the Tuple being stored
	in the wrong bucket.
	
	See HashMap and SkipMap for more information on using Tuples or other 
	composite objects as keys.
	
	In keeping with the contract of the __hash metamethod Tuple objects that
	are equal return the same hash value.

endlongstringliteral
function Tuple:__hash()
	if not self.hash then self.hash = utils.hashIterable(self) end
	return self.hash
end
apidoc[Tuple.__hash] = doc

doc = beginlongstringliteral
	Tuple:rehash() - Recompute and return the hash value for this Tuple.
	
	Since Tuples are intended mostly to be used as immutable objects, this
	method shouldn't normally be called (once the hash value is computed 
	once, it shouldn't need to be computed again). 

endlongstringliteral
function Tuple:rehash()
	self.hash = nil; return self:__hash()
end
apidoc[Tuple.rehash] = doc

doc = beginlongstringliteral
	Tuple:__tostring() - Returns a string of the form "(e1, e2, ..., eN)"

endlongstringliteral
function Tuple:__tostring()
	return "("..sano.iter.toString(self:iter())..")"
end
apidoc[Tuple.__tostring] = doc


doc = beginlongstringliteral
	Tuple:__lt(other) - Returns true if this Tuple is lexicographically less
	  than other.
	
	If a = (a1, a2, ..., aN) and b = (b1, b2, ..., bN), then a < b iff:
		b1 > a1
		OR
		b1 == a1, b2 == a2, ..., bK == aK, bK+1 > aK+1 (b is greater than a)
	
	If a and b have different sizes, the smaller Tuple will be ordered
	before the larger if pairwise comparisons do not reveal an ordering.
	
	Examples: (1,1) <= (1,1), () <= (1), (1,2,1) <= (1,3)
	
	The evaluation is 'short-circuit' and halts as soon as there is enough
	information to determine the ordering. 
	
	See Tuple.documentation.general for more information on how Tuples are 
	ordered.
	
	Also see ordering.lexicographical.

endlongstringliteral
function Tuple:__lt(other)
	local _1,_2,_3,result = ordering.lexicographical(self, other)
	return result==-1
end
apidoc[Tuple.__lt] = doc

doc = beginlongstringliteral
	Tuple:test() - Unit test.

endlongstringliteral
function Tuple:test()
	local tuple = function(...) return Tuple:make(unpack(arg)) end
	
	local t1 = tuple(1,2,3)
	
	-- check comparison operators
	assert(t1 == tuple(1,2,3))
	
	assert(t1 < tuple(1,2,4))
	assert(t1 <= tuple(1,2,4))
	assert(t1 <= tuple(1,2,3))
	
	assert(t1 > tuple(1,2,1,9))
	assert(t1 >= tuple(1,2,1,9))
	assert(t1 > tuple()) -- corner case: the empty tuple comes before everything
	
	assert(t1 ~= tuple(1,2,3,4))
	
	-- make sure hash and equals methods are consistent
	assert(utils.hash(t1)==utils.hash(tuple(1,2,3)))
	assert(utils.hash(t1)~=utils.hash(tuple(1,1,1)))
	
	local a,b,c = t1:unpack()
	assert(a==1); assert(b==2); assert(c==3)
	
end
apidoc[Tuple.test] = doc


return Tuple
]],
SkipVector=[[
local sano = require 'sano'
local oop = sano.oop
local iter = sano.iter
local collections = sano.collections
local sequences = sano.sequences

local function default0(t,ind) return 0 end

local function SINode(val,numSkips)
	local t = {val=val, numSkips=numSkips, skips={}, distance={__index=default0}}
	setmetatable(t.distance,t.distance); return t
end

local E = 2.718
local MAX_LEVELS = 24

local function exponential(survivalp, intmax)
	local toReturn = 1
	while math.random() > survivalp and toReturn <= intmax do
		toReturn = toReturn + 1
	end
	return toReturn
end

local SkipVector = {documentation={}}
local apidoc = SkipVector.documentation

apidoc.general = beginlongstringliteral
 	SkipVector - Skip-list implementation of a general purpose vector. 
	
	Provides worst-case logarithmic random access, constant time sequential 
	access, and logarithmic time inserts or removals at ANY position. Also 
	provides functions for splicing and concatenating, and joining other 
	SkipVectors.
	
	The basic functions are:
	+) add: adds an element to this vector
	+) remove: removes an element from the vector
	+) get: return the element stored at a position in the vector
	+) set: set the value stored at an index to a different value 
	+) splice: remove and return a range of elements in the vector as a new 
	   vector
	+) join: concatenates or inserts in another SkipVector
	+) iter: returns an iterator over the elements of the vector
	+) size: returns the number of elements in the vector
	
	add, remove, get, set, splice, and join all have worst case O(lg(size)) 
	performance. However, the vector maintains search 'fingers' into the 
	structure, so that the time complexity for get and set operations is 
	O(lg(k)), where k is the distance from the most recently accessed 
	element. E.g., for the access pattern 1,2,3..n, get/set will both run in 
	O(1).
	
	Indices in the vector start at 1 go to size(), and can be negative. 
	Negative indices count backward from the end of the vector: get(-1) 
	returns the last element, get(-2) returns the second to last element, 
	get(-size()) returns the first element, etc. Any method that accepts 
	index arguments can use negative indexing. Indexing modes can be mixed 
	in the same call, i.e. iter(1,-2) will iterate from the first element to
	the second to last element.
	
	nil values can be stored in a SkipVector, although nil values will 
	cause the iterators returned by this class to halt prematurely, since 
	Lua interprets nil being returned as signaling the end of the iteration. 
	
	Implementation is based heavily on: 'A Skip List Cookbook', William Pugh, 
	1990.

endlongstringliteral

local doc

doc = beginlongstringliteral
	SkipVector:new() - Creates and returns a new, empty SkipVector

endlongstringliteral
function SkipVector:new(mHead, mMaxLevel, mSize)
	local obj = {}
	obj.mHead = mHead or SINode(nil, MAX_LEVELS)
	obj.mMaxLevel = mMaxLevel or 1
	obj.mFingers = {}
	obj.mSize = mSize or 0
	setmetatable(obj, self)
	self.__index = oop.loadOnFirstUse(self)
	return obj
end
 apidoc[SkipVector.new] = doc

-- Private methods --

local function checkRange(vec, ind)
	if not ind then error("Index is null",3) end
	if ind < 1 or ind > vec.mSize then error("Index: "..ind.." out of range,"
	  .." size is: "..vec.mSize,3) end
end

local function translate(vec, index)
	index = index or rawget(vec,"mSize")
	if index < 0 then index = vec.mSize+1+index end
	return index
end

local function searchByIndex(vec, ind, node, pos, startLevel)
	checkRange(vec, ind)
	node, pos, startLevel = node or vec.mHead, pos or 0, startLevel or vec.mMaxLevel  
	--local count = 0
	for i=startLevel,1,-1 do
		while node.skips[i] and pos + node.distance[i] < ind 
			do pos = pos + node.distance[i]; node = node.skips[i]; --beginlongstringliteralcount = count + 1endlongstringliteral end
		vec.mFingers[i] = {node,pos}
	end
	--print("steps:"..count)
	return node, node.skips[1]
end

local function fingerSearch(vec, ind)
	if not rawget(vec,"mFingers") then rawset(vec,"mFingers",{}); return searchByIndex(vec, ind) end
	checkRange(vec, ind)
	local v,startNode,pos = 2,nil,0
	if vec.mFingers[1] and vec.mFingers[1][2] < ind then 
	-- move up to first level that skips past the target index
		while v <= vec.mMaxLevel and vec.mFingers[v][2] + vec.mFingers[v][1].distance[v] < ind 
			do v = v + 1 end
		v = v - 1
		startNode,pos = unpack(vec.mFingers[v])
	else -- move backward
		while v <= vec.mMaxLevel and vec.mFingers[v][2] >= ind do -- TODO: getting attempt to index nil here
			v = v + 1
		end
		if v > vec.mMaxLevel then v = vec.mMaxLevel; startNode,pos = vec.mHead,0
		else startNode,pos = unpack(vec.mFingers[v]) end
	end
	return searchByIndex(vec, ind, startNode, pos, v)
end
	
local function insertAt(vec, ind, val)
	if ind < 0 or ind > vec.mSize then error("Index "..ind.." out of range, "
	  .."size is: "..vec.mSize) end
	local v = exponential(1/E, MAX_LEVELS)
	local toInsert = SINode(val, v)
	if v > vec.mMaxLevel then 
		for i=vec.mMaxLevel+1,v do vec.mHead.distance[i] = vec.mSize + 1 end
		vec.mMaxLevel = v
	end
	-- search for the position, adjusting distances as we go
	local node, pos = vec.mHead, 0
	for i=vec.mMaxLevel,1,-1 do
		while node.skips[i] and pos + node.distance[i] <= ind 
			do pos = pos + node.distance[i]; node = node.skips[i] end
		if i > v then node.distance[i] = node.distance[i] + 1
		else
			local after = node.skips[i]
			toInsert.skips[i] = after
			node.skips[i] = toInsert
			toInsert.distance[i] = pos + node.distance[i] - ind
			node.distance[i] = ind + 1 - pos
		end
	end
	vec.mFingers = nil
	vec.mSize = vec.mSize + 1
end

local function deleteAt(vec, ind)
	rawset(vec,"mFingers",rawget(vec,"mFingers") or {})
	local prev, node = searchByIndex(vec, ind)
	for i=1,vec.mMaxLevel do 
		if vec.mFingers[i][1].skips[i] == node then
			vec.mFingers[i][1].skips[i] = node.skips[i]
			vec.mFingers[i][1].distance[i] = vec.mFingers[i][1].distance[i] + node.distance[i] - 1
		else
			vec.mFingers[i][1].distance[i] = vec.mFingers[i][1].distance[i] - 1
		end
	end
	vec.mSize = vec.mSize - 1
	while vec.mHead.skips[vec.mMaxLevel] == nil and vec.mMaxLevel > 1
		do vec.mMaxLevel = vec.mMaxLevel - 1 end
	vec.mFingers = nil
	return node
end

 --beginlongstringliteral
	Remove the sublist from [ind,size()]
	endlongstringliteral
local function split(vec,ind)
	checkRange(vec,ind)
	local sHead = SINode(nil,MAX_LEVELS)
	local sSize = 0
	local sMaxLevel = vec.mMaxLevel
	-- do the split		
	local node, pos = vec.mHead, 0
	for i=vec.mMaxLevel,1,-1 do
		while node.skips[i] and pos + node.distance[i] < ind
			do pos = pos + node.distance[i]; node = node.skips[i] end
		sHead.skips[i],sHead.distance[i] = node.skips[i],node.distance[i]-(ind-pos)+1
		node.skips[i],node.distance[i] = nil, nil
	end
	while not vec.mHead.skips[vec.mMaxLevel] and vec.mMaxLevel > 1 
		do vec.mMaxLevel = vec.mMaxLevel-1 end
	while sHead.skips[sMaxLevel] == nil and sMaxLevel > 1
		do sMaxLevel = sMaxLevel-1 end
	
	local toReturn = SkipVector:new(sHead, sMaxLevel, vec.mSize-ind+1)
	vec.mSize = vec.mSize - toReturn:size()
	vec.mFingers = nil
	toReturn.mFingers = nil
	return toReturn
end

local function concatenate(vec,vec2)
	local vec2MaxLevel = vec2.mMaxLevel
	local vec2Head = vec2.mHead
	
	if vec.mMaxLevel < vec2MaxLevel then vec.mMaxLevel = vec2MaxLevel end
	local node = vec.mHead
	local pos = 0
	for i=vec.mMaxLevel,1,-1 do 
		while node.skips[i] do node,pos = node.skips[i],pos+node.distance[i] end
		if i <= vec2MaxLevel then 
			node.skips[i] = vec2Head.skips[i]
			node.distance[i] = (vec.mSize-pos) + vec2Head.distance[i]
		end
	end
	vec.mSize = vec.mSize + vec2:size()
	vec.mFingers = nil
	oop.invalidate(vec2, SkipVector, 
		"This vector is no longer valid -- it has been joined into another vector.")
end

  -- PUBLIC INTERFACE --
 
 --beginlongstringliteral
 	Prints a human-friendly representation of this SkipVector for debugging. 
	Each node in the vector is printed out in order, along with the skip links
	and the distances associated with each link.
	endlongstringliteral
function SkipVector:debug()
	local function strNode(node)
		local v = node.val and node.val or "nil"
		local links = ""
		for ind,l in pairs(node.skips) do links = links..l.val.."/d="..node.distance[ind]..", " end
		return v.."->"..links
	end
	print("maxLevel="..self.mMaxLevel..", size="..self.mSize)
	local curNode,ind = self.mHead, 0
	while curNode do 
		print ("["..ind.."]"..strNode(curNode))
		curNode = curNode.skips[1]
		ind = ind + 1
	end
end

doc = beginlongstringliteral
	SkipVector:get([index]) - Returns the element currently stored at 
	  position index in this SkipVector, defaulting to the last element.
	
	Indices start at 1 and go to size(), and can be negative. Negative 
	indices count backward from the end of the vector get(-1) returns the 
	last element, get(-2) returns the second to last element, get(-size())
	returns the first element, etc. Any method that accepts index arguments
	can use negative indexing.
	
	param index: in +/-[1,size()]
	
	example: 
	[a,b,c]:get(-1) == c, [a,b,c]:get(1) == a
	time complexity: O(lg(k)), where k is the distance from the most recent
	  get/set

endlongstringliteral
function SkipVector:get(index)
	index = translate(self,index)
	local prev,at = fingerSearch(self,index); 
	--local prev,at = searchByIndex(self,index)
	return at.val
end
 apidoc[SkipVector.get] = doc	

doc = beginlongstringliteral
	SkipVector:remove(index [,stopIndex]) - Removes the element at the 
	  supplied index, or, the range [index..stopIndex] and returns the 
	  result as a SkipVector. 
	  
	If no parameters are specified, the last element is removed and 
	returned. Negative indices can also be used.
	
	param index, stopIndex: in +/-[1,size()]
	
	time complexity: 0(lg(size())), for either operation.
	
	examples: 
	[1,2,3]:remove(-2) results in [1,3]
	[1,2,3,4]:remove(2,3) results in [1,4]
	[1,2,3,4]:remove()==4 and results in [1,2,3]

endlongstringliteral
function SkipVector:remove(index, stopIndex)
	index = translate(self,index)
	if not stopIndex then -- we're just removing a single element
		return deleteAt(self,index).val
	else
		return self:splice(index, translate(self,stopIndex))
	end
end
 apidoc[SkipVector.remove] = doc

doc = beginlongstringliteral
	SkipVector:set(index, val) - Sets the element at index to val and 
	  returns the previous element stored.

	param index: in +/-[1,size()]
	
	time complexity: O(lg(k)), where k is the distance from the most recent
	  get/set
	
	example: 
	[1,2,3,4]:set(2,"a") results in [1,"a",3,4] and returns 2

endlongstringliteral
function SkipVector:set(index, val) 
	index = translate(self,index)
	local prev,at = fingerSearch(self,index);
	local old = at.val; at.val = val; return old
end
 apidoc[SkipVector.set] = doc

doc = beginlongstringliteral
	SkipVector:add(val [,index]) - Adds val to the end of this vector, 
	  or, if index is specified, inserts val AFTER index. 
	
	Negative indices can be used.		
	
	param index: in +/-[0,size()] (0 inserts before the first element)
	
	time complexity: O(lg(size())
	
	example: 
	[1,2,3]:add(a, 1) results in [1,a,2,3]

endlongstringliteral
function SkipVector:add(val, index)
	index = translate(self,index)
	insertAt(self, index, val)
	return index+1
end
 apidoc[SkipVector.add] = doc

local kBufferSize = 20

doc = beginlongstringliteral
	SkipVector:addAll(iterable [, index]) - Adds all elements of iterable to 
	  this vector AFTER the index'th element, or at the end if no index is
	  specified.
	  
	param index: in +/-[0,size()] (0 inserts before the first element)
	
	param iterable: an iterator, an object with an iter method, or a table,
	  in which case the values of the table are added

endlongstringliteral
function SkipVector:addAll(iterable, index)
	-- 
	local stack = {}; table.insert(stack, SkipVector:new())
	local buffer = SkipVector:new()
	for i in iter.from(iterable) do
		buffer:add(i)
		if buffer:size() == kBufferSize then
			table.insert(stack, buffer)
			buffer = SkipVector:new()
			local i = table.getn(stack)
			while i > 1 and (stack[i]:size() >= stack[i-1]:size()) do
				stack[i-1]:join(table.remove(stack))
				i = i - 1
			end
		end
	end
	-- print("stack size: "..table.getn(stack))
	for i=2,table.getn(stack) do
		stack[1]:join(stack[i])
	end
	stack[1]:join(buffer) -- flush anything still in the buffer
	self:join(stack[1], translate(self,index))
end
 apidoc[SkipVector.addAll] = doc

doc = beginlongstringliteral
	SkipVector:splice(start [,stop]) - Removes the range [start,stop] from 
	  this vector and returns it as a SkipVector. 
	
	If omitted, the stop parameter defaults to the end of the vector. The 
	call splice(s1,s2) is equivalent to the call remove(s1,s2). Negative 
	indices can also be used.
	
	param start,stop: in +/-[1,size()]
	
	time complexity: O(lg(size()))
	
	example: 
	['a','b','c',1,2,3]:splice(2,4) returns ['b','c',1], and the
	 original list becomes ['a',2,3]

endlongstringliteral
function SkipVector:splice(start,stop)
	if start then start = translate(self,start) end
	stop = translate(self, stop) 
	checkRange(self,start); checkRange(self,stop); 
	if stop < start then 
		error("Stop index, "..stop..", is less than start index, "..start) end
	if stop == self.mSize then return split(self, start) end
	local newTail = split(self, stop+1)
	local removed = split(self, start)
	concatenate(self, newTail)
	return removed
end
 apidoc[SkipVector.addAll] = doc

function SkipVector:divide(pieceCount)
	local copy = self:make(self:iter())
	local modulo = math.floor(self:size()/pieceCount)
	local pieces = {}
	for i=pieceCount,1,-1 do
		pieces[i] = copy:splice(copy:size()-modulo+1)
	end
	--for k,v in pairs(pieces) do print(k,v) end
	return unpack(pieces)
end

doc = beginlongstringliteral 
	SkipVector:join(vec2 [, index]) - Splices vec2 into this list AFTER 
	  position index. 
	
	vec2 is destroyed as a result of this call. If the index parameter is 
	omitted, vec2 is concatenated onto the end of this vector.
	
	time complexity: O(lg(size())).
	
	param: index in +/-[0,size()] the index after which to insert vec2
	
	examples: 
	[1,2,3]:join([a,b,c],2) results in [1,2,a,b,c,3]
	 [1,2,3,4,5]:join([a,b,c],-2) results in [1,2,3,4,a,b,c,5]

endlongstringliteral
function SkipVector:join(vec2, index)
	index = translate(self,index)
	if index < 0 or index-1 > self.mSize then 
		error("Index "..index.." out of range, list size is: "..self.mSize) end
	if vec2:size() == 0 then return self end
	if index == self.mSize then concatenate(self,vec2)
	else
		local tmp = split(self,index+1)
		concatenate(self, vec2);
		concatenate(self, tmp); -- that was easy!
	end
	return self
end
 apidoc[SkipVector.join] = doc

doc = beginlongstringliteral
	SkipVector:replace(start,stop,iterable) - Replaces the range 
	  [start,stop] with the elements in iterable. 
	
	If the range being replaced has the close to the same number of elements 
	as the iteration, this function runs in roughly linear time. Otherwise, 
	the time complexity can be no worse than O(k*lg(size)), where k is the 
	number of elements in iterable.
	
	param start,stop: in +/-[1,size()]
	
	time complexity: min(k,r) + abs(k-r)*lg(size), where 
	 k is the number of elements in iterable and r is the number of elements 
	 in the range (start, stop)
	
	example: 
	[a,b,c,d,e]:replace(2,5,{1,2}) results in [a,1,2]

endlongstringliteral
function SkipVector:replace(start, stop, iterable)
	assert(start)
	start = translate(self,start); stop = translate(self,stop)
	iterable = iter.from(iterable)
	local indices = iter.count(start,stop)
	local z = iter.zip(indices,iterable)
	local num = 0
	for ind,val in z do 
		self:set(ind, val)
		num = num + 1
	end
	if num == stop-start+1 then -- iterable had >= elements than [start,stop]
		for e in z do self:add(e, stop); stop = stop + 1 end
	else -- iterable had fewer elements than [start,stop]
		for i in z do self:remove(start+num) end -- remove the remaining elements in [start,stop]
	end
end
 apidoc[SkipVector.replace] = doc

doc = beginlongstringliteral 
	SkipVector:size() - Returns the number of elements in this vector. 
	
	This is also the largest valid index for a call to get(). 
endlongstringliteral
function SkipVector:size() return self.mSize end
 apidoc[SkipVector.size] = doc

doc = beginlongstringliteral
	SkipVector:reverseIter(start, finish) - Returns a reverse iterator 
	  over the elements in this vector, starting from start (inclusive) and 
	  moving backward to finish (inclusive).
	  
	param finish: in +/-[1,size()], defaults to 1

	param start: in +/-[1,size()], defaults to size()
endlongstringliteral
function SkipVector:reverseIter(start, finish)
	if self:size() == 0 then return iter.EMPTY end
	start = translate(self,start); finish = finish or 1; finish = translate(self,finish)
	local indices = iter.count(start, finish, -1)
	return function()
		local ind = indices()
		if ind then return self:get(ind) end
	end
end
apidoc[SkipVector.reverseIter] = doc

doc = beginlongstringliteral
	SkipVector:iter([start [,finish] ]) - Returns an iterator over the 
	elements at indices [start..finish] in this vector. 
	
	If no finish is specified, the iteration is from start (inclusive)
	to the end of the vector; if no start is specified, the iteration is 
	over all elements in this vector. Negative indices can be used when 
	specifying the range. If start > finish, a reverse iteration over the 
	range [finish,start],-1 is returned.
	
	param start,finish: in +/-[1,size()]
	
	time complexity: advancing to the next element in the iteration is a 
	 constant-time operation.
	
	Examples: 
	for i in [1,2,3,4,5]:iter(3) do print(i) end --> 3,4,5
	for i in [1,2,3,4,5]:iter(1,-2) do print(i) end --> 1,2,3,4

endlongstringliteral
function SkipVector:iter(start, finish)
	if self:size() == 0 then return iter.EMPTY end
	start = start or 1; start = translate(self,start); finish = translate(self,finish)
	if start > finish then return self:reverseIter(start, finish) end
	local n,_ = fingerSearch(self,start)
	local pos = start - 1
	return function()
		if n.skips[1] and pos < finish then 
			pos = pos + 1; n = n.skips[1]; return n.val end
	end
end
 apidoc[SkipVector.iter] = doc

doc = beginlongstringliteral
	SkipVector:__eq(other) - Returns true if both vectors are of the same
	  size and contain the same elements in the same order.

endlongstringliteral
function SkipVector:__eq(other)
	return self:size() == other:size() and iter.equals(self:iter(),other:iter())
end
 apidoc[SkipVector.__eq] = doc

SkipVector.__call = SkipVector.get

doc = beginlongstringliteral
	SkipVector:asString(sep) - Returns a string created by concatenating
	  together all the elements in this SkipVector, using sep as the
	  separator (default empty)
	
	This method is useful when SkipVector is being used as a string
	buffer.
	
	Example:
	> a = SkipVector:make("a","b","c")
	> a:add("d")
	> print(a:asString())
	abcd

endlongstringliteral
function SkipVector:asString(sep)
	local t = {}
	for i in self:iter() do table.insert(t,i) end
	return table.concat(t, sep)
end

doc = beginlongstringliteral
	SkipVector:test() - Unit test.

endlongstringliteral
function SkipVector:test()
	local v = self:make(1,2,3,4,5)
	for i=1,v:size() do v:get(i) end
	assert(self:make(v:reverseIter()) == self:make(5,4,3,2,1))
	assert(v:remove(2)==2)
	assert(v:remove(-1)==5)
	assert(v:add("hello",0))
	
	local count = iter.count
	local v2 = self:make(count(1000))
	local v2Split = v2:splice(500)
	assert(v2Split==self:make(count(500,1000)))
	v2:join(v2Split, 1)
	assert(v2:splice(2,502)==self:make(count(500,1000))) 
	
end
apidoc[SkipVector.test] = doc

-- add some convenience methods
oop.fallback(SkipVector, sequences.mixins.convenienceMethods)
oop.fallback(SkipVector, sequences.mixins.plauralizeHelper)
oop.fallback(SkipVector, sequences.mixins.randomAccessHelper)


return SkipVector

]],
oop=[[
local sano = require 'sano'
local utils =  sano.utils
local apidoc = {}
local doc
local oop = {documentation = apidoc}

apidoc.general = beginlongstringliteral
	oop - Internally used module for miscellaneous object-oriented
	  programming support functions.

endlongstringliteral
 --beginlongstringliteral
 	Returns a function that can be used 
 	endlongstringliteral
local function indexWithCopyFrom(source)
	return function(t,k)
		t[k] = source[k]
		return rawget(t,k)
	end
end

oop.loadOnFirstUse = utils.memoized(indexWithCopyFrom,1) 

function oop.loadAll(obj, interface)
	assert(obj~=interface)
	for k,v in pairs(interface) do
		rawset(obj,k,v)
	end
end
	
function oop.methodCaller(obj, methodName)
	return function(...)
		return obj[methodName](obj, unpack(arg))
	end
end

-- Any method in interface is replaced with function that throws the error message
-- msg
function oop.invalidate(obj, interface, msg)
	for name,method in pairs(interface) do
		if type(method) == "function" then
			rawset(obj, name, function() error(msg,3) end)
		end
	end
end

 --beginlongstringliteral
 	Throws an error if any of the mixed-in methods are already defined.
	endlongstringliteral
function oop.mixin(class, methods)
	for name,fn in pairs(methods) do
		if class[name] ~= nil then
			error("oop.mixin error: '"..name.."' is already defined by obj/class "..tostring(class),2)
		else
			class[name] = fn
		end
	end
end

 --beginlongstringliteral
 	Adds each method in methods to class only if they do not already exist.
	endlongstringliteral
function oop.fallback(class, methods)
	for name,fn in pairs(methods) do
		if class[name] == nil then
			class[name] = fn
		end
	end
end

function oop.decorate(class, methods)
	for name,fn in pairs(methods) do
		class[name] = fn
	end
end

function oop.ancestors(obj)
	return coroutine.wrap(function()
		coroutine.yield(obj)
		local objParent = getmetatable(obj)
		if objParent and not rawequal(objParent, obj) then
			for p in oop.ancestors(objParent) do
				coroutine.yield(p)
			end
		end
	end)
end

function oop.isInstance(obj, class)
	for p in oop.ancestors(obj) do 
		if rawequal(p, class) then return true end
	end
	return false
end

return oop
]],
SkipSet=[[
local sano = require 'sano'
local SkipMap = sano.SkipMap
local ordering = sano.ordering

local SkipSet = SkipMap:new()
sano.maps.makeSet(SkipSet)

local apidoc = SkipSet.documentation
local doc
--SkipSet.documentation = apidoc

apidoc.general = beginlongstringliteral
	SkipSet - Ordered set implementation based on SkipMap.
	
	The SkipSet class was created by a call to 
	sano.maps.makeSet(SkipMap:new()). The set is implemented
	by storing the elements of the set as the keys in SkipMap, where all
	keys simply map to the value 'true'.
	
	Example usage:
	> oset = function(...) return SkipSet:make(unpack(arg)) end
	> -- above is equivalent to: oset = sano.makers.oset
	> s = oset(1,2,3,3,4); print(s)
	{1, 2, 3, 4}
	> print(s == oset(4,3,2,1))
	true
	> print(s == oset(1,3,5,7))
	false
	> print(s:remove(1))
	1
	> s:addAll(iter.count(10,15)); print(s)
	{2, 3, 4, 10, 11, 12, 13, 14, 15}
	> s:removeAll(iter.count(2,4)); print(s)
	{10, 11, 12, 13, 14, 15}
	> print(s:size())
	6
	
endlongstringliteral

doc = beginlongstringliteral
	SkipSet:test() - Unit test.

endlongstringliteral
function SkipSet:test()
	local skipset = function(...) return self:make(unpack(arg)) end
	
	local s = skipset(1,2,3,3,4)
	assert(s == skipset(4,3,2,1), "equals failed")
	assert(s:size()==4, "size() failed")
	assert(s:remove(3)==3, "remove failed")
	assert(s:remove(7)==nil, "remove failed")
	assert(s:contains(2), "contains failed")
	assert(s:containsAll(sano.iter.count(2)))
	s:merge(skipset(9,8,7,6,5,4,3,2))
	assert(s:add(11))
	assert(ordering.isSorted(s))
	local t = s:splice(1,4)
	assert(t==skipset(1,2,3,4),tostring(t))
end
apidoc[SkipSet.test] = doc


return SkipSet
]],
Phonebook=[[
local sano = require 'sano'
local SkipMap = sano.SkipMap
local oop = sano.oop

local apidoc = {}
local doc
local Phonebook = SkipMap:new()
Phonebook.documentation = apidoc

apidoc.general = beginlongstringliteral
	Phonebook - An ordered map based on SkipMap which allows duplicate 
	 keys.
	
	In SkipMap, calling add(key, val) for a key which already exists
	overwrites the previous value. In a Phonebook, the new key-value pair is
	added AFTER the previous pair. Calling Phonebook:get(key) retrieves the
	FIRST value stored agains the key. Phonebook:remove(key) also removes
	the first value stored against key.
	
	For more control over the handling of duplicate keys in a map, see
	Multimap.
	
	Example usage:
	> p = Phonebook:new()
	> p:add('Doe', 'John'); p:add('Doe', 'Jane'); print(p)
	{Doe="John", Doe="Jane"}
	> p:add('Smith','Mike'); p:add('Smith','Mary'); p:add('Jones','Jim')
	> print(p)
	{Doe="John", Doe="Jane", Jones="Jim", Smith="Mike", Smith="Mary"}
	> for first,last in p:iter('Doe','Jones') do print(first,last) end
	Doe     John
	Doe     Jane
	Jones   Jim
	> print(p:remove('Doe'))
	John

endlongstringliteral

doc = beginlongstringliteral
	Phonebook:add(key,val) - Adds the key-value pair to this Phonebook after
	  any existing pair with the same key.

endlongstringliteral
function Phonebook:add(key, val)
	return SkipMap.add(self, key, val, true)
end
apidoc[Phonebook.add] = doc

doc = beginlongstringliteral
	Phonebook:merge(orderedMap) - Merges the key-value pairs in the other 
	  orderedMap into this Phonebook.
  
	Duplicate keys are retained in the merged Phonebook. orderedMap is 
	invalidated as a result of this call.

endlongstringliteral
function Phonebook:merge(orderedMap)
	return SkipMap.merge(self, orderedMap, true)
end
apidoc[Phonebook.merge] = doc


doc = beginlongstringliteral
	Phonebook:test() - Unit test.

endlongstringliteral
function Phonebook:test()
	local p, p2 = self:new(), self:new()
	p:add('Smith', 'Joe')
	p:add('Smith', 'Betty')
	p2:add('Smith', 'Joe')
	p2:add('Smith', 'Mike')
	p:merge(p2)
	assert(p:size()==4)
end
apidoc[Phonebook.test] = doc


return Phonebook
]],
QueueVector=[[

-- random access + efficient adds and removes from both start and end of list

-- imports
local sano = require 'sano'
local oop = sano.oop
local iter = sano.iter
local sequences = sano.sequences

local QueueVector = {}
QueueVector.documentation = {}

local apidoc = QueueVector.documentation

apidoc.general = beginlongstringliteral
	QueueVector - Table-based vector implementation with efficient 
	  (amortized O(1)) inserts and removals at both the start and end of the
	  vector. 
	
	Indicies of a QueueVector, q run from 1 to q:size() and can be negative, 
	where q:get(-1) retrieves the last element, q:get(-2) the second to last 
	element, and q:get(-q:size()) and q:get(1) both retrieve the first 
	element.
	
	Elements can be nil, with the caveat that the iter method will halt 
	prematurely at these elements if the iterator is not wrapped in an 
	enumeration.
	
	Example uses:
	> q = QueueVector:make(1,2,3,4,5)
	> print(q)
	[1, 2, 3, 4, 5]
	> print(q:remove()) -- O(1), remove removes first element by default
	1
	> q:add("hello", 0) -- O(1), adds "hello" after index 0 (prepends it)
	> print(q)
	[hello, 2, 3, 4, 5]
	> q:add("goodbye"); print(q)
	[hello, 2, 3, 4, 5, goodbye]

endlongstringliteral

local function translate(self, index)
	index = index or self.mSize
	if index < 0 then return self.mSize+1+index else return index end
end

local doc -- temp var we'll reuse to store documentation of each function

doc = beginlongstringliteral
	QueueVector:new(obj) - Creates a new, empty QueueVector.

endlongstringliteral
function QueueVector:new(obj)
	obj = obj or {}
	obj.mStart = 1
	obj.mSize = table.getn(obj) or 0
	setmetatable(obj, self)
	self.__index = oop.loadOnFirstUse(self)
	return obj
end
apidoc[QueueVector.new] = doc


doc = beginlongstringliteral
	QueueVector:add(val,ind) - Adds the value, val, AFTER position ind in 
	  this QueueVector. 
	
	ind defaults to the end of the vector. 0 inserts at the head of the 
	vector. Values can be nil, with the caveat that the iter method will 
	halt prematurely at these values if not wrapped in an enumeration.
	
	param ind: in +/-[0..size]
	error: if ind not in valid range
	returns: true (fulfills general contract of collections.add)

endlongstringliteral
function QueueVector:add(val,ind)
	ind = translate(self, ind)
	if ind < 0 or ind > self.mSize then
		error("Index: "..ind.." out of range, queue size is: "..self.mSize,2)
	end
	self.mSize = self.mSize+1
	if ind == 0 then
		--print("eq start", self, "size "..self.mSize, self.mStart)
		self.mStart = self.mStart - 1
		self[self.mStart] = val
	else
		--print("appending", self, "size "..self.mSize, self.mStart)
		table.insert(self,self.mStart+ind, val)
	end
	return ind+1
end
apidoc[QueueVector.add] = doc

doc = beginlongstringliteral
	QueueVector:remove(ind) - Removes and returns the element at index ind 
	  (default 1) from this vector.
	
	If ind is 1 or the size of the vector, this runs in constant time; 
	otherwise the elements to the left of the removed element are shifted
	over to fill the hole created.
	
	param ind: in +/-[1..size], default=1
	error: if ind not in valid range
	returns: the removed element
	endlongstringliteral
function QueueVector:remove(ind)
	ind = ind and translate(self, ind) or 1
	if ind < 1 or ind > self.mSize then
		error("Index: "..ind.." out of range, queue size is: "..self.mSize,2)
	end
	self.mSize = self.mSize - 1
	if ind == 1 then
		local removed = self[self.mStart]
		self[self.mStart] = nil
		self.mStart = self.mStart + 1
		return removed
	else
		return table.remove(self, ind+self.mStart-1)
	end
end
apidoc[QueueVector.remove] = doc


doc = beginlongstringliteral
	QueueVector:get(ind) - Returns the ind'th element of this queue. 
	
	Indicies are in +/-[1..size]. Negative indices count backward from the 
	end of the queue: the -1'th element is the last element, the -2'th 
	element is the second to last element, and the -size'th is the first 
	element.
	
	param ind: in +/-[1..size] default=1
	
	error: if ind not in valid range

	synonym: __call
endlongstringliteral
function QueueVector:get(ind)
	ind = ind and translate(self, ind) or self.mStart
	if ind < 1 or ind > self.mSize then
		error("Index: "..ind.." out of range, queue size is: "..self.mSize,2)
	end
	return self[self.mStart+ind-1]
end
apidoc[QueueVector.get] = doc

QueueVector.__call = QueueVector.get

doc = beginlongstringliteral
	QueueVector:set(ind, val) - Sets the value at index ind equal to val and
	  returns the previous value.
	
	param ind: in +/-[1..size]
	returns: the previous value stored at index ind

endlongstringliteral
function QueueVector:set(ind, val)
	assert(ind, "ind is nil, must be in range +/-[1..size]")
	ind = translate(self, ind)
	if ind < 1 or ind > self.mSize then
		error("Index: "..ind.." out of range, queue size is: "..self.mSize,2)
	end
	local old
	old, self[self.mStart+ind-1] = self[self.mStart+ind-1], val
	return old
end
apidoc[QueueVector.set] = doc


doc = beginlongstringliteral
	QueueVector:size() - Returns the number of elements in this queue. 
	
	This is also the largest valid index for a call to get().

endlongstringliteral
function QueueVector:size() return self.mSize end
apidoc[QueueVector.size] = doc


doc = beginlongstringliteral
	QueueVector:iter(start, stop) - Returns an iterator over the elements 
	  in the index range [start..stop].
	
	Example uses:
	> q = QueueVector:make(1,2,3)
	> for e in q:iter() do print(e) end
	1
	2
	3
	> q:set(2,nil)
	> for ind,e in iter.enum(q:iter(),q:size()) do print(ind,e) end
	1	1
	1	nil
	2	2
	> q:addAll{"a","b","c"}; q:set(2,2); print(q)
	[1, 2, 3, a, b, c]
	> q2 = QueueVector:make(q:iter(2,4)); print(q2)
	[2, 3, a]

endlongstringliteral
function QueueVector:iter(start, stop)
	start = start and translate(self, start) or 1
	stop = stop and translate(self, stop) or self.mSize
	start = self.mStart+start-1
	stop = self.mStart+stop-1
	if start > stop then return iter.EMPTY end
	return coroutine.wrap(function()
		for i in iter.count(start,stop) do
			coroutine.yield(self[i])
		end
	end)
end
apidoc[QueueVector.iter] = doc

doc = beginlongstringliteral
	QueueVector:__eq(other) - Returns true if both QueueVector objects are 
	  the same size and contain the same elements in the same order.

endlongstringliteral
function QueueVector:__eq(other)
	return self:size()==other:size() and iter.equals(self, other)
end
apidoc[QueueVector.__eq], apidoc["=="] = doc

doc = beginlongstringliteral
	QueueVector:test() - Unit test.

endlongstringliteral
function QueueVector:test()
	local queue = function(...) return self:make(unpack(arg)) end
	
	local q1 = queue(1,2,3,4,5)
	
	-- removals
	assert(q1:remove(2)==2) -- in the middle
	assert(q1:remove(-1)==5) -- last
	assert(q1:remove()==1) -- first
	
	assert(q1 == queue(3,4))
	
	-- adds
	assert(q1:add("hello",0)) -- at front
	assert(q1:add("goodbye",-1)) -- at end
	assert(q1:add(3.14159, 2)) -- in middle
	
	assert(q1 == queue("hello", 3, 3.14159, 4, "goodbye"))
	
	local q2 = queue(q1)
	-- subiter
	assert(queue(q2:iter(2,-2)) == queue(3,3.14159,4))
	
	-- bounds checking
	while q1:size() > 0 do q1:remove() end -- empty it
	if pcall(function() q1:remove() end) then
		error("Trying to remove from an empty queue should throw an error!")
	end
	if pcall(function() q1:add("no good",-22) end) then
		error("Invalid index should throw an error!")
	end
	
	-- set
	assert(q2:set(3, 3.5) == 3.14159)
	assert(q2 == queue("hello",3,3.5,4,"goodbye"))
	
end
apidoc[QueueVector.test] = doc

oop.mixin(QueueVector, sequences.mixins.plauralizeHelper)
oop.mixin(QueueVector, sequences.mixins.convenienceMethods)
oop.mixin(QueueVector, sequences.mixins.randomAccessHelper)

 -- Test before returning; ok if tests don't take long
return QueueVector
]],
PairingHeap=[[
local sano = require "sano"
local ordering = sano.ordering
local queue = sano.makers.queue
local heaps = sano.heaps
local oop = sano.oop
local iter = sano.iter
local utils = sano.utils
local HashSet = sano.HashSet

local PairingHeap = {cDefaultOrdering = ordering.INCREASING}

PairingHeap.documentation = {}

local apidoc = PairingHeap.documentation
local doc

apidoc.general = beginlongstringliteral
	PairingHeap - Heap implementation with constant-time merge and get-first
	  operations.
	
	This implementation has the following complexity guarantees:
	+) add, merge, get - O(1)
	+) remove - amortized O(lg(size))
	+) set - O(1) if new value is ordered before the old value, otherwise
	   amortized O(lg(size))
	
	By default, a PairingHeap uses the natural increasing order of the data,
	but any function which satisfies the ordering contract can be supplied
	to the new() method.
	
	Examples of use:
	> h1 = PairingHeap:make(1,3,5,2,4,6); print(h1)
	/1, 6, 4, 2, 5, 3\
	> node = h1:add(-1); print(h1) -- returns the node allocated
	/-1, 1, 6, 4, 2, 5, 3\
	> h1:set(node, 44); print(h1) -- adjust value, O(lg N) for decrease
	/1, 6, 4, 2, 5, 3, 44\
	> print(h1:remove()) -- removes and returns top/first element, O(lg N)
	1
	> print(h1:get()) -- the first element (equivalent to getFirst())
	2
	> h1:merge(heap(6,2,3,9)) -- merge in another heap, O(1)
	> print(vector(h1:extractFirstK())) -- heapsort
	[2, 2, 3, 3, 4, 5, 6, 6, 9, 44]
	
	See: http://www.cise.ufl.edu/~sahni/dsaaj/enrich/c13/pairing.htm for
	  more information on pairing heaps

endlongstringliteral

local function HeapNode(val)
	return {val=val} -- child=child, prev=prev, next=next
end

-- private function used to merge/add
local function compareAndLink(orderFn, nodeA, nodeB)
	-- we assume both nodes are roots
	nodeA.prev, nodeA.next, nodeB.prev, nodeB.next = nil, nil, nil, nil
	-- we require that neither node has any common children
	local first, second = nodeA, nodeB
	if orderFn(nodeA.val, nodeB.val)==nodeB.val then 
		first, second = nodeB, nodeA
	end
	first.child, second.prev, second.next = second, first, first.child
	if second.next then second.next.prev = second end
	return first
end

-- used for delete/set operations
local function detatch(self, node)
	if node.prev and node.prev.child==node then -- this is the leftmost child
		node.prev.child = node.next 
		if node.next then node.next.prev = node.prev end
	elseif node.prev then -- this is just a standard linked-list delete
		node.prev.next = node.next
		if node.next then node.next.prev = node.prev end
	elseif node == self.mRoot then
		self.mRoot = nil
	end
	node.prev, node.next = nil, nil
	local tmpHeap = getmetatable(self):new(self.mOrderFn)
	tmpHeap.mRoot = node
	return tmpHeap
end

-- core operation for set() if new val is comes before the old val according
-- to the ordering function
local function removeInternal(self, node)
	local sizeToBe = self.mSize - 1
	local subtree = detatch(self, node)
	subtree:remove()
	self:merge(subtree)
	self.mSize = sizeToBe
	return node.val
end

doc = beginlongstringliteral
	PairingHeap:new(orderFn) - Constructs a new PairingHeap using the
	  supplied ordering function (default is natural increasing order).
	  
	If no ordering function is specified, the ordering function will become
	PairingHeap.cDefaultOrdering, which is by default ordering.INCREASING.
	Thus the heap created is a so-called 'min-heap': get() will return the
	smallest element. By setting PairingHeap.cDefaultOrdering to 
	ordering.DECREASING or by explicitly passing ordering.DECREASING
	to this method, get() will return the largest element.
	
	param orderFn: function meeting the *ordering* contract

endlongstringliteral
function PairingHeap:new(orderFn)
	local obj = {}
	obj.mSize = 0
	obj.mOrderFn = orderFn or self.cDefaultOrdering
	obj.cDefaultOrdering = obj.mOrderFn
	setmetatable(obj, self)
	self.__index = oop.loadOnFirstUse(self)
	self.mSize = 0
	return obj
end
apidoc[PairingHeap.new] = doc

doc = beginlongstringliteral
	PairingHeap.cDefaultOrdering - The default ordering for elements in a 
	  PairingHeap (initially ordering.INCREASING)

endlongstringliteral
apidoc[PairingHeap.cDefaultOrdering] = doc

local function addNode(self, node)
	--self:assertInvariants()
	self.mRoot = self.mRoot and compareAndLink(self.mOrderFn, self.mRoot, node) or node
	self.mSize = self.mSize + 1
	--self:assertInvariants()
	return node
end

doc = beginlongstringliteral
	PairingHeap:add(val) - Adds val to this PairingHeap.
	
	param val: an element which is totaly ordered wrt all other elements in 
	 the heap
	
	returns: the allocated node in the heap -- this reference can be passed
	 as the first argument to the remove() or set() methods
	
	time complexity: O(1)

endlongstringliteral
function PairingHeap:add(val)
	local node = HeapNode(val)
	return addNode(self, node)
end
apidoc[PairingHeap.add] = doc

doc = beginlongstringliteral
	PairingHeap:get() - Returns the first/topmost element in the heap.
	
	The heap is not modified by the call; this merely 'peeks' at the top
	element.
	
	time complexity: O(1)
	synonyms: getFirst

endlongstringliteral
function PairingHeap:get()
	return self.mRoot and self.mRoot.val
end
apidoc[PairingHeap.get] = doc

PairingHeap.getFirst = PairingHeap.get -- synonym

doc = beginlongstringliteral
	PairingHeap:set(node, newVal) - Adjusts the value stored at node to be
	  newVal and reestablishes the heap property.
	
	param node: a node returned by a prior call to add()
	time complexity: O(1) if newVal comes before the old val according to
	  the ordering function of the heap; O(lg(size)) if newVal comes after

endlongstringliteral
function PairingHeap:set(node, newVal)
	local oldSize = self.mSize
	local t = detatch(self, node)
	local old = node.val
	if self.mOrderFn(node.val, newVal) == newVal then -- constant time
		t.mRoot.val = newVal; self:merge(t)
	else
		-- maintain the node reference for later use
		t:remove(); node.prev, node.next, node.child = nil, nil, nil
		node.val=newVal; addNode(t, node)
		self:merge(t)
	end
	self.mSize = oldSize -- the detatch/merge musses up the mSize; restore it
	return old
end
apidoc[PairingHeap.set] = doc

doc = beginlongstringliteral
	PairingHeap:update(node) - Removes and reinserts node.
	
	This method is used for re-establishing the heap property after the
	value stored at node has been altered.

endlongstringliteral
function PairingHeap:update(node)
	self:remove(node)
	node.child, node.prev, node.next = nil, nil, nil
	addNode(self, node)
end
apidoc[PairingHeap.update] = doc

doc = beginlongstringliteral
	PairingHeap:remove(node) - Removes the node from this PairingHeap and
	  returns the value that was stored at that node.
	  
	param node: a node returned by a prior call to add()
	time complexity: amortized O(lg(size))

endlongstringliteral
function PairingHeap:remove(node)
	--self:assertInvariants()
	if not self.mRoot then error("Cannot remove from empty heap.",2) end
	if node and node ~= self.mRoot then return removeInternal(self,node) end
	local toReturn = self.mRoot.val
	local c, q = self.mRoot.child, queue();
	while c do q:add(c); c=c.next end	
	while q:size() > 1 do -- multipass pairing heap
		q:add(compareAndLink(self.mOrderFn, q:remove(), q:remove()))
	end
	self.mRoot = q:size() > 0 and q:get(1) or nil
	self.mSize = self.mSize - 1
	--self:assertInvariants()
	return toReturn
end
apidoc[PairingHeap.remove] = doc

doc = beginlongstringliteral
	PairingHeap:replaceRoot(newRoot) - 
endlongstringliteral
function PairingHeap:replaceRoot(newRoot)
	newRoot.next, newRoot.prev, newRoot.child = nil, nil, self.mRoot.child
	if newRoot.child then newRoot.child.prev = newRoot end
	local oldRoot = self.mRoot; self.mRoot = newRoot
	oldRoot.prev, oldRoot.next, oldRoot.child = nil, nil, nil
	assert(self.mRoot)
	return oldRoot
end
apidoc[PairingHeap.replaceRoot] = doc

doc = beginlongstringliteral
	PairingHeap:addNode(node) - Adds this node to the heap and returns it.
	
	This method is part of the contract which enables a PairingHeap to be 
	used in a MinMaxHeap; it should not normally be used by clients.
	
	param node: a node returned by a prior call to add()
	returns the added node

endlongstringliteral
PairingHeap.addNode = addNode
apidoc[PairingHeap.addNode] = doc

doc = beginlongstringliteral
	PairingHeap:merge(pheap) - Merges another PairingHeap, pheap, in-place
	  into this heap.
	
	This heap is modified and pheap is invalidated as a result of this call.
	
	returns: this PairingHeap (to allow chaining)
	time complexity: O(1)

endlongstringliteral
function PairingHeap:merge(pheap)
	--assert(self.mOrderFn==pheap.mOrderFn, "Heaps do not share the same ordering function.")
	--self:assertInvariants()
	if pheap.mRoot then
		self.mRoot = self.mRoot and compareAndLink(self.mOrderFn, self.mRoot, pheap.mRoot) or pheap.mRoot
	end
	self.mSize = self.mSize + pheap.mSize
	oop.invalidate(pheap, PairingHeap, "Heap is no longer valid; it has been merged into another heap.")
	--self:assertInvariants()
	return self -- to allow chaining
end
apidoc[PairingHeap.merge] = doc

doc = beginlongstringliteral
	PairingHeap:iter(node) - Returns an iterator over the values in the
	  heap, starting from node, which defaults to the root.
	
	The elements of the iteration are not returned in sorted order,
	although the first element returned will by default be the topmost
	element of the heap.
	
	param node: a node returned by a prior call to add() (default is root
	  of heap)

endlongstringliteral
function PairingHeap:iter(node)
	return coroutine.wrap(function()
		for n in self:nodeIter(node) do coroutine.yield(n.val) end
	end)
end
apidoc[PairingHeap.iter] = doc

doc = beginlongstringliteral
	PairingHeap:nodeIter(node) - Returns an iterator over the NODES in the 
	  heap, starting from node (default is root).
	
   The nodes returned by the iteration could be passed to the set() or 
	remove() methods, although it is not safe to modify the heap while
	iterating.
	
	param node: a node returned by a prior call to add() (default is root)

endlongstringliteral
function PairingHeap:nodeIter(node)
	return iter.preorder({node or self.mRoot}, 
		function(n)
			local neighbors = {} 
			if n.child then table.insert(neighbors, n.child) end
			if n.next then table.insert(neighbors, n.next) end
			return neighbors
		end)
end
apidoc[PairingHeap.nodeIter] = doc

doc = beginlongstringliteral
	PairingHeap:size() - Returns the number of elements in the PairingHeap.

endlongstringliteral
function PairingHeap:size() return self.mSize end
apidoc[PairingHeap.size] = doc

doc = beginlongstringliteral
	PairingHeap:__eq(otherheap) - Returns true if both heaps have the same
	  size and both self:iter() and otherheap:iter() return the same
	  sequence of values.

endlongstringliteral
function PairingHeap:__eq(otherheap)
	return self:size()==otherheap:size() and iter.equals(self:iter(),otherheap:iter())
end
apidoc[PairingHeap.__eq],  apidoc["=="] = doc


function PairingHeap:assertInvariants()
	-- Ensure there are no cycles in the tree
	local unique = sano.HashSet:uniqueFilter()
	local traversal = iter.preorder({self.mRoot},
		function(n)
			local neighbors = {}
			if n.child then table.insert(neighbors, n.child) end
			if n.next then table.insert(neighbors, n.next) end
			return neighbors
		end,
		function(e) return unique(e) and true or error("Cycle in tree.",3) end
	)
	repeat until not traversal()
end

function PairingHeap:debug(node, prefix)
	prefix = prefix or ""
	node = node or self.mRoot
	if not node then return "( )" end
	local str = sano.makers.vector()
	str:add(prefix..tostring(node.val)..(node.child and "\n" or ""))
	local child = node.child
	while child~=nil do
		str:add(self:debug(child, prefix.."  ")..(child.next and "\n" or ""))
		child=child.next
	end
	return str:asString()
end

doc = beginlongstringliteral
	PairingHeap:test() - Unit test.

endlongstringliteral
function PairingHeap:test()
	local heap = function(...) return self:make(unpack(arg)) end
	local h1 = heap(1,3,5,6,4,2)
	local node = h1:add(-1)
	assert(h1.mRoot == node)
	assert(node.val == -1)
	assert(h1:size()==7, "Size is: "..h1:size())
	
	assert(h1:set(node,22)==-1) -- test adjust up
	assert(h1:size()==7, "Size is: "..h1.mSize)
   assert(h1:set(node,-1)==22) -- test adjust down
	assert(h1:set(node,5.5)) -- adjust into middle of heap
	
	assert(h1:remove(node)==5.5)
	assert(h1:remove()==1)
	
	local h2 = heap(2.5, 3.5, 4.5, 5.5)
	h1:merge(h2)
	if pcall(function() h2:size() end) then
		error("h2 should have been invalidated by the merge")
	end
	assert(h1:size()==9)
	assert(ordering.isSorted(h1:extractFirstK(4)))
	
	-- test of update
	local h3 = heap()
	for i=1,500 do h3:add(math.random(1,10)) end
	h3:assertInvariants()
	local h3Nodes = queue(h3:nodeIter())
	for node in h3Nodes:iter() do
		node.val = math.random(1,4)
		h3:update(node)
	end
	h3:assertInvariants()
	assert(ordering.isSorted(h3:extractFirstK()))
end
apidoc[PairingHeap.test] = doc

oop.mixin(PairingHeap, heaps.mixins.convenienceMethods)


return PairingHeap
]],
Multimap=[[
local sano = require 'sano'
local oop = sano.oop
local iter = sano.iter
local maps = sano.maps
local Vector = sano.Vector

local apidoc = {}
local doc
local Multimap = {documentation = apidoc}
Multimap.cDefaultMapType = sano.HashMap
Multimap.cDefaultValuesType = Vector

apidoc.general = beginlongstringliteral
	Multimap - Map in which each key maps to a collection of values.
	
	A Multimap is a map in which each key maps to a collection of values. 
	Multimaps merely provide a few convenience methods for behavior that 
	is achievable with a regular Map. 
	
	When add(key, val) is called, Multimap first checks to see if there is
	already a collection stored against key. If yes, val is added to that
	collection. If no (val is the very first value to be stored against
	key), then a new collection is allocated and val is added to that
	collection. For example:
	
	> m = Multimap:new()
	> m:add("R. White","'Tater Salad")
	> m:add("R. White", "The 'Tater")
	> m:add("G. Pitcher", "The Ace")
	> m:addValues("G. Pitcher", {"Ace", "The Kid"})
	> print(m)
	{G. Pitcher=["The Ace", "Ace", "The Kid"], 
	 R. White  =["'Tater Salad", "The 'Tater"]}
	
	Both the map implementation and values collection type can be specified
	in the Multimap constructor. By default, the map implementation is a
	HashMap and the values collection is a Vector. But, for instance:
	
	> m = Multimap:new(HashMap, SkipSet)
	...
	> m:add("R. White", "The 'Tater")
	> m:add("R. White", "The 'Tater")
	> print(m:get("R. White")) -- The 'Tater only appears once, since the
	{"'Tater Salad", "The 'Tater"} -- values collection is a set
	
	Multimaps provide several other convenience methods, see the method
	documentation.

endlongstringliteral

doc = beginlongstringliteral
	Multimap:new(mapType, valuesCollection) - Creates and returns a new 
	  Multimap, using the supplied map implementation and values 
	  collection.
	
	mapType defaults to self.cDefaultMapType, which is initially HashMap,
	and valuesCollection defaults to self.cDefaultValuesType, which is
	initally Vector.

endlongstringliteral
function Multimap:new(map, valuesCollection)
	local obj = {}
	setmetatable(obj, self)
	self.__index = oop.loadOnFirstUse(self)
	obj.mMap = map or self.cDefaultMapType:new()
	obj.mValuesPrototype = valuesCollection or self.cDefaultValuesType
	obj.mSize = 0
	return obj
end
apidoc[Multimap.new] = doc

doc = beginlongstringliteral
	Multimap:decorate(map, valuesType) - Decorates the supplied map's add
	  method to have multimap behavior.
	
	After this method is called, map.add(key,val) will first check to see
	if there is a collection stored against key. If yes, val is added to 
	that collection. If not, val is added to a newly allocated collection
	of type valuesType and the new collection is stored against key.
	
	It also adds a method, valSize, which returns the total number of 
	values in the map.

endlongstringliteral
function Multimap:decorate(map, valuesType)
	local oldAdd = map.add
	local mmap = self
	assert(map:size()==0, "Cannot decorate a non-empty map.")
	map.mValSize = 0
	map.add = function(self, key, val)
		local t = self:get(key) or (valuesType or mmap.cDefaultValuesType):new()
		local success = t:add(val)
		oldAdd(self, key, t)
		self.mValSize = self.mValSize + (success and 1 or 0)
		return success
	end
	map.valSize = function(self) return self.mValSize end
end
apidoc[Multimap.decorate] = doc

doc = beginlongstringliteral
	Multimap:add(key, val) - Adds val to the collection stored against key
	  or to a new collection if no values are yet stored against key.
	
	Examples:
	{a=[1,2]}:add(a,3) results in {a=[1,2,3]}
	{}:add(a,3) results in {a=[3]}

endlongstringliteral
function Multimap:add(key, val)
	local t = self:get(key) or self.mValuesPrototype:new()
	local success = t:add(val)
	self.mMap:add(key, t)
	self.mSize = success and self.mSize + 1 or self.mSize
	return success
end
apidoc[Multimap.add] = doc

doc = beginlongstringliteral
	Multimap:addValues(key, vals) - Equivalent to the loop: 
	  for v in iter(vals) do self:add(key,v) end

endlongstringliteral
function Multimap:addValues(key, vals)
	for v in iter(vals) do
		self:add(key, v)
	end
end
apidoc[Multimap.addValues] = doc

doc = beginlongstringliteral
	Multimap:allocate(key) - Allocate an empty collection of values for
	  key.

endlongstringliteral
function Multimap:allocate(key)
	self.mMap:add(key, self.mValuesPrototype:new())
end
apidoc[Multimap.allocate] = doc

doc = beginlongstringliteral
	Multimap:get(key) - Returns the collection of values stored against
	  key, or nil if no mapping exists.
	
	Synonym: operator () ( so a:get(b) == a(b) )
	Examples:
	{a=[1,2,3]}:get(a) == [1,2,3]
	{a=[1,2,3]}:get(b) == nil

endlongstringliteral
function Multimap:get(key)
	return self.mMap:get(key)
end
apidoc[Multimap.get] = doc


Multimap.__call = Multimap.get

doc = beginlongstringliteral
	Multimap:iter([key]) - Returns an iterator over this map, or, if key
	  is specified, over the values collection stored against key.
	
	If key is specified but maps to nil, an empty iterator is returned.

endlongstringliteral
function Multimap:iter(key)	
	if self.mSize == 0 then return iter.EMPTY end
	if key then 
		local t = self.mMap:get(key)
		return t and t:iter() or iter.EMPTY
	else
		return self.mMap:iter()
	end
end
apidoc[Multimap.iter] = doc

doc = beginlongstringliteral
	Multimap:allValsIter() - Returns an iterator over all the values
	  stored in this map.
	
	Example:
	mmap = {a=[1,2,3],b=[4,5,6]}
	print( vector(mmap:allValsIter()) )
	[1, 2, 3, 4, 5, 6]

endlongstringliteral
function Multimap:allValsIter()
	return iter.chain(iter.exchange(self:iter()))
end
apidoc[Multimap.allValsIter] = doc

doc = beginlongstringliteral
	Multimap:remove(key [,value]) - Remove all values stored against key,
	  or, if value is specified, remove only value in the collection stored
	  against key.

endlongstringliteral
function Multimap:remove(key, value)
	if not value then 
		local vals = self.mMap:remove(key)
		self.mSize = self.mSize - (vals and vals:size() or 0)
		return vals
	else
		local vals = self:get(key)
		if not vals then return nil
		else
			assert(vals.removeElement, "This method expects the values collection to have a removeElement method.")
			local removed = vals:removeElement(value)
			self.mSize = self.mSize - (removed and 1 or 0)
			return removed
		end
	end
end
apidoc[Multimap.remove] = doc

doc = beginlongstringliteral
	Multimap:contains(key) - Returns true iff self:get(key) ~= nil

endlongstringliteral
function Multimap:contains(key)
	return self.mMap:contains(key)
end
apidoc[Multimap.contains] = doc

doc = beginlongstringliteral
	Multimap:size() - Returns the number of keys in this Multimap.
	
	Multimap:valSize() returns the number of values in this Multimap.

endlongstringliteral
function Multimap:size() return self.mMap:size() end
apidoc[Multimap.size] = doc


doc = beginlongstringliteral
	Multimap:valSize() - Returns the number of values in this Multimap.
	
	Multimap:size() returns the number of keys in this Multimap.

endlongstringliteral
function Multimap:valSize() return self.mSize end
apidoc[Multimap.valSize] = doc

doc = beginlongstringliteral
	Multimap:__eq(other) - Returns true if self and other both have the
	  same set of keys and collections of values.

endlongstringliteral
function Multimap:__eq(other)
	return self:valSize()==other:valSize() and maps.unorderedEquals(self, other)
end
apidoc[Multimap.__eq] = doc

doc = beginlongstringliteral
	Multimap:test() - Unit test.

endlongstringliteral
function Multimap:test()
	local multimap = function(...) return self:make(unpack(arg)) end
	local vector = sano.makers.vector
	local set = sano.makers.set
	local count = iter.count
	local m = multimap(iter.chain(count(10),count(6,15)), count(20))
	assert(m == multimap(iter.chain(count(10),count(6,15)), count(20)))
	assert(m(6)==vector(6,11),tostring(m(6)).." "..tostring(vector(6,11)))

	assert(vector(m:iter(6)) == vector(6,11))
	assert(m:valSize()==20)
	assert(m:size()==15)

	assert(set(m:allValsIter())==set(count(20)))
	assert(m:remove(6)==vector(6,11))
	assert(m:valSize()==18)
	assert(m:size()==14)
	assert(m:remove(7,12)==12)
	assert(m(7)==vector{7})

	local m2 = sano.HashMap:new()
	self:decorate(m2)
	m2:add(1,1); m2:add(1,2)
	assert(m2:get(1)==vector(1,2))
end
apidoc[Multimap.test] = doc

oop.mixin(Multimap, maps.mixins.convenienceMethods)
oop.mixin(Multimap, maps.mixins.plauralizeHelper)


return Multimap
]],
}
-- -------------------------------------------

local function doNothing() end
setmetatable(autoloader, autoloader)

local function load(t, name)
	if not archive[name] then
		error(name..' not found in archive')
	end
	local text = archive[name]
	text = string.gsub(
		               string.gsub(text, 'beginlongstringliteral', '[['), 
		                  'endlongstringliteral', ']]')
	t[name] = assert(loadstring(text)())

	local loader = onLoad or doNothing
	loader(name, rawget(t,name));
	return rawget(t, name)
end

autoloader.__index = function(t, name)
	return load(t, name)
end

function autoloader.loadAll()
	for k,_ in pairs(archive) do load(autoloader, k) end
end

autoloader.source = archive
return autoloader
