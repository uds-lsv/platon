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

package de.uds.lsv.platon.script

import groovy.transform.TypeChecked

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

import de.uds.lsv.platon.action.Action
import de.uds.lsv.platon.session.DialogEngine;

@TypeChecked
public class Then {
	private static final Log logger = LogFactory.getLog(Then.class.getName());
	
	/**
	 * The action after which the Then comes.
	 */
	private final DialogEngine dialogEngine;
	private final Action afterAction;
	private List<Closure> thenClosures = new ArrayList<>(4);
	private boolean thenClosuresCalled = false;
	private boolean errorHandlerSet = false;
	private boolean completionReactionAdded = false;
	
	public Then(Action afterAction, DialogEngine dialogEngine) {
		this.afterAction = afterAction;
		this.dialogEngine = dialogEngine;
		afterAction.errorHandler = {
			errorCode ->
			if (!dialogEngine.triggerNamedReaction("main:unhandled error", errorCode)) {
				logger.error(String.format(
					"Unhandled error in %s: %s",
					afterAction,
					errorCode
				));
			}
		};
	}
	
	/**
	 * @param thenClosure
	 *   This closure is called when the action is complete.
	 *   If the closure accepts 2 parameters, the parameters
	 *   are (boolean success, String errorCode) and it is
	 *   additionally used as an error handler.
	 *   (In this case you can't use .or to handle errors.)
	 */
	public Then then(Closure thenClosure) {
		this.thenClosures.add(thenClosure);
		
		if (thenClosure.getMaximumNumberOfParameters() == 2) {
			if (errorHandlerSet) {
				throw new RuntimeException("You can only set one error handler! (A .this closure accepting two arguments acts as an error handler.)");
			}
			
			errorHandlerSet = true;
			afterAction.errorHandler = {
				errorCode -> thenClosure(false, errorCode);
			}
		} else if (thenClosure.getMaximumNumberOfParameters() > 2) {
			throw new RuntimeException(".then closure accepts too many arguments.");
		}
		
		if (!completionReactionAdded) {
			afterAction.addCompletionReaction({
				boolean successful ->
				
				if (!dialogEngine.session.isActive()) {
					logger.warn("Session no longer active, skipping .then: " + this);
					return;
				}
				
				if (successful) {
					synchronized(this) {
						if (!thenClosuresCalled) {
							logger.debug("Calling completion action for " + afterAction);
							for (Closure then : thenClosures) {
								if (then.getMaximumNumberOfParameters() == 2) {
									then(true, "");
								} else {
									then();
								}
							}
							thenClosuresCalled = true;
						}
					}
				}
			});
		
			completionReactionAdded = true;
		}
	
		return this;
	}

	/**
	 * @param orClosure
	 *   Use this closure as an error handler.
	 *   It gets an error code String as argument.
	 *   Can't be combined with the two-argument .then.
	 */
	public Then or(Closure orClosure) {
		if (errorHandlerSet) {
			throw new RuntimeException("You can only set one error handler! (A .this closure accepting two arguments acts as an error handler.)");
		}
		
		if (orClosure.getMaximumNumberOfParameters() != 1) {
			throw new RuntimeException(".or closure must accept exactly one argument.");
		}
		
		errorHandlerSet = true;
		
		logger.debug("Setting error handler on ${afterAction}. Now handled by ${this}.")
		afterAction.errorHandler = {
			errorCode -> orClosure(errorCode);
		}
		
		return this;
	}
}
