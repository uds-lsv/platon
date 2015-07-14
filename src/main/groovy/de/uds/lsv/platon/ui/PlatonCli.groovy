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

package de.uds.lsv.platon.ui;

import de.uds.lsv.platon.DialogClient
import de.uds.lsv.platon.DialogWorld
import de.uds.lsv.platon.Platon
import de.uds.lsv.platon.session.DialogSession
import de.uds.lsv.platon.session.User

/**
 * Simple command line interface for dialog
 * sessions with only one user. 
 */
public class PlatonCli {
	public static void main(String[] args) {
		CliBuilder cli = new CliBuilder(usage: 'PlatonCli <script file>');
		cli.url('load script file from a url (instead of a file)');
		cli.language('set dialog language');
		cli.world('set dialog world class');
		
		def options = cli.parse(args);
		if (options == null || options.arguments().size() != 1) {
			cli.usage();
			System.exit(1);
		}
		
		URL scriptUrl;
		if (options.url) {
			scriptUrl = new URL(options.arguments()[0]);
		} else {
			scriptUrl = new File(options.arguments()[0]).toURI().toURL();
		}
		
		DialogWorld dialogWorld = null;
		if (options.world) {
			Class<DialogWorld> dialogWorldClass = Class.forName(options.world);
			dialogWorld = dialogWorldClass.newInstance();
		}
		
		// TODO: set language
		List<User> users = [ new User(1, "TestUser", "en", "us")];
		
		DialogClient dialogClient = new CliDialogClient();
		DialogSession session = Platon.createSession(scriptUrl, dialogClient, dialogWorld, users);
		
		dialogClient.run(session, users[0]);
	}
}
