package de.uds.lsv.platon.debug.interactions

import groovy.transform.TupleConstructor;
import groovy.transform.TypeChecked;

import java.util.Map;

import de.uds.lsv.platon.action.IOType;
import de.uds.lsv.platon.session.User;

@TypeChecked
public class Input implements Interaction {
	String text;
	User user;
	boolean interrupting;
	IOType ioType = IOType.TEXT;
	Map<String,String> details = null;
	
	public Input(String text, User user, boolean interrupting=false) {
		this.text = text;
		this.user = user;
		this.interrupting = interrupting;
	}
	
	@Override
	public String toString() {
		return "Input: »${text}«";
	}
}
