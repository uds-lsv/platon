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

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

import de.uds.lsv.platon.session.DialogSession;

public class FailureAction extends Action {
	private static final Log logger = LogFactory.getLog(FailureAction.class.getName());
	
	private final Action action;
	private final String errorCode;
	
	public FailureAction(Action action, String errorCode) {
		super(action.getSession());
		this.action = action;
		this.errorCode = errorCode;
	}
	
	public FailureAction(DialogSession session, String errorCode) {
		super(session);
		this.action = null;
		this.errorCode = errorCode;
	}

	@Override
	protected void doExecute() {
		if (errorHandler != null) {
			if (action == null) {
				logger.debug("Error code: " + errorCode);
			} else {
				logger.debug(String.format(
					"Error »%s «in action %s. Running error handler: %s",
					errorCode, action, errorHandler
				));
			}
			
			errorHandler(errorCode);

		} else {
			logger.error(String.format(
				"No error handler found, error code: %s",
				errorCode
			));
		}
		
		submitted();
		complete(false);
	}
	
	@Override
	public String toString() {
		if (action != null) {
			return String.format(
				"[ FailureAction: %s Error code: %s ]",
				action, errorCode
			);
		} else {
			return String.format(
				"[ FailureAction: %s ]",
				errorCode
			);
		}
	}
}
