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

package de.uds.lsv.platon.test;

import de.uds.lsv.platon.script.Agent
import de.uds.lsv.platon.script.AgentStack
import de.uds.lsv.platon.script.ScriptAdapter

class AgentTest extends TestImplBase {
	def last(list, count) {
		return list.subList(list.size()-count, list.size());
	}
	
	def testCall() {
		setup:
			AgentStack stack = new AgentStack();
			ScriptAdapter scriptAdapter = Mock(ScriptAdapter);
			scriptAdapter.getAgentStack() >> stack;
			List<Agent> agents = (0..4).collect { new Agent(scriptAdapter, Integer.toString(it)) }

			agents[4].push();
			agents[3].push();
			agents[2].push();
			agents[1].push();
			
		when:
			stack.setActiveAgentInstance(stack[activeIndex-1]);
			agents[0]();
		then:
			stack.size() == agents.size() - activeIndex + 1
			stack[0].getAgent() == agents[0]
			last(stack.asList(), stack.size()-1).collect({ it.getAgent() }) == last(agents, stack.size()-1)
		where:
			activeIndex << (1..4)
	}
	
	def testPush() {
		setup:
			AgentStack stack = new AgentStack();
			ScriptAdapter scriptAdapter = Mock(ScriptAdapter);
			scriptAdapter.getAgentStack() >> stack;
			List<Agent> agents = (0..4).collect { new Agent(scriptAdapter, Integer.toString(it)) }

			agents[4].push();
			agents[3].push();
			agents[2].push();
			agents[1].push();
		
		when:
			stack.setActiveAgentInstance(stack[activeIndex-1]);
			agents[0].push();
		then:
			stack.size() == agents.size()
			stack[0].getAgent() == agents[0]
			stack.asList().collect({ it.getAgent() }) == agents
		where:
			activeIndex << (1..4)
	}
	
	def testReplace() {
		setup:
			AgentStack stack = new AgentStack();
			ScriptAdapter scriptAdapter = Mock(ScriptAdapter);
			scriptAdapter.getAgentStack() >> stack;
			List<Agent> agents = (0..4).collect { new Agent(scriptAdapter, Integer.toString(it)) }

			agents[4].push();
			agents[3].push();
			agents[2].push();
			agents[1].push();
		
		when:
			stack.setActiveAgentInstance(stack[activeIndex-1]);
			agents[0].replace()
			
		then:
			stack.size() == agents.size() - activeIndex
			stack[0].getAgent() == agents[0]
			last(stack.asList(), stack.size()-1).collect({ it.getAgent() }) == last(agents, stack.size()-1)
		where:
			activeIndex << (1..3)
	}
	
	def testLeaveLastAgentException() {
		setup:
			AgentStack stack = new AgentStack();
			ScriptAdapter scriptAdapter = Mock(ScriptAdapter);
			scriptAdapter.getAgentStack() >> stack;
			List<Agent> agents = (0..4).collect { new Agent(scriptAdapter, Integer.toString(it)) }

			agents[4].push();
			agents[3].push();
			agents[2].push();
			agents[1].push();
		
		when:
			stack.setActiveAgentInstance(stack[3]);
			stack.popActive()
		then:
			thrown(IllegalArgumentException)
	}
	
