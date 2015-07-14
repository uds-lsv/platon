package de.uds.lsv.platon.test

class InputMatcherTest extends TestImplBase {
	def testInputString() {
		setup:
			init(
				"input('ping') { tell player, 'pong' }"
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
				"input(en:~/ping/) { tell player, 'pong' }"
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
				"input(String) { tell player, 'pong' }"
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
				"input(_) { tell player, 'pong' }"
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
				"input({ 'ping'.equals(it) }) { tell player, 'pong' }"
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
			init(
				"object = new Object() {\n" +
				"  public boolean equals(Object other) {\n" +
				"    return 'ping'.equals(other);\n" +
				"  }\n" +
				"};\n" +
				"input({ 'ping'.equals(it) }) { tell player, 'pong' }"
			)
		when:
			input("ping");
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "pong", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}
	
	def testInputList() {
		setup:
			init(
				"input([ 'ping', String ]) { tell player, 'pong' }"
			)
		when:
			input("ping");
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "pong", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}
}
