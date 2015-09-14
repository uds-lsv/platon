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

package de.uds.lsv.platon.action

import groovy.transform.TypeChecked

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

import de.uds.lsv.platon.session.DialogSession;
import de.uds.lsv.platon.session.User;

@TypeChecked
public abstract class Action {
	private static final Log logger = LogFactory.getLog(Action.class.getName());
	
	protected long timeoutMillis;
	
	/**
	 * Submission reactions are called when the request has been
	 * submitted to the server.
	 * They get the result of the Thrift API function as an argument.
	 */
	public List<Closure> onSubmitted = new ArrayList<>();
	
	/**
	 * Completion reactions are called when the action is actually
	 * complete, i.e. requested modifications have been applied on
	 * the game server and have been transferred back to the dialog
	 * engine.
	 */
	public List<Closure> onComplete = new ArrayList<>();
	
	/**
	 * A (single) error handling closure that's called in
	 * case the action fails. It gets an error code as
	 * parameter.
	 * Currently only used in Modify/DeleteObjectAction.
	 */
	public Closure errorHandler = {
		errorCode ->
		logger.error("Error executing ${this}: ${errorCode}");
	};

	/**
	 * Actions created while running this action. 
	 */
	private List<Action> followupActions = new ArrayList<>();
	
	protected boolean completed = false;
	protected boolean successful = false;
	protected boolean executed = false;
	boolean uninterruptible;
	public long submissionTime = -1;
	
	/**
	 * The user that is affected by this action,
	 * or null if more than one user is affected.
	 */
	final User user;
	final DialogSession session;
	
	public Action(DialogSession session, boolean uninterruptible=true, User user=null) {
		this.session = session;
		this.uninterruptible = uninterruptible;
		this.user = user;
		if (session?.config != null) {
			this.timeoutMillis = session.config.defaultActionTimeoutMillis;
		}
	}
	
	public Action(DialogSession session, Closure onSubmitted, boolean uninterruptible=true, User user=null) {
		this.session = session;
		this.onSubmitted.add(onSubmitted);
		this.uninterruptible = uninterruptible;
		this.user = user;
		if (session?.config != null) {
			this.timeoutMillis = session.config.defaultActionTimeoutMillis;
		}
	}

	/**
	 * The actual contents of the action, without boilerplate.
	 */
	protected abstract void doExecute();
	
	/**
	 * Execute the action.
	 * Internally calls doExecute.
	 */
	public final void execute() {
		synchronized (this) {
			if (executed) {
				throw new IllegalStateException("This action was already executed!");
			}
			executed = true;
		}

		session.setActiveAction(this);
		try {
			doExecute();
		}
		finally {
			session.setActiveAction(null);
		}
		
		session.schedule(
			{
				if (!completed) {
					logger.warn("Action is taking too long to run: " + this);
				}
			},
			timeoutMillis
		);
	}

	/**
	 * Submission reactions are called when the request has been
	 * submitted to the server.
	 * They get the result of the Thrift API function as an argument.
	 */
	public void addSubmissionReaction(Closure closure) {
		onSubmitted.add(closure);
	}
	
	/**
	 * Completion reactions are called when the action is actually
	 * complete, i.e. requested modifications have been applied on
	 * the game server and have been transferred back to the dialog
	 * engine.
	 */
	public void addCompletionReaction(Closure closure) {
		onComplete.add(closure);
	}
	
	/**
	 * Trigger submission reactions.
	 */
	public void submitted() {
		submissionTime = System.nanoTime();
		logger.debug("Action submitted: ${this}");
		if (onSubmitted != null) {
			for (c in onSubmitted) {
				c();
			}
		}
	}
	
	/**
	 * Trigger completion reactions.
	 */
	public void complete(boolean successful=true) {
		this.completed = true;
		this.successful = successful;
		
		logger.debug("Action completed: ${this} (successful: " + successful + ")");
		
		// Add to history
		if (user == null) {
			session.dialogEngines?.values()*.addHistoryItem(this);
		} else {
			session.dialogEngines?.get(user.id)?.addHistoryItem(this);
		}
		
		// Run listeners
		if (onComplete != null) {
			for (c in onComplete) {
				c(successful);
			}
		}
	}
	
	/**
	 * Abort the action (if applicable;
	 * currently only supported by VerbalOutputAction).
	 */
	public void abort() {
		// default: empty 
	}
	
	/**
	 * true iff the action was aborted.
	 */
	public boolean wasAborted() {
		return false;
	}
	
	public boolean wasSuccessful() {
		return successful;
	}
	
	public void addFollowupAction(Action action) {
		if (this.is(action)) {
			throw new IllegalArgumentException("An action can not be a followup action of itself!");
		}
		followupActions.add(action);
	}
	
	public boolean followupActionsCompleted() {
		if (!completed) {
			return false;
		}
		
		for (Action action : followupActions) {
			if (!action.followupActionsCompleted()) {
				return false;
			}
		}
		
		return true;
	}
}

