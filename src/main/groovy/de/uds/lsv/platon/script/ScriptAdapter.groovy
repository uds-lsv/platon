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

package de.uds.lsv.platon.script

import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode

import java.util.Map.Entry
import java.util.regex.Matcher
import java.util.regex.Pattern

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.codehaus.groovy.jsr223.GroovyScriptEngineFactory
import org.codehaus.groovy.jsr223.GroovyScriptEngineImpl

import de.martingropp.util.IdleManager;
import de.martingropp.util.ReactionTrigger;
import de.martingropp.util.ScoredObject;
import de.uds.lsv.platon.script.ListenableBindings.FallbackListener
import de.uds.lsv.platon.session.DialogEngine;
import de.uds.lsv.platon.session.DialogSession.SessionActiveListener
import de.uds.lsv.platon.world.WorldObject
import de.uds.lsv.platon.world.WorldState.AddListener
import de.uds.lsv.platon.world.WorldState.DeleteListener
import de.uds.lsv.platon.world.WorldState.EnvironmentListener
import de.uds.lsv.platon.world.WorldState.ModifyListener

/**
 * The layer between a dialog script and the DialogEngine class.
 * Uses the ScriptBindings class to provide new things to the DSL script.
 * 
 * @author mgropp
 */
@TypeChecked
public class ScriptAdapter implements AddListener, ModifyListener, DeleteListener, ReactionTrigger, EnvironmentListener, SessionActiveListener, FallbackListener {
	private static final Log logger = LogFactory.getLog(ScriptAdapter.class.getName());
	
	public static final String NAMED_REACTION_STARTED = "main:game started";
	public static final String NAMED_REACTION_STOPPED = "main:game stopped";
		
	Object addresseeUser;
	final Object addresseeAll = "all";

	DialogEngine dialogEngine;
	String language;
	
	private GroovyScriptEngineImpl scriptEngine = null;
	private URL scriptUrl = null;
	ListenableBindings bindings;
	
	List<Closure> prepareInput = new ArrayList<>();
	
	Map<String,Agent> agents = [:];
	AgentStack agentStack;
	
	// baseAgent is not really an agent, it is on top of the stack
	// when no other agent is active, but it can not be removed
	// from the stack
	Agent baseAgent;

	Agent initialAgent;
	
	private ExceptionMapper exceptionMapper;
	
	IdleManager idleManager;
	
	private Map<WorldObject,WorldObjectWrapper> worldObjectWrappers = new WeakHashMap<>();
	
	private boolean initializing = true;
	
	/**
	 * set to true if the main init closure was run so
	 * we don't run it again
	 */
	private boolean initRun = false;
	
	private final List<UnknownIdentifier> unconsumedUnknownIdentifiers = [];
	
	private class UnknownIdentifier {
		final String name;
		def args = null;
		private boolean consumed = false;
		
		public UnknownIdentifier(String name) {
			this.name = name;
			unconsumedUnknownIdentifiers.add(this);
		}
		
		public void consume() {
			if (consumed) {
				throw new IllegalStateException("You cannot consume an UnknownIdentifier twice!");
			}
			unconsumedUnknownIdentifiers.remove(this);
		}
		
		public boolean isConsumed() {
			return consumed;
		}
		
		public def call(Object... args) {
			this.args = args;
			return this;
		}
		
		@Override
		public String toString() {
			return "[Unknown identifier: ${name}]";
		}
	}
	
	public ScriptAdapter(Reader scriptReader, URI rootUri, DialogEngine dialogEngine, Map<String,Object> definitions=null, String initScript=null) {
		initialize(
			new IncludeReader(new BufferedReader(scriptReader), rootUri),
			dialogEngine,
			definitions,
			initScript
		);
	}
	
	public ScriptAdapter(BufferedReader scriptReader, URI rootUri, DialogEngine dialogEngine, Map<String,Object> definitions=null, String initScript=null) {
		initialize(
			new IncludeReader(scriptReader, rootUri),
			dialogEngine,
			definitions,
			initScript
		);
	}
	
	public ScriptAdapter(URL scriptUrl, DialogEngine dialogEngine, Map<String,Object> definitions=null, String initScript=null) {
		this.scriptUrl = scriptUrl;
		
		IncludeReader reader = new IncludeReader(scriptUrl, dialogEngine.session.config.scriptCharset);
		try {
			initialize(reader, dialogEngine, definitions, initScript);
		}
		finally {
			reader.close();
		}
	}
	
