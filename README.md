# MultithreadedDFASimulation
Problem Description:\
A subset of floating point numbers is represented by the following regular expression:\
-?(0|[1-9][0-9]*)\.[0-9]+\
which maps to deterministic finite automaton.

Given a string and the DFA, we can then look for all longest matches. Starting at the beginning of
the string, greedily make transitions up until we cannot make a transition anymore. At that point we’ve
either matched a floating point number (we are in ther terminal state, represented by the node made of
two nested circles), or the sequence we have been looking at is not an acceptable number.

Your program should hard-code the DFA, and include a function that generates a random string
The characters should be uniformly chosen from the set:\
{-,0,1,2,3,4,5,6,7,8,9,.,x}\
Once the string is constructed, print it out to the console and convert it to an array of characters for easier
processing. The goal is then to identify all acceptable, longest matches for floating point numbers as per
the DFA, converting any unused characters into spaces.

To do this divide the array among the threads; each thread will look for matches in its fragment, blanking
characters that would not be matched if this was done sequentially from the start of the string. This faces
a problem in that the boundary between recognized floating point values may not occur at the boundaries
used for thread partitioning. To handle this, most threads must compute speculatively.

Assume we have 1 normal thread (the first/leftmost), and n speculative threads. We divide the input string
into n+ 1 pieces. The normal thread gets the first piece of the string, and performs normal matching. The
n speculative threads each apply the regular expression to their own portion of the string, but since they
are not sure what state the DFA should be in to start with, they simulate matching starting from every
possible state simultaneously. For instance, given the above DFA, a speculative thread would consider
the processing of its fragment to start in any of the 6 states and compute 6 possible outcomes.

Once the normal thread reaches the end of its input fragment i, the thread handling the next fragment
i + 1 will know the starting state it should’ve started in, and can thus merge one of its outputs with the
previous. This repeats until the matching process is completed for the entire string. Once the entire string
has been fully matched with characters changed to blanks appropriately print out the string again.

You will need a very long string, such that your 1-threaded simulation runs for at least a 50ms or more
(not including I/O or the initial string construction). Your simulation should accept a command-line
parameter for controlling the number of speculative threads
