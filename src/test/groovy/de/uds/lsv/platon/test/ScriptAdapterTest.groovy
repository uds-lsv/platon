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

import spock.lang.Specification
import de.uds.lsv.platon.DialogClient
import de.uds.lsv.platon.DialogWorld
import de.uds.lsv.platon.action.Action
import de.uds.lsv.platon.action.ModifyObjectAction
import de.uds.lsv.platon.action.VerbalOutputAction
import de.uds.lsv.platon.exception.DialogWorldException
import de.uds.lsv.platon.script.DialogScriptException
import de.uds.lsv.platon.script.ScriptAdapter
import de.uds.lsv.platon.script.ScriptBindings
import de.uds.lsv.platon.session.DialogEngine
import de.uds.lsv.platon.session.DialogSession
import de.uds.lsv.platon.session.User
import de.uds.lsv.platon.test.WorldObjectWrapperTest.TestObject
import de.uds.lsv.platon.world.WorldClass
import de.uds.lsv.platon.world.WorldMethod
import de.uds.lsv.platon.world.WorldObject
import de.uds.lsv.platon.world.WorldState

public class ScriptAdapterTest extends Specification {
	private static final Log logger = LogFactory.getLog(ScriptAdapterTest.class.getName());
	
	static class ReactionMonitor {
		public void reaction(long id) { }
	}

	@WorldClass("test.TestClass")
	static class TestClass extends WorldObject {
		@WorldMethod
		public List<Action> test() {
			return [
				new ModifyObjectAction(
					session,
					[ "id": id ]
				)
			];
		}
	};
		
	def testTopLevelStatementsException(String scriptStatement) {
		setup:
			def user = new User(0, "test user", "en", "us");
			def session = Stub(DialogSession);
			session.runOnSessionThread(_) >> { it[0]() };
			def dialogEngine = Mock(DialogEngine);
			dialogEngine.getUser() >> user;
			dialogEngine.getSession() >> session;
			
		when:
			try {
				def scriptAdapter = new ScriptAdapter(
					new StringReader(scriptStatement),
					null,
					dialogEngine
				);
			}
			catch (DialogScriptException e) {
				for (Throwable t = e.getCause(); t != null; t = t.getCause()) {
					if (!(t instanceof javax.script.ScriptException)) {
						throw t;
					}
				}
				
				throw e;
			}
		then:
			thrown(IllegalStateException)
		
		where:
			scriptStatement << [
				"tell user, 'foo'",
				"objects { it.type == 'foo' }",
				"object { it.type == 'foo' }",
			]
	}
	
	def testInputSimple() {
		setup:
			def user = new User(0, "test user", "en", "us");
			def session = Stub(DialogSession);
			session.runOnSessionThread(_) >> { it[0]() };
			def dialogEngine = Mock(DialogEngine);
			dialogEngine.getUser() >> user;
			dialogEngine.getSession() >> session;
			
			def scriptAdapter = new ScriptAdapter(
				new StringReader(
					"input(en:~/.*/){ tell user, it; }"
				),
				null,
				dialogEngine
			);
		
			def text = "test text";
			
		when:
			scriptAdapter.handleInput(text);
		then:
			1 * dialogEngine.addAction({ VerbalOutputAction action ->
				action.session == session &&
				action.user.id == user.id &&
				action.text == text
			})
	}
	
	def testInputCaseInsensitive() {
		setup:
			def user = new User(0, "test user", "en", "us");
			def session = Stub(DialogSession);
			session.runOnSessionThread(_) >> { it[0]() };
			def dialogEngine = Mock(DialogEngine);
			dialogEngine.getUser() >> user;
			dialogEngine.getSession() >> session;
			
			def scriptAdapter = new ScriptAdapter(
				new StringReader(
					"input(en:~/A/){ tell user, it; }\n" +
					"input(en:~/b/){ tell user, it; }"
				),
				null,
				dialogEngine
			);

			
		when:
			scriptAdapter.handleInput("A");
		then:
			1 * dialogEngine.addAction({ VerbalOutputAction action ->
				action.session == session &&
				action.user.id == user.id &&
				action.text == "A"
			})
		
		when:
			scriptAdapter.handleInput("a");
		then:
			1 * dialogEngine.addAction({ VerbalOutputAction action ->
				action.session == session &&
				action.user.id == user.id &&
				action.text == "a"
			})
		
		when:
			scriptAdapter.handleInput("B");
		then:
			1 * dialogEngine.addAction({ VerbalOutputAction action ->
				action.session == session &&
				action.user.id == user.id &&
				action.text == "B"
			})
		
		when:
			scriptAdapter.handleInput("b");
		then:
			1 * dialogEngine.addAction({ VerbalOutputAction action ->
				action.session == session &&
				action.user.id == user.id &&
				action.text == "b"
			})
	}
	
	def testInputLanguages() {
		setup:
			def user = new User(0, "test user", "de", "de");
			def session = Stub(DialogSession);
			session.runOnSessionThread(_) >> { it[0]() };
			def dialogEngine = Mock(DialogEngine);
			dialogEngine.getUser() >> user;
			dialogEngine.getSession() >> session;
			
			def scriptAdapter = new ScriptAdapter(
				new StringReader(
					"input(en: ~/.*/, de:  ~/.*/){ tell user, [ de: it, en: 'ERROR' ]; }"
				),
				null,
				dialogEngine
			);
		
			def text = "test text";
			
		when:
			scriptAdapter.handleInput(text);
		then:
			1 * dialogEngine.addAction({ VerbalOutputAction action ->
				action.session == session &&
				action.user.id == user.id &&
				action.text == text
			})
	}
	
	def testInputPattern() {
		setup:
			def user = new User(0, "test user", "en", "us");
			def session = Stub(DialogSession);
			session.runOnSessionThread(_) >> { it[0]() };
			def dialogEngine = Mock(DialogEngine);
			dialogEngine.getUser() >> user;
			dialogEngine.getSession() >> session;
			
			def scriptAdapter = new ScriptAdapter(
				new StringReader(
					"input(en:~/A/){ tell user, 'A'; }\n" +
					"input(en:~/B/){ tell user, 'B'; }"
				),
				null,
				dialogEngine
			);
			
		when:
			scriptAdapter.handleInput("A");
		then:
			1 * dialogEngine.addAction({ VerbalOutputAction action ->
				action.session == session &&
				action.user.id == user.id &&
				action.text == "A"
			})
		
		when:
			scriptAdapter.handleInput("B");
		then:
			1 * dialogEngine.addAction({ VerbalOutputAction action ->
				action.session == session &&
				action.user.id == user.id &&
				action.text == "B"
			})

		when:
			scriptAdapter.handleInput("C");
		then:
			0 * dialogEngine.addAction(_)
	}
	
	def testInputClosure() {
		setup:
			def user = new User(0, "test user", "en", "us");
			def session = Stub(DialogSession);
			session.runOnSessionThread(_) >> { it[0]() };
			def dialogEngine = Mock(DialogEngine);
			dialogEngine.getUser() >> user;
			dialogEngine.getSession() >> session;
			
			def scriptAdapter = new ScriptAdapter(
				new StringReader(
					"input(en: { it == 'A' }){ tell user, 'A'; }\n" +
					"input(en: { it == 'B' }){ tell user, 'B'; }"
				),
				null,
				dialogEngine
			);
			
		when:
			scriptAdapter.handleInput("A");
		then:
			1 * dialogEngine.addAction({ VerbalOutputAction action ->
				action.session == session &&
				action.user.id == user.id &&
				action.text == "A"
			})
		
		when:
			scriptAdapter.handleInput("B");
		then:
			1 * dialogEngine.addAction({ VerbalOutputAction action ->
				action.session == session &&
				action.user.id == user.id &&
				action.text == "B"
			})

		when:
			scriptAdapter.handleInput("C");
		then:
			0 * dialogEngine.addAction(_)
	}
	
