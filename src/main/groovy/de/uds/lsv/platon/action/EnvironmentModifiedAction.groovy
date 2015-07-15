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
import de.uds.lsv.platon.session.DialogSession;
import de.uds.lsv.platon.world.WorldState

/**
 * The Action representation of a changeNotificationModify event.
 * Executing the action updates the WorldState (but does not
 * automatically run the listeners registered with the WorldState).
 */
@TypeChecked
public class EnvironmentModifiedAction extends Action {
	final Map<String,String> modifications;
	String key;
	String value;
	boolean noEffect = false;
	
	public EnvironmentModifiedAction(DialogSession session, String key, String value) {
		super(session);
		this.key = key;
		this.value = value;
	}
	
	@Override
	protected void doExecute() {
		noEffect = !session.worldState.doSetEnvironmentVariable(key, value);
		
		submitted();
		complete();
	}
	
	public void notifyListener(WorldState.EnvironmentListener listener) {
		if (!this.executed) {
			throw new IllegalStateException("Action has not been executed yet!");
		}
		
		if (!noEffect) {
			listener.environmentModified(key, value);
		}
	}
	
	@Override
	public String toString() {
		return String.format(
			"[EnvironmentModifiedAction: %s = »%s«]",
			key,
			value
		);
	}
}
