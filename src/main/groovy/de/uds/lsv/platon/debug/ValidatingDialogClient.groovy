/*
 * Copyright 2015, Spoken Language Systems Group, Saarland University.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.uds.lsv.platon.debug;

import groovy.transform.InheritConstructors
import groovy.transform.TupleConstructor
import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode

import java.text.SimpleDateFormat
import java.util.Map.Entry
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Pattern

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

import de.uds.lsv.platon.DialogClient
import de.uds.lsv.platon.DialogWorld
import de.uds.lsv.platon.Platon
import de.uds.lsv.platon.action.IOType
import de.uds.lsv.platon.debug.interactions.AbortExpectation
import de.uds.lsv.platon.debug.interactions.Input
import de.uds.lsv.platon.debug.interactions.Interaction
import de.uds.lsv.platon.debug.interactions.ObjectDeletionExpectation
import de.uds.lsv.platon.debug.interactions.ObjectModificationExpectation
import de.uds.lsv.platon.debug.interactions.OutputExpectation
import de.uds.lsv.platon.session.DialogSession
import de.uds.lsv.platon.session.User
import de.uds.lsv.platon.session.TransactionManager.Transaction
import de.uds.lsv.platon.world.WorldObject

/**
 * A dialog client that can send input and check defined output expectations.
 * See the main method for an example.
 */
@TypeChecked
public class ValidatingDialogClient implements DialogClient, DialogWorld {
	private static final Log logger = LogFactory.getLog(ValidatingDialogClient.class.getName());
	
	@InheritConstructors
	public static class InvalidOutputError extends AssertionError {
	}
	
	@TupleConstructor
	public static class OutputEvent {
		User user;
		IOType ioType;
		String text;
		int outputId;
		
		@Override
		public String toString() {
			return "Output started (${outputId}): »${text}«";
		}
	}
	
	@TupleConstructor
	public static class OutputCompleteEvent {
		int outputId;
		
		@Override
		public String toString() {
			return "Output complete (${outputId}).";
		}
	}
	
	@TupleConstructor
	public static class AbortEvent {
		int outputId;
		User user;
		
		@Override
		public String toString() {
			return "Output aborted (${outputId}).";
		}
	}
	
	@TupleConstructor
	public static class ObjectModifiedEvent {
		Map<String,String> properties;
		
		@Override
		public String toString() {
			return "Object modified: ${properties}";
		}
	}
	
	public static class ObjectDeletedEvent {
		String objectId;
		
		@Override
		public String toString() {
			return "Object deleted:  ${objectId}";
		}
	}
	
	public static class TimedEvent {
		private static SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss.SSS");
		
		Date timestamp;
		def event;
		
		public TimedEvent(event) {
			this.timestamp = new Date(System.currentTimeMillis());
			this.event = event;
		}
			
		@Override
		public String toString() {
			return String.format(
				"[%s] %s",
				dateFormat.format(timestamp),
				event
			);
		}
	}
			
	private DialogSession session;
	private long outputDuration = 500l;
	// unmatched requested/expected interactions (output, input, object modifications/deletions)
	private LinkedList unmatchedInteractions = new LinkedList();
	private LinkedList unmatchedObservations = new LinkedList();
	private AtomicInteger nextOutputId = new AtomicInteger(1);
	private int lastCheckedOutputId = -1;
	private Set<Integer> activeOutputs = new HashSet<>();
	private Map<String,Map<String,String>> objects = new HashMap<>();
	
	private List<TimedEvent> transcript = [];
	
	@Override
	public void init(DialogSession session) {
		this.session = session;
	}
	
	/**
	 * Expect an output that is equals to a value.
	 */
	public void expect(String value) {
		OutputExpectation expectation = new OutputExpectation();
		expectation.setExpectedOutput(value);
		doExpect(expectation);
	}
	
	/**
	 * Expect an output that matches this pattern.
	 */
	public void expect(Pattern pattern) {
		OutputExpectation expectation = new OutputExpectation();
		expectation.setExpectedOutput(pattern);
		doExpect(expectation);
	}
	
