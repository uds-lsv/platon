package de.uds.lsv.platon.test

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import spock.lang.Specification
import de.uds.lsv.platon.DialogClient
import de.uds.lsv.platon.action.IOType
import de.uds.lsv.platon.session.DialogEngine
import de.uds.lsv.platon.session.DialogSession
import de.uds.lsv.platon.session.User
import de.uds.lsv.platon.world.WorldState

/**
 * A few complex tests to test the DE's barge in behavior.
 * Uses lots of stubs to eventually test
 * DialogEngine, ScriptAdapter, ActionQueue
 */
class BargeInTest extends Specification {
	private static int sessionId = 1;
	private static AtomicInteger nextOutputId = new AtomicInteger(0);
	
	private def outputReactions = [:]
	
	private def createDialogSession(users, dialogClient) {
		def worldState = Stub(WorldState);
		//def env = [ (DialogSession.ACTIVE_VARIABLE): "true" ];
		def env = [:];
		worldState.getEnvironmentVariables() >> env;
		worldState.getEnvironmentVariable(_) >> { args -> def key = args[0]; env.get(key) };
		worldState.getEnvironmentVariable(_, _) >> { key, defaultValue -> env.get(key, defaultValue) };
		
		def session = Stub(DialogSession);
		session.isActive() >> true;
		session.submit(_) >> { it[0]() };
		session.runOnSessionThread(_) >> { it[0]() };
		session.isOnSessionThread() >> true;
		session.getWorldState() >> worldState;
		session.getDialogClient() >> dialogClient;
		session.addOutputReaction(_, _) >> {
			outputId, closure ->
			outputReactions[outputId] = closure;
		}
		
		return session;
	}
	
	private DialogEngine createDialogEngine(session, User user, String script) {
		Reader reader = new StringReader(script);
		try {
			return new DialogEngine(
				session,
				user,
				reader,
				null
			);
		}
		finally {
			reader.close();
		}
	}
	
	private DialogClient createDialogClient(dialogClientMonitor, outputStartedSemaphore) {
		DialogClient dialogClient = Stub(DialogClient);
		ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
		
		dialogClient.outputStart(_, _, _, _) >> {
			User user, IOType ioType, String text, Map<String,String> details ->
			dialogClientMonitor.outputStart(user, ioType, text, details)
			
			def outputId = nextOutputId.incrementAndGet();
			scheduledExecutor.schedule(
				{
					outputReactions[outputId](1.0);
					outputReactions.remove(outputId);
				} as Runnable,
				3L, TimeUnit.SECONDS
			);
			
			outputStartedSemaphore.release();
			return outputId;
		}
		
		dialogClient.outputAbort(_, _) >> {
			int outputId, User user ->
			dialogClientMonitor.outputAbort(outputId, user)
			outputReactions[outputId](0.1)
		}
		
		return dialogClient;
	}
	
	def testBargeIn() {
		setup:
			def script =
				"input(en:~/A/) { tell player, 'AA'; }\n" +
				"input(en:~/B/) { tell player, 'BB'; }";
		
			Semaphore outputStartedSemaphore = new Semaphore(0);
				
			// Somehow defining expectations directly on the
			// mocked object sometimes breaks the defined methods
			DialogClient dialogClientMonitor = Mock(DialogClient)
			DialogClient dialogClient = createDialogClient(dialogClientMonitor, outputStartedSemaphore) 
			
			User user = new User(1, "Test User", "en", "US");
			DialogSession session = createDialogSession([user], dialogClient);
			DialogEngine dialogEngine = createDialogEngine(session, user, script)
			
		when:
			dialogEngine.inputStarted(IOType.SPEECH);
			dialogEngine.inputComplete(IOType.SPEECH, "A", null);
			outputStartedSemaphore.acquire();
			Thread.sleep(500);
			
			dialogEngine.inputStarted(IOType.SPEECH);
			dialogEngine.inputComplete(IOType.SPEECH, "B", null);
			outputStartedSemaphore.acquire();
			Thread.sleep(500);
		then:
			1 * dialogClientMonitor.outputStart(_, _, "AA", _)
			1 * dialogClientMonitor.outputAbort(1, _) // assuming we'll get outputId=1 for AA
			1 * dialogClientMonitor.outputStart(_, _, "BB", _)
	}
	
	def testBargeInUninterruptible() {
		setup:
			def script =
				"input(en:~/A/) { tell player, 'AA', uninterruptible: true }\n" +
				"input(en:~/B/) { tell player, 'BB'; }";
		
			def outputStartedSemaphore = new Semaphore(0);
				
			// Somehow defining expectations directly on the
			// mocked object sometimes breaks the defined methods 			
			DialogClient dialogClientMonitor = Mock(DialogClient)
			DialogClient dialogClient = createDialogClient(dialogClientMonitor, outputStartedSemaphore) 
			
			User user = new User(1, "Test User", "en", "US");
			def session = createDialogSession([user], dialogClient);
			def dialogEngine = createDialogEngine(session, user, script)
			
		when:
			dialogEngine.inputStarted(IOType.SPEECH);
			dialogEngine.inputComplete(IOType.SPEECH, "A", null);
			outputStartedSemaphore.acquire();
			Thread.sleep(500);
			
			dialogEngine.inputStarted(IOType.SPEECH);
			dialogEngine.inputComplete(IOType.SPEECH, "B", null);
			outputStartedSemaphore.acquire();
			Thread.sleep(500);
		then:
			1 * dialogClientMonitor.outputStart(_, _, "AA", _)
			1 * dialogClientMonitor.outputStart(_, _, "BB", _)
			0 * dialogClientMonitor.outputAbort(_)
	}
}