	def testInputAddressee() {
		setup:
			def user = new User(0, "test user", "en", "us");
			def session = Stub(DialogSession);
			session.runOnSessionThread(_) >> { it[0]() };
			def dialogEngine = Mock(DialogEngine);
			dialogEngine.getUser() >> user;
			dialogEngine.getSession() >> session;
			
			def scriptAdapter = new ScriptAdapter(
				new StringReader(
					"input(en:~/A/){ tell user, 'ok'; }\n" +
					"input(en:~/B/){ tell all, 'ok'; }"
				),
				null,
				dialogEngine
			);
		
		when:
			scriptAdapter.handleInput("A");
		then:
			1 * dialogEngine.addAction({ VerbalOutputAction action ->
				action.session == session &&
				action.user.id == user.id &&
				action.text == "ok"
			})
		
		when:
			scriptAdapter.handleInput("B");
		then:
			1 * dialogEngine.addAction({ VerbalOutputAction action ->
				action.session == session &&
				action.user == null &&
				action.text == "ok"
			})
	}
	
	def testObjects() {
		setup:
			def user = new User(0, "test user", "en", "us");
			def objects = [:];
			def objectsResult = null;
			
			def worldState = Stub(WorldState);
			worldState.getObjects() >> objects;
			
			def session = Stub(DialogSession);
			session.getWorldState() >> worldState;
			
			def dialogEngine = Mock(DialogEngine);
			dialogEngine.getUser() >> user;
			dialogEngine.getSession() >> session;
		
			objects["foo"] = new TestObject();
			objects["foo"].init(session, [ (WorldObject.FIELD_ID): "foo", "propertyA": "1", (WorldObject.FIELD_TYPE): TestObject.TYPE ]);
			objects["bar"] = new TestObject();
			objects["bar"].init(session, [ (WorldObject.FIELD_ID): "bar", "propertyA": "2", (WorldObject.FIELD_TYPE): TestObject.TYPE ]);
			objects["baz"] = new TestObject();
			objects["baz"].init(session, [ (WorldObject.FIELD_ID): "baz", "propertyA": "3", (WorldObject.FIELD_TYPE): TestObject.TYPE ]);
			
			def scriptAdapter = new ScriptAdapter(
				new StringReader(
					"input(en:~/0/){ s -> checkObjects(objects { it.type == 'WRONG' }) }\n" +
					"input(en:~/1/){ s -> checkObjects(objects { it.type == '${TestObject.TYPE}' }) }\n" +
					"input(en:~/2/){ s -> checkObjects(objects { it.type == '${TestObject.TYPE}' && it.propertyA == 1 }) }\n" +
					"input(en:~/3/){ s -> checkObjects(objects { it.type == '${TestObject.TYPE}' && it.propertyA < 3 }) }"
				),
				null,
				dialogEngine,
				[ "checkObjects": { objectsResult = it } ]
			);
		
		when:
			objectsResult = null;
			scriptAdapter.handleInput("0")
		then:
			objectsResult.isEmpty();
		
		when:
			objectsResult = null;
			scriptAdapter.handleInput("1")
		then:
			new HashSet(objectsResult.collect { it.id }) == new HashSet(objects.collect { it.value.id })
		
		when:
			objectsResult = null;
			scriptAdapter.handleInput("2")
		then:
			new HashSet(objectsResult.collect { it.id }) == new HashSet([ "foo" ])
		
		when:
			objectsResult = null;
			scriptAdapter.handleInput("3")
		then:
			new HashSet(objectsResult.collect { it.id }) == new HashSet([ "foo", "bar" ])
	}
	
	def testSingleObject() {
		setup:
			def user = new User(0, "test user", "en", "us");
			def objects = [:];
			def objectResult = null;
			
			def worldState = Stub(WorldState);
			worldState.getObjects() >> objects;
			
			def session = Stub(DialogSession);
			session.runOnSessionThread(_) >> { it[0]() };
			session.getWorldState() >> worldState;
			
			def dialogEngine = Mock(DialogEngine);
			dialogEngine.getUser() >> user;
			dialogEngine.getSession() >> session;
		
			objects["foo"] = new TestObject();
			objects["foo"].init(session, [ (WorldObject.FIELD_ID): "foo", "propertyA": "1", (WorldObject.FIELD_TYPE): TestObject.TYPE ]);
			objects["bar"] = new TestObject();
			objects["bar"].init(session, [ (WorldObject.FIELD_ID): "bar", "propertyA": "2", (WorldObject.FIELD_TYPE): TestObject.TYPE ]);
			objects["baz"] = new TestObject();
			objects["baz"].init(session, [ (WorldObject.FIELD_ID): "baz", "propertyA": "3", (WorldObject.FIELD_TYPE): TestObject.TYPE ]);
			
			def scriptAdapter = new ScriptAdapter(
				new StringReader(
					"input(en:~/0/){ s -> checkObject(object { it.type == 'WRONG' }) }\n" +
					"input(en:~/1/){ s -> checkObject(object { it.type == '${TestObject.TYPE}' }) }\n" +
					"input(en:~/2/){ s -> checkObject(object { it.type == '${TestObject.TYPE}' && it.propertyA == 1 }) }\n" +
					"input(en:~/3/){ s -> checkObject(object { it.type == '${TestObject.TYPE}' && it.propertyA < 3 }) }"
				),
				null,
				dialogEngine,
				[ "checkObject": { objectResult = it } ]
			);
		
		when:
			objectResult = null;
			scriptAdapter.handleInput("0");
		then:
			def e0 = thrown(DialogScriptException)
			e0.getCause() instanceof ScriptBindings.WrongNumberOfObjectsException
			
		when:
			objectResult = null;
			scriptAdapter.handleInput("1");
		then:
			def e1 = thrown(DialogScriptException)
			e1.getCause() instanceof ScriptBindings.WrongNumberOfObjectsException
			
		when:
			objectResult = null;
			scriptAdapter.handleInput("2");
		then:
			objectResult.id == "foo"
		
		when:
			objectResult = null;
			scriptAdapter.handleInput("3");
		then:
			def e3 = thrown(DialogScriptException)
			e3.getCause() instanceof ScriptBindings.WrongNumberOfObjectsException
	}
	
	def testObjectAdded() {
		setup:
			def user = new User(0, "test user", "en", "us");
			def objects = [:];
			def objectsResult = null;
			
			def worldState = Stub(WorldState);
			worldState.getObjects() >> objects;
			
			def session = Stub(DialogSession);
			session.runOnSessionThread(_) >> { it[0]() };
			session.getWorldState() >> worldState;
			
			def dialogEngine = Mock(DialogEngine);
			dialogEngine.getUser() >> user;
			dialogEngine.getSession() >> session;
		
			def testObject = new TestObject();
			testObject.init(session, [ (WorldObject.FIELD_ID): "foo", (WorldObject.FIELD_TYPE): TestObject.TYPE ]);
			
			def reactionMonitor = Mock(ReactionMonitor);
			def scriptAdapter = new ScriptAdapter(
				new StringReader(
					"objectAdded({ it.id == 'foo' }) { reactionTriggered(0) }\n" +
					"objectAdded({ it.id == 'bar' }) { reactionTriggered(1) }"
				),
				null,
				dialogEngine,
				[ "reactionTriggered": { int id -> reactionMonitor.reaction(id) } ]
			);
	
		when:
			scriptAdapter.objectAdded(testObject)
		then:
			1 * reactionMonitor.reaction(0)
			0 * reactionMonitor.reaction(_)
	}
	
