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

import de.uds.lsv.platon.action.VerbalInputAction
import de.uds.lsv.platon.action.VerbalOutputAction
import de.uds.lsv.platon.script.DialogScriptException
import de.uds.lsv.platon.session.DialogSession
import de.uds.lsv.platon.session.TransactionManager.Transaction
import de.uds.lsv.platon.world.WorldObject
import TestEnvironment.TestDoor

class EndToEndTest extends TestImplBase {
	/**
	 * Test general input without specifying a language. 
	 */
	def testInputGeneral() {
		setup:
			init(
				"input(~/ping/) { tell user, 'pong' }"
			)
		when:
			input("ping");
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "pong", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}
	
	/**
	 * Test output for a single language.
	 */
	def testOutputSimple() {
		setup:
			init(
				"input(~/ping/) { tell user, 'pong' }"
			)
		when:
			input("ping");
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "pong", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}

	/**
	 * Test general output without specifying a language.
	 */
	def testOutputGeneral() {
		setup:
			init(
				"input(~/ping/) { tell user, 'pong' }"
			)
		when:
			input("ping");
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "pong", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}
	
	def testModifyObject() {
		setup:
			init("input(~/ping/) { object('door1').open(); }")
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
			worldState.getObjects().get('door1').isOpen
			!worldState.getObjects().get('door1').isLocked
	}
	
	def testModifyObjectSetPropertyAssign() {
		setup:
			init("input(~/ping/) { object('door1').isOpen = true }")
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
			worldState.getObjects().get('door1').isOpen
			!worldState.getObjects().get('door1').isLocked
	}
	
	def testModifyObjectSetPropertyMethod() {
		setup:
			init("input(~/ping/) { object('door1').setIsOpen(true) }")
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
			worldState.getObjects().get('door1').isOpen
			!worldState.getObjects().get('door1').isLocked
	}
	
