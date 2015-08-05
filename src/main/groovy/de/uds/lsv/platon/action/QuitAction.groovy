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

import groovy.transform.TypeChecked

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

import de.uds.lsv.platon.session.DialogEngine;
import de.uds.lsv.platon.session.DialogSession;

@TypeChecked
public class QuitAction extends Action {
	private static final Log logger = LogFactory.getLog(QuitAction.class.getName());
	
	private final DialogEngine dialogEngine;
	
	public QuitAction(DialogSession session, DialogEngine dialogEngine) {
		super(session);
		this.dialogEngine = dialogEngine;
	}
	
	public QuitAction(QuitAction action) {
		this(action.session, action.dialogEngine);
	}
	
	@Override
	protected void doExecute() {
		logger.debug("Executing " + this);
		dialogEngine.terminated = true;
		
		synchronized (session) {
			for (DialogEngine d : session.dialogEngines.values()) {
				if (!d.terminated) {
					logger.debug("Still waiting for dialog engine to quit: " + d);
					return;
				}
			}
			
			session.close();
			submitted();
			complete();
		}
	}
	
	@Override
	public String toString() {
		return String.format(
			"[QuitAction %s]",
			dialogEngine
		);
	}
}