	def testObjectAddedById() {
		setup:
			def user = new User(0, "test user", "en", "us");
			def objects = [:];
			def objectsResult = null;
			
			def worldState = Stub(WorldState);
			worldState.getObjects() >> objects;
			
			def session = Stub(DialogSession);
			session.runOnSessionThread(_) >> { it[0]() };
			session.getWorldState() >> worldState;
			
			def dialogEngine = Mock(DialogEngine);
			dialogEngine.getUser() >> user;
			dialogEngine.getSession() >> session;
		
			def testObject = new TestObject();
			testObject.init(session, [ (WorldObject.FIELD_ID): "foo", (WorldObject.FIELD_TYPE): TestObject.TYPE ]);
			
			def reactionMonitor = Mock(ReactionMonitor);
			def scriptAdapter = new ScriptAdapter(
				new StringReader(
					"objectAdded('foo') { reactionTriggered(0) }\n" +
					"objectAdded('bar') { reactionTriggered(1) }"
				),
				null,
				dialogEngine,
				[ "reactionTriggered": { int id -> reactionMonitor.reaction(id) } ]
			);
	
		when:
			scriptAdapter.objectAdded(testObject)
		then:
			1 * reactionMonitor.reaction(0)
			0 * reactionMonitor.reaction(_)
	}
	
	def testObjectAddedInInput() {
		setup:
			def user = new User(0, "test user", "en", "us");
			def objects = [:];
			def objectsResult = null;
			
			def worldState = Stub(WorldState);
			worldState.getObjects() >> objects;
			
			def session = Stub(DialogSession);
			session.runOnSessionThread(_) >> { it[0]() };
			session.getWorldState() >> worldState;
			
			def dialogEngine = Mock(DialogEngine);
			dialogEngine.getUser() >> user;
			dialogEngine.getSession() >> session;
		
			def testObject = new TestObject();
			testObject.init(session, [ (WorldObject.FIELD_ID): "foo", (WorldObject.FIELD_TYPE): TestObject.TYPE ]);
			
			def reactionMonitor = Mock(ReactionMonitor);
			def scriptAdapter = new ScriptAdapter(
				new StringReader(
					"input(~/ping/) { objectAdded({ it.id == 'foo' }) { reactionTriggered(0) } }"
				),
				null,
				dialogEngine,
				[ "reactionTriggered": { int id -> reactionMonitor.reaction(id) } ]
			);
	
		when:
			scriptAdapter.objectAdded(testObject)
		then:
			0 * reactionMonitor.reaction(_)
		
		when:
			scriptAdapter.handleInput("ping")
			scriptAdapter.objectAdded(testObject)
			scriptAdapter.objectAdded(testObject)
		then:
			1 * reactionMonitor.reaction(0)
			0 * reactionMonitor.reaction(_)
	}
	
	def testObjectModified() {
		setup:
			def user = new User(0, "test user", "en", "us");
			def objects = [:];
			def objectsResult = null;
			
			def worldState = Stub(WorldState);
			worldState.getObjects() >> objects;
			
			def session = Stub(DialogSession);
			session.runOnSessionThread(_) >> { it[0]() };
			session.getWorldState() >> worldState;
			
			def dialogEngine = Mock(DialogEngine);
			dialogEngine.getUser() >> user;
			dialogEngine.getSession() >> session;
		
			def testObject = new TestObject();
			testObject.init(session, [ (WorldObject.FIELD_ID): "foo", "propertyA": "1", (WorldObject.FIELD_TYPE): TestObject.TYPE ]);
			
			def reactionMonitor = Mock(ReactionMonitor)
			def scriptAdapter = new ScriptAdapter(
				new StringReader(
					"objectModified({ it.id == 'foo' && it.propertyA == 1 }, { println it; it.propertyA == 2 }) { println it; reactionTriggered(0) }\n" +
					"objectModified({ it.id == 'foo' && it.propertyA == 2 }, { true }) { reactionTriggered(1) }\n" +
					"objectModified({ it.id == 'foo' && it.propertyA == 1 }, { it.propertyA == 3 }) { reactionTriggered(2) }"
				),
				null,
				dialogEngine,
				[ "reactionTriggered": { int id -> reactionMonitor.reaction(id) } ]
			);
	
		when:
			def oldProperties = testObject.getProperties();
			testObject.propertyA = 2;
			scriptAdapter.objectModified(testObject, oldProperties)
		then:
			1 * reactionMonitor.reaction(0)
			0 * reactionMonitor.reaction(_)
	}
	
	def testObjectModifiedById() {
		setup:
			def user = new User(0, "test user", "en", "us");
			def objects = [:];
			def objectsResult = null;
			
			def worldState = Stub(WorldState);
			worldState.getObjects() >> objects;
			
			def session = Stub(DialogSession);
			session.runOnSessionThread(_) >> { it[0]() };
			session.getWorldState() >> worldState;
			
			def dialogEngine = Mock(DialogEngine);
			dialogEngine.getUser() >> user;
			dialogEngine.getSession() >> session;
		
			def testObject = new TestObject();
			testObject.init(session, [ (WorldObject.FIELD_ID): "foo", "propertyA": "1", (WorldObject.FIELD_TYPE): TestObject.TYPE ]);
			
			def reactionMonitor = Mock(ReactionMonitor)
			def scriptAdapter = new ScriptAdapter(
				new StringReader(
					"objectModified('foo', { it.propertyA == 2 }) { reactionTriggered(0) }"
				),
				null,
				dialogEngine,
				[ "reactionTriggered": { int id -> reactionMonitor.reaction(id) } ]
			);
	
		when:
			def oldProperties = testObject.getProperties();
			testObject.propertyA = 2;
			scriptAdapter.objectModified(testObject, oldProperties)
		then:
			1 * reactionMonitor.reaction(0)
			0 * reactionMonitor.reaction(_)
	}
	
	def testObjectModifiedInInput() {
		setup:
			def user = new User(0, "test user", "en", "us");
			def objects = [:];
			def objectsResult = null;
			
			def worldState = Stub(WorldState);
			worldState.getObjects() >> objects;
			
			def session = Stub(DialogSession);
			session.runOnSessionThread(_) >> { it[0]() };
			session.getWorldState() >> worldState;
			
			def dialogEngine = Mock(DialogEngine);
			dialogEngine.getUser() >> user;
			dialogEngine.getSession() >> session;
		
			def testObject = new TestObject();
			testObject.init(session, [ (WorldObject.FIELD_ID): "foo", "propertyA": "1", (WorldObject.FIELD_TYPE): TestObject.TYPE ]);
			
			def reactionMonitor = Mock(ReactionMonitor)
			def scriptAdapter = new ScriptAdapter(
				new StringReader(
					"input(~/ping/) { objectModified({ it.id == 'foo' }, { it.propertyA == 2 }) { reactionTriggered(0) } }"
				),
				null,
				dialogEngine,
				[ "reactionTriggered": { int id -> reactionMonitor.reaction(id) } ]
			);
	
		when:
			def oldProperties = testObject.getProperties();
			testObject.propertyA = 2;
			scriptAdapter.objectModified(testObject, oldProperties)
		then:
			0 * reactionMonitor.reaction(_)
		
		when:
			scriptAdapter.handleInput("ping")
			testObject.propertyA = 1;
			def oldProperties2 = testObject.getProperties();
			testObject.propertyA = 2;
			scriptAdapter.objectModified(testObject, oldProperties2)
			scriptAdapter.objectModified(testObject, oldProperties2)
		then:
			1 * reactionMonitor.reaction(0)
			0 * reactionMonitor.reaction(_)
	}
	
