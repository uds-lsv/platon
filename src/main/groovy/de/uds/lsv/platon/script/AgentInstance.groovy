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

import groovy.lang.Closure;
import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

import de.martingropp.util.CachingIterable;
import de.martingropp.util.Pair;
import de.martingropp.util.Triple;
import de.uds.lsv.platon.script.Agent.InternalPatternAction

@TypeChecked
public class AgentInstance implements ReactionAgent {
	private static final Log logger = LogFactory.getLog(AgentInstance.class.getName());
	private final AgentStack stack;
	private final Agent agent;
	
	private List<Triple<Closure,Closure,Closure>> objModifiedOnce = new ArrayList<>();
	private List<Pair<Closure,Closure>> objAddedOnce = new ArrayList<>();
	private List<Pair<Closure,Closure>> objDeletedOnce = new ArrayList<>();
	private Map<String,List<Triple<String,Closure,Closure>>> envModifiedOnce = new HashMap<>();

	private Iterable<PatternAction> instanceInputActions = null;
	private Iterable<PatternAction> instanceIntercomActions = null;
	
	private boolean active = false;
		
	public AgentInstance(AgentStack stack, Agent agent) {
		this.stack = stack;
		this.agent = agent;
	}
	
	public Agent getAgent() {
		return agent;
	}
	
	public AgentStack getStack() {
		return stack;
	}
	
	public void addObjectAddedReaction(Closure filter, Closure action) {
		objAddedOnce.add(new Pair<>(
			filter,
			action
		));
	}
	
	public void addObjectDeletedReaction(Closure filter, Closure action) {
		objDeletedOnce.add(new Pair<>(
			filter,
			action
		));
	}
	
