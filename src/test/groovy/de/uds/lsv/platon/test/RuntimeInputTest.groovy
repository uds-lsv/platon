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

class RuntimeInputTest extends TestImplBase {
	def testRuntimeInputMatch() {
		setup:
			init("""
				input('ping') {
					input('pong') { tell user, 'peng' }
				}
				input('pong') { tell user, 'error' }
			""")
		when:
			input("ping");
			input("pong");
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "peng", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}
	
	def testRuntimeInputNoMatch() {
		setup:
			init("""
				input('ping') {
					input('blah') { tell user, 'peng' }
				}
				input('pong') { tell user, 'error' }
			""")
		when:
			input("ping");
			input("pong");
			shutdownExecutors();
		then:
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}
	
	def testRuntimeInputNoMatchElse() {
		setup:
			init("""
				input('ping') {
					input(
						'blah',
						{ tell user, 'error: wrong case' },
						{ tell user, 'peng' }
					)
				}
				input('pong') { tell user, 'error: wrong input statement' }
			""")
		when:
			input("ping");
			input("pong");
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "peng", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}
	
	def testRuntimeInputMatchNext() {
		setup:
			init("""
				input('ping') {
					input('pong') { next() }
				}
				input('pong') { tell user, 'peng' }
			""")
		when:
			input("ping");
			input("pong");
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "peng", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}
	
	def testRuntimeInputNoMatchNext() {
		setup:
			init("""
				input('ping') {
					input(
						'blah',
						{ tell user, 'error' },
						{ next() }
					)
				}
				input('pong') { tell user, 'peng' }
			""")
		when:
			input("ping");
			input("pong");
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "peng", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}
}
