package de.uds.lsv.platon.exception;

public class DialogWorldException extends RuntimeException {
	private static final long serialVersionUID = -8844373696746975734L;
	
	public final String id;
	
	public DialogWorldException(String id, String message) {
		super(message);
		this.id = id;
	}
	
	public DialogWorldException(String id, Throwable cause) {
		super(cause);
		this.id = id;
	}
	
	public DialogWorldException(String id) {
		super();
		this.id = id;
	}
}
