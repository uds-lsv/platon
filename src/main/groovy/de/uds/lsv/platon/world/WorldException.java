package de.uds.lsv.platon.world;

/**
 * An exception class to be thrown in WorldMethod
 * methods.
 */
public class WorldException extends RuntimeException {
	private static final long serialVersionUID = 6683584146023331660L;
	
	private final String errorCode;
	
	public WorldException(String errorCode) {
		this.errorCode = errorCode;
	}
	
	public String getErrorCode() {
		return errorCode;
	}
}
