package de.uds.lsv.platon.action

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

import de.uds.lsv.platon.exception.DialogWorldException
import de.uds.lsv.platon.session.DialogSession

public class DeleteObjectAction extends Action {
	private static final Log logger = LogFactory.getLog(DeleteObjectAction.class.getName());
	
	final String objectId;
	final int transactionId;
	
	public DeleteObjectAction(DialogSession session, String objectId, Closure errorHandler=null, int transactionId=-1) {
		super(session);
		this.objectId = objectId;
		this.transactionId = transactionId;
	}

	@Override
	protected void doExecute() {
		session.submit({
			logger.debug("Executing " + this);
			String error = null;
			try {
				session.getDialogWorld().changeRequestDelete(
					transactionId,
					objectId
				);
			}
			catch (DialogWorldException e) {
				error = e.id;
			}
			
			submitted();
			
			if (error != null) {
				if (errorHandler != null) {
					logger.debug(String.format(
						"changeRequestDelete failed, result=%s. Calling error handler %s with argument »%s«.",
						error, errorHandler, error
					));
					errorHandler(result);
				} else {
					logger.error(String.format(
						"changeRequestDelete failed, result=%s. No error handler available!",
						error
					));
				}
				
				complete(false);
			}
		});
	}
	
	@Override
	public String toString() {
		return String.format("[DeleteObjectAction: %s (transaction: %d)]", objectId, transactionId); 
	}
}
