package de.uds.lsv.platon.script;

public class NextThrowable extends Throwable {
	private static final long serialVersionUID = -5688958715605426419L;
	
	public final Object nextInput;
	
	public NextThrowable() {
		this(null);
	}
	
	public NextThrowable(Object nextInput) {
		// Users should not see this.
		super("Potential invalid usage of next!");
		this.nextInput = nextInput;
	}
}
