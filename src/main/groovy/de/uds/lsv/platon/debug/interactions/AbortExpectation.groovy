package de.uds.lsv.platon.debug.interactions

import groovy.transform.TypeChecked;
import de.uds.lsv.platon.debug.ValidatingDialogClient.AbortEvent
import de.uds.lsv.platon.script.Wildcard

@TypeChecked
public class AbortExpectation implements Interaction {
	def user;
	def outputId;

	public AbortExpectation(user=Wildcard.INSTANCE, outputId=Wildcard.INSTANCE) {
		this.user = user;
		this.outputId = outputId;
	}

	boolean matches(AbortEvent abort) {
		if (outputId != Wildcard.INSTANCE) {
			if (abort.outputId != outputId) {
				return false;
			}
		}

		if (user != Wildcard.INSTANCE) {
			if (abort.user != user) {
				return false;
			}
		}

		return true;
	}

	@Override
	public String toString() {
		return String.format("Expectation: Abort (user: ${user})");
	}
}
