package de.uds.lsv.platon.action

import groovy.transform.TypeChecked
import de.martingropp.util.Pair;
import de.uds.lsv.platon.session.DialogSession;
import de.uds.lsv.platon.world.WorldObject
import de.uds.lsv.platon.world.WorldState

/**
 * The Action representation of a changeNotificationModify event.
 * Executing the action updates the WorldState but does not
 * automatically run the listeners registered with the WorldState.
 * This is why the preferred way of executing this action is
 * by using the WorldState.apply(Action) method.
 */
@TypeChecked
public class ObjectModifiedAction extends Action {
	final Map<String,String> modifications;

	/** Not set before execution! */
	WorldObject modifiedObject = null;
	
	/** Not set before execution! */
	Map<String,Object> oldState = null;
	
	public ObjectModifiedAction(DialogSession session, Map<String,String> modifications) {
		super(session);
		this.modifications = modifications;
	}
	
	@Override
	protected void doExecute() {
		Pair<WorldObject,Map<String,Object>> result =
			session.worldState.doChangeNotificationModify(modifications);
		
		this.modifiedObject = result.first;
		this.oldState = result.second;
		
		submitted();
		complete();
	}
	
	public void notifyListener(WorldState.ModifyListener listener) {
		if (!this.executed) {
			throw new IllegalStateException("Action has not been executed yet!");
		}
		listener.objectModified(modifiedObject, oldState);
	}
	
	@Override
	public String toString() {
		return String.format(
			"[ObjectModifiedAction: %s]",
			properties
		);
	}
}
