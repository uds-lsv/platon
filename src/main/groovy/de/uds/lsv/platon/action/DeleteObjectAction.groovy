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

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

import de.uds.lsv.platon.exception.DialogWorldException
import de.uds.lsv.platon.session.DialogSession

public class DeleteObjectAction extends Action {
	private static final Log logger = LogFactory.getLog(DeleteObjectAction.class.getName());
	
	final String objectId;
	final int transactionId;
	
	public DeleteObjectAction(DialogSession session, String objectId, Closure errorHandler=null, int transactionId=-1) {
		super(session);
		this.objectId = objectId;
		this.transactionId = transactionId;
	}

	@Override
	protected void doExecute() {
		session.submit({
			logger.debug("Executing " + this);
			String error = null;
			try {
				session.getDialogWorld().changeRequestDelete(
					transactionId,
					objectId
				);
			}
			catch (DialogWorldException e) {
				error = e.id;
			}
			
			submitted();
			
			if (error != null) {
				if (errorHandler != null) {
					logger.debug(String.format(
						"changeRequestDelete failed, result=%s. Calling error handler %s with argument »%s«.",
						error, errorHandler, error
					));
					errorHandler(result);
				} else {
					logger.error(String.format(
						"changeRequestDelete failed, result=%s. No error handler available!",
						error
					));
				}
				
				complete(false);
			}
		});
	}
	
	@Override
	public String toString() {
		return String.format("[DeleteObjectAction: %s (transaction: %d)]", objectId, transactionId); 
	}
}
