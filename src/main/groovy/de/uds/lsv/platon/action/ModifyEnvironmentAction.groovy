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

package de.uds.lsv.platon.action;

import groovy.transform.TypeChecked
import de.uds.lsv.platon.session.DialogSession;

@TypeChecked
public class ModifyEnvironmentAction extends ModifyObjectAction {
	public ModifyEnvironmentAction(DialogSession session, Map<String,String> modifications) {
		super(session, modifications);
	}
	
	public ModifyEnvironmentAction(DialogSession session, Map<String,String> modifications, int transactionId) {
		super(session, modifications, transactionId);
	}
	
	@Override
	public String toString() {
		return String.format("[ModifyEnvironmentAction: %s (transaction: %d)]", modifications.toString(), transactionId);
	}
}
