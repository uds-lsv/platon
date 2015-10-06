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

import groovy.transform.TupleConstructor
import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode

import java.util.Map.Entry

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

import de.martingropp.util.Pair;
import de.martingropp.util.Triple;

@TypeChecked
public class Agent implements ReactionAgent {
	public static class InternalPatternAction {
		public final Object pattern;
		public final Closure action;
		public final double priority;
		
		public InternalPatternAction(Object pattern, Closure action, double priority) {
			this.pattern = pattern;
			this.action = action;
			this.priority = priority;
		}
	}
	
	private static final Log logger = LogFactory.getLog(Agent.class.getName());
	
	ScriptAdapter scriptAdapter;
	AgentStack stack;
	String name;
	
	private List<Closure> initClosures = [];
	private List<Closure> enterClosures = [];
	
	/**
	 * Do not use this field directly!
	 * Input reactions. 
	 */
	List<InternalPatternAction> inputActions = new ArrayList<>();

	/**
	 * Do not use this field directly!
	 * Actions for communication between dialog engine instances of a session.
	 */
	List<InternalPatternAction> intercomActions = new ArrayList<>();
	
	/** Do not use this field directly! */
	List<Triple<Closure,Closure,Closure>> objModified = new ArrayList<>();
	
	/** Do not use this field directly! */
	List<Pair<Closure,Closure>> objAdded = new ArrayList<>();
	
	/** Do not use this field directly! */
	List<Pair<Closure,Closure>> objDeleted = new ArrayList<>();
	
	/**
	 * Do not use this field directly!
	 * key => ( valueString, valueClosure, action )
	 */
	Map<String,List<Triple<String,Closure,Closure>>> envModified = new HashMap<>();
	
	/** Do not use this field directly! */
	Map<String,Closure> reactionMap = new LinkedHashMap<>();

	private AgentInstance activeInstance = null;
	
	/**
	 * @param scriptAdapter
	 *   the ScriptAdapter instance owning this agent
	 * @param name
	 *   the name of the agent
	 */
	public Agent(ScriptAdapter scriptAdapter, String name) {
		this.scriptAdapter = scriptAdapter;
		this.stack = scriptAdapter.agentStack;
		this.name = name;
	}
	
	/**
	 * copy all definitions from another agent to this agent 
	 */
	public void addAgent(Agent agent) {
		for (Pair<Closure,Closure> p : agent.objAdded) {
			objAdded.add(p);
		} 
		
		for (Pair<Closure,Closure> p : agent.objDeleted) {
			objDeleted.add(p);
		}
		
		for (Triple<Closure,Closure,Closure> t : agent.objModified) {
			objModified.add(t);
		}
		
		for (Entry<String,List<Triple<String,Closure,Closure>>> entry : agent.envModified.entrySet()) {
			List<Triple<String,Closure,Closure>> list = envModified.get(entry.getKey());
			if (list == null) {
				list = new ArrayList<>(entry.getValue());
				envModified.put(entry.getKey(), list);
			} else {
				list.addAll(entry.getValue());
			}
		}
		
		this.inputActions.addAll(agent.inputActions);
		this.initClosures.addAll(agent.initClosures);
		this.enterClosures.addAll(agent.enterClosures);
	}
	
	public void addInputAction(Object pattern, Closure action, double priority) {
		inputActions.add(
			new InternalPatternAction(pattern, action, priority)
		);
	}
	
	public void addIntercomAction(Object pattern, Closure action, double priority) {
		intercomActions.add(
			new InternalPatternAction(pattern, action, priority)
		);
	}
	
	public void addObjectAddedReaction(Closure filter, Closure action) {
		objAdded.add(new Pair<>(
			filter,
			action
		));
	}
	
	public void addObjectDeletedReaction(Closure filter, Closure action) {
		objDeleted.add(new Pair<>(
			filter,
			action
		));
	}
	
	public void addObjectModifiedReaction(Closure fromState, Closure toState, Closure action) {
		objModified.add(
			new Triple<>(
				fromState,
				toState,
				action
			)
		);
	}
	
	public void addEnvironmentModifiedReaction(String key, String value, Closure action) {
		Triple<String,Closure,Closure> item = new Triple<>(
			value,
			null,
			action
		);
		List<Triple<String,Closure,Closure>> list = envModified.get(key);
		if (list == null) {
			list = new ArrayList<>(2);
			envModified.put(key, list);
		}
		list.add(item);
	}
	
	public void addEnvironmentModifiedReaction(String key, Closure value, Closure action) {
		Triple<String,Closure,Closure> item = new Triple<>(
			null,
			value,
			action
		);
		List<Triple<String,Closure,Closure>> list = envModified.get(key);
		if (list == null) {
			list = new ArrayList<>(2);
			envModified.put(key, list);
		}
		list.add(item);
	}
	
	public void addNamedReaction(String id, Closure action) {
		reactionMap[id] = action;
	}
	
	public void addInit(Closure initClosure) {
		this.initClosures.add(initClosure);
	}
	
	public void addEnter(Closure enterClosure) {
		this.enterClosures.add(enterClosure);
	}
	
	/**
	 * remove all agents on the stack covering this one and
	 * replace this agent with a new one.
	 */
	@TypeChecked(TypeCheckingMode.SKIP)
	public void replace(Object... args) {
		AgentInstance instance = new AgentInstance(stack, this);
		
		AgentInstance oldInstance = scriptAdapter.focusAgentInstance; 
		scriptAdapter.focusAgentInstance = instance;
		try {
			stack.replaceActive(instance);
			instance.init(*args);
			instance.enter(*args);
		}
		finally {
			scriptAdapter.focusAgentInstance = oldInstance;
		}
	}
	
	/**
	 * remove all agents covering the active agent from the stack,
	 * push this agent (stack cutting).
	 */
	@TypeChecked(TypeCheckingMode.SKIP)
	public void call(Object... args) {
		AgentInstance instance = new AgentInstance(stack, this);
		
		AgentInstance oldInstance = scriptAdapter.focusAgentInstance;
		scriptAdapter.focusAgentInstance = instance;
		try {
			stack.push(instance, true);
			instance.init(*args);
			instance.enter(*args);
		}
		finally {
			scriptAdapter.focusAgentInstance = oldInstance;
		}
	}
	
	/**
	 * push a new agent to the top of the stack
	 * (no stack cutting).
	 */
	@TypeChecked(TypeCheckingMode.SKIP)
	public void push(Object... args) {
		AgentInstance instance = new AgentInstance(stack, this);
		
		AgentInstance oldInstance = scriptAdapter.focusAgentInstance;
		scriptAdapter.focusAgentInstance = instance;
		try {
			stack.push(instance, false);
			instance.init(*args);
			instance.enter(*args);
		}
		finally {
			scriptAdapter.focusAgentInstance = oldInstance;
		}
	}

	public AgentInstance getActiveInstance() {
		return activeInstance;
	}
	
	public void setActiveInstance(AgentInstance instance) {
		this.activeInstance = instance;
	}
	
	public boolean isActive() {
		return stack.any {
			AgentInstance instance ->
			this.is(instance.agent);
		};
	}
	
	public boolean isTop() {
		return (
			!stack.isEmpty() &&
			this.is(stack.peekTop())
		);
	}

	@Override
	public String toString() {
		return name;
	}
}
