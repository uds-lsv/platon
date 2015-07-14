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

import groovy.time.TimeDuration
import groovy.transform.InheritConstructors
import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode

import java.util.Map.Entry
import java.util.regex.Pattern

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

import de.martingropp.util.PrimitiveUtil;
import de.uds.lsv.platon.action.Action
import de.uds.lsv.platon.action.ModifyEnvironmentAction
import de.uds.lsv.platon.action.QuitAction
import de.uds.lsv.platon.action.VerbalOutputAction
import de.uds.lsv.platon.script.ScriptAdapter.UnknownIdentifier
import de.uds.lsv.platon.session.DialogEngine
import de.uds.lsv.platon.session.User
import de.uds.lsv.platon.world.WorldObject

@TypeChecked
class ScriptBindings {
	private static final Log logger = LogFactory.getLog(ScriptBindings.class.getName());

	private final ScriptAdapter scriptAdapter;
	
	// During initialization: the agent to which elements are added
	Agent agentUnderConstruction;

	public ScriptBindings(ScriptAdapter scriptAdapter) {
		this.scriptAdapter = scriptAdapter;
		this.agentUnderConstruction = scriptAdapter.baseAgent;
	}
	
	@TypeChecked(TypeCheckingMode.SKIP)
	public void scriptInitialAgent(Object identifier, Object... args) {
		if (!scriptAdapter.initializing) {
			throw new RuntimeException("initialAgent can be used only as a top-level element.");
		}
		
		if (scriptAdapter.initialAgent != scriptAdapter.baseAgent) {
			throw new RuntimeException("There can't be more than one initial agent!")
		}
		
		scriptAdapter.initialAgent = scriptAgent(identifier, *args);
	}
	
	@TypeChecked(TypeCheckingMode.SKIP)
	public Agent scriptAgent(Object agentIdentifier, Object... args) {
		if (!scriptAdapter.initializing) {
			throw new IllegalStateException("Top-level Blah");
		}
		
		String agentName;
		if (agentIdentifier instanceof String) {
			agentName = (String)agentIdentifier;
		} else if (agentIdentifier instanceof UnknownIdentifier) {
			agentName = agentIdentifier.name;
			args = agentIdentifier.args;
			agentIdentifier.consume();
		} else {
			throw new IllegalArgumentException("Illegal agent identifier (name already used?): " + agentIdentifier);
		}
		
		try {
			if (scriptAdapter.agents.containsKey(agentName)) {
				throw new RuntimeException("Duplicate agent name: ${agentName}");
			}
			
			if (scriptAdapter.bindings.containsKeyWithoutFallback(agentName)) {
				// it is more likely that the exception will be thrown somewhere else.
				// agent used { ... }
				// becomes
				// agent(used({ ...}))
				// in Groovy, so unless the user write agent(used) { ... }
				// we don't see the used identifier here
				throw new RuntimeException("Agent name already in use for : ${agentName}");
			}
			
			if (args.length < 1 || !(args[0] instanceof Closure)) {
				throw new IllegalArgumentException("Missing agent body for agent ${agentName}!");
			}
			if (args.length > 1) {
				throw new IllegalArgumentException("Too many arguments for agent!");
			}
			Closure closure = (Closure)args[0];
			
			final Agent newAgent = new Agent(scriptAdapter, agentName);
			
			scriptAdapter.agents[agentName] = newAgent;
			Agent previousStateUnderConstruction = agentUnderConstruction;
			agentUnderConstruction = newAgent;
			try {
				closure();
			}
			finally {
				agentUnderConstruction = previousStateUnderConstruction;
			}
			
			scriptAdapter.bindings.put(agentName, newAgent);
			
			return newAgent;
		}
		catch (Exception e) {
			logger.error(e);
			throw scriptAdapter.exceptionMapper.translateException(e);
		}
	}
	
