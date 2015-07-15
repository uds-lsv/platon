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

package de.uds.lsv.platon.action;

import java.util.Map.Entry

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

import de.uds.lsv.platon.exception.DialogWorldException
import de.uds.lsv.platon.session.DialogSession
import de.uds.lsv.platon.world.WorldState
import de.uds.lsv.platon.world.SubscriptionManager.Subscription

public class ModifyObjectAction extends Action {
	protected static final Log logger = LogFactory.getLog(ModifyObjectAction.class.getName());
	
	final Map<String,String> modifications;
	final int transactionId;
	protected Subscription worldStateSubscription = null; 
	
	public ModifyObjectAction(DialogSession session, Map<String,String> modifications) {
		this(session, modifications, -1);
	}
	
	public ModifyObjectAction(DialogSession session, Map<String,String> modifications, int transactionId) {
		super(session);
		this.modifications = modifications;
		this.transactionId = transactionId;
	}
	
	@Override
	protected void doExecute() {
		/*
		 *   <li>0: The request was accepted and has been executed.</li>
		 *   <li>1: The request was accepted, but its execution has been
		 *          deferred or takes some more time. Eventual success is
		 *          guaranteed.</li>
		 *   <li>2: The request was tentatively accepted, but its execution
		 *          has been deferred or takes some more time. The request
		 *          may be rejected later without additional
		 *          notification.</li>
		 *   <li>-1: The request was rejected.</li>
		 */
		
		session.submit({
			WorldState worldState = session.getWorldState();
			
			if (
				transactionId < 0 &&
				worldState.containsMatchingObject(modifications)
			) {
				logger.debug("World state already matches request: " + this);
				submitted();
				complete();
				return;
			}
			
			worldStateSubscription = worldState.getSubscriptionManager().subscribe(
				// filter
				{
					newProperties ->
					modifications.every {
						Entry<String,String> entry ->
						newProperties.get(entry.key) as String == entry.value
					}
				},
				// action
				{
					// we shouldn't need any synchronization here?
					if (worldStateSubscription != null) {
						worldStateSubscription.cancel();
						worldStateSubscription = null;
						complete();
					}
				}
			);
			
			logger.debug("Executing " + this);
			String error = null;
			try {
				session.getDialogWorld().changeRequestModify(
					transactionId,
					modifications
				);
			}
			catch (DialogWorldException e) {
				error = e.id;
			}

			submitted();
			
			if (error != null) {
				if (worldStateSubscription != null) {
					worldStateSubscription.cancel();
					worldStateSubscription = null;
				}
				
				if (errorHandler != null) {
					logger.debug(String.format(
						"changeRequestModify failed, result=%s. Calling error handler %s with argument »%s«.",
						error, errorHandler, error
					));
					errorHandler(error);
				} else {
					logger.error(String.format(
						"changeRequestModify failed, result=%s. No error handler available!",
						error
					));
				}
				
				complete(false);
			}
		});
	}
	
	@Override
	public String toString() {
		return String.format("[ModifyObjectAction: %s (transaction: %d)]", modifications.toString(), transactionId);
	}
}
