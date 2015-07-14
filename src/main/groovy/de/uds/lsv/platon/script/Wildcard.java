package de.uds.lsv.platon.script;

public class Wildcard {
	public static final Wildcard INSTANCE = new Wildcard();
	
	private Wildcard() {
	}
	
	@Override
	public String toString() {
		return "*";
	}
}
