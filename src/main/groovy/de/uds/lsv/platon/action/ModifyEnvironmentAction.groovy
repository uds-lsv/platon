package de.uds.lsv.platon.action

import groovy.transform.TypeChecked
import de.uds.lsv.platon.session.DialogSession;

@TypeChecked
public class ModifyEnvironmentAction extends ModifyObjectAction {
	public ModifyEnvironmentAction(DialogSession session, Map<String,String> modifications) {
		super(session, modifications);
	}
	
	public ModifyEnvironmentAction(DialogSession session, Map<String,String> modifications, int transactionId) {
		super(session, modifications, transactionId);
	}
	
	@Override
	public String toString() {
		return String.format("[ModifyEnvironmentAction: %s (transaction: %d)]", modifications.toString(), transactionId);
	}
}