	@TypeChecked(TypeCheckingMode.SKIP)
	public void scriptAfter(durationAndClosure) {
		if (scriptAdapter.initializing) {
			throw new IllegalStateException("You cannot use 'after' as a top-level statement.");
		}
		
		TimeDuration duration;
		Closure closure;
		(duration, closure) = durationAndClosure;
		
		if (duration.toMilliseconds() < 100) {
			throw new RuntimeException("You cannot set timers for less than 100ms!");
		}
		
		logger.debug("Submitting task to be run in ${duration}.")
		AgentInstance agentInstance = scriptAdapter.agentStack.getActiveAgentInstance();
		scriptAdapter.dialogEngine.getSession().schedule(
			new AgentCallable(agentInstance, closure),
			duration.toMilliseconds()
		);
	}
	
	/**
	 * If initializing (i.e. as a top-level statement), installs a permanent
	 * idle task, if used inside another statement (e.g. in an input block)
	 * installs a idle task that's only used once, i.e. if there's input
	 * between now and the idle timeout, the task is not run at all.
	 */
	@TypeChecked(TypeCheckingMode.SKIP)
	public void scriptIdle(durationAndClosure) {
		TimeDuration duration;
		Closure closure;
		(duration, closure) = durationAndClosure;
		
		if (duration.toMilliseconds() < 100) {
			throw new RuntimeException("You cannot set idle times less than 100ms!");
		}
		
		logger.debug("Adding idle task, ${duration}, once=${!scriptAdapter.initializing}.")
		if (scriptAdapter.initializing) {
			scriptAdapter.idleManager.add(duration.toMilliseconds(), closure);
		} else {
			AgentInstance agentInstance = scriptAdapter.agentStack.getActiveAgentInstance();
			scriptAdapter.idleManager.addOnce(
				duration.toMilliseconds(),
				new AgentCallable(agentInstance, closure),
				true
			);
		}
	}
	
