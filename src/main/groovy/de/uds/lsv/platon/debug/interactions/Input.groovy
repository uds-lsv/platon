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

package de.uds.lsv.platon.debug.interactions;

import groovy.transform.TupleConstructor;
import groovy.transform.TypeChecked;

import java.util.Map;

import de.uds.lsv.platon.action.IOType;
import de.uds.lsv.platon.session.User;

@TypeChecked
public class Input implements Interaction {
	String text;
	User user;
	boolean interrupting;
	IOType ioType = IOType.TEXT;
	Map<String,String> details = null;
	
	public Input(String text, User user, boolean interrupting=false) {
		this.text = text;
		this.user = user;
		this.interrupting = interrupting;
	}
	
	@Override
	public String toString() {
		return "Input: »${text}«";
	}
}
