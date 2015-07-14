package de.uds.lsv.platon.action

import groovy.transform.TypeChecked

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

import de.uds.lsv.platon.session.DialogEngine;
import de.uds.lsv.platon.session.DialogSession;

@TypeChecked
public class QuitAction extends Action {
	private static final Log logger = LogFactory.getLog(QuitAction.class.getName());
	
	private final DialogEngine dialogEngine;
	
	public QuitAction(DialogSession session, DialogEngine dialogEngine) {
		super(session);
		this.dialogEngine = dialogEngine;
	}
	
	@Override
	protected void doExecute() {
		logger.debug("Executing " + this);
		dialogEngine.terminated = true;
		
		synchronized (session) {
			for (DialogEngine d : session.dialogEngines.values()) {
				if (!d.terminated) {
					logger.debug("Still waiting for dialog engine to quit: " + d);
					return;
				}
			}
			
			session.close();
			submitted();
			complete();
		}
	}
	
	@Override
	public String toString() {
		return String.format(
			"[QuitAction %s]",
			dialogEngine
		);
	}
}
