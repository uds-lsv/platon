package de.uds.lsv.platon.test

import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode

import java.util.concurrent.atomic.AtomicInteger

import spock.lang.Specification
import de.uds.lsv.platon.DialogClient
import de.uds.lsv.platon.DialogWorld
import de.uds.lsv.platon.action.IOType
import de.uds.lsv.platon.config.Config
import de.uds.lsv.platon.exception.DialogWorldException
import de.uds.lsv.platon.session.DialogSession
import de.uds.lsv.platon.session.User
import de.uds.lsv.platon.world.WorldObject
import de.uds.lsv.platon.world.WorldState

@TypeChecked
public class TestImplBase extends Specification {
	Config config = new Config();
	DialogClient dialogClient;
	DialogClient dialogClientMonitor;
	DialogWorld dialogWorld;
	DialogWorld dialogWorldMonitor;
	List<User> users;
	DialogSession session;
	WorldState worldState;
	
	AtomicInteger nextSessionId = new AtomicInteger(0);
	AtomicInteger nextOutputId = new AtomicInteger(0);
	
	public void init(Map kwargs=[:], String script) {
		config.openDialogScript = (Closure<Reader>){
			->
			return new StringReader(script);
		};
		config.dialogScriptRoot = TestImplBase.class.getClassLoader().getResource("script-include/")?.toURI();
		
		doInit(kwargs);
	}
	
	public void init(Map kwargs=[:], URL scriptUrl) {
		config.dialogScript = scriptUrl;
		doInit(kwargs);
	}
	
	private void doInit(Map kwargs=[:]) {
		TestEnvironment.registerTypes();
		
		config.wrapExceptions = kwargs.get("wrapExceptions", false);
		config.scriptDefinitions = kwargs.get("definitions", [:]);
		
		config.serverExceptionHandler = (Closure)kwargs.get("serverExceptionHandler", null);
		users = createUsers(kwargs.get("numUsers", 1));
		dialogClient = createDialogClient()
		dialogWorld = createDialogWorld(
			kwargs.get("changeRequestModifyFails", false),
			kwargs.get("changeRequestDeleteFails", false)
		);
		
		session = new DialogSession(config, dialogClient, dialogWorld, users);
	
		worldState = session.getWorldState();
		
		if (kwargs.get("setActive", true)) {
			setActive(true);
		}
	}
	
	List<User> createUsers(int numUsers) {
		List<User> users = [];
		for (i in 1..numUsers) {
			users.add(new User(i, "User ${i}", "en", "US"));
		}
		
		return users;
	}
	
	@TypeChecked(TypeCheckingMode.SKIP)
	DialogWorld createDialogWorld(boolean changeRequestModifyFails=false, boolean changeRequestDeleteFails=false) {
		DialogWorld dialogWorld = Stub(DialogWorld);
		dialogWorldMonitor = Mock(DialogWorld);
		
		dialogWorld.changeRequestModify(_,_) >> {
			int transactionId, Map<String,String> modifications ->
			dialogWorldMonitor.changeRequestModify(transactionId, modifications);
			println("changeRequestModify(${transactionId}, ${modifications}) (dialogWorldMonitor: ${System.identityHashCode(dialogWorldMonitor)})");
			if (changeRequestModifyFails) {
				throw new DialogWorldException("error:test");
			} else {
				// we hijack the session's executor so we can ensure a orderly
				// shutdown where all tasks have been executed 
				session.submit({
					if (modifications.containsKey(WorldObject.FIELD_ID)) {
						session.getTransactionManager().addChangeNotificationModify(
							transactionId,
							modifications
						);
					} else {
						for (Map.Entry<String,String> entry : modifications.entrySet()) {
							session.getTransactionManager().addEnvironmentModification(
								transactionId,
								entry.getKey(), entry.getValue()
							);
						}
					}
				});
			
				return "";
			}
		}
		
		dialogWorld.changeRequestDelete(_,_) >> {
			int transactionId, String objectId ->
			dialogWorldMonitor.changeRequestDelete(transactionId, objectId);
			println("changeRequestDelete(${transactionId}, »${objectId}«) (dialogWorldMonitor: ${System.identityHashCode(dialogWorldMonitor)})");
			if (changeRequestDeleteFails) {
				throw new DialogWorldException("error:test");
			} else {
				// we hijack the session's executor so we can ensure a orderly
				// shutdown where all tasks have been executed
				session.submit({
					session.getTransactionManager().addChangeNotificationDelete(
						transactionId,
						objectId
					);
				});
				return "";
			}
		}
		
		return dialogWorld;
	}
	
	@TypeChecked(TypeCheckingMode.SKIP)
	DialogClient createDialogClient() {
		DialogClient dialogClient = Stub(DialogClient);
		dialogClientMonitor = Mock(DialogClient);
		
		dialogClient.outputStart(_,_,_, _) >> {
			User user, IOType ioType, String text, Map<String,String> details ->
			synchronized (dialogWorldMonitor) {
				dialogClientMonitor.outputStart(user, ioType, text, details);
			}
			def outputId = nextOutputId.incrementAndGet();
			println("${text} (${details}) [${Thread.currentThread()} outputStart(${user}, ${ioType}, »${text}«) => ${outputId} (dialogClientMonitor: ${System.identityHashCode(dialogClientMonitor)})]");
			try {
				session.submit({
					session.outputEnded(outputId, user, 1.0);
				});
			}
			catch (Exception e){
				e.printStackTrace();
				System.err.println("Could not submit outputEnded task for output id ${outputId}.");
			}
			return outputId;
		}
		
		dialogClient.outputAbort(_, _) >> {
			int outputId, User user ->
			dialogClientMonitor.outputAbort(outputId, user);
			println("outputAbort(${outputId}, ${user}) (dialogClientMonitor: ${System.identityHashCode(dialogClientMonitor)})");
		}
		
		return dialogClient;
	}
	
	/*
	def createDispatcher(gameServer, sessionInfo) {
		ServerConnection serverConnection = new ServerConnection(gameServer, sessionInfo);
	
		config.connectToGameServer = {
			_gameServer, _sessionInfo ->
			serverConnection
		};
			
		dispatcher = new DialogDispatcher(config);
		dispatcher.sessionInit(
			// String gameServer
			null,
			// SessionInfo info
			sessionInfo
		)
	
		return dispatcher;
	}
	*/
	
	void shutdownExecutors() {
		session.shutdownExecutor();
	}
	
	void input(String text, Map<String,String> details=null, User speaker=null) {
		session.inputStarted(
			speaker,
			IOType.SPEECH
		);
		session.inputComplete(
			speaker,
			IOType.SPEECH,
			text,
			details
		);
	}
	
	void addObject(Map<String,String> properties) {
		session.getTransactionManager().addChangeNotificationAdd(
			-1,
			properties
		);
	}
	
	void setActive(boolean active) {
		session.setActive(active);
	}
}