	/**
	 * Expect an output for which a closure returns true.
	 */
	public void expect(Closure<Boolean> closure) {
		OutputExpectation expectation = new OutputExpectation();
		expectation.setExpectedOutput(closure);
		doExpect(expectation);
	}
	
	private synchronized void doExpect(OutputExpectation expectation) {
		if (!unmatchedObservations.isEmpty()) {
			check(expectation, unmatchedObservations.removeFirst());
		} else {
			unmatchedInteractions.add(expectation);
		}
	}

	/**
	 * Expect and outputAbort call.
	 */
	public void expectAbort(User user=null) {
		doExpect(new AbortExpectation(user));
	}
		
	private synchronized void doExpect(AbortExpectation expectation) {
		if (!unmatchedObservations.isEmpty()) {
			check(expectation, unmatchedObservations.removeFirst());
		} else {
			unmatchedInteractions.add(expectation);
		}
	}

	public void expectModification(Map<String,String> properties) {
		
	}
	
	/**
	 * Send input immediately (useful only at the beginning of a dialog).
	 */
	public synchronized void inputNow(String text, User user) {
		sendInput(new Input(text, user, true));
	}
	
	/**
	 * Send input after the last output has started.
	 */
	public synchronized void inputInterrupting(String text, User user) {
		unmatchedInteractions.add(new Input(text, user, true));
	}
	
	/**
	 * Send input after the last output is complete.
	 */
	public synchronized void inputAfterOutput(String text, User user) {
		unmatchedInteractions.add(new Input(text, user, false));
	}
	
	/**
	 * As an alternative to calling expect* and input*
	 * this method takes a list of Expectations and Inputs.
	 */
	public void setInteractions(Collection<Interaction> interactions) {
		for (Interaction interaction : interactions) {
			if (interaction instanceof Input) {
				unmatchedInteractions.add(interaction);
			} else if (interaction instanceof OutputExpectation) {
				doExpect((OutputExpectation)interaction);
			} else if (interaction instanceof AbortExpectation) {
				doExpect((AbortExpectation)interaction);
			} else {
				// ??
				throw new IllegalArgumentException();
			}
		}
	}
	
	private List getNextExpectation() {
		List<Input> nonImmediateInputs = [];
		
		def nextThing = unmatchedInteractions.removeFirst();
		while (nextThing instanceof Input) {
			sendInput((Input)nextThing);
			if (unmatchedInteractions.isEmpty()) {
				nextThing = null;
				break;
			}
			nextThing = unmatchedInteractions.removeFirst();
		}
		
		def expectation = nextThing;
		logger.info("Next expectation: ${expectation}")
		
		while (!unmatchedInteractions.isEmpty()) {
			nextThing = unmatchedInteractions.getFirst();
			if (nextThing instanceof Input) {
				unmatchedInteractions.removeFirst();
				Input nextInput = (Input)nextThing;
				if (nextInput.interrupting) {
					sendInput(nextInput);
				} else {
					nonImmediateInputs.add(nextInput);
				}
			} else {
				break;
			}
		}
		
		synchronized (unmatchedInteractions) {
			unmatchedInteractions.notifyAll();
		}
		
		return [ expectation, nonImmediateInputs ];
	}	
	
	private void sendInput(Input input) {
		logger.info("Sending input: " + input.text);
		transcript.add(new TimedEvent(input));
		session.submit({
			session.inputStarted(input.user, input.ioType);
			session.inputComplete(input.user, input.ioType, input.text, input.details);
		});
	}
	
	@Override
	@TypeChecked(TypeCheckingMode.SKIP)
	public synchronized int outputStart(User user, IOType ioType, String text, Map<String,String> details) {
		int outputId = nextOutputId.getAndIncrement();
		
		OutputEvent output = new OutputEvent(user, ioType, text, outputId);
		transcript.add(new TimedEvent(output));
		
		def nextExpectation = null;
		List<Input> inputs;
		if (!unmatchedInteractions.isEmpty()) {
			(nextExpectation, inputs) = getNextExpectation();
		}
		if (nextExpectation != null) {
			checkOutput(nextExpectation, output);
		} else {
			unmatchedObservations.add(output);
		}
		
		activeOutputs.add(outputId);
		
		session.schedule(
			{ -> outputEnded(outputId, inputs) },
			outputDuration
		);
	
		return outputId;
	}
	
