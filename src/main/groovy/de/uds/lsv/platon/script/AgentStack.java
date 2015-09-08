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

package de.uds.lsv.platon.script;

import groovy.lang.IntRange;

import java.util.Iterator;
import java.util.List;

import org.apache.commons.collections4.list.CursorableLinkedList;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import de.martingropp.util.ReverseListIterator;

public class AgentStack implements Iterable<AgentInstance> {
	private static final Log logger = LogFactory.getLog(AgentStack.class.getName());
	
	private CursorableLinkedList<AgentInstance> stack = new CursorableLinkedList<>();
	
	// We store the agent instance that's currently being processed
	// here, but it could just as well be anywhere else.
	private AgentInstance activeAgentInstance = null;
	
	/**
	 * Remove the agents covering the active agent.
	 * Then push a new agent to the top.
	 */
	public void push(AgentInstance agent) {
		push(agent, true);
	}
	
	/**
	 * If cut is true, remove the agents covering the active agent.
	 * Then push a new agent to the top.
	 */
	public void push(AgentInstance agentInstance, boolean cut) {
		// cut stack if necessary
		if (cut) {
			AgentInstance activeAgentInstance = getActiveAgentInstance();
			if (activeAgentInstance == null) {
				throw new IllegalStateException("There is no active agent!");
			}
			
			popExcluding(activeAgentInstance);
		}
		
		stack.addFirst(agentInstance);
	}
	
	/**
	 * Replace the active agent after removing every other
	 * agent covering it.
	 */
	public void replaceActive(AgentInstance agentInstance) {
		AgentInstance activeAgentInstance = getActiveAgentInstance();
		if (activeAgentInstance == null) {
			throw new IllegalStateException("There is no active agent!");
		}
		
		popIncluding(activeAgentInstance, true);
		stack.addFirst(agentInstance);
	}
	
	/**
	 * Remove the active agent (and every agent covering it) from the stack.
	 * Returns the new top of the stack.
	 */
	public AgentInstance popActive() {
		AgentInstance activeAgentInstance = getActiveAgentInstance();
		if (activeAgentInstance == null) {
			throw new IllegalStateException("There is no active agent!");
		}
		
		popIncluding(activeAgentInstance, false);
		return stack.getFirst();
	}
	
	private void popIncluding(AgentInstance lastAgentInstanceToRemove, boolean removeLast) {
		if (lastAgentInstanceToRemove == null) {
			throw new IllegalArgumentException("lastAgentInstanceToRemove cannot be null!");
		}
		
		while (
			(stack.size() > 1) ||
			(removeLast && !stack.isEmpty())
		) {
			AgentInstance removed = stack.removeFirst();
			if (removed == lastAgentInstanceToRemove) {
				return;
			}
		}
		
		if (stack.getFirst() == lastAgentInstanceToRemove) {
			throw new IllegalArgumentException("You cannot pop the last agent!");
		} else {
			throw new IllegalArgumentException("Agent instance is not on the stack: " + lastAgentInstanceToRemove);
		}
	}
	
	private void popExcluding(AgentInstance firstAgentInstanceToKeep) {
		if (firstAgentInstanceToKeep == null) {
			throw new IllegalArgumentException("firstAgentInstanceToKeep cannot be null!");
		}
		
		while (stack.size() > 1) {
			if (stack.getFirst() == firstAgentInstanceToKeep) {
				return;
			}
			stack.removeFirst();
		}
		
		if (stack.getFirst() != firstAgentInstanceToKeep) {
			throw new IllegalArgumentException("You cannot pop the last agent!");
		}
	}
	
	/**
	 * Return an iterator that notifies AgentInstances that they are active.
	 */
	@Override
	public Iterator<AgentInstance> iterator() {
		return stack.listIterator();
	}
	
	/**
	 * Return a reverse iterator that notifies AgentInstances that they are active.
	 */
	public Iterator<AgentInstance> reverseIterator() {
		return new ReverseListIterator<AgentInstance>(stack.listIterator());
	}
	
	/**
	 * Return the top element of the stack without modifying it.
	 */
	public AgentInstance peekTop() {
		return stack.getFirst();
	}
	
	/**
	 * Return the number of agent instances on the stack. 
	 */
	public int size() {
		return stack.size();
	}
	
	public boolean isEmpty() {
		return stack.isEmpty();
	}
	
	public boolean contains(AgentInstance agentInstance) {
		return stack.contains(agentInstance);
	}
	
	/**
	 * Get the agent instance that's currently being processed.
	 */
	public AgentInstance getActiveAgentInstance() {
		return activeAgentInstance;
	}
	
	/**
	 * Set the agent instance that's currently being processed.
	 */
	public void setActiveAgentInstance(AgentInstance agentInstance) {
		logger.debug("Active agent instance set to: " + agentInstance);
		this.activeAgentInstance = agentInstance;
	}
	
	public AgentInstance getAt(int index) {
		if (index < 0) {
			index += stack.size();
		}
		
		return stack.get(index);
	}
	
	public List<AgentInstance> getAt(IntRange range) {
		return stack.subList(range.getFromInt(), range.getToInt()+1);
	}
	
	/**
	 * Return the list backing the agent stack.
	 */
	public List<AgentInstance> asList() {
		return stack;
	}
	
	@Override
	public String toString() {
		return stack.toString();
	}
}