	def testObjectDeleted() {
		setup:
			def user = new User(0, "test user", "en", "us");
			def objects = [:];
			def objectsResult = null;
			
			def worldState = Stub(WorldState);
			worldState.getObjects() >> objects;
			
			def session = Stub(DialogSession);
			session.runOnSessionThread(_) >> { it[0]() };
			session.getWorldState() >> worldState;
			
			def dialogEngine = Mock(DialogEngine);
			dialogEngine.getUser() >> user;
			dialogEngine.getSession() >> session;
		
			def testObject = new TestObject();
			testObject.init(session, [ (WorldObject.FIELD_ID): "foo", (WorldObject.FIELD_TYPE): TestObject.TYPE ]);
			
			def reactionMonitor = Mock(ReactionMonitor)
			def scriptAdapter = new ScriptAdapter(
				new StringReader(
					"objectDeleted({ it.id == 'foo' }) { reactionTriggered(0) }\n" +
					"objectDeleted({ it.id == 'bar' }) { reactionTriggered(1) }"
				),
				null,
				dialogEngine,
				[ "reactionTriggered": { int id -> reactionMonitor.reaction(id) } ]
			);
	
		when:
			scriptAdapter.objectDeleted(testObject)
		then:
			1 * reactionMonitor.reaction(0)
			0 * reactionMonitor.reaction(_)
	}
	
	def testObjectDeletedById() {
		setup:
			def user = new User(0, "test user", "en", "us");
			def objects = [:];
			def objectsResult = null;
			
			def worldState = Stub(WorldState);
			worldState.getObjects() >> objects;
			
			def session = Stub(DialogSession);
			session.runOnSessionThread(_) >> { it[0]() };
			session.getWorldState() >> worldState;
			
			def dialogEngine = Mock(DialogEngine);
			dialogEngine.getUser() >> user;
			dialogEngine.getSession() >> session;
		
			def testObject = new TestObject();
			testObject.init(session, [ (WorldObject.FIELD_ID): "foo", (WorldObject.FIELD_TYPE): TestObject.TYPE ]);
			
			def reactionMonitor = Mock(ReactionMonitor)
			def scriptAdapter = new ScriptAdapter(
				new StringReader(
					"objectDeleted('foo') { reactionTriggered(0) }\n" +
					"objectDeleted('bar') { reactionTriggered(1) }"
				),
				null,
				dialogEngine,
				[ "reactionTriggered": { int id -> reactionMonitor.reaction(id) } ]
			);
	
		when:
			scriptAdapter.objectDeleted(testObject)
		then:
			1 * reactionMonitor.reaction(0)
			0 * reactionMonitor.reaction(_)
	}
	
	def testObjectDeletedInInput() {
		setup:
			def user = new User(0, "test user", "en", "us");
			def objects = [:];
			def objectsResult = null;
			
			def worldState = Stub(WorldState);
			worldState.getObjects() >> objects;
			
			def session = Stub(DialogSession);
			session.runOnSessionThread(_) >> { it[0]() };
			session.getWorldState() >> worldState;
			
			def dialogEngine = Mock(DialogEngine);
			dialogEngine.getUser() >> user;
			dialogEngine.getSession() >> session;
		
			def testObject = new TestObject();
			testObject.init(session, [ (WorldObject.FIELD_ID): "foo", (WorldObject.FIELD_TYPE): TestObject.TYPE ]);
			
			def reactionMonitor = Mock(ReactionMonitor)
			def scriptAdapter = new ScriptAdapter(
				new StringReader(
					"input(~/ping/) { objectDeleted({ it.id == 'foo' }) { reactionTriggered(0) } }"
				),
				null,
				dialogEngine,
				[ "reactionTriggered": { int id -> reactionMonitor.reaction(id) } ]
			);
	
		when:
			scriptAdapter.objectDeleted(testObject)
		then:
			0 * reactionMonitor.reaction(_)

		when:
			scriptAdapter.handleInput("ping")
			scriptAdapter.objectDeleted(testObject)
			scriptAdapter.objectDeleted(testObject)
		then:
			1 * reactionMonitor.reaction(0)
			0 * reactionMonitor.reaction(_)
	}
	
	def testEnvironmentModifiedString() {
		setup:
			def user = new User(0, "test user", "en", "us");
			def objects = [:];
			def objectsResult = null;
			
			def worldState = Stub(WorldState);
			worldState.getObjects() >> objects;
			
			def session = Stub(DialogSession);
			session.runOnSessionThread(_) >> { it[0]() };
			session.getWorldState() >> worldState;
			
			def dialogEngine = Mock(DialogEngine);
			dialogEngine.getUser() >> user;
			dialogEngine.getSession() >> session;
		
			def reactionMonitor = Mock(ReactionMonitor)
			def scriptAdapter = new ScriptAdapter(
				new StringReader(
					"environmentModified('foo', 'bar') { reactionTriggered(0) }\n" +
					"environmentModified('foo', 'baz') { reactionTriggered(1) }\n" +
					"environmentModified('bar', 'bar') { reactionTriggered(2) }"
				),
				null,
				dialogEngine,
				[ "reactionTriggered": { int id -> reactionMonitor.reaction(id) } ]
			);
	
		when:
			scriptAdapter.environmentModified('foo', 'bar')
		then:
			1 * reactionMonitor.reaction(0)
			0 * reactionMonitor.reaction(_)
	}
	
	def testEnvironmentModifiedStringInInput() {
		setup:
			def user = new User(0, "test user", "en", "us");
			def objects = [:];
			def objectsResult = null;
			
			def worldState = Stub(WorldState);
			worldState.getObjects() >> objects;
			
			def session = Stub(DialogSession);
			session.runOnSessionThread(_) >> { it[0]() };
			session.getWorldState() >> worldState;
			
			def dialogEngine = Mock(DialogEngine);
			dialogEngine.getUser() >> user;
			dialogEngine.getSession() >> session;
		
			def reactionMonitor = Mock(ReactionMonitor)
			def scriptAdapter = new ScriptAdapter(
				new StringReader(
					"input(~/ping/) { environmentModified('foo', 'bar') { reactionTriggered(0) } }"
				),
				null,
				dialogEngine,
				[ "reactionTriggered": { int id -> reactionMonitor.reaction(id) } ]
			);
	
		when:
			scriptAdapter.handleInput("ping")
			scriptAdapter.environmentModified('foo', 'bar')
			scriptAdapter.environmentModified('foo', 'bar')
		then:
			1 * reactionMonitor.reaction(0)
			0 * reactionMonitor.reaction(_)
	}
	
