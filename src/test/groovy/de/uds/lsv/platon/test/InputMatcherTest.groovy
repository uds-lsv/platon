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

class InputMatcherTest extends TestImplBase {
	def testInputString() {
		setup:
			init(
				"input('ping') { tell user, 'pong' }"
			)
		when:
			input("ping");
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "pong", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}

	def testInputPattern() {
		setup:
			init(
				"input(~/ping/) { tell user, 'pong' }"
			)
		when:
			input("ping");
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "pong", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}
	
	def testInputClass() {
		setup:
			init(
				"input(String) { tell user, 'pong' }"
			)
		when:
			input("ping");
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "pong", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}
	
	def testInputWildcard() {
		setup:
			init(
				"input(_) { tell user, 'pong' }"
			)
		when:
			input("ping");
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "pong", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}
	
	def testInputClosure() {
		setup:
			init(
				"input({ 'ping'.equals(it) }) { tell user, 'pong' }"
			)
		when:
			input("ping");
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "pong", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}
	
	def testInputObject() {
		setup:
			init("""
				object = new Object() {
					public boolean equals(Object other) {
						return 'ping'.equals(other);
					}
				};
				input(object) {
					tell user, 'pong';
				}
			""")
		when:
			input("ping");
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "pong", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}
	
	def testInputOneOf() {
		setup:
			init(
				"input(oneOf(Integer, 'ping', Boolean)) { tell user, 'pong' }"
			)
		when:
			input("ping");
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "pong", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}
}