	private void initialize(IncludeReader script, DialogEngine dialogEngine, Map<String,Object> definitions, String initScript) {
		bindings = new ListenableBindings();
		bindings.setFallbackListener(this);
		
		baseAgent = new Agent(this, "⟨base agent⟩");
		initialAgent = baseAgent;
		
		agentStack = new AgentStack();
		
		this.dialogEngine = dialogEngine;
		this.language = dialogEngine.getUser().language;
		
		addresseeUser = dialogEngine.user;
		
		logger.info(String.format(
			"Initializing script adapter for user %d, language %s.",
			dialogEngine.user.id,
			dialogEngine.user.language
		));
		
		idleManager = new IdleManager(dialogEngine.session.executor);
		
		GroovyScriptEngineFactory factory = new GroovyScriptEngineFactory();
		logger.info(String.format(
			"Script engine: %s %s %s %s",
			factory.getEngineName(),
			factory.getEngineVersion(),
			factory.getLanguageName(),
			factory.getLanguageVersion()
		));
		scriptEngine = (GroovyScriptEngineImpl)factory.getScriptEngine();
		
		ScriptBindings scriptBindings = new ScriptBindings(this);
		
		bindings.put("bindings", bindings);
		bindings.put("scriptAdapter", this);
		bindings.put("logger", logger);
		
		bindings.put("history", dialogEngine.history);
		bindings.put("queue", dialogEngine.getActionQueueView());
		
		bindings.put("agent", scriptBindings.&scriptAgent);
		bindings.put("initialAgent", scriptBindings.&scriptInitialAgent);
		bindings.put("delegateAgent", scriptBindings.&scriptDelegateAgent);
		bindings.put("exit", scriptBindings.&scriptExit);
		
		bindings.put("init", scriptBindings.&scriptInit);
		bindings.put("enter", scriptBindings.&scriptEnter);
		
		bindings.put("prepareInput", scriptBindings.&scriptPrepareInput);
		
		bindings.put("input", scriptBindings.&scriptInput);
		bindings.put("_", Wildcard.INSTANCE);
		
		bindings.put("tell", scriptBindings.&scriptTell);
		
		bindings.put("users", dialogEngine.session.users);
		bindings.put("player", addresseeUser);
		bindings.put("user", addresseeUser);
		bindings.put("all", addresseeAll);
		
		bindings.put("objectModified", scriptBindings.&scriptObjectModified);
		bindings.put("objectAdded", scriptBindings.&scriptObjectAdded);
		bindings.put("objectDeleted", scriptBindings.&scriptObjectDeleted);
		bindings.put("environmentModified", scriptBindings.&scriptEnvironmentModified);
		
		bindings.put("objects", scriptBindings.&scriptObjects);
		bindings.put("object", scriptBindings.&scriptObject);
		
		bindings.put("getenv", scriptBindings.&scriptGetEnvironment);
		bindings.put("setenv", scriptBindings.&scriptSetEnvironment);
		
		bindings.put("reaction", scriptBindings.&scriptReaction);
		bindings.put("after", scriptBindings.&scriptAfter);
		bindings.put("idle", scriptBindings.&scriptIdle);
		
		bindings.put("quit", scriptBindings.&scriptQuit);
		bindings.put("next", scriptBindings.&scriptNext);
		bindings.put("selectTranslation", scriptBindings.&scriptSelectTranslation);
		
		bindings.put("send", scriptBindings.&scriptSend);
		bindings.put("internal", scriptBindings.&scriptInternal);
		
		// additional definitions
		if (definitions != null) {
			for (Entry<String,Object> entry : definitions.entrySet()) {
				bindings.put(entry.getKey(), entry.getValue());
			}
		}
		
		// for after 3.seconds { ... } etc.
		scriptEngine.eval(
			"Integer.metaClass.getMillisecond << { def value = delegate; if (value != 1) { logger.error(String.format('Grammar exception! Plural expected in »%d.millisecond«! ;)', delegate)); }\ndef duration = new groovy.time.TimeDuration(0, 0, 0, delegate); { closure -> [ duration, closure ]} }\n" +
			"Integer.metaClass.getSecond << { if (delegate != 1) { logger.error(String.format('Grammar exception! Plural expected in »%d.second«! ;)', delegate)); }\ndef duration = new groovy.time.TimeDuration(0, 0, delegate, 0); { closure -> [ duration, closure ]} }\n" +
			"Integer.metaClass.getMinute << { if (delegate != 1) { logger.error(String.format('Grammar exception! Plural expected in »%d.minute«! ;)', delegate)); }\ndef duration = new groovy.time.TimeDuration(0, delegate, 0, 0); { closure -> [ duration, closure ]} }\n" +
			"Integer.metaClass.getHour << { if (delegate != 1) { logger.error(String.format('Grammar exception! Plural expected in »%d.hour«! ;)', delegate)); }\ndef duration = new groovy.time.TimeDuration(delegate, 0, 0, 0); { closure -> [ duration, closure ]} }\n" +
			"Integer.metaClass.getMilliseconds << { def duration = new groovy.time.TimeDuration(0, 0, 0, delegate); { closure -> [ duration, closure ]} }\n" +
			"Integer.metaClass.getSeconds << { def duration = new groovy.time.TimeDuration(0, 0, delegate, 0); { closure -> [ duration, closure ]} }\n" +
			"Integer.metaClass.getMinutes << { def duration = new groovy.time.TimeDuration(0, delegate, 0, 0); { closure -> [ duration, closure ]} }\n" +
			"Integer.metaClass.getHours << { def duration = new groovy.time.TimeDuration(delegate, 0, 0, 0); { closure -> [ duration, closure ]} }"
		);
		
		this.exceptionMapper = new ExceptionMapper(script, scriptEngine.getClassLoader());
		
		initializing = true;
		
		// run init script (mainly for testing)
		if (initScript != null) {
			logger.debug("Running initialization script");
			scriptEngine.eval(initScript);
		}
		
		try {
			scriptEngine.eval(script, bindings);
			
			dialogEngine.session.worldState.getWorldMaker().registerTypes(
				scriptEngine.getClassLoader()
			);
		
			checkUnconsumedUnknownIdentifiers();
		}
		catch (Exception e) {
			logger.error(e);
			throw exceptionMapper.translateException(e);
		}
		finally {
			// init/enter are called when activated
			agentStack.push(new AgentInstance(agentStack, baseAgent), false);
			if (initialAgent != baseAgent) {
				agentStack.push(new AgentInstance(agentStack, initialAgent), false);
			}
			
			initializing = false;
		}
		
		dialogEngine.session.addSessionActiveListener(this);
	}
	
