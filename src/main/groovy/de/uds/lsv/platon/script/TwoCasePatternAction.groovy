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

public class TwoCasePatternAction extends PatternAction {
	public final AgentCallable elseAction;
	
	public TwoCasePatternAction(
		Object pattern,
		AgentCallable action,
		AgentCallable elseAction,
		double priority
	) {
		super(pattern, action, priority);
		this.elseAction = elseAction;
	}
	
	public TwoCasePatternAction(
		Object pattern,
		AgentInstance agentInstance,
		Closure action,
		Closure elseAction,
		double priority
	) {
		this(
			pattern,
			action != null ? new AgentCallable(agentInstance, action) : null,
			elseAction != null ? new AgentCallable(agentInstance, elseAction) : null,
			priority
		);
	}
	
	public TwoCasePatternAction(TwoCasePatternAction other) {
		this(other.pattern, other.action, other.elseAction, other.priority);
	}
	
	@Override
	public String toString() {
		return String.format(
			"TwoCasePatternAction(%s, %s, %s)",
			pattern,
			action,
			elseAction
		);
	}
}