	def testEnvironmentModifiedClosure() {
		setup:
			def user = new User(0, "test user", "en", "us");
			def objects = [:];
			def objectsResult = null;
			
			def worldState = Stub(WorldState);
			worldState.getObjects() >> objects;
			
			def session = Stub(DialogSession);
			session.runOnSessionThread(_) >> { it[0]() };
			session.getWorldState() >> worldState;
			
			def dialogEngine = Mock(DialogEngine);
			dialogEngine.getUser() >> user;
			dialogEngine.getSession() >> session;
		
			def reactionMonitor = Mock(ReactionMonitor)
			def scriptAdapter = new ScriptAdapter(
				new StringReader(
					"environmentModified('foo', { it.equals('bar') }) { reactionTriggered(0) }\n" +
					"environmentModified('foo', { it.equals('baz') }) { reactionTriggered(1) }\n" +
					"environmentModified('bar', { it.equals('bar') }) { reactionTriggered(2) }"
				),
				null,
				dialogEngine,
				[ "reactionTriggered": { int id -> reactionMonitor.reaction(id) } ]
			);
	
		when:
			scriptAdapter.environmentModified('foo', 'bar')
		then:
			1 * reactionMonitor.reaction(0)
			0 * reactionMonitor.reaction(_)
	}
	
	def testEnvironmentModifiedClosureInInput() {
		setup:
			def user = new User(0, "test user", "en", "us");
			def objects = [:];
			def objectsResult = null;
			
			def worldState = Stub(WorldState);
			worldState.getObjects() >> objects;
			
			def session = Stub(DialogSession);
			session.runOnSessionThread(_) >> { it[0]() };
			session.getWorldState() >> worldState;
			
			def dialogEngine = Mock(DialogEngine);
			dialogEngine.getUser() >> user;
			dialogEngine.getSession() >> session;
		
			def reactionMonitor = Mock(ReactionMonitor)
			def scriptAdapter = new ScriptAdapter(
				new StringReader(
					"input(~/ping/) { environmentModified('foo', { it.equals('bar') }) { reactionTriggered(0) } }"
				),
				null,
				dialogEngine,
				[ "reactionTriggered": { int id -> reactionMonitor.reaction(id) } ]
			);
	
		when:
			scriptAdapter.handleInput("ping")
			scriptAdapter.environmentModified('foo', 'bar')
			scriptAdapter.environmentModified('foo', 'bar')
		then:
			1 * reactionMonitor.reaction(0)
			0 * reactionMonitor.reaction(_)
	}
	
	def testReactions() {
		setup:
			def user = new User(0, "test user", "en", "us");
			def session = Stub(DialogSession);
			session.runOnSessionThread(_) >> { it[0]() };
			def dialogEngine = Mock(DialogEngine);
			dialogEngine.getUser() >> user;
			dialogEngine.getSession() >> session;
			
			def reactionMonitor = Mock(ReactionMonitor)
			def scriptAdapter = new ScriptAdapter(
				new StringReader(
					"reaction('1') { reactionTriggered(0) }\n" +
					"reaction('2') { reactionTriggered(1) }\n"
				),
				null,
				dialogEngine,
				[ "reactionTriggered": { int id -> reactionMonitor.reaction(id) } ]
			);
		
		when:
			scriptAdapter.triggerNamedReaction("1");
		then:
			1 * reactionMonitor.reaction(0)
			0 * reactionMonitor.reaction(_)
		
		when:
			scriptAdapter.triggerNamedReaction("2");
		then:
			1 * reactionMonitor.reaction(1)
			0 * reactionMonitor.reaction(_)
	}
	
	def testInputReactionOrder() {
		setup:
			def user = new User(0, "test user", "en", "us");
			def session = Stub(DialogSession);
			session.runOnSessionThread(_) >> { it[0]() };
			def dialogEngine = Mock(DialogEngine);
			dialogEngine.getUser() >> user;
			dialogEngine.getSession() >> session;
			
			def reactionMonitor = Mock(ReactionMonitor);
			def scriptAdapter = new ScriptAdapter(
				new StringReader(
					"input(en:~/A/){ reactionTriggered(0) }\n" +
					"input(en:~/A/){ reactionTriggered(1) }\n" +
					"input(en:~/A/){ reactionTriggered(2) }\n" +
					"input(en:~/A/){ reactionTriggered(3) }\n" +
					"input(en:~/A/){ reactionTriggered(4) }\n" +
					"input(en:~/A/){ reactionTriggered(5) }\n" +
					"input(en:~/A/){ reactionTriggered(6) }\n" +
					"input(en:~/A/){ reactionTriggered(7) }\n" +
					"input(en:~/A/){ reactionTriggered(8) }\n" +
					"input(en:~/A/){ reactionTriggered(9) }\n" +
					"input(en:~/A/){ reactionTriggered(10) }\n" +
					"input(en:~/A/){ reactionTriggered(11) }\n" +
					"input(en:~/A/){ reactionTriggered(12) }\n" +
					"input(en:~/A/){ reactionTriggered(13) }\n" +
					"input(en:~/A/){ reactionTriggered(14) }\n" +
					"input(en:~/A/){ reactionTriggered(15) }\n" +
					"input(en:~/B/){ reactionTriggered(20) }\n" +
					"input(en:~/B/){ reactionTriggered(21) }\n" +
					"input(en:~/B/){ reactionTriggered(22) }\n" +
					"input(en:~/B/){ reactionTriggered(23) }\n" +
					"input(en:~/B/){ reactionTriggered(24) }\n" +
					"input(en:~/B/){ reactionTriggered(25) }\n" +
					"input(en:~/B/){ reactionTriggered(26) }\n" +
					"input(en:~/B/){ reactionTriggered(27) }\n" +
					"input(en:~/B/){ reactionTriggered(28) }\n" +
					"input(en:~/B/){ reactionTriggered(29) }\n" +
					"input(en:~/B/){ reactionTriggered(20) }\n" +
					"input(en:~/B/){ reactionTriggered(21) }\n" +
					"input(en:~/B/){ reactionTriggered(22) }\n" +
					"input(en:~/B/){ reactionTriggered(23) }\n" +
					"input(en:~/B/){ reactionTriggered(24) }\n" +
					"input(en:~/B/){ reactionTriggered(25) }"
				),
				null,
				dialogEngine,
				[ "reactionTriggered": { int id -> reactionMonitor.reaction(id) } ]
			);
		
		when:
			scriptAdapter.handleInput("A");
		then:
			1 * reactionMonitor.reaction(0)
			0 * reactionMonitor.reaction(_)

		when:
			scriptAdapter.handleInput("B");
		then:
			1 * reactionMonitor.reaction(20)
			0 * reactionMonitor.reaction(_)
	}
	
