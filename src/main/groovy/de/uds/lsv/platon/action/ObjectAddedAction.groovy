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
import de.uds.lsv.platon.session.DialogSession;
import de.uds.lsv.platon.world.WorldObject
import de.uds.lsv.platon.world.WorldState

/**
 * The Action representation of a changeNotificationAdd event.
 * Executing the action updates the WorldState but does not
 * automatically run the listeners registered with the WorldState.
 * This is why the preferred way of executing this action is
 * by using the WorldState.apply(Action) method.
 */
@TypeChecked
public class ObjectAddedAction extends Action {
	private final Map<String,String> properties;

	/** Not set before execution! */
	WorldObject addedObject = null;
	
	public ObjectAddedAction(DialogSession session, Map<String,String> properties) {
		super(session);
		this.properties = properties;
	}
	
	@Override
	protected void doExecute() {
		addedObject = session.worldState.doChangeNotificationAdd(properties);
		
		submitted();
		complete();
	}
	
	public void notifyListener(WorldState.AddListener listener) {
		if (!this.executed) {
			throw new IllegalStateException("Action has not been executed yet!");
		}
		
		listener.objectAdded(addedObject);
	}
	
	@Override
	public String toString() {
		return String.format(
			"[ObjectAddedAction: %s]",
			properties
		);
	}
}
