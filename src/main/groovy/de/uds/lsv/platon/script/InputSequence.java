package de.uds.lsv.platon.script;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Used to represent an input text that is split in several parts
 * (ScriptAdapter.handleInput / prepareInput).
 */
public class InputSequence extends ArrayList<Object> {
	private static final long serialVersionUID = -4473554326152836355L;
	
	public InputSequence() {
		super();
	}
	
	public InputSequence(Collection<?> collection) {
		super(collection);
	}
}
