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

import de.uds.lsv.platon.session.DialogSession;
import de.uds.lsv.platon.session.User;

@TypeChecked
public class VerbalInputAction extends Action {
	private static final Log logger = LogFactory.getLog(VerbalInputAction.class.getName());
	
	final IOType type;
	final String text;
	final Map<String, String> details;
	
	public VerbalInputAction(
		DialogSession session,
		User user,
		IOType type,
		String text,
		Map<String, String> details
	) {
		super(session, true, user);
		
		if (user == null) {
			throw new IllegalArgumentException("User cannot be null!");
		}
		
		this.type = type;
		this.text = text;
		this.details = details;
	}
	
	@Override
	protected void doExecute() {
		assert (session.isOnSessionThread());
		
		logger.debug("Executing " + this);
		//if (user == null) {
		//	session.dialogEngines.values()*.inputComplete(type, text, details);
		//} else {
		
		if (!session.dialogEngines.containsKey(user.id)) {
			throw new RuntimeException("User not in session: " + user);
		}
		session.dialogEngines[user.id].inputComplete(type, text, details);
		
		//}
		
		submitted();
		complete();
	}
	
	@Override
	public String toString() {
		return String.format(
			"[VerbalInputAction: »%s« @ %s]",
			text,
			user
		);
	}
}
