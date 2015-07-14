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

package de.uds.lsv.platon;

import groovy.transform.TypeChecked;

import java.io.Reader;
import java.net.URL;
import java.util.List;

import de.uds.lsv.platon.config.Config;
import de.uds.lsv.platon.session.DialogSession;
import de.uds.lsv.platon.session.User;

@TypeChecked
public class Platon {
	public static DialogSession createSession(Config config, DialogClient dialogClient, DialogWorld dialogWorld, List<User> users) {
		return new DialogSession(config, dialogClient, dialogWorld, users);
	}
	
	public static DialogSession createSession(URL dialogScript, DialogClient dialogClient, DialogWorld dialogWorld, List<User> users) {
		Config config = new Config();
		config.dialogScript = dialogScript;
		return createSession(config, dialogClient, dialogWorld, users);
	}
	
	public static DialogSession createSession(Reader dialogScriptReader, DialogClient dialogClient, DialogWorld dialogWorld, List<User> users) {
		Config config = new Config();
		config.openDialogScript = { return dialogScriptReader };
		return createSession(config, dialogClient, dialogWorld, users);
	}
}
