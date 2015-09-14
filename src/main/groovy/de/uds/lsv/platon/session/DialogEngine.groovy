/*
 * Copyright 2015, Spoken Language Systems Group, Saarland University.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.uds.lsv.platon.session;

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

import de.martingropp.util.ReactionTrigger;
import de.uds.lsv.platon.action.IOType
import de.uds.lsv.platon.action.Action
import de.uds.lsv.platon.action.ActionQueue
import de.uds.lsv.platon.script.ScriptAdapter
import de.uds.lsv.platon.world.WorldObject
import de.uds.lsv.platon.world.WorldState.AddListener
import de.uds.lsv.platon.world.WorldState.DeleteListener
import de.uds.lsv.platon.world.WorldState.EnvironmentListener
import de.uds.lsv.platon.world.WorldState.ModifyListener

public class DialogEngine implements Closeable, AddListener, ModifyListener, DeleteListener, EnvironmentListener, ReactionTrigger {
	private static final Log logger = LogFactory.getLog(DialogEngine.class.getName());

	private ActionQueue actionQueue = new ActionQueue(this);
	public final LinkedList<Action> history = new LinkedList<>();
	
	private final DialogSession session;
	private final User user;
	
	private ScriptAdapter scriptAdapter;
	
	public boolean terminated = false;
	
	/**
	 * Create a new dialog engine.
	 * 
	 * @param session
	 * @param user
	 * @param dialogScriptReader
	 *   If null, the dialog script is read from the resource set
	 *   in the session configuration.
	 *   Otherwise, this reader is used.
	 */
	public DialogEngine(DialogSession session, User user, Reader dialogScriptReader, URI dialogScriptRoot) {
		this.session = session;
		this.user = user;
		
		logger.info("Creating dialog engine for user ${user} from a Reader.");
		scriptAdapter = new ScriptAdapter(dialogScriptReader, dialogScriptRoot, this, session.config.scriptDefinitions, session.config.scriptInit);
		
		session.getWorldState().addAddListener(this);
		session.getWorldState().addModifyListener(this);
		session.getWorldState().addDeleteListener(this);
		session.getWorldState().addEnvironmentListener(this);
	}
	
	/**
	 * Create a new dialog engine.
	 *
	 * @param session
	 * @param user
	 * @param dialogScriptReader
	 *   If null, the dialog script is read from the resource set
	 *   in the session configuration.
	 *   Otherwise, this reader is used.
	 */
	public DialogEngine(DialogSession session, User user, URL dialogScriptUrl) {
		this.session = session;
		this.user = user;
		
		logger.info("Creating dialog engine for user ${user.id} (${user.name}, ${user.language}) from ${dialogScriptUrl}.");
		scriptAdapter = new ScriptAdapter(dialogScriptUrl, this, session.config.scriptDefinitions, session.config.scriptInit);

		session.getWorldState().addAddListener(this);
		session.getWorldState().addModifyListener(this);
		session.getWorldState().addDeleteListener(this);
		session.getWorldState().addEnvironmentListener(this);
	}

	public DialogSession getSession() {
		return session;
	}
	
	public User getUser() {
		return user;
	}
	
	public void inputStarted(IOType type) {
		user.speaking = true;
		
		if (session.config.ignoreGameInactive || session.isActive()) {
			if (!Boolean.parseBoolean(session.worldState.getEnvironmentVariable("disableBargeIn", "false"))) {
				actionQueue.bargeIn();
			} else {
				logger.debug("Barge in handling disabled.");
			}
			
			scriptAdapter.handleInputStarted();
		} else {
			logger.info("Game is not active -- ignoring inputStarted.");
		}
	}
	
	public void inputComplete(IOType type, String text, Map<String,String> details) {
		user.speaking = false;
		
		if (session.config.ignoreGameInactive || session.isActive()) {
			// Barge in duplicated here, because of this scenario:
			// * inputStarted1
			// * inputComplete1 -> to Action queue
			// * inputStarted2
			// * inputComplete2 -> to Action queue
			// * inputComplete1 (from queue)
			// * processing of input1
			// * inputComplete2 (from queue)
			// * processing of input2
			// => no inputStarted could trigger a barge in
			if (!Boolean.parseBoolean(session.worldState.getEnvironmentVariable("disableBargeIn", "false"))) {
				actionQueue.bargeIn();
			}
			
			scriptAdapter.handleInput(text, details);
		
		} else {
			logger.info("Game is not active -- ignoring inputComplete.");
		}
	}
	
	public void inputAbandoned(IOType type) {
	}

	@Override
	public void objectAdded(WorldObject object) {
		if (session.config.ignoreGameInactive || session.isActive()) {
			scriptAdapter.objectAdded(object);
		} else {
			logger.debug("Game is not active -- not running objectAdded reactions.");
		}
	}
	
	@Override
	public void objectModified(WorldObject object, Map<String,Object> oldState) {
		if (session.config.ignoreGameInactive || session.isActive()) {
			scriptAdapter.objectModified(object, oldState);
		} else {
			logger.debug("Game is not active -- not running objectModified reactions.");
		}
	}
	
	@Override
	public void objectDeleted(WorldObject object) {
		if (session.config.ignoreGameInactive || session.isActive()) {
			scriptAdapter.objectDeleted(object);
		} else {
			logger.debug("Game is not active -- not running objectDeleted reactions.");
		}
	}
	
	@Override
	public void environmentModified(String key, String value) {
		if (session.config.ignoreGameInactive || session.isActive()) {
			scriptAdapter.environmentModified(key, value);
		} else {
			logger.debug("Game is not active -- not running environmentModified reactions.");
		}
	}
	
	@Override
	public void close() throws IOException {
	}
	
	@Override
	public boolean triggerNamedReaction(String id, Object... arguments) {
		return scriptAdapter.triggerNamedReaction(id, arguments);
	}
	
	public void addAction(Action action) {
		actionQueue.add(action);
	}
	
	/**
	 * Get an unmodifiable view of the action queue.
	 */
	public List<Action> getActionQueueView() {
		return actionQueue.getQueue();
	}
	
	public void addHistoryItem(Action action) {
		history.addFirst(action);
		if (
			session.config.maxHistorySize >= 0 &&
			history.size() > session.config.maxHistorySize
		) {
			history.removeLast();
		}
	}
	
	public void sendInternal(DialogEngine sender, Object message) {
		scriptAdapter.handleInternalMessage(sender, message);
	}
	
	@Override
	public String toString() {
		return String.format(
			"DialogEngine(%d)",
			user.id
		);
	}
}