	public void checkUnconsumedUnknownIdentifiers() {
		if (!unconsumedUnknownIdentifiers.isEmpty()) {
			throw new DialogScriptException(
				new MissingPropertyException(String.format(
					"Unknown identifiers: %s",
					unconsumedUnknownIdentifiers.collect({ it.name }).join(" ")
				))
			);
		}
	}
	
	public void handleInputStarted() {
		idleManager.ping();
	}
	
	/**
	 * @return
	 *   null to stop input processing, or
	 *   a new input object to continue
	 */
	private Object runInputAction(AgentCallable action, Object input, Object other) {
		for (;;) {
			try {
				def result;
				if (action.getMaximumNumberOfParameters() == 1) {
					action(input);
				} else {
					action(input, other);
				}
			}
			catch (NextThrowable t) {
				if (t.nextInput != null) {
					return t.nextInput;
				} else {
					return input;
				}
			}
			
			return null;
		}
	}
	
	@TypeChecked(TypeCheckingMode.SKIP)
	private static def scored(object, score) {
		object.metaClass.mixin(ScoredObject);
		object.score = score;
		return object;
	}
	
	@TypeChecked(TypeCheckingMode.SKIP)
	public boolean handleInput(String input, Map<String,String> details=null) {
		if (
			dialogEngine.getSession().executorThread != null &&
			dialogEngine.getSession().executorThread != Thread.currentThread()
		) {
			logger.warn("handleInput invoked outside session executor thread! Current thread: " + Thread.currentThread());
		}
		
		idleManager.ping();
		
		if (input == null || input.trim().length() == 0) {
			logger.info("Ignoring empty input.");
			return true;
		}
		
		if (details != null) {
			details = Collections.unmodifiableMap(details);
		}
		
		try {
			// prepareInput
			List<Object> preparedInputs = [ input ];
			for (prepare in prepareInput) {
				List<Object> newInputs = new ArrayList<>();
				
				for (Object oldInput : preparedInputs) {
					Object result;
					if (prepare.maximumNumberOfParameters == 1) {
						result = prepare(oldInput);
					} else {
						result = prepare(oldInput, details);
					}
					
					if (result instanceof InputSequence) {
						newInputs.addAll((InputSequence)result);
					} else {
						newInputs.add(result);
					}
				}
				
				logger.debug("prepareInput: »${preparedInputs}« => »${newInputs}«")
				preparedInputs = newInputs;
			}
			
			// handle prepared parts
			for (Object currentInput : preparedInputs) {
				if (!handlePreparedInput(currentInput, details)) {
					logger.debug("Script can not handle input »${input}« (prepared: ${currentInput}). State stack: »${agentStack}«.");
					return false;
				}
			}
		
		}
		catch (Exception e) {
			logger.error(e);
			throw exceptionMapper.translateException(e);
		}
		
		return true;
	}
	