	def testInputReactionOrderAgents() {
		setup:
			def user = new User(0, "test user", "en", "us");
			def session = Stub(DialogSession);
			session.runOnSessionThread(_) >> { it[0]() };
			def dialogEngine = Mock(DialogEngine);
			dialogEngine.getUser() >> user;
			dialogEngine.getSession() >> session;
			
			def reactionMonitor = Mock(ReactionMonitor);
			def scriptAdapter = new ScriptAdapter(
				new StringReader(
					"input(en:~/A/){ reactionTriggered(0) }\n" +
					"input(en:~/A/){ reactionTriggered(1) }\n" +
					"input(en:~/A/){ reactionTriggered(2) }\n" +
					"input(en:~/A/){ reactionTriggered(3) }\n" +
					"input(en:~/A/){ reactionTriggered(4) }\n" +
					"input(en:~/A/){ reactionTriggered(5) }\n" +
					"input(en:~/A/){ reactionTriggered(6) }\n" +
					"input(en:~/A/){ reactionTriggered(7) }\n" +
					"input(en:~/A/){ reactionTriggered(8) }\n" +
					"input(en:~/A/){ reactionTriggered(9) }\n" +
					"input(en:~/A/){ reactionTriggered(10) }\n" +
					"input(en:~/A/){ reactionTriggered(11) }\n" +
					"input(en:~/A/){ reactionTriggered(12) }\n" +
					"input(en:~/A/){ reactionTriggered(13) }\n" +
					"input(en:~/A/){ reactionTriggered(14) }\n" +
					"input(en:~/A/){ reactionTriggered(15) }\n" +
					"input(en:~/B/){ reactionTriggered(20) }\n" +
					"input(en:~/B/){ reactionTriggered(21) }\n" +
					"input(en:~/B/){ reactionTriggered(22) }\n" +
					"input(en:~/B/){ reactionTriggered(23) }\n" +
					"input(en:~/B/){ reactionTriggered(24) }\n" +
					"input(en:~/B/){ reactionTriggered(25) }\n" +
					"input(en:~/B/){ reactionTriggered(26) }\n" +
					"input(en:~/B/){ reactionTriggered(27) }\n" +
					"input(en:~/B/){ reactionTriggered(28) }\n" +
					"input(en:~/B/){ reactionTriggered(29) }\n" +
					"input(en:~/B/){ reactionTriggered(20) }\n" +
					"input(en:~/B/){ reactionTriggered(21) }\n" +
					"input(en:~/B/){ reactionTriggered(22) }\n" +
					"input(en:~/B/){ reactionTriggered(23) }\n" +
					"input(en:~/B/){ reactionTriggered(24) }\n" +
					"input(en:~/B/){ reactionTriggered(25) }\n" +
					"initialAgent('testAgent') {\n" +
					"input(en:~/A/){ reactionTriggered(30) }\n" +
					"input(en:~/A/){ reactionTriggered(31) }\n" +
					"input(en:~/A/){ reactionTriggered(32) }\n" +
					"input(en:~/A/){ reactionTriggered(33) }\n" +
					"input(en:~/A/){ reactionTriggered(34) }\n" +
					"input(en:~/A/){ reactionTriggered(35) }\n" +
					"input(en:~/A/){ reactionTriggered(36) }\n" +
					"input(en:~/A/){ reactionTriggered(37) }\n" +
					"input(en:~/A/){ reactionTriggered(38) }\n" +
					"input(en:~/A/){ reactionTriggered(39) }\n" +
					"input(en:~/A/){ reactionTriggered(40) }\n" +
					"input(en:~/A/){ reactionTriggered(41) }\n" +
					"input(en:~/A/){ reactionTriggered(42) }\n" +
					"input(en:~/A/){ reactionTriggered(43) }\n" +
					"input(en:~/A/){ reactionTriggered(44) }\n" +
					"input(en:~/A/){ reactionTriggered(45) }\n" +
					"input(en:~/B/){ reactionTriggered(50) }\n" +
					"input(en:~/B/){ reactionTriggered(51) }\n" +
					"input(en:~/B/){ reactionTriggered(52) }\n" +
					"input(en:~/B/){ reactionTriggered(53) }\n" +
					"input(en:~/B/){ reactionTriggered(54) }\n" +
					"input(en:~/B/){ reactionTriggered(55) }\n" +
					"input(en:~/B/){ reactionTriggered(56) }\n" +
					"input(en:~/B/){ reactionTriggered(57) }\n" +
					"input(en:~/B/){ reactionTriggered(58) }\n" +
					"input(en:~/B/){ reactionTriggered(59) }\n" +
					"input(en:~/B/){ reactionTriggered(60) }\n" +
					"input(en:~/B/){ reactionTriggered(61) }\n" +
					"input(en:~/B/){ reactionTriggered(62) }\n" +
					"input(en:~/B/){ reactionTriggered(63) }\n" +
					"input(en:~/B/){ reactionTriggered(64) }\n" +
					"input(en:~/B/){ reactionTriggered(65) }\n" +
					"}"
				),
				null,
				dialogEngine,
				[ "reactionTriggered": { int id -> reactionMonitor.reaction(id) } ]
			);
		
		when:
			scriptAdapter.handleInput("A");
		then:
			1 * reactionMonitor.reaction(30)
			0 * reactionMonitor.reaction(_)

		when:
			scriptAdapter.handleInput("B");
		then:
			1 * reactionMonitor.reaction(50)
			0 * reactionMonitor.reaction(_)
	}
	
	def testAgents() {
		setup:
			def user = new User(0, "test user", "en", "us");
			def session = Stub(DialogSession);
			session.runOnSessionThread(_) >> { it[0]() };
			def dialogEngine = Mock(DialogEngine);
			dialogEngine.getUser() >> user;
			dialogEngine.getSession() >> session;
			
			def reactionMonitor = Mock(ReactionMonitor);
			def scriptAdapter = new ScriptAdapter(
				new StringReader(
					"agent agent1 { input(en:~/A/){ reactionTriggered(0) }\ninput(en:~/B/){ reactionTriggered(1) } }\n" +
					"agent agent2 { input(en:~/A/){ reactionTriggered(2) }\ninput(en:~/C/){ reactionTriggered(3) } }\n" +
					"input(en:~/D/){ reactionTriggered(4) }\n" +
					"input(en:~/agent1/){ agent1() }\n" +
					"input(en:~/agent2/){ agent2() }"
				),
				null,
				dialogEngine,
				[ "reactionTriggered": { int id -> reactionMonitor.reaction(id) } ]
			);
		
		// test free reactions 
		when:
			scriptAdapter.handleInput("A");
		then:
			0 * reactionMonitor.reaction(_)
		
		// test global reactions
		when: 
			scriptAdapter.handleInput("D");
		then:
			1 * reactionMonitor.reaction(4)
			0 * reactionMonitor.reaction(_)
		
		when:
			scriptAdapter.handleInput("agent1");
			scriptAdapter.handleInput("D");
		then:
			1 * reactionMonitor.reaction(4)
			0 * reactionMonitor.reaction(_)
		
		when:
			scriptAdapter.handleInput("agent2");
			scriptAdapter.handleInput("D");
		then:
			1 * reactionMonitor.reaction(4)
			0 * reactionMonitor.reaction(_)
			
		// test reactions (positive)
		when:
			scriptAdapter.handleInput("agent1");
			scriptAdapter.handleInput("A");
		then:
			1 * reactionMonitor.reaction(0)
			0 * reactionMonitor.reaction(_)
		
		when:
			scriptAdapter.handleInput("agent1");
			scriptAdapter.handleInput("B");
		then:
			1 * reactionMonitor.reaction(1)
			0 * reactionMonitor.reaction(_)
			
		when:
			scriptAdapter.handleInput("agent2");
			scriptAdapter.handleInput("A");
		then:
			1 * reactionMonitor.reaction(2)
			0 * reactionMonitor.reaction(_)
		
		when:
			scriptAdapter.handleInput("agent2");
			scriptAdapter.handleInput("C");
		then:
			1 * reactionMonitor.reaction(3)
			0 * reactionMonitor.reaction(_)
		
		// test reactions (negative) 
		when:
			scriptAdapter.handleInput("agent1");
			scriptAdapter.handleInput("C");
		then:
			0 * reactionMonitor.reaction(_)
		
		when:
			scriptAdapter.handleInput("agent2");
			scriptAdapter.handleInput("B");
		then:
			0 * reactionMonitor.reaction(_)
	}
	