	public synchronized void outputEnded(int outputId, List<Input> inputs) {
		if (activeOutputs.remove(outputId)) {
			logger.info("Output ended (${outputId}), next inputs: ${inputs}.");
			transcript.add(new TimedEvent(new OutputCompleteEvent(outputId)));
			session.outputEnded(outputId, null, 1.0);;
			inputs.collect { sendInput(it) };
			
			if (activeOutputs.isEmpty()) {
				synchronized (unmatchedInteractions) {
					unmatchedInteractions.notifyAll();
				}
			}
		}
	}
	
	@Override
	@TypeChecked(TypeCheckingMode.SKIP)
	public synchronized void outputAbort(int outputId, User user) {
		if (!activeOutputs.contains(outputId)) {
			throw new IllegalStateException("Output is not active: ${outputId}");
		}
		activeOutputs.remove(outputId);
		
		AbortEvent abort = new AbortEvent(outputId, user);
		transcript.add(new TimedEvent(abort));
		
		if (!unmatchedInteractions.isEmpty()) {
			def expectation;
			List<Input> inputs;
			(expectation, inputs) = getNextExpectation();
			checkAbort(expectation, abort);
			inputs.collect { sendInput(it) };
		} else {
			unmatchedObservations.add(abort);
		}
		
		session.outputEnded(outputId, user, 0.0);
		
		if (activeOutputs.isEmpty()) {
			synchronized (unmatchedInteractions) {
				unmatchedInteractions.notifyAll();
			}
		}
	}
	
	private void check(Object expected, Object actual) {
		if (actual instanceof AbortEvent) {
			checkAbort(expected, (AbortEvent)actual);
		} else if (actual instanceof OutputEvent) {
			checkOutput(expected, (OutputEvent)actual);
		} else if (actual instanceof ObjectModificationExpectation) {
			// TODO
		} else if (actual instanceof ObjectDeletionExpectation) {
			// TODO
		} else {
			throw new AssertionError();
		}
	}

	private void checkAbort(Object expected, AbortEvent actual) {
		if (!(expected instanceof AbortExpectation)) {
			throw new InvalidOutputError("Found outputAbort, expected: " + expected);
		}
		
		if (!((AbortExpectation)expected).matches(actual)) {
			throw new InvalidOutputError("Bad outputAbort. Found ${actual}, expected ${expected}.");
		}
	}
	
	private void checkOutput(Object expected, OutputEvent actual) {
		if (!(expected instanceof OutputExpectation)) {
			throw new InvalidOutputError("Found outputStart, expected: " + expected);
		}
		
		if (!((OutputExpectation)expected).matches(actual)) {
			throw new InvalidOutputError("Bad outputStart. Found ${actual}, expected ${expected}.");
		}
	}
	
	/**
	 * @param properties
	 *   This map is not copied!
	 */
	public void addObject(Map<String,String> properties) {
		String id = properties.get(WorldObject.FIELD_ID);
		if (id == null) {
			throw new IllegalArgumentException("Missing id (key: ${WorldObject.FIELD_ID})");
		}
		
		if (objects.containsKey(id)) {
			throw new RuntimeException("An object with the id ${id} already exists!");
		}
		
		objects.put(id, properties);
		
		if (session != null && session.isActive()) {
			session.changeNotificationAdd(properties);
		}
	}
	
	public void modifyObject(Map<String,String> properties) {
		String id = properties.get(WorldObject.FIELD_ID);
		if (id == null) {
			throw new IllegalArgumentException("Missing id (key: ${WorldObject.FIELD_ID})");
		}
		
		Map<String,String> object = objects.get(id);
		if (object == null) {
			throw new RuntimeException("An object with the id ${id} does not exist!");
		}
		
		object.putAll(properties);
		
		if (session != null && session.isActive()) {
			session.changeNotificationModify(properties);
		}
	}
	
