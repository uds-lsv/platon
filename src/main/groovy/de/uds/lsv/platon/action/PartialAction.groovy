package de.uds.lsv.platon.action;

public class PartialAction extends Action {
	private final Action parent;
	private final Closure closure;
	
	public PartialAction(Action parent, Closure closure) {
		this.parent = parent;
		this.closure = closure;
	}
	
	@Override
	protected void doExecute() {
		logger.debug("Executing " + this);
		session.submit(closure);
	}
	
	@Override
	public String toString() {
		return String.format("[PartialAction of %s]", parent);
	}
}