	/**
	 * @param languagePatterns
	 *   either a map (of named arguments!) language: patterns,
	 *   or just the patterns (which will then be used for any
	 *   language).
	 * @param action
	 */
	@TypeChecked(TypeCheckingMode.SKIP)
	public void scriptInput(priority=true, languagePatterns, Closure action) {
		if (!scriptAdapter.initializing) {
			throw new IllegalStateException("input has to be a top-level statement!");
		}
		
		if (!true.is(priority) && !(Number.isAssignableFrom(priority.getClass()))) {
			throw new IllegalArgumentException("Bad value for priority: " + priority)
		}
		if (true.is(priority)) {
			priority = Double.POSITIVE_INFINITY;
		}
		
		Object patterns;
		if (languagePatterns instanceof Map) {
			if (!languagePatterns.containsKey(scriptAdapter.language)) {
				logger.warn("Missing language ${scriptAdapter.language} in input ${languagePatterns}")
				return;
			}
			
			patterns = languagePatterns[scriptAdapter.language];
		
		} else {
			patterns = languagePatterns;
		}
		
		if (patterns instanceof Iterable) {
			for (pattern in patterns) {
				logger.debug("Adding action for ${pattern.toString()} to agent ${agentUnderConstruction}");
			
				if (pattern == null) {
					throw new IllegalArgumentException("Pattern cannot be null!");
				} else if (
					scriptAdapter.dialogEngine.session.config.caseInsensitiveInputPatterns &&
					pattern instanceof Pattern
				) {
					pattern = Pattern.compile(
						((Pattern)pattern).pattern(),
						Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
					);
				}
			
				agentUnderConstruction.addInputAction(pattern, action, priority);
			}
		
		} else {
			def pattern = patterns;
			
			logger.debug("Adding action for ${pattern.toString()} to agent ${agentUnderConstruction}");

			if (pattern == null) {
				throw new IllegalArgumentException("Pattern cannot be null!");
			} else if (
				scriptAdapter.dialogEngine.session.config.caseInsensitiveInputPatterns &&
				pattern instanceof Pattern
			) {
				pattern = Pattern.compile(pattern.pattern(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
			}
			
			agentUnderConstruction.addInputAction(pattern, action, priority);
		}
	}

	public void scriptObjectModified(Object fromObjectState, Closure toObjectState, Closure action) {
		if (!(fromObjectState instanceof Closure || fromObjectState instanceof String)) {
			throw new IllegalArgumentException("Invalid fromObjectState filter: " + fromObjectState);
		}
		
		if (fromObjectState instanceof String) {
			final String id = (String)fromObjectState;
			fromObjectState = { id.equals(it[WorldObject.FIELD_ID]) };
		}
		
		logger.debug(String.format(
			"Adding %sobjectModified reaction for %s -> %s%s",
			scriptAdapter.initializing ? "" : "one-time ",
			fromObjectState, toObjectState,
			scriptAdapter.initializing ? " to agent ${agentUnderConstruction}" : ""
		));
		
		ReactionAgent agent = scriptAdapter.initializing ? agentUnderConstruction : scriptAdapter.agentStack.getActiveAgentInstance();
		agent.addObjectModifiedReaction((Closure)fromObjectState, toObjectState, action);
	}

	public void scriptObjectAdded(Object filter, Closure action) {
		if (!(filter instanceof Closure || filter instanceof String)) {
			throw new IllegalArgumentException("Invalid filter: " + filter);
		}
		
		logger.debug(String.format(
			"Adding %sobjectAdded reaction for %s%s",
			scriptAdapter.initializing ? "" : "one-time ",
			filter,
			scriptAdapter.initializing ? " to agent ${agentUnderConstruction}" : ""
		));
		
		if (filter instanceof String) {
			final String id = (String)filter;
			filter = { id.equals(it[WorldObject.FIELD_ID]) };
		}
	
		ReactionAgent agent = scriptAdapter.initializing ? agentUnderConstruction : scriptAdapter.agentStack.getActiveAgentInstance();
		agent.addObjectAddedReaction((Closure)filter, action);
	}
	
	public void scriptObjectDeleted(Object filter, Closure action) {
		if (!(filter instanceof Closure || filter instanceof String)) {
			throw new IllegalArgumentException("Invalid filter: " + filter);
		}
		
		logger.debug(String.format(
			"Adding objectDeleted reaction for %s to agent %s",
			filter, agentUnderConstruction
		));
	
		if (filter instanceof String) {
			final String id = (String)filter;
			filter = { id.equals(it[WorldObject.FIELD_ID]) };
		}
		
		ReactionAgent agent = scriptAdapter.initializing ? agentUnderConstruction : scriptAdapter.agentStack.getActiveAgentInstance();
		agent.addObjectDeletedReaction((Closure)filter, action);
	}
	
	public void scriptEnvironmentModified(String key, Object value, Closure action) {
		logger.debug(String.format(
			"Adding environmentModified reaction for %s => %s to agent %s",
			key, value, agentUnderConstruction
		));
		
		ReactionAgent agent = scriptAdapter.initializing ? agentUnderConstruction : scriptAdapter.agentStack.getActiveAgentInstance();
		
		if (value instanceof String) {
			agent.addEnvironmentModifiedReaction(key, (String)value, action);
		} else if (value instanceof Closure) {
			agent.addEnvironmentModifiedReaction(
				key, (Closure)value, action
			);
		} else {
			throw new IllegalArgumentException("Unsupported value type in environmentModified: " + value.getClass().getName());
		}
	}
	
	public void scriptReaction(String id, Closure action) {
		if (!scriptAdapter.initializing) {
			throw new RuntimeException("You can use 'reaction' only as a top-level statement.");
		}
		
		if (agentUnderConstruction.reactionMap.containsKey(id)) {
			logger.warn("Warning: Overwriting reaction for id ${id} in agent ${agentUnderConstruction}");
		}

		logger.debug("Adding reaction for id »${id}« to agent ${agentUnderConstruction}");
		
		agentUnderConstruction.addNamedReaction(id, action);
	}

	public Then scriptTell(Map<String,Object> details=[:], Object addressee, Object textOrMap) {
		if (scriptAdapter.initializing) {
			throw new IllegalStateException("You cannot use 'tell' as a top-level statement.");
		}

		String text;
		if (textOrMap instanceof String) {
			text = (String)textOrMap;
		} else if (textOrMap instanceof GString) {
			text = ((GString)textOrMap).toString();
		} else if (PrimitiveUtil.isPrimitiveWrapper(textOrMap)) {
			text = textOrMap.toString();
		} else if (textOrMap instanceof Map) {
			if (!textOrMap.containsKey(scriptAdapter.language)) {
				logger.warn("Language ${scriptAdapter.language} missing in tell ${textOrMap}");
				return;
			} else {
				text = textOrMap.get(scriptAdapter.language);
			}
		} else {
			throw new RuntimeException("Bad syntax for tell! Text type: " + textOrMap?.getClass());
		}
		
		// Map user string to user
		User addresseeUser;
		if (addressee == scriptAdapter.addresseeUser) {
			addresseeUser = scriptAdapter.dialogEngine.user;
		} else if (addressee == scriptAdapter.addresseeAll) {
			addresseeUser = null;
		} else {
			throw new IllegalArgumentException("Invalid addressee: " + addressee);
		}
		
		def uninterruptible = details.get("uninterruptible", false);
		if (!(uninterruptible instanceof Boolean)) {
			throw new IllegalArgumentException("uninterruptible has to be boolean!");
		}
		
		Action action = new VerbalOutputAction(
			scriptAdapter.dialogEngine.getSession(),
			addresseeUser,
			text,
			(Boolean)uninterruptible,
			details
		);
		scriptAdapter.dialogEngine.addAction(action);
		
		return new Then(action, scriptAdapter.dialogEngine);
	}

	// returns a collection of results
	public Collection<WorldObjectWrapper> scriptObjects(Closure filter) {
		if (scriptAdapter.initializing) {
			throw new IllegalStateException("You cannot use 'objects' as a top-level statement.");
		}
		if (filter == null) {
			throw new IllegalArgumentException("filter cannot be null!");
		}
		
		return scriptAdapter.dialogEngine.session.getWorldState().getObjects().values().collect({
			object -> new WorldObjectWrapper(object, scriptAdapter.dialogEngine)
		}).findAll(filter);
	}
	
	/**
	 * Thrown in a script when the filter for object { ... }
	 * does not match exactly one object.
	 */
	@InheritConstructors
	public static class WrongNumberOfObjectsException extends RuntimeException {
	}

	// exactly one result or WrongNumberOfObjectsException
	@TypeChecked(TypeCheckingMode.SKIP)
	public WorldObjectWrapper scriptObject(filter) throws WrongNumberOfObjectsException {
		if (scriptAdapter.initializing) {
			throw new IllegalStateException("You cannot use 'object' as a top-level statement.");
		}
		
		def originalFilter = filter;
		if (filter instanceof String) {
			String objectId = filter;
			filter = { it.id == objectId };
		}
		
		def allObjects = scriptObjects(filter);
		if (allObjects.isEmpty()) {
			throw new WrongNumberOfObjectsException("Object filter yielded no results: " + originalFilter);
		} else if (allObjects.size() > 1) {
			throw new WrongNumberOfObjectsException("Object filter yielded ambiguous results: " + originalFilter);
		}
		
		return allObjects[0];
	}
	
	public String scriptGetEnvironment(String key) {
		if (scriptAdapter.initializing) {
			throw new IllegalStateException("You cannot use 'getEnvironment' as a top-level statement.");
		}
		
		return scriptAdapter.dialogEngine.session.worldState.getEnvironmentVariable(key);
	}
	
	public void scriptSetEnvironment(String key, String value) {
		if (scriptAdapter.initializing) {
			throw new IllegalStateException("You cannot use 'setEnvironment' as a top-level statement.");
		}
		
		if ("id".equals(key)) {
			throw new RuntimeException("»id« is not a valid variable name.");
		}
		
		scriptAdapter.dialogEngine.addAction(
			new ModifyEnvironmentAction(
				scriptAdapter.dialogEngine.session,
				[ (key): value ]
			)
		);
	}
	
	public void scriptQuit() {
		if (scriptAdapter.initializing) {
			throw new IllegalStateException("You cannot use 'quit' as a top-level statement.");
		}
		
		logger.debug("Script called quit!");
		scriptAdapter.dialogEngine.addAction(
			new QuitAction(scriptAdapter.dialogEngine.session, scriptAdapter.dialogEngine)
		);
	}
	
	public void scriptNext(nextInput=null) {
		if (scriptAdapter.initializing) {
			throw new IllegalStateException("You cannot use 'next' as a top-level statement.");
		}
		
		logger.debug("next(${nextInput})");
		throw new NextThrowable(nextInput);
	}
	
	public void scriptPrepareInput(Closure closure) {
		if (!scriptAdapter.initializing) {
			throw new IllegalStateException("You can use 'prepareInput' only as a top-level statement.");
		}
		
		scriptAdapter.prepareInput.add(closure);
	}
	
	private static final Object noDefaultValue = new Object();
	public Object scriptSelectTranslation(Map<String,Object> options, Object defaultValue=noDefaultValue) {
		if (!options.containsKey(scriptAdapter.language)) {
			if (!noDefaultValue.is(defaultValue)) {
				return defaultValue;
			} else {
				throw new RuntimeException("No option for language ${scriptAdapter.language}!");
			}
		}
		
		return options.get(scriptAdapter.language);
	}
	
	public void scriptInit(Closure closure) {
		if (!scriptAdapter.initializing) {
			throw new IllegalStateException("You can use 'init' only as a top-level statement.");
		}
		
		logger.debug("Setting initializer for agent " + agentUnderConstruction + ": " + closure);
		agentUnderConstruction.addInit(closure);
	}
	
	/**
	 * Pop an agent from the stack.
	 */
	@TypeChecked(TypeCheckingMode.SKIP)
	public void scriptExit(Object... args) {
		if (scriptAdapter.initializing) {
			throw new IllegalStateException("You cannot use 'exit' as a top-level statement.");
		}
		
		logger.debug("Exiting agent instance: " + scriptAdapter.agentStack.getActiveAgentInstance() + " (stack: " + scriptAdapter.agentStack + ")");
		scriptAdapter.agentStack.popActive().enter(*args);
	}
	
	public void scriptEnter(Closure closure) {
		if (!scriptAdapter.initializing) {
			throw new IllegalStateException("You can use 'enter' only as a top-level statement.");
		}
		
		logger.debug("Setting enter for agent " + agentUnderConstruction.name + ": " + closure);
		agentUnderConstruction.addEnter(closure);
	}
	
	public void scriptDelegateAgent(Object agentIdentifier) {
		if (!scriptAdapter.initializing) {
			throw new IllegalStateException("You can use 'delegateAgent' only as a top-level statement.");
		}
		
		Agent agent;
		if (agentIdentifier instanceof Agent) {
			agent = (Agent)agentIdentifier;
		} else if (agentIdentifier instanceof String) {
			agent = scriptAdapter.agents.get((String)agentIdentifier);
			if (agent == null) {
				throw new RuntimeException("Unknown agent: ${agentIdentifier}");
			}
		} else if (agentIdentifier instanceof UnknownIdentifier) {
			throw new RuntimeException("Agent not known at this point: " + agentIdentifier);
		} else {
			throw new RuntimeException("Invalid agent identifier: " + agentIdentifier);
		}
		
		if (agent.is(agentUnderConstruction)) {
			throw new IllegalArgumentException("Cannot delegate to self.")
		}
		
		agentUnderConstruction.addAgent(agent);
	}
	
	public void scriptSend(User addressee, Object message) {
		scriptAdapter.dialogEngine.session.submit({
			DialogEngine otherDialogEngine = scriptAdapter.dialogEngine.session.dialogEngines[addressee.id];
			otherDialogEngine.sendInternal(scriptAdapter.dialogEngine, message);
		});
	}
	
	public void scriptInternal(priority=true, pattern, Closure action) {
		if (!scriptAdapter.initializing) {
			throw new IllegalStateException("internal has to be a top-level statement!");
		}
		
		if (!true.is(priority) && !(Number.isAssignableFrom(priority.getClass()))) {
			throw new IllegalArgumentException("Bad value for priority: " + priority)
		}
		if (true.is(priority)) {
			priority = Double.POSITIVE_INFINITY;
		}
		
		agentUnderConstruction.addIntercomAction(pattern, action, ((Number)priority).doubleValue());
	}
}
