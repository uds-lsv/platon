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
