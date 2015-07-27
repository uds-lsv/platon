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

class StdlibTest extends TestImplBase {
	def testOnceSimple() {
		setup:
			init("""
				#include 'stdlib.groovy'
				input('ping') { tell user, Stdlib.once('pong', 'peng'), uninterruptible: true; }
			""")
		when:
			input("ping");
			input("ping");
			input("ping");
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "pong", _)
		then:
			2 * dialogClientMonitor.outputStart(_, _, "peng", _)
	}
	
	def testOnceLoop() {
		setup:
			init("""
				#include 'stdlib.groovy'
				input('ping') { for (i in 1..3) { tell user, Stdlib.once('pong', 'peng'), uninterruptible: true; } }
			""")
		when:
			input("ping");
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "pong", _)
		then:
			2 * dialogClientMonitor.outputStart(_, _, "peng", _)
	}
	
	def testOnceTwice() {
		setup:
			init("""
				#include 'stdlib.groovy'
				input('ping') { for (i in 1..3) { tell user, Stdlib.once('pong', 'peng', "my id"), uninterruptible: true; } }
			""")
		when:
			input("ping");
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "pong", _)
		then:
			2 * dialogClientMonitor.outputStart(_, _, "peng", _)
		
		where:
			round << [ 1, 2 ]
	}
	
	def testSelectTranslation() {
		setup:
			init("""
				#include 'stdlib.groovy'
				input(~/ping/) {
					tell user, stdlib.selectTranslation(
						de: 'error',
						en: 'pong',
						fr: 'error'
					);
				}
			""")
		when:
			input("ping");
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "pong", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}
	
	def testSelectTranslationDefault() {
		setup:
			init("""
				#include 'stdlib.groovy'
				input(~/ping/) {
					tell user, stdlib.selectTranslation(
						x: 'error',
						y: 'error',
						z: 'error',
						'pong'
					);
				}
			""")
		when:
			input("ping");
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "pong", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}
	
	def testWaitForInputMatching() {
		setup:
			init("""
				#include 'stdlib.groovy'
				input('ping') {
					stdlib.waitForInput(
						'pong',
						1.second,
						{ tell user, 'peng' },
						{ tell user, 'error' }
					);
				}
			""")
		when:
			input("ping");
			input("pong");
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "peng", _)
	}
	
	def testWaitForInputNotMatching() {
		setup:
			init("""
				#include 'stdlib.groovy'
				input('ping') {
					stdlib.waitForInput(
						'blah',
						1.second,
						{ tell user, 'error' },
						{ tell user, 'peng' }
					);
				}
			""")
		when:
			input("ping");
			input("pong");
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "peng", _)
	}
	
	def testWaitForInputTimeout() {
		setup:
			init("""
				#include 'stdlib.groovy'
				input('ping') {
					stdlib.waitForInput(
						'blah',
						100.milliseconds,
						{ tell user, 'error' },
						{ tell user, 'peng' }
					);
				}
			""")
		when:
			input("ping");
			Thread.sleep(800);
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "peng", _)
	}
}
