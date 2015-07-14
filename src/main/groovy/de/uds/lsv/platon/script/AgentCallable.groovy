package de.uds.lsv.platon.script

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
