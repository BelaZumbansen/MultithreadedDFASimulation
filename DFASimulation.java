import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Random;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class DFASimulation {
	
	private static class SpeculativeThread extends Thread {
		
		/* Thread identifier */
		private int threadNum;
		
		/** Sections of input for this thread to handle */
		private int startOfSection;
		private int endOfSection;
		
		/** Point at which we are sure of the state */
		private int convergenceIndex;
		
		/** List to keep track of possible states until we are sure */
		private ArrayList<Integer> possibleStates;
		
		public SpeculativeThread(int threadNum, int start, int end) {
			this.threadNum = threadNum;
			this.startOfSection = start;
			this.endOfSection = end;

			// When we start a speculative thread all states are possible
			possibleStates = new ArrayList<Integer>();
			for (int i = 1; i <= 6; i++) {
				possibleStates.add(i);
			}
		}
		
		@Override
		public void run() {
			
			// Attempt to find the convergence point
			for (int i = startOfSection; i < endOfSection; i++) {
				
				char c = begin[i];
				
				ArrayList<Integer> possibleFollow = new ArrayList<>();
				
				// Narrow down possible states
				for (Integer possibleState : possibleStates) {
					
					Integer potentialNext = dfaAccept(possibleState, c);
					if (!possibleFollow.contains(potentialNext)) {
						possibleFollow.add(dfaAccept(possibleState, c));
					}
				}
				
				// If we have successfully narrowed down the possible states
				if (possibleFollow.size() == 1 && possibleFollow.get(0) == 1) {
					startOfSection = i + 1;
					convergenceIndex = i;
					break;
				} else {
					
					// Update the possible states and move on to next character
					possibleStates = new ArrayList<Integer>();
					for (Integer possible : possibleFollow) {
						if (!possibleStates.contains(possible)) {
							possibleStates.add(possible);
						}
					}
				}
				
			}
			
			int lastEndpoint = convergenceIndex;
			int curState = 1;
			
			// Iterate through the rest of the string, passing it through our DFA simulation
			for (int i = startOfSection; i < endOfSection; i++) {
				
				// Retrieve character
				char c = begin[i];
				
				// Pass into DFA
				int nextState = dfaAccept(curState, c);
				
				// If we have reached a failure point
				if (nextState == 1) {
					
					// If in an accept state mark a new endpoint and place a blank
					if (curState == 6) {
						
						lastEndpoint = i - 1;
						result[i] = ' ';
						curState =1;
					} 
					// If are already at the start move on
					else if (curState == 1) {
						result[i] = ' ';
						curState = 1;
					} else {
						
						// Clear remnants since we know this section cant be accepted
						int ndx = lastEndpoint + 1;
						
						while (ndx < i) {
							result[ndx] = ' ';
							ndx++;
						}
						
						// Make sure we do not lose a potential start of next match
						switch (c) {
							case 'x':
							case '.':
								result[i] = ' ';
								curState = 1;
								break;
							case '-':
								result[i] = '-';
								curState = 3;
								break;
							case '0':
								result[i] = '0';
								curState = 2;
								break;
							default:
								result[i] = c;
								curState = 4;
								break;
						}
					}
				} 
				else {
					// Update index in result array and update state
					result[i] = c;
					curState = nextState;
				}
			}
			
			// Set the convergence index for this thread
			convergedNdx[threadNum - 1] = convergenceIndex;
			
			// Any thread waiting for the convergence index can now retrieve it
			doneConverging.get(threadNum-1).release();
			
			// If we have reached the end make sure we have no straggler characters
			if (t == threadNum) {
				
				int ndx = lastEndpoint + 1;
				
				// If we are in an accept state currently, leave it
				while (ndx < result.length && curState != 6) {
					result[ndx] = ' ';
					ndx++;
				}
			} else {
				
				// If we are not the last section wait for the next section to converge
				try  {
					doneConverging.get(threadNum).acquire();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				
				int nextConverged = convergedNdx[threadNum];
				
				// Handle end of this section and start of next
				for (int i = lastEndpoint + 1; i < nextConverged; i++) {
					
					char c = begin[i];
					
					int nextState = dfaAccept(curState, c);
					
					if (nextState == 1) {
						
						if (curState == 6) {
							
							lastEndpoint = i - 1;
							result[i] = ' ';
							curState =1;
						} else if (curState == 1) {
							result[i] = ' ';
							curState = 1;
						} else {
							
							int ndx = lastEndpoint + 1;
							
							while (ndx < i) {
								result[ndx] = ' ';
								ndx++;
							}
							
							switch (c) {
								case 'x':
								case '.':
									result[i] = ' ';
									curState = 1;
									break;
								case '-':
									result[i] = '-';
									curState = 3;
									break;
								case '0':
									result[i] = '0';
									curState = 2;
									break;
								default:
									result[i] = c;
									curState = 4;
									break;
							}
						}
					} else {
						result[i] = c;
						curState = nextState;
					}
				}
				
				int ndx = lastEndpoint + 1;
				
				while (ndx < nextConverged && curState != 6) {
					result[ndx] = ' ';
					ndx++;
				}
			}
		}
	}
	
	private static class NormalThread extends Thread {
		
		/** Section delimiters */
		int startOfSection;
		int endOfSection;
		
		private NormalThread(int startOfSection, int endOfSection) {
			this.startOfSection = startOfSection;
			this.endOfSection = endOfSection;
		}
		
		@Override
		public void run() {
			
			int curState = 1;
			int lastEndpoint = -1;
			
			for (int i = startOfSection; i < endOfSection; i++) {
				
				char c = begin[i];
				
				int nextState = dfaAccept(curState, c);
				
				if (nextState == 1) {
					
					if (curState == 6) {
						
						lastEndpoint = i - 1;
						result[i] = ' ';
						curState =1;
					} else if (curState == 1) {
						result[i] = ' ';
						curState = 1;
					} else {
						
						int ndx = lastEndpoint + 1;
						
						while (ndx < i) {
							result[ndx] = ' ';
							ndx++;
						}
						
						switch (c) {
							case 'x':
							case '.':
								result[i] = ' ';
								curState = 1;
								break;
							case '-':
								result[i] = '-';
								curState = 3;
								break;
							case '0':
								result[i] = '0';
								curState = 2;
								break;
							default:
								result[i] = c;
								curState = 4;
								break;
						}
					}
				} else {
					result[i] = c;
					curState = nextState;
				}
			}
			
			if (t == 0) {
				
				int ndx = lastEndpoint + 1;
				
				while (ndx < result.length && curState != 6) {
					result[ndx] = ' ';
					ndx++;
				}
			} else {
				
				try  {
					doneConverging.get(0).acquire();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				
				int nextConverged = convergedNdx[0];
				
				for (int i = lastEndpoint + 1; i < nextConverged; i++) {
					
					char c = begin[i];
					
					int nextState = dfaAccept(curState, c);
					
					if (nextState == 1) {
						
						if (curState == 6) {
							
							lastEndpoint = i - 1;
							result[i] = ' ';
							curState =1;
						} else if (curState == 1) {
							result[i] = ' ';
							curState = 1;
						} else {
							
							int ndx = lastEndpoint + 1;
							
							while (ndx < i) {
								result[ndx] = ' ';
								ndx++;
							}
							
							switch (c) {
								case 'x':
								case '.':
									result[i] = ' ';
									curState = 1;
									break;
								case '-':
									result[i] = '-';
									curState = 3;
									break;
								case '0':
									result[i] = '0';
									curState = 2;
									break;
								default:
									result[i] = c;
									curState = 4;
									break;
							}
						}
					} else {
						result[i] = c;
						curState = nextState;
					}
				}
				
				int ndx = lastEndpoint + 1;
				
				while (ndx < nextConverged && curState != 6) {
					result[ndx] = ' ';
					ndx++;
				}
			}
		}
	}
	
	private static int t;
	private static char begin[];
	private static char result[];
	
	private static ArrayList<Semaphore> doneConverging;
	private static int[] convergedNdx;
	private static ArrayList<SpeculativeThread> specThreads;
	private static final int STRING_LENGTH = 69420420;

	/**
	 * Handle command line parameters
	 */
	public static boolean opts(String[] args) {
		
		try {
			
			if (args.length != 1) {
				System.out.println("Please pass a parameter to control the number of speculative threads.");
				return false;
			}
			
			t = Integer.parseInt(args[0]);
			
			if (t > 8) {
				System.out.println("Program does not support a thread count higher than 8.");
			}
		} catch (Exception e) {
			System.err.println(e);
			return false;
		}
		return true;
	}

	
	public static void main(String[] args) {
		
		// Process options
		if (!opts(args)) {
			return;
		}
		
		// Initialize arrays for the input to the simulation and the result
		begin = new char[STRING_LENGTH];
		result = new char[STRING_LENGTH];
		
		// Initialize simulation arrays
		specThreads = new ArrayList<>();
		convergedNdx = new int[t];
		doneConverging = new ArrayList<>();
		
		// Iniitialize semaphores to 0 so that a thread must release before the first
		// acquire -> See MUTEX lock
		for (int i = 0; i < t; i++) {
			doneConverging.add(new Semaphore(0));
		}
		
		// Generate a string of the appropriate length
		String toProcess = generateString(STRING_LENGTH);
		
		System.out.println(toProcess);
		
		// Fill the input array
		for (int i = 0; i < toProcess.length(); i++) {
			begin[i] = toProcess.charAt(i);
		}
		
		// Calculate characters to process for each spec thread
		int charsForEach = STRING_LENGTH / (t + 1);
		
		// Calculate characters remaining for the main thread
		int charsForFirst = (STRING_LENGTH) - t*charsForEach;	
		
		// Initialize Normal thread
		NormalThread baseThread = new NormalThread(0, charsForFirst);
		
		// Initialize all spec threads with appropriate string sections
		for (int i = 1; i <= t; i++) {
			
			int startingPoint = charsForFirst + (i - 1)*charsForEach;
			SpeculativeThread specThread = new SpeculativeThread(i, startingPoint, startingPoint + charsForEach);
			specThreads.add(specThread);
		}
		
		// start timer
		long simulationStart = System.currentTimeMillis();
		
		// Start threads
		baseThread.start();
		for (SpeculativeThread specThread : specThreads) {
			specThread.start();
		}
		
		// Wait for threads to complete execution
		try {
			
			baseThread.join();
			
			for (SpeculativeThread specThread : specThreads) {
				specThread.join();
			}
		} catch (InterruptedException e) {
			System.err.println(e);
			System.out.println("Interruped while attempting to join all threads.");
		}
		
		// end timer
		long simulationEnd = System.currentTimeMillis();
		
		// Print result
		for (int i = 0; i < result.length; i++) {
			System.out.print(result[i]);
		}
		
		System.out.println("\nSimulation with " + t + " threads took: " + (simulationEnd - simulationStart) + "ms");
	}
	
	/**
	 * Handle DFA input based on current state
	 * 
	 * @param state current state of DFA
	 * @param input input to handle
	 * @return resultant state
	 */
	private static int dfaAccept(int state, char input) {
		
		switch (state) {
			case 1: return state1(input);
			case 2: return state2(input);
			case 3: return state3(input);
			case 4: return state4(input);
			case 5: return state5(input);
			case 6: return state6(input);
			default: return 0;
		}
	}
	
	/**
	 * Return result of input when DFA is in State 1 
	 */
	private static int state1(char input) {
		
		switch (input) {
		
			case '-': 
				return 3;
			case '0': 
				return 2;
			case '1':
			case '2':
			case '3':
			case '4':
			case '5':
			case '6':
			case '7':
			case '8':
			case '9': 
				return 4;
			case '.': 
			case 'x': 
				return 1;
			default:
				return 1;
		}
	}
	
	/**
	 * Return result of input when DFA is in State 2 
	 */
	private static int state2(char input) {
		
		switch (input) {
		
			case '0': 
			case '1':
			case '2':
			case '3':
			case '4':
			case '5':
			case '6':
			case '7':
			case '8':
			case '9':
			case '-':
			case 'x':
				return 1;
			case '.': 
				return 5;
			default:
				return 1;
		}
	}
	
	/**
	 * Return result of input when DFA is in State 3 
	 */
	private static int state3(char input) {
		
		switch (input) {
		
			case '0':
				return 2;
			case '1':
			case '2':
			case '3':
			case '4':
			case '5':
			case '6':
			case '7':
			case '8':
			case '9':
				return 4;
			case '-':
			case 'x':
			case '.': 
				return 1;
			default:
				return 1;
		}
	}
	
	/**
	 * Return result of input when DFA is in State 4 
	 */
	private static int state4(char input) {
		
		switch (input) {
		
			case '0':
			case '1':
			case '2':
			case '3':
			case '4':
			case '5':
			case '6':
			case '7':
			case '8':
			case '9':
				return 4;
			case '-':
			case 'x':
				return 1;
			case '.': 
				return 5;
			default:
				return 1;
		}
	}
	
	/**
	 * Return result of input when DFA is in State 5 
	 */
	private static int state5(char input) {
		
		switch (input) {
		
			case '0':
			case '1':
			case '2':
			case '3':
			case '4':
			case '5':
			case '6':
			case '7':
			case '8':
			case '9':
				return 6;
			case '-':
			case 'x':
			case '.': 
				return 1;
			default:
				return 1;
		}
	}
	
	/**
	 * Return result of input when DFA is in State 6 
	 */
	private static int state6(char input) {
		
		switch (input) {
		
			case '0':
			case '1':
			case '2':
			case '3':
			case '4':
			case '5':
			case '6':
			case '7':
			case '8':
			case '9':
				return 6;
			case '-':
			case 'x':
			case '.': 
				return 1;
			default:
				return 1;
		}
	}
	
	/**
	 * Convenience method to generate a string of length n
	 */
	private static String generateString(int n) {
		
		String validChars = "-0123456789.x";
		StringBuilder generate = new StringBuilder();
		Random rnd = new Random(260865725);
		
		while (generate.length() < n) {
			int ndx = (int) (rnd.nextFloat() * 13);
			generate.append(validChars.charAt(ndx));
		}
		
		return generate.toString();
	}
}
