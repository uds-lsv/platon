package de.uds.lsv.platon.script;

import groovy.lang.Closure;

/**
 * Just the common add* methods from Agent and AgentInstance.
 */
interface ReactionAgent {
	void addObjectAddedReaction(Closure<?> filter, Closure<?> action);
	void addObjectDeletedReaction(Closure<?> filter, Closure<?> action);
	void addObjectModifiedReaction(Closure<?> fromState, Closure<?> toState, Closure<?> action);
	void addEnvironmentModifiedReaction(String key, String value, Closure<?> action);
	void addEnvironmentModifiedReaction(String key, Closure<?> value, Closure<?> action);
}