	@TypeChecked(TypeCheckingMode.SKIP)
	private boolean handlePreparedInput(Object currentInput, Map<String,String> details) {
		List matching = new ArrayList();
		
		for (AgentInstance agent : agentStack) {
			for (PatternAction patternAction : agent.getInputActions()) {
				Object result = patternAction.matches(currentInput, details);
				if (result != null) {
					if (patternAction.priority == Double.POSITIVE_INFINITY) {
						currentInput = runInputAction(patternAction.action, currentInput, result);
						if (currentInput == null) {
							return true;
						} else {
							matching.clear();
						}
					} else {
						matching.add(scored([ patternAction.action, currentInput, result ], patternAction.priority));
					}
				}
			}
		}
		
		// we didn't find a score=inf rule that didn't call next() yet
		while (!matching.isEmpty()) {
			// ...but we still have something that matched!
			// => select the item with the highest score
			def scores = matching.collect({ it.score });
			def index = scores.indexOf(scores.max())
			def best = matching[index]
			
			def nextInput = runInputAction(*best);
			if (nextInput != best[1]) {
				throw new RuntimeException("next with argument is not yet implemented for rules with finite priority.");
			}
			if (nextInput == null) {
				return true;
			}
			
			// next() => only consider following items
			matching = matching.subList(index+1, matching.size());
		}
		
		return false;
	}
	
	@TypeChecked(TypeCheckingMode.SKIP)
	private boolean handleInternalMessage(DialogEngine sender, Object message) {
		List matching = new ArrayList();
		
		for (AgentInstance agent : agentStack) {
			for (PatternAction patternAction : agent.getInputActions()) {
				Object result = patternAction.matches(message, sender.user);
				if (result != null) {
					if (patternAction.priority == Double.POSITIVE_INFINITY) {
						message = runInputAction(patternAction.action, message, result);
						if (message == null) {
							return true;
						} else {
							matching.clear();
						}
					} else {
						matching.add(scored([ patternAction.action, message, result ], patternAction.priority));
					}
				}
			}
		}
		
		// we didn't find a score=inf rule that didn't call next() yet
		while (!matching.isEmpty()) {
			// ...but we still have something that matched!
			// => select the item with the highest score
			def scores = matching.collect({ it.score });
			def index = scores.indexOf(scores.max())
			def best = matching[index]
			
			def nextInput = runInputAction(*best);
			if (nextInput != best[1]) {
				throw new RuntimeException("next with argument is not yet implemented for rules with finite priority.");
			}
			if (nextInput == null) {
				return true;
			}
			
			// next() => only consider following items
			matching = matching.subList(index+1, matching.size());
		}
		
		return false;
	}
	
	@TypeChecked(TypeCheckingMode.SKIP)
	@Override
	public boolean triggerNamedReaction(String id, Object... arguments) {
		if (
			dialogEngine.getSession().executorThread != null &&
			dialogEngine.getSession().executorThread != Thread.currentThread()
		) {
			logger.warn("triggerNamedReaction invoked outside session executor thread! Current thread: " + Thread.currentThread());
		}
		
		boolean skipped = false;
		try {
			for (AgentInstance agent : agentStack) {
				if (agent.hasNamedReaction(id)) {
					while (true) {
						try {
							agent.triggerNamedReaction(id, *arguments);
						}
						catch (NextThrowable t) {
							skipped = true;
							break;
						}
						
						return true;
					}
				}
			}
		}
		catch (Exception e) {
			logger.error(e);
			throw exceptionMapper.translateException(e);
		}
		
		if (!skipped) {
			logger.debug("Script has no reaction for id »${id}«.");
		}
		return false;
	}
	
