package de.uds.lsv.platon.action;

import groovy.transform.TypeChecked

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

import de.uds.lsv.platon.session.DialogSession;
import de.uds.lsv.platon.session.User;

@TypeChecked
public class VerbalInputAction extends Action {
	private static final Log logger = LogFactory.getLog(VerbalInputAction.class.getName());
	
	final IOType type;
	final String text;
	final Map<String, String> details;
	
	public VerbalInputAction(
		DialogSession session,
		User user,
		IOType type,
		String text,
		Map<String, String> details
	) {
		super(session, true, user);
		this.type = type;
		this.text = text;
		this.details = details;
	}
	
	@Override
	protected void doExecute() {
		logger.debug("Executing " + this);
		if (user == null) {
			session.submit({
				session.dialogEngines.values()*.inputComplete(type, text, details);
			});
		} else {
			if (!session.dialogEngines.containsKey(user.id)) {
				throw new RuntimeException("User not in session: " + user);
			}
			session.submit({
				session.dialogEngines[user.id].inputComplete(type, text, details);
			});
		}
		
		submitted();
		complete();
	}
	
	@Override
	public String toString() {
		return String.format(
			"[VerbalInputAction: »%s« @ %s]",
			text,
			user
		);
	}
}
