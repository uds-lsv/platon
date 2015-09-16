package de.uds.lsv.platon.action

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

public class PartialAction extends Action {
	private static final Log logger = LogFactory.getLog(PartialAction.class.getName());
	
	private final Action parent;
	private final Closure closure;
	
	public PartialAction(Action parent, Closure closure) {
		this(parent, closure, true);
	}
	
	public PartialAction(Action parent, Closure closure, boolean uninterruptible) {
		super(parent.session, uninterruptible, parent.user);
		this.parent = parent;
		this.closure = closure;
	}
	
	@Override
	protected void doExecute() {
		logger.debug("Executing " + this);
		assert (session.isOnSessionThread());
		closure();
		
		submitted();
		complete();
	}
	
	@Override
	public String toString() {
		return String.format("[PartialAction of %s]", parent);
	}
}
