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

package de.uds.lsv.platon.script;

import java.net.URL;

public class DialogScriptException extends RuntimeException {
	private static final long serialVersionUID = 3465877849198516774L;
	
	public URL sourceUrl = null;
	public int startLine = -1;
	public int endLine = -1;
	public int startColumn = -1;
	public int endColumn = -1;
	
	public DialogScriptException(Throwable cause) {
		super(cause);
	}
	
	@Override
	public String getMessage() {
		return String.format(
			"FILE: %s\nLINE: %d\n%s",
			(sourceUrl == null) ? "(unknown source)" : sourceUrl.toString(),
			startLine,
			super.getMessage()
		);
	}
}
