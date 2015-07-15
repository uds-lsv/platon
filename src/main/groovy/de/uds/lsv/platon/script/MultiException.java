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

import java.util.Collection;

public class MultiException extends DialogScriptException {
	private static final long serialVersionUID = 1729204619698256538L;
	
	public Collection<DialogScriptException> causes;
	
	public MultiException(Throwable rootThrowable, Collection<DialogScriptException> causes) {
		super(rootThrowable);
		this.causes = causes;
		
		if (!causes.isEmpty()) {
			DialogScriptException dse = causes.iterator().next();
			this.sourceUrl = dse.sourceUrl;
			this.startLine = dse.startLine;
			this.endLine = dse.endLine;
			this.startColumn = dse.startColumn;
			this.endColumn = dse.endColumn;
		}
	}
	
	@Override
	public String getMessage() {
		StringBuilder sb = new StringBuilder();
		sb.append(String.format(
			"MultiException of %d exceptions:\n",
			causes.size()
		));
		
		for (Throwable cause : causes) {
			sb.append(cause.toString());
			sb.append('\n');
		}
		
		return sb.toString();
	}
}
