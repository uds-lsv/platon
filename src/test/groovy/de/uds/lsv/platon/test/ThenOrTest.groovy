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

package de.uds.lsv.platon.test;

import de.uds.lsv.platon.world.WorldObject
import TestEnvironment.TestDoor

class ThenOrTest extends TestImplBase {
	def testSimpleThen() {
		setup:
			init("""\
				input(en:~/ping/) {
					object('door1').open().then({
						if (object('door1').isOpen) {
							tell player, 'pong'
						} else {
							tell player, 'not open!'
						}
					})
				}
			""")
			addObject([
				(WorldObject.FIELD_TYPE): TestDoor.TYPE,
				(WorldObject.FIELD_ID): "door1",
				"isOpen": "false",
				"isLocked": "false"
			])
		
		when:
			input("ping")
			shutdownExecutors();
		then:
			1 * dialogWorldMonitor.changeRequestModify(
				-1,
				[ (WorldObject.FIELD_ID): "door1", "isOpen": "true" ]
			)
		then:
			1 * dialogClientMonitor.outputStart(_, _, "pong", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}
	
	def testOr() {
		setup:
			init(
				"input(en:~/ping/) { object({ it.id=='door1' }).open().or({ tell player, 'pong' }) }",
				changeRequestModifyFails: true
			)
			addObject([
				(WorldObject.FIELD_TYPE): TestDoor.TYPE,
				(WorldObject.FIELD_ID): "door1",
				"isOpen": "false",
				"isLocked": "false"
			])
		
		when:
			input("ping")
			shutdownExecutors();
		then:
			1 * dialogWorldMonitor.changeRequestModify(
				-1,
				[ "id": "door1", "isOpen": "true" ]
			)
		then:
			1 * dialogClientMonitor.outputStart(_, _, "pong", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}
	
	def testOrAndNotThen() {
		setup:
			init(
				"input(en:~/ping/) { object('door1').open().or({ tell player, 'pong' }).then({ tell player, 'error' }); }",
				changeRequestModifyFails: true
			)
			addObject([
				(WorldObject.FIELD_TYPE): TestDoor.TYPE,
				(WorldObject.FIELD_ID): "door1",
				"isOpen": "false",
				"isLocked": "false"
			])
		
		when:
			input("ping")
			shutdownExecutors();
		then:
			1 * dialogWorldMonitor.changeRequestModify(
				-1,
				[ "id": "door1", "isOpen": "true" ]
			)
		then:
			1 * dialogClientMonitor.outputStart(_, _, "pong", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}
	
	def testThenAndNotOr() {
		setup:
			init(
				"input(en:~/ping/) { object('door1').open().or({ tell player, 'error' }).then({ tell player, 'pong' }); }"
			)
			addObject([
				(WorldObject.FIELD_TYPE): TestDoor.TYPE,
				(WorldObject.FIELD_ID): "door1",
				"isOpen": "false",
				"isLocked": "false"
			])
		
		when:
			input("ping")
			shutdownExecutors();
		then:
			1 * dialogWorldMonitor.changeRequestModify(
				-1,
				[ (WorldObject.FIELD_ID): "door1", "isOpen": "true" ]
			)
		then:
			1 * dialogClientMonitor.outputStart(_, _, "pong", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}
	
	def testErrorHandlingThenError() {
		setup:
			init(
				"input(en:~/ping/) { object({ it.id=='door1' }).open().then({ success, errorCode -> if (success) { tell player, 'error' } else { tell player, 'pong' } }) }",
				changeRequestModifyFails: true
			)
			addObject([
				(WorldObject.FIELD_TYPE): TestDoor.TYPE,
				(WorldObject.FIELD_ID): "door1",
				"isOpen": "false",
				"isLocked": "false"
			])
		
		when:
			input("ping")
			shutdownExecutors();
		then:
			1 * dialogWorldMonitor.changeRequestModify(
				-1,
				[ (WorldObject.FIELD_ID): "door1", "isOpen": "true" ]
			)
		then:
			1 * dialogClientMonitor.outputStart(_, _, "pong", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}
	
	def testErrorHandlingThenNoError() {
		setup:
			init(
				"input(en:~/ping/) { object({ it.id=='door1' }).open().then({ success, errorCode -> if (success) { tell player, 'pong' } else { tell player, 'error' } }) }"
			)
			addObject([
				(WorldObject.FIELD_TYPE): TestDoor.TYPE,
				(WorldObject.FIELD_ID): "door1",
				"isOpen": "false",
				"isLocked": "false"
			])
		
		when:
			input("ping")
			shutdownExecutors();
		then:
			1 * dialogWorldMonitor.changeRequestModify(
				-1,
				[ (WorldObject.FIELD_ID): "door1", "isOpen": "true" ]
			)
		then:
			1 * dialogClientMonitor.outputStart(_, _, "pong", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}
}