	def testInitialAgent() {
		setup:
			def user = new User(0, "test user", "en", "us");
			def session = Stub(DialogSession);
			session.runOnSessionThread(_) >> { it[0]() };
			def dialogEngine = Mock(DialogEngine);
			dialogEngine.getUser() >> user;
			dialogEngine.getSession() >> session;
			
			def reactionMonitor = Mock(ReactionMonitor);
			def scriptAdapter = new ScriptAdapter(
				new StringReader(
					"agent('agent1') { input(en:~/A/){ reactionTriggered(0) } }\n" +
					"initialAgent('agent2') { input(en:~/A/){ reactionTriggered(1) } }\n" +
					"input(en:~/A/){ reactionTriggered(2) }\n"
				),
				null,
				dialogEngine,
				[ "reactionTriggered": { int id -> reactionMonitor.reaction(id) } ]
			);
		
		when:
			scriptAdapter.handleInput("A");
		then:
			1 * reactionMonitor.reaction(1)
			0 * reactionMonitor.reaction(_)
	}
	
	def testPersistence() {
		setup:
			def user = new User(0, "test user", "en", "us");
			def session = Stub(DialogSession);
			session.runOnSessionThread(_) >> { it[0]() };
			def dialogEngine = Mock(DialogEngine);
			dialogEngine.getUser() >> user;
			dialogEngine.getSession() >> session;
			
			def scriptAdapter = new ScriptAdapter(
				new StringReader(
					'def x = 0;\ninput(en:~/.*/){ x++; tell user, x }'
				),
				null,
				dialogEngine
			);
		
		when:
			scriptAdapter.handleInput("ping");
			scriptAdapter.handleInput("ping");
			scriptAdapter.handleInput("ping");
		then:
			1 * dialogEngine.addAction({ VerbalOutputAction action ->
				action.session == session &&
				action.user.id == user.id &&
				action.text == "1"
			})
			1 * dialogEngine.addAction({ VerbalOutputAction action ->
				action.session == session &&
				action.user.id == user.id &&
				action.text == "2"
			})
			1 * dialogEngine.addAction({ VerbalOutputAction action ->
				action.session == session &&
				action.user.id == user.id &&
				action.text == "3"
			})
	}
	
	def testPersistenceInBindings() {
		setup:
			def user = new User(0, "test user", "en", "us");
			def session = Stub(DialogSession);
			session.runOnSessionThread(_) >> { it[0]() };
			def dialogEngine = Mock(DialogEngine);
			dialogEngine.getUser() >> user;
			dialogEngine.getSession() >> session;
			
			def scriptAdapter = new ScriptAdapter(
				new StringReader(
					"x = 0;\n" +
					"def inc() { x++ }\n" +
					"input(en:~/.*/){ inc(); tell user, x }"
				),
				null,
				dialogEngine
			);
		
		when:
			scriptAdapter.handleInput("ping");
			scriptAdapter.handleInput("ping");
			scriptAdapter.handleInput("ping");
		then:
			1 * dialogEngine.addAction({ VerbalOutputAction action ->
				action.session == session &&
				action.user.id == user.id &&
				action.text == "1"
			})
			1 * dialogEngine.addAction({ VerbalOutputAction action ->
				action.session == session &&
				action.user.id == user.id &&
				action.text == "2"
			})
			1 * dialogEngine.addAction({ VerbalOutputAction action ->
				action.session == session &&
				action.user.id == user.id &&
				action.text == "3"
			})
	}
	
	def testPersistenceWithAgents() {
		setup:
			def user = new User(0, "test user", "en", "us");
			def session = Stub(DialogSession);
			session.runOnSessionThread(_) >> { it[0]() };
			def dialogEngine = Mock(DialogEngine);
			dialogEngine.getUser() >> user;
			dialogEngine.getSession() >> session;
			
			def scriptAdapter = new ScriptAdapter(
				new StringReader(
					'initialAgent agentA { def x = 0;\ninput(en:~/.*/){ x++; tell user, x; if (x > 1) agentB(); } }\n' +
					'agent agentB { def x = 100;\ninput(en:~/.*/){ x++; tell user, x; agentA() } }'
				),
				null,
				dialogEngine
			);
		
		when:
			scriptAdapter.handleInput("ping");
			scriptAdapter.handleInput("ping");
			scriptAdapter.handleInput("ping");
			scriptAdapter.handleInput("ping");
		then:
			1 * dialogEngine.addAction({ VerbalOutputAction action ->
				action.session == session &&
				action.user.id == user.id &&
				action.text == "1"
			})
			1 * dialogEngine.addAction({ VerbalOutputAction action ->
				action.session == session &&
				action.user.id == user.id &&
				action.text == "2"
			})
			1 * dialogEngine.addAction({ VerbalOutputAction action ->
				action.session == session &&
				action.user.id == user.id &&
				action.text == "101"
			})
			1 * dialogEngine.addAction({ VerbalOutputAction action ->
				action.session == session &&
				action.user.id == user.id &&
				action.text == "3"
			})
	}
	
	/**
	 * Test if uninterruptible flag gets set on VerbalOutputAction.
	 */
	def testUninterruptible() {
		setup:
			def user = new User(0, "test user", "en", "us");
			def session = Stub(DialogSession);
			session.runOnSessionThread(_) >> { it[0]() };
			def dialogEngine = Mock(DialogEngine);
			dialogEngine.getUser() >> user;
			dialogEngine.getSession() >> session;
			
			def scriptAdapter = new ScriptAdapter(
				new StringReader(
					'input(en: ~/unint/) { tell user, "unint", uninterruptible: true }\n' +
					'input(en: ~/int/) { tell user, "int" }'
				),
				null,
				dialogEngine
			);
		
		when:
			scriptAdapter.handleInput("unint");
		then:
			1 * dialogEngine.addAction({ VerbalOutputAction action ->
				action.session == session &&
				action.user.id == user.id &&
				action.text == "unint" &&
				action.uninterruptible == true
			})

		when:
			scriptAdapter.handleInput("int");
		then:
			1 * dialogEngine.addAction({ VerbalOutputAction action ->
				action.session == session &&
				action.user.id == user.id &&
				action.text == "int" &&
				action.uninterruptible == false
			})
	}
	
	def testThenSimple() {
		setup:
			def reactionMonitor = Mock(ReactionMonitor);
			
			def user = new User(0, "test user", "en", "us");
			
			def worldObjects = [:];
			List<Action> actions = [];
			
			def worldState = Stub(WorldState);
			worldState.getObjects() >> worldObjects;
			
			def session = Stub(DialogSession);
			session.getWorldState() >> worldState;
			session.runOnSessionThread(_) >> { it[0]() };
			session.isActive() >> true;
			
			def dialogEngine = Mock(DialogEngine);
			dialogEngine.getUser() >> user;
			dialogEngine.getSession() >> session;
			dialogEngine.addAction(_) >> { actions.addAll(it[0]) }
			
			def testObject = new TestClass();
				
			testObject.init(session, [ "id": "testObject" ]);
			worldObjects.put("testObject", testObject);
			
			def scriptAdapter = new ScriptAdapter(
				new StringReader(
					"input(en:~/.*/) { object('testObject').test().then({ reactionTriggered(1) }); }"
				),
				null,
				dialogEngine,
				[ "reactionTriggered": { int id -> reactionMonitor.reaction(id) } ]
			);
		
			scriptAdapter.handleInput("ping");
			Thread.sleep(1000);
			
		when:
			actions*.complete();
		then:
			1 * reactionMonitor.reaction(1)
			0 * reactionMonitor.reaction(_)
	}
	
