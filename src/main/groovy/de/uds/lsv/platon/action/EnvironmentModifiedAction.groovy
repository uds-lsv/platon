package de.uds.lsv.platon.action;

import groovy.transform.TypeChecked
import de.uds.lsv.platon.session.DialogSession;
import de.uds.lsv.platon.world.WorldState

/**
 * The Action representation of a changeNotificationModify event.
 * Executing the action updates the WorldState (but does not
 * automatically run the listeners registered with the WorldState).
 */
@TypeChecked
public class EnvironmentModifiedAction extends Action {
	final Map<String,String> modifications;
	String key;
	String value;
	boolean noEffect = false;
	
	public EnvironmentModifiedAction(DialogSession session, String key, String value) {
		super(session);
		this.key = key;
		this.value = value;
	}
	
	@Override
	protected void doExecute() {
		noEffect = !session.worldState.doSetEnvironmentVariable(key, value);
		
		submitted();
		complete();
	}
	
	public void notifyListener(WorldState.EnvironmentListener listener) {
		if (!this.executed) {
			throw new IllegalStateException("Action has not been executed yet!");
		}
		
		if (!noEffect) {
			listener.environmentModified(key, value);
		}
	}
	
	@Override
	public String toString() {
		return String.format(
			"[EnvironmentModifiedAction: %s = »%s«]",
			key,
			value
		);
	}
}
