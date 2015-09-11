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
import de.martingropp.util.Pair;
import de.uds.lsv.platon.session.DialogSession;
import de.uds.lsv.platon.world.WorldObject
import de.uds.lsv.platon.world.WorldState

/**
 * The Action representation of a changeNotificationModify event.
 * Executing the action updates the WorldState but does not
 * automatically run the listeners registered with the WorldState.
 * This is why the preferred way of executing this action is
 * by using the WorldState.apply(Action) method.
 */
@TypeChecked
public class ObjectModifiedAction extends Action {
	final Map<String,String> modifications;

	/** Not set before execution! */
	WorldObject modifiedObject = null;
	
	/** Not set before execution! */
	Map<String,Object> oldState = null;
	
	public ObjectModifiedAction(DialogSession session, Map<String,String> modifications) {
		super(session);
		this.modifications = modifications;
	}
	
	@Override
	protected void doExecute() {
		Pair<WorldObject,Map<String,Object>> result =
			session.worldState.doChangeNotificationModify(modifications);
		
		this.modifiedObject = result.first;
		this.oldState = result.second;
		
		submitted();
		complete();
	}
	
	public void notifyListener(WorldState.ModifyListener listener) {
		if (!this.executed) {
			throw new IllegalStateException("Action has not been executed yet!");
		}
		listener.objectModified(modifiedObject, oldState);
	}
	
	@Override
	public String toString() {
		return String.format(
			"[ObjectModifiedAction: %s (old: %s, new: %s)]",
			modifications,
			oldState,
			modifiedObject.getProperties()
		);
	}
}
