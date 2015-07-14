package de.uds.lsv.platon.test

class StdlibTest extends TestImplBase {
	def testOnceSimple() {
		setup:
			init("""
				#include 'stdlib.groovy'
				input('ping') { tell player, Stdlib.once('pong', 'peng'), uninterruptible: true; }
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
				input('ping') { for (i in 1..3) { tell player, Stdlib.once('pong', 'peng'), uninterruptible: true; } }
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
				input('ping') { for (i in 1..3) { tell player, Stdlib.once('pong', 'peng', "my id"), uninterruptible: true; } }
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
}