	/*-----------------------------------------------------------------------------*/
	/* world state listeners                                                       */
	/*-----------------------------------------------------------------------------*/
	private WorldObjectWrapper getWorldObjectWrapper(WorldObject worldObject) {
		WorldObjectWrapper wrapper = worldObjectWrappers.get(worldObject);
		if (wrapper == null) {
			wrapper = new WorldObjectWrapper(worldObject, dialogEngine);
			worldObjectWrappers.put(worldObject, wrapper);
		}
		
		return wrapper;
	} 
	
	@Override
	@TypeChecked(TypeCheckingMode.SKIP)
	public void objectAdded(WorldObject object) {
		if (
			dialogEngine.getSession().executorThread != null &&
			dialogEngine.getSession().executorThread != Thread.currentThread()
		) {
			logger.warn("objectAdded invoked outside session executor thread! Current thread: " + Thread.currentThread());
		}
		
		try {
			def wrapper = getWorldObjectWrapper(object);
			for (AgentInstance agent : agentStack) {
				agent.triggerObjectAddedReactions(wrapper);
			}
		}
		catch (Exception e) {
			logger.error(e);
			throw exceptionMapper.translateException(e);
		}
	}
	
	@Override
	@TypeChecked(TypeCheckingMode.SKIP)
	public void objectModified(WorldObject object, Map<String,Object> oldProperties) {
		if (
			dialogEngine.getSession().executorThread != null &&
			dialogEngine.getSession().executorThread != Thread.currentThread()
		) {
			logger.warn("objectModified invoked outside session executor thread! Current thread: " + Thread.currentThread());
		}
		
		try {
			def wrapper = getWorldObjectWrapper(object);
			Map<String,Object> newProperties = object.getProperties();
			for (AgentInstance agent : agentStack) {
				agent.triggerObjectModifiedReactions(
					oldProperties, newProperties, wrapper
				);
			}
		}
		catch (Exception e) {
			logger.error(e);
			throw exceptionMapper.translateException(e);
		}
	}
	
	@Override
	@TypeChecked(TypeCheckingMode.SKIP)
	public void objectDeleted(WorldObject object) {
		if (
			dialogEngine.getSession().executorThread != null &&
			dialogEngine.getSession().executorThread != Thread.currentThread()
		) {
			logger.warn("objectDeleted invoked outside session executor thread! Current thread: " + Thread.currentThread());
		}
		
		try {
			def wrapper = getWorldObjectWrapper(object);
			for (AgentInstance agent : agentStack) {
				agent.triggerObjectDeletedReactions(wrapper);
			}
		}
		catch (Exception e) {
			logger.error(e);
			throw exceptionMapper.translateException(e);
		}
	}
	
	@Override
	@TypeChecked(TypeCheckingMode.SKIP)
	public void environmentModified(String key, String value) {
		if (
			dialogEngine.getSession().executorThread != null &&
			dialogEngine.getSession().executorThread != Thread.currentThread()
		) {
			logger.warn("environmentModified invoked outside session executor thread! Current thread: " + Thread.currentThread());
		}
		
		try {
			for (AgentInstance agent : agentStack) {
				agent.triggerEnvironmentModifiedReactions(key, value);
			}
		}
		catch (Exception e) {
			logger.error(e);
			throw exceptionMapper.translateException(e);
		}
	}
	
	@Override
	@TypeChecked(TypeCheckingMode.SKIP)
	public void sessionActiveChanged(boolean active) {
		if (
			dialogEngine.session != null &&
			!dialogEngine.session.isOnSessionThread()
		) {
			logger.warn("sessionActiveChanged called outside session executor thread! Current thread: " + Thread.currentThread());
		}
		
		if (active) {
			logger.debug("Session activated.");
			idleManager.start();
			
			triggerNamedReaction(NAMED_REACTION_STARTED);
			
			if (!initRun) {
				initRun = true;
				for (AgentInstance agent in agentStack.reverseIterator()) {
					agent.init();
				}
				
				agentStack.peekTop().enter();
			}
			
		} else {
			logger.debug("Session paused.");
			idleManager.pause();
			triggerNamedReaction(NAMED_REACTION_STOPPED);
		}
	}
		
	@Override
	public Object bindingsGetFallback(String key) {
		if (!initializing) {
			return null;
		}
		
		return new UnknownIdentifier(key);
	}
	
	/*-----------------------------------------------------------------------------*/
	/* getters & setters                                                           */
	/*-----------------------------------------------------------------------------*/
	public Agent getActiveAgent() {
		return activeAgent;
	}
}