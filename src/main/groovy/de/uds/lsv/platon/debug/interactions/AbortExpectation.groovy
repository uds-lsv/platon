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
