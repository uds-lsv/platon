package de.uds.lsv.platon.action

import groovy.transform.TypeChecked
import de.uds.lsv.platon.session.DialogSession;
import de.uds.lsv.platon.world.WorldObject
import de.uds.lsv.platon.world.WorldState

/**
 * The Action representation of a changeNotificationDelete event.
 * Executing the action updates the WorldState but does not
 * automatically run the listeners registered with the WorldState.
 * This is why the preferred way of executing this action is
 * by using the WorldState.apply(Action) method.
 */
@TypeChecked
public class ObjectDeletedAction extends Action {
	private final String objectId;
	
	/** Not set before execution! */
	WorldObject deletedObject = null;
	
	public ObjectDeletedAction(DialogSession session, String objectId) {
		super(session);
		this.objectId = objectId;
	}
	
	@Override
	protected void doExecute() {
		deletedObject = session.worldState.doChangeNotificationDelete(objectId);
		
		submitted();
		complete();
	}
	
	public void notifyListener(WorldState.DeleteListener listener) {
		if (!this.executed) {
			throw new IllegalStateException("Action has not been executed yet!");
		}
		listener.objectDeleted(deletedObject);
	}
	
	@Override
	public String toString() {
		return String.format(
			"[ObjectDeletedAction: %s]",
			objectId
		);
	}
}
