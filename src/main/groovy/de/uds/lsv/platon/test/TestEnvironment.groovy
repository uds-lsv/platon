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

package de.uds.lsv.platon.test

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

import de.uds.lsv.platon.action.Action
import de.uds.lsv.platon.action.ModifyObjectAction
import de.uds.lsv.platon.session.DialogEngine
import de.uds.lsv.platon.session.DialogSession
import de.uds.lsv.platon.session.User
import de.uds.lsv.platon.world.WorldClass
import de.uds.lsv.platon.world.WorldField
import de.uds.lsv.platon.world.WorldMaker
import de.uds.lsv.platon.world.WorldMethod
import de.uds.lsv.platon.world.WorldObject

public class TestEnvironment {
	private static final Log logger = LogFactory.getLog(TestEnvironment.class.getName());
	
	@WorldClass("TestDoor")
	public static class TestDoor extends WorldObject {
		@WorldField
		public boolean isOpen = false;
		
		@WorldField
		public boolean isLocked = false;
		
		@WorldField
		public String name = "door";
		
		@WorldField
		public String roomId = null;
		
		public static final String TYPE = "TestDoor";
		
		@Override
		public String getType() {
			return TYPE;
		}
		
		@WorldMethod
		public List<Action> open(DialogEngine dialogEngine) {
			return [
				new ModifyObjectAction(
					session,
					[ (FIELD_ID): id, "isOpen": "true" ]
				)
			];
		}
		
		@WorldMethod
		public List<Action> close(DialogEngine dialogEngine) {
			return [
				new ModifyObjectAction(
					session,
					[ (FIELD_ID): id, "isOpen": "false" ]
				)
			];
		}
		
		@WorldMethod
		public List<Action> lock(DialogEngine dialogEngine) {
			return [
				new ModifyObjectAction(
					session,
					[ (FIELD_ID): id, "isLocked": "true" ]
				)
			];
		}
		
		@WorldMethod
		public List<Action> unlock(DialogEngine dialogEngine) {
			return [
				new ModifyObjectAction(
					session,
					[ (FIELD_ID): id, "isLocked": "false" ]
				)
			];
		}
		
		@Override
		public String toString() {
			def state = isOpen ? "open" : (isLocked ? "locked" : "closed");
			return String.format("[%s=Door(%s)]", id, state);
		}
	}
	
	@WorldClass("TestSwitch")
	public static class TestSwitch extends WorldObject {
		private static final Log logger = LogFactory.getLog(TestSwitch.class.getName());
		
		public static final String TYPE = "TestSwitch";
		
		@WorldField
		public boolean isOn = false;
		
		@WorldField
		public String name = "switch";
		
		@WorldField
		public String roomId = null;
		
		@Override
		public String getType() {
			return TYPE;
		}
		
		@WorldMethod
		public List<Action> switchOn(DialogEngine dialogEngine) {
			return [
				new ModifyObjectAction(
					session,
					[ (FIELD_ID): id, "isOn": "true" ]
				)
			];
		}
		
		@WorldMethod
		public List<Action> switchOff(DialogEngine dialogEngine) {
			return [
				new ModifyObjectAction(
					session,
					[ (FIELD_ID): id, "isOn": "false" ]
				)
			];
		}
		
		@WorldMethod
		public List<Action> toggle(DialogEngine dialogEngine) {
			return [
				new ModifyObjectAction(
					session,
					[ (FIELD_ID): id, "isOn": isOn ? "false" : "true" ]
				)
			];
		}
		
		@Override
		public String toString() {
			def state = isOn ? "on" : "off";
			return String.format("[Switch %s/%s, state: %s]", id, name, state);
		}
	}
	
	static def objects = [
		"door1": [
			(WorldObject.FIELD_ID): "door1",
			(WorldObject.FIELD_TYPE): TestDoor.TYPE,
			"isOpen": false,
			"isLocked": true
		],
		"switch1": [
			(WorldObject.FIELD_ID): "switch1",
			(WorldObject.FIELD_TYPE): TestSwitch.TYPE,
			"isOn": false
		]
	];

	static def objectsStr;
	static {
		objectsStr = [:]
		for (object in objects.values()) {
			def id = object[WorldObject.FIELD_ID]
			objectsStr[id] = [:]
			for (e in object) {
				objectsStr[id][e.key] = e.value as String
			}
		}
	}

	static void registerTypes() {
		WorldMaker.baseTypeRegistry.put(
			TestDoor.TYPE, TestDoor.class
		);
		WorldMaker.baseTypeRegistry.put(
			TestSwitch.TYPE, TestSwitch.class
		);
	}
	
	static User user = new User(1, "Test User", "en", "US");
}
