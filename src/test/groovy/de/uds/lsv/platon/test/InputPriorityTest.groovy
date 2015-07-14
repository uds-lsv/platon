package de.uds.lsv.platon.test

class InputPriorityTest extends TestImplBase {
	def testInputPrioritySimple() {
		init(
			"input(0.5, ~/ping/) { tell player, 'error' }\n" +
			"input(0.6, ~/ping/) { tell player, 'pong' }"
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
			"input(0.5, ~/ping/) { tell player, 'error' }\n" +
			"input(true, ~/ping/) { tell player, 'pong' }"
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
			"input(0.5, ~/.*/) { tell player, 'error' }\n" +
			"initialAgent('A') { input(true, ~/ping/) { tell player, 'pong' } }"
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
			"input(0.9, ~/.*/) { tell player, 'error' }\n" +
			"input(true, ~/.*/) { next() }\n" +
			"input(0.5, ~/.*/) { tell player, 'pong' }"
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
			"input(0.8, ~/.*/) { tell player, 'error' }\n" +
			"input(0.9, ~/.*/) { next() }\n" +
			"input(0.5, ~/.*/) { tell player, 'pong' }"
		)
		when:
			input("ping")
			shutdownExecutors();
		then:
			1 * dialogClientMonitor.outputStart(_, _, "pong", _)
			0 * dialogClientMonitor.outputStart(_, _, _, _)
	}
}