	public void addObjectModifiedReaction(Closure fromState, Closure toState, Closure action) {
		objModifiedOnce.add(
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
		List<Triple<String,Closure,Closure>> list = envModifiedOnce.get(key);
		if (list == null) {
			list = new ArrayList<>(2);
			envModifiedOnce.put(key, list);
		}
		list.add(item);
	}
	
	public void addEnvironmentModifiedReaction(String key, Closure value, Closure action) {
		Triple<String,Closure,Closure> item = new Triple<>(
			null,
			value,
			action
		);
		List<Triple<String,Closure,Closure>> list = envModifiedOnce.get(key);
		if (list == null) {
			list = new ArrayList<>(2);
			envModifiedOnce.put(key, list);
		}
		list.add(item);
	}
	
	/**
	 * init: called when the agent is put on the stack.
	 * Called automatically by replace/push.
	 */
	@TypeChecked(TypeCheckingMode.SKIP)
	public void init(Object... args) {
		logger.debug("Initializing agent instance ${this}");
		for (Closure initClosure : agent.initClosures) {
			AgentCallable.callClosure(this, initClosure, *args);
		}
	}
	
	/**
	 * enter: called when the agent becomes active = the
	 * top stack element, either because it is put on the
	 * stack or because another agent is popped from the
	 * stack leaving this agent as the top element.
	 * Called automatically by replace/push/pop.
	 */
	@TypeChecked(TypeCheckingMode.SKIP)
	public void enter(Object... args) {
		logger.debug("Entering agent instance ${this}");
		for (Closure enterClosure : agent.enterClosures) {
			AgentCallable.callClosure(this, enterClosure, *args);
		}
	}
	
	/**
	 * Convert InternaPatternActions to PatternActions,
	 * wrapping closures in AgentCallables. 
	 */
	private Iterable<PatternAction> convertPatternActions(
		final Iterator<InternalPatternAction> iterator
	) {
		return new CachingIterable<PatternAction>(
			new Iterator<PatternAction>() {
				@Override
				public boolean hasNext() {
					return iterator.hasNext();
				}
				
				@Override
				public PatternAction next() {
					InternalPatternAction internal = iterator.next();
					return new PatternAction(
						internal.pattern,
						new AgentCallable(AgentInstance.this, internal.action),
						internal.priority
					);
				}
				
				@Override
				public void remove() {
					throw new UnsupportedOperationException();
				}
			}
		);
	}
	
	public Iterable<PatternAction> getInputActions() {
		if (instanceInputActions == null) {
			instanceInputActions = convertPatternActions(agent.inputActions.iterator());
		}
		
		return instanceInputActions;
	}
	
	public Iterable<PatternAction> getIntercomActions() {
		if (instanceIntercomActions == null) {
			instanceIntercomActions = convertPatternActions(agent.intercomActions.iterator());
		}
		
		return instanceIntercomActions;
	}
	
	public void triggerObjectAddedReactions(Object object) {
		doTriggerObjectAddedReactions(objAddedOnce, object, true);
		doTriggerObjectAddedReactions(agent.objAdded, object, false);
	}
		
	private void doTriggerObjectAddedReactions(List<Pair<Closure,Closure>> objAdded, Object object, boolean once) {
		def matching = objAdded.findAll {
			item ->
			Closure filter = item.first;
			return filter(object);
		};
		
		for (Pair<Closure,Closure> item : matching) {
			if (once) {
				objAdded.remove(item);
			}
			Closure action = item.second;
			AgentCallable.callClosure(this, action, object);
		}
	}
	
	public void triggerObjectDeletedReactions(Object object) {
		doTriggerObjectDeletedReactions(objDeletedOnce, object, true);
		doTriggerObjectDeletedReactions(agent.objDeleted, object, false);
	}
	
	private void doTriggerObjectDeletedReactions(List<Pair<Closure,Closure>> objDeleted, Object object, boolean once) {
		def matching = objDeleted.findAll {
			item ->
			Closure filter = item.first;
			return filter(object);
		};
		
		for (Pair<Closure,Closure> item : matching) {
			if (once) {
				objDeleted.remove(item);
			}
			Closure action = item.second;
			AgentCallable.callClosure(this, action, object);
		}
	}
	
	public void triggerObjectModifiedReactions(Map<String,Object> oldProperties, Map<String,Object> newProperties, Object object) {
		doTriggerObjectModifiedReactions(
			objModifiedOnce,
			oldProperties,
			newProperties,
			object,
			true
		);
	
		doTriggerObjectModifiedReactions(
			agent.objModified,
			oldProperties,
			newProperties,
			object,
			false
		);
	}
	
	private void doTriggerObjectModifiedReactions(
		List<Triple<Closure,Closure,Closure>> objModified,
		Map<String,Object> oldProperties,
		Map<String,Object> newProperties,
		Object object,
		boolean once
	) {
		def matching = objModified.findAll {
			item ->
			Closure filterFrom = item.first;
			Closure filterTo = item.second;
			return filterFrom(oldProperties) && filterTo(newProperties);
		};
	
		for (Triple<Closure,Closure,Closure> item : matching) {
			if (once) {
				objModified.remove(item);
			}
			Closure action = item.third;
			AgentCallable.callClosure(this, action, object);
		}
	}
	
	public void triggerEnvironmentModifiedReactions(String key, String value) {
		doTriggerEnvironmentModifiedReactions(envModifiedOnce, key, value, true);
		doTriggerEnvironmentModifiedReactions(agent.envModified, key, value, false);
	}
	
	private void doTriggerEnvironmentModifiedReactions(
		Map<String,List<Triple<String,Closure,Closure>>> envModified,
		String key,
		String value,
		boolean once
	) {
		List<Triple<String,Closure,Closure>> list = envModified.get(key);
		if (list == null) {
			return;
		}
		
		def matching = list.findAll {
			item ->
			String valueString = item.first;
			Closure valueClosure = item.second;
			
			if (valueString != null) {
				if (value.equals(valueString)) {
					return true;
				}
			} else if (valueClosure != null) {
				if (valueClosure(value)) {
					return true;
				}
			}
			
			return false;
		};
		
		for (Triple<String,Closure,Closure> item : matching) {
			if (once) {
				list.remove(item);
			}
			
			Closure action = item.third;
			AgentCallable.callClosure(this, action);
		}
	}

	public boolean hasNamedReaction(String id) {
		return agent.reactionMap.containsKey(id);
	}
	
	@TypeChecked(TypeCheckingMode.SKIP)
	public void triggerNamedReaction(String id, Object... args) {
		AgentCallable.callClosure(this, agent.reactionMap[id], *args)
	}
	
	/**
	 * Return true iff the agent instance is still on the stack.
	 */
	public boolean isActive() {
		return stack.contains(this);
	}
		
	@Override
	public String toString() {
		return String.format("%s@%d", agent.toString(), System.identityHashCode(this));
	}
}