	def testAgentInit() {
		setup:
			init(
				"initialAgent agentA { init { tell user, 'ping', uninterruptible: true; agentB(); } }\n" +
				"agent agentB { init { tell user, 'pong', uninterruptible: true; } }",
				setActive: false
			)
		when:
			setActive(true);
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "ping", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
		then:
			1 * dialogClientMonitor.outputStart(_, _, "pong", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}
	
	def testAgentStack() {
		setup:
			init(
				"init { tell user, 'init', uninterruptible: true; }\n" +
				"initialAgent agentA {\n" +
				"  init { tell user, 'initA', uninterruptible: true; agentB(); }\n" +
				"}\n" +
				"agent agentB {\n" +
				"  init { tell user, 'initB', uninterruptible: true; agentC(); }\n" +
				"  input(~/ping/) { tell user, 'peng', uninterruptible: true; exit(); }\n" +
				"}\n" +
				"agent agentC {\n" +
				"  init { tell user, 'initC', uninterruptible: true; }\n" +
				"  input(~/ping/) { tell user, 'pong', uninterruptible: true; exit(); }\n" +
				"}",
				setActive: false
			)
		when:
			setActive(true);
			input('ping');
			input('ping');
			input('ping');
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "init", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
		then:
			1 * dialogClientMonitor.outputStart(_, _, "initA", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
		then:
			1 * dialogClientMonitor.outputStart(_, _, "initB", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
		then:
			1 * dialogClientMonitor.outputStart(_, _, "initC", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
		then:
			1 * dialogClientMonitor.outputStart(_, _, "pong", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
		then:
			1 * dialogClientMonitor.outputStart(_, _, "peng", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}
	
	def testEnter() {
		setup:
			init(
				"enter { tell user, 'main' }\n" +
				"initialAgent agentA {\n" +
				"  enter { tell user, 'a', uninterruptible: true; }\n" +
				"  input(~/ping/) { agentB() }" +
				"}\n" +
				"agent agentB { enter { tell user, 'b', uninterruptible: true; exit(); } }",
				setActive: false
			)
		when:
			setActive(true);
			input('ping')
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "a", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
		then:
			1 * dialogClientMonitor.outputStart(_, _, "b", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
		then:
			1 * dialogClientMonitor.outputStart(_, _, "a", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}
	
	def testEnterMain() {
		setup:
			init(
				"enter { tell user, 'main' }\n" +
				"initialAgent agentA {\n" +
				"  enter { exit() }\n" +
				"}\n",
				setActive: false
			)
		when:
			setActive(true);
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "main", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}
	
	def testPushInInit() {
		setup:
			init(
				"initialAgent agent0 {\n" +
				"  init { agentA() }\n" +
				"}\n" +
				"agent agentA {\n" +
				"  init { agentB() }\n" +
				"}\n" +
				"agent agentB {\n" +
				"  init { tell user, 'pong', uninterruptible: true; }\n" +
				"}",
				setActive: false
			)
		when:
			setActive(true);
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "pong", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}
	
	def testPushInEnter() {
		setup:
			init(
				"enter { agentA.push() }\n" +
				"agent agentA {\n" +
				"  enter { agentB.push() }\n" +
				"}\n" +
				"agent agentB {\n" +
				"  enter { tell user, 'pong', uninterruptible: true; }\n" +
				"}",
				setActive: false
			)
		when:
			setActive(true);
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "pong", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}
	
	def testAgentArgumentsCall() {
		setup:
			init(
				"init { agentA('ping') }\n" +
				"agent agentA {\n" +
				"  init { agentB(it) }\n" +
				"}\n" +
				"agent agentB {\n" +
				"  init { tell user, it }\n" +
				"}",
				setActive: false
			)
		when:
			setActive(true);
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "ping", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}
	
	def testAgentArgumentsPush() {
		setup:
			init(
				"init { agentA.push('ping') }\n" +
				"agent agentA {\n" +
				"  init { tell user, it }\n" +
				"}",
				setActive: false
			)
		when:
			setActive(true);
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "ping", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}
	
	def testAgentArgumentsReplace() {
		setup:
			init(
				"initialAgent agent0 { init { agent1.replace('ping') } }\n" +
				"agent agent1 {\n" +
				"  init { tell user, it }\n" +
				"}",
				setActive: false
			)
		when:
			setActive(true);
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "ping", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}
	
	def testNestedAgents() {
		setup:
			init(
				"initialAgent agentA {\n" +
				"  def value = 'error';\n" +
				"  init { value = 'ping'; agentB.push() }\n" +
				"  agent agentB {\n" +
				"    init { tell user, value }\n" +
				"  }\n" +
				"}",
				setActive: false
			)
		when:
			setActive(true);
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "ping", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}
}
