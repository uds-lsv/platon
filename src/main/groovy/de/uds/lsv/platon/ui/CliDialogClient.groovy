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

package de.uds.lsv.platon.ui

import java.util.concurrent.atomic.AtomicInteger

import org.fusesource.jansi.Ansi
import org.fusesource.jansi.Ansi.Color

import de.uds.lsv.platon.DialogClient
import de.uds.lsv.platon.action.IOType
import de.uds.lsv.platon.session.DialogSession
import de.uds.lsv.platon.session.User

public class CliDialogClient implements DialogClient {
	private DialogSession dialogSession = null;
	private User user = null;
	private AtomicInteger nextOutputId = new AtomicInteger(1);
	
	public void run(DialogSession dialogSession, User user) {
		this.dialogSession = dialogSession;
		
		dialogSession.setActive(true);
		
		Scanner scanner = new Scanner(System.in);
		while (scanner.hasNextLine()) {
			String line = scanner.nextLine();
			dialogSession.inputStarted(user, IOType.TEXT);
			dialogSession.inputComplete(user, IOType.TEXT, line.trim(), null);
		}
	}
	
	@Override
	public int outputStart(User user, IOType ioType, String text, Map<String,String> details) {
		int outputId = nextOutputId.getAndIncrement();
		println(Ansi.ansi().fg(Color.GREEN).a(text).reset().a(" (@${user == null ? 'all' : user?.name})"));
		dialogSession.outputEnded(outputId, user, 1.0);
		return outputId;
	}

	@Override
	public void outputAbort(int outputId, User user) {
		dialogSession.outputEnded(outputId, user, 0.0);
	}

	@Override
	public void close() throws IOException {
		if (dialogSession == null) {
			return;
		}
		
		dialogSession.close();
		dialogSession = null;
	}
}