	/**
	 * Just make sure we don't run into any exceptions there.
	 * There should also be a log entry.
	 */
	def testDefaultErrorHandling() {
		setup:
			init(
				"input(~/ping/) { object({ it.id=='door1' }).open() }",
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
	}
	
	/**
	 * Test reaction("main:unhandled error").
	 */
	def testUnhandledErrorReaction() {
		setup:
			init(
				"reaction('main:unhandled error') { tell user, it }\n" +
				"input(~/ping/) { object({ it.id=='door1' }).open() }",
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
			1 * dialogClientMonitor.outputStart(_, _, "error:test", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}
	
	def testAfter() {
		setup:
			long startTime = -1L;
			long duration = -1L; 
			init("""
				input(~/ping/) {
					setStartTime();
					after 1.seconds {
						setEndTime()
					}
				}
				""",
				definitions: [
					"setStartTime": { startTime = System.currentTimeMillis() },
					"setEndTime": { duration = System.currentTimeMillis() - startTime },
				]
			);
		
		when:
			input("ping");
			Thread.sleep(1500);
			shutdownExecutors();
		then:
			duration >= 1000L
			duration < 1500L
	}
	
	def testAfterCancel() {
		setup:
			long startTime = -1L;
			long duration = -1L;
			init("""
				input(~/ping/) {
					tell user, 'pong';
					def job = after(
						500.milliseconds,
						{ tell user, 'error' }
					);
					job.cancel();
				}
			""");
		when:
			input("ping");
			Thread.sleep(1500);
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "pong", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}
	
	def testIdle() {
		setup:
			long startTime = -1L;
			long duration = -1L;
			init(
				"""\
				input(~/ping/) { setStartTime(); }
				idle 1.seconds { setEndTime() }
				""",
				definitions: [
					"setStartTime": { startTime = System.currentTimeMillis() },
					"setEndTime": { duration = System.currentTimeMillis() - startTime },
				]
			);
		
		when:
			input("ping");
			Thread.sleep(2000);
			shutdownExecutors();
		then:
			duration >= 800L
			duration < 1500L
	}
	
	/**
	 * Test base agent init.
	 */
	def testInit() {
		setup:
			init(
				"init { tell user, 'init'; }",
				setActive: false
			);
		
		when:
			session.setActive(true);
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "init", _)
	}

	/**
	 * Test base agent init when session is activated twice.
	 */
	def testInitTwice() {
		setup:
			init(
				"init { tell user, 'init'; }",
				setActive: false
			);
		
		when:
			session.setActive(true);
			session.setActive(true);
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "init", _)
	}
	
	/**
	 * Check that nothing goes wrong deactivating the session.
	 */
	def testGameStopped() {
		setup:
			init(
				"input(~/ping/) { tell user, 'pong' }"
			);
		
		when:
			session.setActive(false);
			shutdownExecutors();
		then:
			{}
	}
	
	def testGetEnv() {
		setup:
			init(
				"input(~/ping/) { tell user, getenv('foo') }"
			);
			worldState.getEnvironmentVariables().put("foo", "pong");
	
		when:
			input("ping");
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "pong", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}
	
	def testSetEnv() {
		setup:
			init(
				"input(~/ping/) { setenv('foo', 'bar') }"
			);
			
		when:
			input("ping");
			shutdownExecutors();
		then:
			1 * dialogWorldMonitor.changeRequestModify(-1, [ "foo": "bar" ])
	}
	
	def testExceptionInInput() {
		setup:
			Exception exception = null;
			init(
				EndToEndTest.class.getClassLoader().getResource("test/input-exception.txt"),
				serverExceptionHandler: { e -> exception = e; }
			);
			
		when:
			input("ping");
			shutdownExecutors();
		then:
			exception instanceof DialogScriptException
			exception.startLine == 4
	}
	
	def testGlobal() {
		setup:
			init(
				"x = '1';\n" +
				"def f() { return x; }\n" +
				"input(~/ping/) { x = '2'; tell user, f(); }"
			)
		when:
			input("ping");
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "2", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}
	
	def testLocal() {
		setup:
			init(
				"def x = 1;\n" +
				"x += 1;\n" +
				"input(~/ping/) { tell user, Integer.toString(x); }"
			)
		when:
			input("ping");
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "2", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}
	
	def testFieldScriptAccess() {
		setup:
			init(
				"@groovy.transform.Field def x = 1;\n" +
				"x += 1;\n" +
				"input(~/ping/) { tell user, Integer.toString(x); }"
			)
		when:
			input("ping");
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "2", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}
	
	def testFieldScriptAccessFromMethod() {
		setup:
			init(
				"@groovy.transform.Field def x = 1;\n" +
				"def f() { return x; }\n" +
				"x += 1;\n" +
				"input(~/ping/) { tell user, Integer.toString(f()); }"
			)
		when:
			input("ping");
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "2", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}
	
	def testFieldWithClosures() {
		setup:
			init(
				"@groovy.transform.Field def x = 1;\n" +
				"def f() { return x; }\n" +
				"{ -> this.@x++; }();\n" +
				"input(~/ping/) { tell user, Integer.toString(f()); }"
			)
		when:
			input("ping");
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "2", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}
	
	def testLocalWithClosures() {
		setup:
			init(
				"def x = 1;\n" +
				"{ -> x++; }();\n" +
				"input(~/ping/) { tell user, Integer.toString(x); }"
			)
		when:
			input("ping");
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "2", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}
	
	def testGlobalDoubleAssignment() {
		setup:
			init(
				"x = 1;\n" +
				"def f() { return x; }\n" +
				"input(~/ping/) { x = 2; }\n" +
				"input(~/pong/) { x = 3; }\n" +
				"input(~/peng/) { tell user, Integer.toString(f()); }"
			)
		when:
			input("ping");
			input("pong");
			input("peng");
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "3", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}

	/**
	 * A rather superficial test of the history functionality.
	 */
	def testHistory() {
		setup:
			init(
				"input(~/ping/) { tell user, 'pong' }\n" +
				"input(~/peng/) { println history }\n"
			)
			
			def history = session.dialogEngines[users[0].id].history;

		when:
			input("ping");
			Thread.sleep(1000);
			input("peng");
			shutdownExecutors();
		then:
			history.get(0) instanceof VerbalInputAction
			history.get(1) instanceof VerbalOutputAction
			history.get(2) instanceof VerbalInputAction
			history.get(0).text == "peng"
			history.get(1).text == "pong"
			history.get(2).text == "ping"
	}
	
	def testPrepareInput() {
		setup:
			init(
				"prepareInput { return 'p' + it }\n" +
				"input(~/ping/) { tell user, 'pong' }"
			)
		when:
			input("ing");
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "pong", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}
	
	/*
	def testPrepareInputWithStates() {
		setup:
			init(
				"input(~/ping/) { tell user, 'ERROR' }\n" +
				"initialState('test') {\n" +
				"  prepareInput { return 'p' + it }\n" +
				"  input(~/ping/) { tell user, 'pong' }\n" +
				"}\n" +
				"input(~/ping/) { tell user, 'ERROR' }"
			)
		when:
			input("ing");
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "pong", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}
	
	def testPrepareInputMainWithStates() {
		setup:
			init(
				"input(~/ping/) { tell user, 'ERROR' }\n" +
				"prepareInput { return 'p' + it }\n" +
				"initialState('test') {\n" +
				"  input(~/ping/) { tell user, 'pong' }\n" +
				"}\n" +
				"prepareInput { return 'ERROR' }"
			)
		when:
			input("ing");
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "pong", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}
	*/
	
	def testInputDetails() {
		setup:
			init(
				"input({ text, details -> details.values().find() }) {\n" +
				"  text, result -> tell user, result;\n" +
				"}"
			)
		when:
			input("ping", [ "foo": "pong" ]);
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "pong", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}
	
	def testInputDetailsPrepare() {
		setup:
			init(
				"prepareInput { text, details -> details.values().find() }\n" +
				"input(~/ping/) { tell user, 'pong' }"
			)
		when:
			input("blah", [ "foo": "ping" ]);
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "pong", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}
	
	def testIdleInInput() {
		setup:
			init(
				"input(~/ping/) {\n" +
				"  idle 500.milliseconds { tell user, 'pong' }\n" +
				"}\n"
			)
		when:
			input("ping");
			Thread.sleep(1500);
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "pong", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}
	
	def testIdleInInputTime() {
		setup:
			long timestamp = -1;
			init(
				"input(~/ping/) {\n" +
				"  idle 500.milliseconds { recordTimestamp() }\n" +
				"}\n",
				definitions: [
					"recordTimestamp": { timestamp = System.nanoTime() } 
				]
			)
		when:
			long start = System.nanoTime();
			input("ping");
			Thread.sleep(1500);
			shutdownExecutors();
			
		then:
			timestamp >= 0L
			(timestamp - start) > 500000000L
			(timestamp - start) < 750000000L
	}
	
	def testIdleInInputCancel() {
		setup:
			init("""
				input(~/ping/) {
					tell user, 'pong';
					def job = idle(
						500.milliseconds,
						{ tell user, 'peng' }
					);
					job.cancel();
				}
			""")
		when:
			input("ping");
			Thread.sleep(1500);
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "pong", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}
	
	def testQueue() {
		setup:
			init(
				"input(~/ping/) {\n" +
				"  tell user, 'pong';\n" +
				"  tell user, 'peng';\n" +
				"  if (queue.last().text == 'peng') {\n" +
				"    tell user, 'pung';\n" +
				"  }\n" +
				"}"
			)
		when:
			input("ping");
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "pong", _)
			1 * dialogClientMonitor.outputStart(_, _, "peng", _)
			1 * dialogClientMonitor.outputStart(_, _, "pung", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}

	/**
	 * Test that only input filter closures with matching (first)
	 * argument type are run.
	 */	
	def testInputClosureArgumentTypes() {
		setup:
			init(
				"input({ Double x -> true }) { tell user, 'error' }\n" +
				"input(~/.*/) { tell user, 'pong' }"
			)
		when:
			input("ping");
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "pong", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}
	
	def testMainInit() {
		setup:
			init(
				"init { tell user, 'ping', uninterruptible: true }",
				setActive: false
			)
		when:
			setActive(true);
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "ping", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}
	
	def testInputWildcard() {
		setup:
			init(
				"input(_) { tell user, 'pong' }"
			)
		when:
			input("ping");
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "pong", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}
	
	def testNextWithArguments() {
		setup:
			init(
				"input('ping') { next('pong') }\n" +
				"input('pong') { tell user, it }"
			)
		when:
			input("ping");
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "pong", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}
	
	def testDefaultWorldObject() {
		setup:
			init(
				"input('ping') { def obj = object('test'); tell user, obj.message, uninterruptible: true; }\n" +
				"input('pong') { def obj = object('test'); tell user, obj.type, uninterruptible: true; }"
			)
			addObject([
				(WorldObject.FIELD_TYPE): "some type",
				(WorldObject.FIELD_ID): "test",
				"message": "pong"
			])
		
		when:
			input("ping")
			input("pong")
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "pong", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
		then:
			1 * dialogClientMonitor.outputStart(_, _, "some type", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}
	
	def testAgentBindings() {
		setup:
			init( 
				"input('ping') { foo() }\n" +
				"agent('foo') { input('ping') { tell user, 'pong' } }"
			)
		
		when:
			input("ping")
			input("ping")
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "pong", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}
	
	def testNonStringAgentIdentifier() {
		setup:
			init(
				"agent foo { input('ping') { tell user, 'pong' } }\n" +
				"input('ping') { foo() }"
			)
		when:
			input("ping")
			input("ping")
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "pong", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}
	
	def testNonStringAgentIdentifierInitial() {
		setup:
			init(
				"initialAgent foo { input('ping') { tell user, 'pong' } }"
			)
		when:
			input("ping")
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "pong", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}
	
	def testNonStringAgentIdentifierNested() {
		setup:
			init(
				"agent bar { agent foo { input('ping') { tell user, 'pong' } } }\n" +
				"input('ping') { foo() }"
			)
		when:
			input("ping")
			input("ping")
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "pong", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}
	
	def testUnknownIdentifier() {
		when:
			init(
				"def x = foo;"
			)
			shutdownExecutors();
		then:
			def e = thrown(DialogScriptException)
			e.getCause() instanceof MissingPropertyException
	}
	
	def testDelegateString() {
		setup:
			init("""
				agent foo { input("ping") { tell user, "pong" } }
				initialAgent bar { delegateAgent("foo") }
			""")
		when:
			input("ping")
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "pong", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}
	
	def testDelegateIdentifier() {
		setup:
			init("""
				agent foo { input("ping") { tell user, "pong" } }
				initialAgent bar { delegateAgent(foo) }
			""")
		when:
			input("ping")
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "pong", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}
	
	def testDelegateForwardException() {
		when:
			init("""
				initialAgent bar { delegateAgent(foo) }
				agent foo { input("ping") { tell user, "pong" } }
			""")
		then:
			thrown(Exception)
	}
	
	def testSendInternal() {
		setup:
			init(
				"""
				input("ping") {
					send(users[1], "pong");
				}
				internal(_) {
					tell user, it;
				}
				""",
				numUsers: 3
			)
		when:
			input("ping", null, users[0]);
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(users[1], _, "pong", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}
}

