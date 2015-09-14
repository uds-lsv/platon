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

import de.uds.lsv.platon.session.DialogEngine;

@TypeChecked
public class ActionQueue {
	private static final Log logger = LogFactory.getLog(ActionQueue.class.getName());
	
	private final LinkedList<Action> queue = new LinkedList<>();
	private final DialogEngine dialogEngine;

	Action lastAction = null;
	
	// Actions that are still waiting for followup actions to complete
	private Map<Class<? extends Action>, Action> blockingActions = new HashMap<>();
	
	public ActionQueue(DialogEngine dialogEngine) {
		this.dialogEngine = dialogEngine;
	}
	
	private void prepareAction(Action action) {
		action.onComplete.add(this.&nextAction);
		
		Action active = dialogEngine.getSession().getActiveAction();
		if (active != null) {
			active.addFollowupAction(action);
		}
	}
	
	/**
	 * This has to run on the session executor thread!
	 */
	private synchronized void doAdd(Action action) {
		assert (dialogEngine.session.isOnSessionThread());
		prepareAction(action);
		queue.add(action);
		if (queue.size() == 1) {
			dialogEngine.getSession().submit(this.&nextAction);
		}
	}
	
	/**
	 * This has to run on the session executor thread!
	 */
	private synchronized void doAddFirst(Action action) {
		assert (dialogEngine.session.isOnSessionThread());
		prepareAction(action);
		queue.addFirst(action);
		if (queue.size() == 1) {
			dialogEngine.getSession().submit(this.&nextAction);
		}
	}
	
	public void add(Action action) {
		dialogEngine.session.runOnSessionThread({
			doAdd(action);
		});
	}

	public void addFirst(Action action) {
		dialogEngine.session.runOnSessionThread({
			doAddFirst(action);
		});
	}
		
	public synchronized void add(Collection<Action> actions) {
		dialogEngine.session.runOnSessionThread({
			synchronized (this) {
				for (Action action : actions) {
					doAdd(action);
				}
			}
		});
	}
	
	/**
	 * Important: Actions need to be executed on the session
	 * executor thread! However, session.submit(...) must not run the
	 * submitted task before the current task has finished.  
	 * (Otherwise, object.foo().or { ... } might run the
	 * foo() actions before the reactions are completely set up!)
	 */
	public synchronized void nextAction(result=null) {
		// The previous action is already committed to
		// the history here.
		// All actions: history + lastAction + queue
		
		Action selectedAction = null;
		Iterator<Action> it = queue.iterator();
		while (it.hasNext()) {
			Action a = it.next();
			Class<? extends Action> cls = a.getClass();
			Action blocking = blockingActions.get(cls);
			if (blocking != null) {
				if (blocking.followupActionsCompleted()) {
					blockingActions.remove(cls);
				} else {
					logger.debug(
						"Action still waiting for followup actions: " + a
					);
					continue;
				}
			}
			
			selectedAction = a;
			it.remove();
			break;
		}
		
		lastAction = selectedAction;
		
		if (selectedAction != null) {
			blockingActions.put(lastAction.getClass(), selectedAction);
			selectedAction.execute();
		}
	}
	
	public synchronized void bargeIn() {
		// remove all (verbal) actions from the queue that don't
		// have uninterruptible set
		if (dialogEngine.session.config.disableBargeIn) {
			return;
		}
		
		logger.trace("Barge-in? Checking queue...");
		if (!queue.isEmpty()) {
			def remaining = queue.findAll { it.uninterruptible };
			queue.clear();
			queue.addAll(remaining);
			logger.debug("Barge-in, queue cleaned! Remaining uninterruptible actions: " + remaining.size());
			if (!remaining.isEmpty()) {
				logger.debug("Remaining uninterruptible actions: " + queue);
			}
		}
		
		if (lastAction != null) {
			if (lastAction.uninterruptible) {
				logger.debug("Barge-in, last action is uninterruptible: " + lastAction);
			} else {
				logger.debug("Barge-in, trying to abort last action: " + lastAction);
				lastAction.abort();
				lastAction = null;
			}

			nextAction();
		}
	}
	
	/**
	 * Get an unmodifiable view of the action queue.
	 */
	public List<Action> getQueue() {
		return Collections.unmodifiableList(queue);
	}
}
