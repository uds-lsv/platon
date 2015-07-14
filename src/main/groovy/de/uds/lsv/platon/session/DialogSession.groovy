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

package de.uds.lsv.platon.session;

import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

import de.martingropp.util.ReactionMap;
import de.uds.lsv.platon.DialogClient
import de.uds.lsv.platon.DialogWorld
import de.uds.lsv.platon.action.IOType
import de.uds.lsv.platon.action.VerbalInputAction
import de.uds.lsv.platon.config.Config
import de.uds.lsv.platon.world.WorldState

@TypeChecked
public class DialogSession implements Closeable {
	private static final Log logger = LogFactory.getLog(DialogSession.class.getName());

	public static interface SessionActiveListener {
		void sessionActiveChanged(boolean active);
	}
		
	Config config;
	DialogClient dialogClient;
	DialogWorld dialogWorld;
	List<User> users;
	
	/** Map of dialog engines, one for each user: user id -> dialog engine */
	final Map<Integer,DialogEngine> dialogEngines;
	
	/** world state (shared between dialog engines) */
	final WorldState worldState;
	
	/** transaction manager for world object creation/modification/deletion. */
	final TransactionManager transactionManager = new TransactionManager(this);
	
	ReactionMap pendingOutputReactions = new ReactionMap();
	
	ScheduledExecutorService executor;
	Thread executorThread;
	private volatile boolean executorShutdown = false;

	private boolean active = false;
	private List<SessionActiveListener> sessionActiveListeners = new ArrayList<>();
	
	public DialogSession(
		Config config,
		DialogClient dialogClient,
		DialogWorld dialogWorld,
		List<User> users
	) {
		this.config = config;
		this.dialogClient = dialogClient;
		this.dialogWorld = dialogWorld;
		
		startExecutor();
		
		logger.debug("Creating world state...");
		this.worldState = new WorldState(this);
		
		this.users = users;
		
		logger.debug("Creating dialog engines for users: "  + users.collect { it.id })
		this.dialogEngines = users.collectEntries {
			user -> [ (user.id) : createDialogEngine(user) ]
		};
	
		dialogWorld?.init(this);
	}
	
	@TypeChecked(TypeCheckingMode.SKIP)
	private DialogEngine createDialogEngine(User user) {
		return config.createDialogEngine(this, user);
	}
	
	/**
	 * Tear down this session, i.e.
	 * shutdown all dialog engines,
	 * close the world state,
	 * and eventually close the connection to the game server.
	 */
	@Override
	void close() {
		active = false;

		logger.debug("Closing dialog engines...");
		dialogEngines.collect { it.value.close() }
		
		logger.debug("Closing world state...");
		worldState?.close();
		
		logger.debug("Closing world server...");
		dialogWorld?.close();
		
		logger.debug("Closing dialog client...");
		dialogClient?.close();
		
		logger.debug("Session closed.");
	}
	
	@TypeChecked(TypeCheckingMode.SKIP)
	private synchronized Future doSubmit(Closure closure) {
		logger.debug("Submission to session executor: " + closure);
		executorShutdown = false;
		return executor.submit({
			try {
				closure()
			}
			catch (Throwable t) {
				t.printStackTrace();
				logger.error(t);
				if (config.serverExceptionHandler != null) {
					config.serverExceptionHandler(t);
				}
				
			}
		} as Callable);
	}
	
	/**
	 * If called from the session thread, run the closure
	 * directly.
	 * Otherwise submit it to the session executor.
	 */
	public void runOnSessionThread(Closure closure) {
		if (isOnSessionThread()) {
			closure();
		} else {
			doSubmit(closure);
		}
	}
	
	/**
	 * Submit a task to be executed on the executor thread,
	 * at the next possible time.
	 */
	public void submit(Closure closure) {
		doSubmit(closure);
	}
	
	/**
	 * Run a task on the executor thread and wait for it to complete.
	 */
	public void submitAndWait(Closure closure) {
		if (Thread.currentThread() == executorThread) {
			closure();
		} else {
			doSubmit(closure).get();
		}
	}

	/**
	 * Schedule a task to be executed later on the executor thread.
	 * The delay may be (very) inaccurate as other tasks might be
	 * executed first.
	 */
	@TypeChecked(TypeCheckingMode.SKIP)
	public synchronized void schedule(callable, long delayMilliseconds) {
		logger.debug("Submission (scheduled: ${delayMilliseconds}ms) to session executor: " + callable);
		executorShutdown = false;
		executor.schedule(
			{
				if (!isActive()) {
					logger.debug("Not executing scheduled task " + callable + ": session already ended.")
					return;
				}
				
				try {
					callable()
				}
				catch (Throwable t) {
					t.printStackTrace();
					logger.error(t);
				}
			} as Runnable,
			delayMilliseconds,
			TimeUnit.MILLISECONDS
		);
	}
	
	public boolean isOnSessionThread() {
		// executorThread == null is for testing only
		return (executorThread == null) || executorThread.equals(Thread.currentThread());
	}
	