	def testOrSimple() {
		setup:
			def reactionMonitor = Mock(ReactionMonitor);
			
			def user = new User(0, "test user", "en", "us");
			
			def worldObjects = [:];
			List<Action> actions = [];
			
			WorldState worldState = Stub(WorldState);
			worldState.getObjects() >> worldObjects;
			
			DialogClient dialogClient = Stub(DialogClient);
			
			DialogWorld dialogWorld = Stub(DialogWorld);
			dialogWorld.changeRequestModify(_, _) >> { throw new DialogWorldException("error:test") };
			
			def session = Stub(DialogSession);
			session.submit(_) >> { it[0]() }
			session.getWorldState() >> worldState;
			session.runOnSessionThread(_) >> { it[0]() };
			session.getDialogClient() >> dialogClient;
			session.getDialogWorld() >> dialogWorld;
			session.isActive() >> true;
			
			def dialogEngine = Mock(DialogEngine);
			dialogEngine.getUser() >> user;
			dialogEngine.getSession() >> session;
			dialogEngine.addAction(_) >> { actions.addAll(it[0]) }
			
			def testObject = new TestClass();
				
			testObject.init(session, [ "id": "testObject" ]);
			worldObjects.put("testObject", testObject);
			
			def scriptAdapter = new ScriptAdapter(
				new StringReader(
					"input(en:~/.*/) { object('testObject').test().or({ reactionTriggered(1) }); }"
				),
				null,
				dialogEngine,
				[ "reactionTriggered": { int id -> reactionMonitor.reaction(id) } ]
			);
		
			scriptAdapter.handleInput("ping");
			Thread.sleep(1000);
			
		when:
			actions*.execute();
		then:
			1 * reactionMonitor.reaction(1)
			0 * reactionMonitor.reaction(_)
	}
	
	def testTimeUnits() {
		setup:
			def user = new User(0, "test user", "en", "us");
			def session = Stub(DialogSession);
			session.runOnSessionThread(_) >> { it[0]() };
			def dialogEngine = Mock(DialogEngine);
			dialogEngine.getUser() >> user;
			dialogEngine.getSession() >> session;
			
			def scriptAdapter = new ScriptAdapter(
				new StringReader("""
					if (!(2.milliseconds instanceof groovy.time.TimeDuration)) { throw new AssertionError("2.milliseconds"); }
					if (!(2.seconds instanceof groovy.time.TimeDuration)) { throw new AssertionError("2.seconds"); }
					if (!(2.minutes instanceof groovy.time.TimeDuration)) { throw new AssertionError("2.minutes"); }
					if (!(2.hours instanceof groovy.time.TimeDuration)) { throw new AssertionError("2.hours"); }
					if (!(1.millisecond instanceof groovy.time.TimeDuration)) { throw new AssertionError("1.millisecond"); }
					if (!(1.second instanceof groovy.time.TimeDuration)) { throw new AssertionError("1.second"); }
					if (!(1.minute instanceof groovy.time.TimeDuration)) { throw new AssertionError("1.minute"); }
					if (!(1.hour instanceof groovy.time.TimeDuration)) { throw new AssertionError("1.hour"); }
					if (17.milliseconds.toMilliseconds() != 17) { throw new AssertionError("milliseconds"); }
					if (17.seconds.toMilliseconds() != 17000) { throw new AssertionError("seconds"); }
					if (17.minutes.toMilliseconds() != 1020000) { throw new AssertionError("minutes"); }
					if (17.hours.toMilliseconds() != 61200000) { throw new AssertionError("hours"); }
					if (1.millisecond.toMilliseconds() != 1) { throw new AssertionError("millisecond"); }
					if (1.second.toMilliseconds() != 1000) { throw new AssertionError("second"); }
					if (1.minute.toMilliseconds() != 60000) { throw new AssertionError("minute"); }
					if (1.hour.toMilliseconds() != 3600000) { throw new AssertionError("hour"); }
				"""),
				null,
				dialogEngine
			);
	}
	
	def testSyntaxError() {
		setup:
			def user = new User(0, "test user", "en", "us");
			def session = Stub(DialogSession);
			session.runOnSessionThread(_) >> { it[0]() };
			def dialogEngine = Mock(DialogEngine);
			dialogEngine.getUser() >> user;
			dialogEngine.getSession() >> session;
			
		when:
			def scriptAdapter = new ScriptAdapter(
				ScriptAdapterTest.class.getClassLoader().getResource("test/syntax-error.txt"),
				dialogEngine
			);
		then:
			def e = thrown(DialogScriptException)
			e.startLine == 2
	}
	
	def testException() {
		setup:
			def user = new User(0, "test user", "en", "us");
			def session = Stub(DialogSession);
			session.runOnSessionThread(_) >> { it[0]() };
			def dialogEngine = Mock(DialogEngine);
			dialogEngine.getUser() >> user;
			dialogEngine.getSession() >> session;
			
		when:
			def scriptAdapter = new ScriptAdapter(
				ScriptAdapterTest.class.getClassLoader().getResource("test/exception.txt"),
				dialogEngine
			);
		then:
			def e = thrown(DialogScriptException)
			e.startLine == 2
	}
	
	/**
	 * Errors should not be touched.
	 */
	def testError() {
		setup:
			def user = new User(0, "test user", "en", "us");
			def session = Stub(DialogSession);
			session.runOnSessionThread(_) >> { it[0]() };
			def dialogEngine = Mock(DialogEngine);
			dialogEngine.getUser() >> user;
			dialogEngine.getSession() >> session;
			
		when:
			def scriptAdapter = new ScriptAdapter(
				new StringReader(
					"throw new Error()"
				),
				null,
				dialogEngine
			);
		then:
			thrown(Error)
	}
	
	def testNext() {
		setup:
			def user = new User(0, "test user", "en", "us");
			def session = Stub(DialogSession);
			session.runOnSessionThread(_) >> { it[0]() };
			def dialogEngine = Mock(DialogEngine);
			dialogEngine.getUser() >> user;
			dialogEngine.getSession() >> session;
			
			def scriptAdapter = new ScriptAdapter(
				new StringReader(
					"input(en:~/.*/) { next() }\n" +
					"input(en:~/.*/) { tell user, 'pong' }"
				),
				null,
				dialogEngine
			);
			
		when:
			scriptAdapter.handleInput('ping');
		then:
			1 * dialogEngine.addAction({ VerbalOutputAction action ->
				action.session == session &&
				action.user.id == user.id &&
				action.text == 'pong'
			})
	}
	
	def testDuplicateAgentException() {
		setup:
			def user = new User(0, "test user", "en", "us");
			def session = Stub(DialogSession);
			session.runOnSessionThread(_) >> { it[0]() };
			def dialogEngine = Mock(DialogEngine);
			dialogEngine.getUser() >> user;
			dialogEngine.getSession() >> session;
		
		when:
			def scriptAdapter = new ScriptAdapter(
				new StringReader(
					"agent('test') { }\n" +
					"agent('test') { }"
				),
				null,
				dialogEngine
			);
		
		then:
			thrown(Exception)
	}
	
	def testDuplicateAgentNameException() {
		setup:
			def user = new User(0, "test user", "en", "us");
			def session = Stub(DialogSession);
			session.runOnSessionThread(_) >> { it[0]() };
			def dialogEngine = Mock(DialogEngine);
			dialogEngine.getUser() >> user;
			dialogEngine.getSession() >> session;
		
		when:
			def scriptAdapter = new ScriptAdapter(
				new StringReader(
					"test = 123;\n" +
					"agent test { }"
				),
				null,
				dialogEngine
			);
		
		then:
			thrown(Exception) // this exception is really confusing i guess, but hard to change
	}
}
