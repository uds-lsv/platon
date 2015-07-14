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
