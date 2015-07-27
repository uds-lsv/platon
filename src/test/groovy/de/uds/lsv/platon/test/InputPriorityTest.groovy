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

class InputPriorityTest extends TestImplBase {
	def testInputPrioritySimple() {
		init(
			"input(0.5, ~/ping/) { tell user, 'error' }\n" +
			"input(0.6, ~/ping/) { tell user, 'pong' }"
		)
		when:
			input("ping")
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "pong", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}
	
	def testInputPriorityTrue() {
		init(
			"input(0.5, ~/ping/) { tell user, 'error' }\n" +
			"input(true, ~/ping/) { tell user, 'pong' }"
		)
		when:
			input("ping")
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "pong", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}
	
	def testInputPriorityWithAgents() {
		init(
			"input(0.5, ~/.*/) { tell user, 'error' }\n" +
			"initialAgent('A') { input(true, ~/ping/) { tell user, 'pong' } }"
		)
		when:
			input("ping")
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "pong", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}
	
	def testInputPriorityNextTrue() {
		init(
			"input(0.9, ~/.*/) { tell user, 'error' }\n" +
			"input(true, ~/.*/) { next() }\n" +
			"input(0.5, ~/.*/) { tell user, 'pong' }"
		)
		when:
			input("ping")
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "pong", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}
	
	def testInputPriorityNext() {
		init(
			"input(0.8, ~/.*/) { tell user, 'error' }\n" +
			"input(0.9, ~/.*/) { next() }\n" +
			"input(0.5, ~/.*/) { tell user, 'pong' }"
		)
		when:
			input("ping")
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "pong", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}
}