	/**
	 * Return the WorldState for this session.
	 */
	public WorldState getWorldState() {
		return worldState;
	}
	
	public DialogEngine getDialogEngine(User user) {
		return getDialogEngine(user.id);
	}
	
	public DialogEngine getDialogEngine(int userId) {
		return dialogEngines[userId];
	}
	
	public DialogClient getDialogClient() {
		return dialogClient;
	}
	
	public DialogWorld getDialogWorld() {
		return dialogWorld;
	}
	
	void startExecutor() {
		logger.debug("Starting a new session executor.");
		executor = Executors.newSingleThreadScheduledExecutor();
		Future future = executor.submit({
			executorThread = Thread.currentThread();
			executorThread.setName("session-" + System.identityHashCode(this));
		} as Callable);
		future.get();
	}

	/** Shutdown executor after all (non-scheduled) tasks have finished. */
	private synchronized void waitForTasksAndShutdownExecutor() {
		if (executorShutdown) {
			logger.debug("Shutting down session executor.")
			// shutdownNow cancels scheduled tasks which might
			// still cause problems with just a shutdown.
			executor.shutdownNow();
		} else {
			logger.debug("Session executor is busy - delaying shutdown.")
			executorShutdown = true;
			executor.submit(this.&waitForTasksAndShutdownExecutor);
		}
	}
	
	void shutdownExecutor() {
		logger.debug("Trying to shut down session executor.");
		executor.submit(this.&waitForTasksAndShutdownExecutor);
		if (!executor.awaitTermination(1L, TimeUnit.MINUTES)) {
			logger.warn("Timeout while waiting for executor to terminate.");
		}
		logger.info("Session executor shut down successfully.");
	}
	
	public boolean isActive() {
		return active;
	}
	
	/**
	 * Notifies world server!
	 * @param active
	 */
	public void setActive(final boolean active) {
		if (this.active == active) {
			return;
		}
		
		this.active = active;
		
		logger.debug("Session active: " + active);
		submit({
			for (SessionActiveListener listener : sessionActiveListeners) {
				listener.sessionActiveChanged(active);
			}
		});
	}

	public void addSessionActiveListener(SessionActiveListener listener) {
		sessionActiveListeners.add(listener);
	}
	
	public void addOutputReaction(int outputId, Closure completionClosure) {
		pendingOutputReactions.add(
			outputId,
			{
				double complete ->
				logger.debug("Output ${outputId} complete.");
				submit({ completionClosure(complete) });
			}
		);
	}
	
	public User getUser(int userId) {
		if (userId <= 0) {
			return null;
		}
		DialogEngine dialogEngine = dialogEngines[userId];
		if (dialogEngine == null) {
			throw new RuntimeException("User not in session: " + userId);
		}
		return dialogEngine.getUser();
	}
	
	public TransactionManager getTransactionManager() {
		return transactionManager;
	}
	
	public TransactionManager.Transaction createTransaction() {
		return transactionManager.createTransaction();
	}
	
	public void inputStarted(User speaker, IOType ioType) {
		logger.debug("inputStarted(${speaker}, ${ioType})");
		if (speaker == null) {
			dialogEngines.values()*.inputStarted(ioType);
		} else {
			if (!dialogEngines.containsKey(speaker.id)) {
				throw new RuntimeException("User not in session: " + speaker);
			}
			
			dialogEngines[speaker.id].inputStarted(ioType);
		}
	}
	
	public void inputComplete(User speaker, IOType ioType, String text, Map<String,String> details) {
		logger.debug("inputComplete(${speaker}, ${ioType}, ${text}, ${details})");
		VerbalInputAction action = new VerbalInputAction(
			this,
			speaker,
			ioType,
			text,
			details
		);
	
		submit(action.&execute);
	}
	
	public void inputAbandoned(User speaker, IOType ioType) {
		logger.debug("inputAbandoned(${speaker}, ${ioType})");
		submit({
			if (speaker == null) {
				dialogEngines.values()*.inputAbandoned(ioType);
			} else {
				if (!dialogEngines.containsKey(speaker.id)) {
					throw new RuntimeException("User not in session: " + speaker);
				}
				
				dialogEngines[speaker.id].inputAbandoned(ioType);
			}
		});
	}
	
	public void outputEnded(int outputId, User user, double complete) {
		logger.debug("outputEnded(${outputId}, ${user}, ${complete})");
		pendingOutputReactions.trigger(outputId, complete);
	}
	
	public void changeNotificationAdd(Map<String,String> properties, int transactionId=-1) {
		getTransactionManager().addChangeNotificationAdd(transactionId, properties);
	}
	
	public void changeNotificationModify(Map<String,String> properties, int transactionId=-1) {
		getTransactionManager().addChangeNotificationModify(transactionId, properties);
	}
	
	public void changeNotificationDelete(String objectId, int transactionId=-1) {
		getTransactionManager().addChangeNotificationDelete(transactionId, objectId);
	}
}
