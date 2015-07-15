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
public class VerbalOutputAction extends Action {
	private static final Log logger = LogFactory.getLog(VerbalOutputAction.class.getName());
	
	private volatile boolean aborted = false;
	final String text;
	final IOType type = IOType.ANY;
	final Map<String,Object> details;
	int outputId = -1;
	
	public VerbalOutputAction(DialogSession session, User user, String text, boolean uninterruptible, Map<String,Object> details) {
		super(session,  uninterruptible, user);
		this.text = text;
		this.details = details;
		if (session?.config != null) {
			this.timeoutMillis = session.config.defaultActionTimeoutMillis;
		}
	}
	
	public VerbalOutputAction(DialogSession session, User user, int text, boolean uninterruptible, Map<String,Object> details) {
		this(session, user, Integer.toString(text), uninterruptible, details);
	}
	
	public VerbalOutputAction(DialogSession session, User user, double text, boolean uninterruptible, Map<String,Object> details) {
		this(session, user, Double.toString(text), uninterruptible, details);
	}
	
	public VerbalOutputAction(DialogSession session, User user, boolean text, boolean uninterruptible, Map<String,Object> details) {
		this(session, user, Boolean.toString(text), uninterruptible, details);
	}
	
	public VerbalOutputAction(DialogSession session, User user, Object text, boolean uninterruptible, Map<String,Object> details) {
		this(session, user, text.toString(), uninterruptible, details);
	}
	
	@Override
	protected void doExecute() {
		session.submit({
			synchronized(this) {
				if (!aborted && session.isActive()) {
					logger.debug("Executing " + this);
					outputId = session.getDialogClient().outputStart(
						user,
						type,
						text,
						details
					);
					logger.debug("Output id is ${outputId} for " + this);
					submitted();
					
					session.addOutputReaction(
						outputId,
						{
							double c ->
							this.complete(c >= 1.0);
						}
					);
				}
			}
		});
	}
	
	@Override
	public synchronized void abort() {
		if (!aborted && outputId >= 0) {
			// should we ignore exceptions here?
			session.submit({
				logger.debug("Aborting VerbalOutputAction with output id ${outputId}: " + this);
				session.getDialogClient().outputAbort(
					outputId,
					null
				);
				complete(false);
			});
		}
		aborted = true;
	}
	
	@Override
	public boolean wasAborted() {
		return aborted;
	}
	
	@Override
	public String toString() {
		return "[VerbalOutputAction: »${text}« (uninterruptible: ${uninterruptible}) @${user}]";
	}
}