	public void deleteObject(String objectId) {
		if (objects.remove(objectId) == null) {
			throw new RuntimeException("An object with the id ${objectId} does not exist!");
		}
		
		if (session != null && session.isActive()) {
			session.changeNotificationDelete(objectId);
		}
	}
	
	public void pushObjects() {
		Transaction transaction = session.createTransaction();
		for (Entry<String,Map<String,String>> entry : objects.entrySet()) {
			transaction.addChangeNotificationAdd(entry.getValue());
		}
		transaction.close();
	}
	
	@Override
	public int beginTransaction() {
		// We don't support transactions (yet?).
		return -1;
	}

	@Override
	public void endTransaction(int transactionId) {
		if (transactionId != -1) {
			throw new RuntimeException("Unknown transaction: ${transactionId}.");
		}
	}

	@Override
	public void changeRequestModify(int transactionId, Map<String, String> obj) {
		if (transactionId != -1) {
			throw new RuntimeException("Unknown transaction: ${transactionId}.");
		}
		
		modifyObject(properties);
	}

	@Override
	public void changeRequestDelete(int transactionId, String objectId) {
		if (transactionId != -1) {
			throw new RuntimeException("Unknown transaction: ${transactionId}.");
		}
		
		deleteObject(objectId);
	}
	
	@Override
	public void close() {
		if (!unmatchedInteractions.isEmpty()) {
			throw new InvalidOutputError("Unmatched expectations:\n" + unmatchedInteractions.join("\n"));
		}
		if (!unmatchedObservations.isEmpty()) {
			throw new InvalidOutputError("Unmatched outputs:\n" + unmatchedObservations.join("\n"));
		}
	}

	public void shutdown(long timeoutMillis) {
		long endNanos = System.nanoTime() + 1000000l * timeoutMillis;
		while (!unmatchedInteractions.isEmpty() || !activeOutputs.isEmpty()) {
			synchronized (unmatchedInteractions) {
				long nextTimeout = (long)((endNanos - System.nanoTime()) / 1000000.0);
				if (nextTimeout <= 0) {
					break;
				}
				 
				unmatchedInteractions.wait(nextTimeout);
			}
		}
		session.shutdownExecutor();
		close();
	}
	
	public long getOutputDuration() {
		return outputDuration;
	}
		
	public void setOutputDuration(long outputDurationMillis) {
		this.outputDuration = outputDurationMillis;
	}
		
	public List getTranscript() {
		return transcript;
	}
	
	/**
	 * test / demo
	 */
	public static void main(String[] args) {
		Reader scriptReader = new StringReader("""
			init {
				uninterruptible { tell user: "Say hello!" }
			}
			
			input(~/.*\\bhello\\b.*/) {
				tell all: "Hello world!"
			}
			
			input(_) {
				tell user: "??"
			}
		""");
		List<User> users = [ new User(1, "TestUser", "en", "us")];
		
		ValidatingDialogClient dialogClient = new ValidatingDialogClient();
		DialogSession dialogSession = Platon.createSession(scriptReader, dialogClient, dialogClient, users);
		dialogSession.setActive(true);
		
		boolean bargeIn = true;
		if (bargeIn) {
			dialogClient.expect("Say hello!");
			dialogClient.inputAfterOutput("hello", users[0]);
			dialogClient.expect("Hello world!");
			dialogClient.inputInterrupting("hello", users[0]);
			dialogClient.expectAbort();
			dialogClient.expect("Hello world!");
		} else {		
			dialogClient.expect("Say hello!");
			dialogClient.inputAfterOutput("hello", users[0]);
			dialogClient.expect("Hello world!");
			dialogClient.inputAfterOutput("hello", users[0]);
			dialogClient.expect("Hello world!");
		}
		
		dialogClient.shutdown(5000);
		
		dialogClient.close();
		dialogSession.close();
		
		println "\nSession transcript:";
		println dialogClient.getTranscript().join("\n");
	}
}
