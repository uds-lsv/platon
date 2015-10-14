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

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.codehaus.groovy.jsr223.GroovyScriptEngineFactory
import org.codehaus.groovy.jsr223.GroovyScriptEngineImpl

import de.martingropp.util.IdleManager
import de.martingropp.util.ReactionTrigger
import de.martingropp.util.ScoredObject
import de.uds.lsv.platon.config.Config;
import de.uds.lsv.platon.script.ListenableBindings.FallbackListener
import de.uds.lsv.platon.session.DialogEngine
import de.uds.lsv.platon.session.User
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
		
	User user;
	final Object addresseeAll = "all";

	DialogEngine dialogEngine;
	String language;
	
	private GroovyScriptEngineImpl scriptEngine = null;
	private URL scriptUrl = null;
	ListenableBindings bindings;
	ScriptBindings scriptBindings;
	
	List<Closure> prepareInput = new ArrayList<>();
	
	Map<String,Agent> agents = [:];
	AgentStack agentStack;
	AgentInstance focusAgentInstance = null;
	
	// baseAgent is not really an agent, it is on top of the stack
	// when no other agent is active, but it can not be removed
	// from the stack
	Agent baseAgent;

	Agent initialAgent;

	// Top priority input action that's executed before
	// all agent actions
	private PriorityInputAction priorityInputAction = null;
		
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
	
	@TypeChecked(TypeCheckingMode.SKIP)
	private String readDialogScript(IncludeReader scriptReader) {
		String script = scriptReader.readLines().join('\n');
		
		if (dialogEngine.session.config.dumpDialogScript != null) {
			dialogEngine.session.config.dumpDialogScript(script);
		}
		
		return script;
	}
	
	private void initialize(IncludeReader scriptReader, DialogEngine dialogEngine, Map<String,Object> definitions, String initScript) {
		bindings = new ListenableBindings();
		bindings.setFallbackListener(this);
		
		baseAgent = new Agent(this, "⟨base agent⟩");
		initialAgent = baseAgent;
		
		agentStack = new AgentStack();
		
		this.dialogEngine = dialogEngine;
		this.language = dialogEngine.getUser().language;
		
		user = dialogEngine.user;
		
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
		
		scriptBindings = new ScriptBindings(this);
		
		bindings.put("bindings", bindings);
		bindings.put("scriptAdapter", this);
		bindings.put("logger", logger);
		
		bindings.put("history", dialogEngine.history);
		bindings.put("queue", dialogEngine.getActionQueueView());
		
		bindings.put("agent", scriptBindings.&agent);
		bindings.put("initialAgent", scriptBindings.&initialAgent);
		bindings.put("delegateAgent", scriptBindings.&delegateAgent);
		bindings.put("exit", scriptBindings.&exit);
		
		bindings.put("init", scriptBindings.&init);
		bindings.put("enter", scriptBindings.&enter);
		
		bindings.put("prepareInput", scriptBindings.&prepareInput);
		
		bindings.put("input", scriptBindings.&input);
		bindings.put("_", Wildcard.INSTANCE);
		bindings.put("oneOf", scriptBindings.&oneOf);
		
		bindings.put("tell", scriptBindings.&tell);
		
		bindings.put("users", dialogEngine.session.users);
		bindings.put("user", user);
		bindings.put("all", addresseeAll);
		
		bindings.put("objectModified", scriptBindings.&objectModified);
		bindings.put("objectAdded", scriptBindings.&objectAdded);
		bindings.put("objectDeleted", scriptBindings.&objectDeleted);
		bindings.put("environmentModified", scriptBindings.&environmentModified);
		
		bindings.put("objects", scriptBindings.&objects);
		bindings.put("object", scriptBindings.&object);
		
		bindings.put("getenv", scriptBindings.&getEnvironment);
		bindings.put("setenv", scriptBindings.&setEnvironment);
		
		bindings.put("reaction", scriptBindings.&reaction);
		bindings.put("after", scriptBindings.&after);
		bindings.put("idle", scriptBindings.&idle);
		
		bindings.put("quit", scriptBindings.&quit);
		bindings.put("next", scriptBindings.&next);
		
		bindings.put("send", scriptBindings.&send);
		bindings.put("receive", scriptBindings.&receive);
		
		bindings.put("decouple", scriptBindings.&decouple);
		
		// additional definitions
		if (definitions != null) {
			for (Entry<String,Object> entry : definitions.entrySet()) {
				bindings.put(entry.getKey(), entry.getValue());
			}
		}
		
		// for after 3.seconds { ... } etc.
		URL timeUrl = ScriptAdapter.class.getResource("/platon-dsl/time.groovy");
		if (timeUrl == null) {
			throw new RuntimeException("Resource not found: /platon-dsl/time.groovy");
		}
		Reader reader = new InputStreamReader(timeUrl.openStream());
		try {
			scriptEngine.eval(reader);
		}
		finally {
			reader.close();
		}
		
		this.exceptionMapper = new ExceptionMapper(scriptReader, scriptEngine.getClassLoader());
		
		initializing = true;
		
		// run init script (mainly for testing)
		if (initScript != null) {
			logger.debug("Running initialization script");
			scriptEngine.eval(initScript);
		}
		
		String script = readDialogScript(scriptReader);
		
		try {
			scriptEngine.eval(script, bindings);
			
			dialogEngine.session.worldState.getWorldMaker().registerTypes(
				scriptEngine.getClassLoader()
			);
		
			checkUnconsumedUnknownIdentifiers();
		}
		catch (Exception e) {
			logger.error(e);
			logger.error("Trace: " + Arrays.asList(e.getStackTrace()).toString());
			e.printStackTrace();
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
				if (action.getMaximumNumberOfParameters() == 0) {
					action();
				} else if (action.getMaximumNumberOfParameters() == 1) {
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
	
	/**
	 * @param input
	 * @return
	 *   true, iff at least a part of the prepared input could be handled.
	 */
	@TypeChecked(TypeCheckingMode.SKIP)
	public void handleInput(String input, Map<String,String> details=null) {
		if (
			dialogEngine.getSession().executorThread != null &&
			dialogEngine.getSession().executorThread != Thread.currentThread()
		) {
			logger.warn("handleInput invoked outside session executor thread! Current thread: " + Thread.currentThread());
		}
		
		idleManager.ping();
		
		if (input == null || input.trim().length() == 0) {
			logger.info("Ignoring empty input.");
			return;
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
				
				logger.debug("prepareInput: »" + preparedInputs + "« => »" + newInputs + "«");
				preparedInputs = newInputs;
			}
			
			// handle prepared parts
			if (preparedInputs.size() == 1) {
				if (!handlePreparedInput(preparedInputs[0], details)) {
					logger.debug("Script can not (fully) handle input »" + input + "« (prepared: " + preparedInputs[0]+ "). Agent stack: »" + agentStack + "«.");
				}
			} else {
				for (Object currentInput : preparedInputs) {
					logger.debug("Decoupling reactions for " + currentInput);
					Object decoupledInput = currentInput;
					dialogEngine.getSession().addPartialAction({
						if (!handlePreparedInput(decoupledInput, details)) {
							logger.debug("Script can not (fully) handle input »" + input + "« (prepared: " + decoupledInput + "). Agent stack: »" + agentStack + "«.");
						}
					});
				}
			}
		}
		catch (Exception e) {
			logger.error(e);
			logger.error("Trace: " + Arrays.asList(e.getStackTrace()).toString());
			e.printStackTrace();
			throw exceptionMapper.translateException(e);
		}
	}
	
	@TypeChecked(TypeCheckingMode.SKIP)
	private boolean handlePreparedInput(Object currentInput, Map<String,String> details) {
		assert (focusAgentInstance == null);
		
		// Handle priority input action first
		if (priorityInputAction != null) {
			TwoCasePatternAction patternAction = priorityInputAction;
			priorityInputAction = null;
			focusAgentInstance = patternAction.action.agentInstance.get();
			assert (focusAgentInstance != null);
			try {
				Object result = patternAction.matches(currentInput, details);
				if (result != null) {
					// match
					currentInput = runInputAction(patternAction.action, currentInput, result);
					if (currentInput == null) {
						// no next()
						return true;
					}
				} else if (patternAction.elseAction != null) {
					// no match, elseAction available
					currentInput = runInputAction(patternAction.elseAction, currentInput, result);
					if (currentInput == null) {
						return true;
					}
				} else {
					// no match and no elseAction
					// => do nothing, but return true because there
					// was a priority action
					return true;
				}
				
				// if we make it here, next() was called
			}
			finally {
				focusAgentInstance = null;
			}
		}
		
		// Now go through the agent stack
		List matching = new ArrayList();
		Iterator<AgentInstance> agentIterator = agentStack.iterator();
		try {
			while (agentIterator.hasNext()) {
				focusAgentInstance = agentIterator.next();
				for (PatternAction patternAction : focusAgentInstance.getInputActions()) {
					Object result = patternAction.matches(currentInput, details);
					if (result != null) {
						if (patternAction.priority == Double.POSITIVE_INFINITY) {
							currentInput = runInputAction(patternAction.action, currentInput, result);
							if (currentInput == null) {
								// done
								return true;
							} else {
								// next called!
								matching.clear();
							}
						} else {
							matching.add(scored([ patternAction.action, currentInput, result ], patternAction.priority));
						}
					}
				}
			}
		}
		finally {
			focusAgentInstance = null;
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
			for (PatternAction patternAction : agent.getIntercomActions()) {
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
			logger.error("Trace: " + Arrays.asList(e.getStackTrace()).toString());
			e.printStackTrace();
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
			logger.error("Trace: " + Arrays.asList(e.getStackTrace()).toString());
			e.printStackTrace();
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
			Map<String,Object> newProperties = object.getPropertiesWithInternalNames();
			for (AgentInstance agent : agentStack) {
				agent.triggerObjectModifiedReactions(
					oldProperties, newProperties, wrapper
				);
			}
		}
		catch (Exception e) {
			logger.error(e);
			logger.error("Trace: " + Arrays.asList(e.getStackTrace()).toString());
			e.printStackTrace();
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
			logger.error("Trace: " + Arrays.asList(e.getStackTrace()).toString());
			e.printStackTrace();
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
			logger.error("Trace: " + Arrays.asList(e.getStackTrace()).toString());
			e.printStackTrace();
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
			
			//triggerNamedReaction(NAMED_REACTION_STARTED);
			
			if (!initRun) {
				initRun = true;
				for (AgentInstance agent in agentStack.reverseIterator()) {
					try {
						focusAgentInstance = agent;
						try {
							agent.init();
						}
						finally {
							focusAgentInstance = null;
						}
					}
					catch (Exception e) {
						logger.error(e);
						logger.error("Trace: " + Arrays.asList(e.getStackTrace()).toString());
						e.printStackTrace();
						throw exceptionMapper.translateException(e);
					}
				}
				
				try {
					agentStack.peekTop().enter();
				}
				catch (Exception e) {
					logger.error(e);
					logger.error("Trace: " + Arrays.asList(e.getStackTrace()).toString());
					e.printStackTrace();
					throw exceptionMapper.translateException(e);
				}
			}
			
		} else {
			logger.debug("Session paused.");
			idleManager.pause();
			//triggerNamedReaction(NAMED_REACTION_STOPPED);
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
	
	public PriorityInputAction getPriorityInputAction() {
		return priorityInputAction;
	} 
	
	public void setPriorityInputAction(TwoCasePatternAction action) {
		this.priorityInputAction = (action == null) ? null : new PriorityInputAction(this, action);
	}
	
	public void cancelPriorityInputAction(PriorityInputAction action) {
		if (action != null && priorityInputAction != action) {
			throw new IllegalStateException("Priority input action is not active: " + action + "\nActive priority input action: ");
		}
		setPriorityInputAction(null);
	}
}
