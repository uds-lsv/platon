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
	
	// Actions that are blocking certain other actions from
	// being processed.
	private LinkedList<Action> blockingActionStack = new LinkedList<>();
	
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
	
	private synchronized void doAddFirst(List<? extends Action> actions) {
		assert (dialogEngine.session.isOnSessionThread());
		boolean idle = queue.isEmpty();
		for (Action action : actions.reverse()) {
			prepareAction(action);
			queue.addFirst(action);
		}
		
		if (idle) {
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
	
	public void addFirst(List<? extends Action> actions) {
		dialogEngine.session.runOnSessionThread({
			doAddFirst(actions);
		});
	}
		
	public synchronized void add(Collection<? extends Action> actions) {
		dialogEngine.session.runOnSessionThread({
			synchronized (this) {
				for (Action action : actions) {
					doAdd(action);
				}
			}
		});
	}
	
	private void cleanBlockingStack() {
		// An action can never be removed from the stack before
		// its "child" actions are complete, because they are in
		// the actions followupActions list.
		ListIterator<Action> it = blockingActionStack.listIterator(blockingActionStack.size());
		while (it.hasPrevious()) {
			Action action = it.previous();
			if (action.followupActionsCompleted()) {
				it.remove();
			} else {
				break;
			}
		}
	}
	
	/**
	 * Blocking stack needs to be cleaned before
	 * calling this method!
	 */
	private boolean isActionBlocked(Action action) {
		if (action instanceof VerbalInputAction) {
			// VerbalInputActions are blocked by everything on the stack
			return !blockingActionStack.isEmpty();
		
		} else if (action instanceof PartialAction) {
			// PartialActions are blocked if they were not created by
			// one of the actions on the stack.
			PartialAction partialAction = (PartialAction)action;
			return !(
				blockingActionStack.isEmpty() ||
				(
					partialAction.parent != null &&
					partialAction.parent.is(blockingActionStack.peekLast())
				)
			);
		
		} else {
			return false;
		}
	}
	
	/**
	 * Add an action to blocking stack if
	 * a) it is a VerbalInputAction, or
	 * b) it is a PartialAction derived from a VIA
	 *    on the blocking stack
	 */
	private void putOnBlockingStack(Action action) {
		if (
			(action instanceof VerbalInputAction) ||
			(
				(action instanceof PartialAction) &&
				blockingActionStack.any {
					((PartialAction)action).parent.is(it)
				}
			)
		) {
			// Cast due to Groovy type checker bug
			blockingActionStack.push((Action)action);
		}
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
		
		cleanBlockingStack();
		
		Action selectedAction = null;
		Iterator<Action> it = queue.iterator();
		while (it.hasNext()) {
			Action a = it.next();
			if (isActionBlocked(a)) {
				logger.debug(
					"Action still waiting for followup actions: " + a
				);
				continue;
			}
			
			selectedAction = a;
			it.remove();
			break;
		}
		
		if (selectedAction != null) {
			logger.debug(String.format(
				"Next action: %s (remaining: %d)",
				selectedAction,
				queue.size()
			));
		}
		
		lastAction = selectedAction;
		
		if (selectedAction != null) {
			putOnBlockingStack(selectedAction);
			// TODO: shouldn't this go through session.submit(Action)?
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
