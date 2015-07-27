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

import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode

import java.lang.ref.WeakReference

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

/**
 * Saves a closure together with its owning agent.
 * This way we can simply run the AgentCallable without
 * caring about which agent is a active. 
 */
@TypeChecked
public class AgentCallable {
	private static final Log logger = LogFactory.getLog(AgentCallable.class.getName());
	private final WeakReference<AgentInstance> agentInstance;
	private final Closure closure;
	
	public AgentCallable(AgentInstance agentInstance, Closure closure) {
		this.agentInstance = new WeakReference<>(agentInstance);
		this.closure = closure;
	}
	
	@TypeChecked(TypeCheckingMode.SKIP)
	public def call(Object... args) {
		AgentInstance agentInstance = agentInstance.get();
		if (agentInstance == null || !agentInstance.isActive()) {
			logger.debug("The agent instance belonging to ${this} is no longer active and has been garbage-collected -- not running.");
			return;
		}
		
		agentInstance.getStack().setActiveAgentInstance(agentInstance);
		return closure(*args);
	}
	
	public int getMaximumNumberOfParameters() {
		return closure.getMaximumNumberOfParameters();
	}
	
	@TypeChecked(TypeCheckingMode.SKIP)
	public static def callClosure(AgentInstance agentInstance, Closure closure, Object... args) {
		agentInstance.getStack().setActiveAgentInstance(agentInstance);
		return closure(*args);
	}
	
	@Override
	public String toString() {
		return String.format(
			"AgentCallable(%s, %s)",
			agentInstance.get(),
			closure
		);
	}
}
