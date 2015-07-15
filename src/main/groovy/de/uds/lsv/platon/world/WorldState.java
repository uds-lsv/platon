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

package de.uds.lsv.platon.world;

import java.io.Closeable;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import de.martingropp.util.Pair;
import de.uds.lsv.platon.action.Action;
import de.uds.lsv.platon.action.EnvironmentModifiedAction;
import de.uds.lsv.platon.action.ObjectAddedAction;
import de.uds.lsv.platon.action.ObjectDeletedAction;
import de.uds.lsv.platon.action.ObjectModifiedAction;
import de.uds.lsv.platon.session.DialogSession;

/**
 * This is just the representation of the world state.
 * To modify it, use the DialogSession.Action classes.
 * 
 * @author mgropp
 */
public class WorldState implements Closeable {
	private static final Log logger = LogFactory.getLog(WorldState.class.getName());
	
	private final DialogSession session;
	private final WorldMaker worldMaker = new WorldMaker();
	
	public static interface AddListener {
		void objectAdded(WorldObject object);
	}
	
	public static interface ModifyListener {
		void objectModified(WorldObject object, Map<String,Object> oldState);
	}
	
	public static interface DeleteListener {
		void objectDeleted(WorldObject object);
	}
	
	public static interface EnvironmentListener {
		void environmentModified(String key, String value);
	}
	
	/** Don't modify this directly! */
	private final Map<String,WorldObject> objects = new HashMap<>();
	
	private final List<AddListener> addListeners = new ArrayList<>();
	private final List<ModifyListener> modifyListeners = new ArrayList<>();
	private final List<DeleteListener> deleteListeners = new ArrayList<>();
	private final List<EnvironmentListener> environmentListeners = new ArrayList<>();
	
	// environment variables
	private final Map<String,String> environmentVariables = new HashMap<>();
	
	/** alternative to listeners */
	private final SubscriptionManager subscriptionManager = new SubscriptionManager(this);
	
	public WorldState(DialogSession session) {
		this.session = session;
	}
	
	/**
	 * Performs changes, but does not invoke listeners.
	 * Exists mainly for the Object*edAction classes (-> transactions).
	 */
	public WorldObject doChangeNotificationAdd(Map<String,String> properties) {
		logger.debug(String.format("changeNotificationAdd(%s)", properties));
				
		if (!properties.containsKey(WorldObject.FIELD_ID)) {
			throw new IllegalArgumentException("Object does not have an id field: " + properties);
		}
		
		String objectId = properties.get(WorldObject.FIELD_ID);
		if (objects.containsKey(objectId)) {
			throw new IllegalStateException("Object already exists: " + objectId);
		}
		
		WorldObject object = worldMaker.create(session, properties);
		assert (object != null);
		
		objects.put(objectId, object);
		
		logger.info("New object: " + object);
		
		return object;
	}
	
	public synchronized void changeNotificationAdd(Map<String,String> properties) {
		if (
			session != null &&
			!session.isOnSessionThread()
		) {
			logger.warn("World state modification outside session executor thread! Current thread: " + Thread.currentThread());
		}
		
		WorldObject object = doChangeNotificationAdd(properties);
		if (object != null) {
			for (AddListener listener : addListeners) {
				listener.objectAdded(object);
			}
		}
	}
	
	/**
	 * Performs changes, but does not invoke listeners.
	 * Exists mainly for the Object*edAction classes (-> transactions).
	 */
	public Pair<WorldObject,Map<String,Object>> doChangeNotificationModify(Map<String,String> modifications) {
		logger.debug(String.format("changeNotificationModify(%s)", modifications));
		
		if (!modifications.containsKey(WorldObject.FIELD_ID)) {
			throw new IllegalArgumentException("Object does not have an id field: " + modifications);
		}
		
		String objectId = modifications.get(WorldObject.FIELD_ID);
		if (!objects.containsKey(objectId)) {
			throw new IllegalArgumentException("Object does not exist: " + objectId);
		}
		
		WorldObject object = objects.get(objectId);
		Map<String,Object> oldState = object.getProperties();
		
		object.modified(modifications);
		
		return new Pair<>(object, oldState);
	}
	
	public synchronized void changeNotificationModify(Map<String,String> modifications) {
		if (
			session != null &&
			!session.isOnSessionThread()
		) {
			logger.warn("World state modification outside session executor thread! Current thread: " + Thread.currentThread());
		}
		
		Pair<WorldObject,Map<String,Object>> r = doChangeNotificationModify(modifications);
		WorldObject object = r.first;
		Map<String,Object> oldState = r.second;
		
		for (ModifyListener listener : modifyListeners) {
			listener.objectModified(object, oldState);
		}
	}
	
	/**
	 * Performs changes, but does not invoke listeners.
	 * Exists mainly for the Object*edAction classes.
	 */
	public synchronized WorldObject doChangeNotificationDelete(String objectId) {
		if (!objects.containsKey(objectId)) {
			throw new IllegalArgumentException("Object does not exist: " + objectId);
		}
		
		logger.info("Object deleted: " + objectId);
		
		WorldObject object = objects.get(objectId);
		objects.remove(objectId);
		
		return object;
	}
	
	public synchronized void changeNotificationDelete(String objectId) {
		if (
			session != null &&
			!session.isOnSessionThread()
		) {
			logger.warn("World state modification outside session executor thread! Current thread: " + Thread.currentThread());
		}
		
		WorldObject object = doChangeNotificationDelete(objectId);
		
		for (DeleteListener listener : deleteListeners) {
			listener.objectDeleted(object);
		}
	}
	
	/**
	 * Set an environment variable for this session and notify
	 * environment listeners.
	 * Does not notify the game server!
	 */
	public void setEnvironmentVariable(String key, String value) {
		if (!doSetEnvironmentVariable(key, value)) {
			return;
		}
		
		for (EnvironmentListener listener : environmentListeners) {
			listener.environmentModified(key, value);
		}
	}
	
	/**
	 * Performs changes, but does not invoke listeners.
	 * Exists mainly for the EnvironmentModifiedAction class.
	 * Does not notify the game server!
	 * 
	 * @return
	 *   true if the variable was modified
	 *   false if the new value is the same as the old one 
	 */
	public synchronized boolean doSetEnvironmentVariable(String key, String value) {
		if (
			session != null &&
			!session.isOnSessionThread()
		) {
			logger.warn("World state modification outside session executor thread! Current thread: " + Thread.currentThread());
		}
	
		if (environmentVariables.containsKey(key) && environmentVariables.get(key).equals(value)) {
			logger.debug(String.format(
				"setEnvironmentVariable: %s ← »%s« (same as old value)",
				key, value
			));
			return false;
		}
		
		logger.debug(String.format(
			"setEnvironmentVariable: %s ← »%s«",
			key, value
		));
		environmentVariables.put(key, value);
		
		return true;
	}
	
	public synchronized void apply(Action change) {
		assert(
			(change instanceof ObjectAddedAction) ||
			(change instanceof ObjectModifiedAction) ||
			(change instanceof ObjectDeletedAction) ||
			(change instanceof EnvironmentModifiedAction)
		);
		
		if (
			session != null &&
			!session.isOnSessionThread()
		) {
			logger.warn("World state modification outside session executor thread! Current thread: " + Thread.currentThread());
		}
		
		change.execute();
		
		notifyListeners(change);
	}
	
	/**
	 * Apply all of the changes and notify the listeners after the
	 * entire change set has been applied.
	 * Also handles environment modifications, which is not beautiful,
	 * but has to happen in the same place to preserve the order
	 * of modifications.
	 */
	public synchronized void batchApply(Iterable<Action> changes) {
		if (
			session != null &&
			!session.isOnSessionThread()
		) {
			logger.warn("World state modification outside session executor thread! Current thread: " + Thread.currentThread());
		}
		
		// Apply changes
		for (Action change : changes) {
			assert(
				(change instanceof ObjectAddedAction) ||
				(change instanceof ObjectModifiedAction) ||
				(change instanceof ObjectDeletedAction) ||
				(change instanceof EnvironmentModifiedAction)
			);
			
			change.execute();
		}
		
		// Notify listeners
		for (Action change : changes) {
			notifyListeners(change);
		}
	}
	
	private void notifyListeners(Action change) {
		if (change instanceof ObjectAddedAction) {
			for (AddListener listener : addListeners) {
				((ObjectAddedAction)change).notifyListener(listener);
			}
		} else if (change instanceof ObjectModifiedAction) {
			for (ModifyListener listener : modifyListeners) {
				((ObjectModifiedAction)change).notifyListener(listener);
			}
		} else if (change instanceof ObjectDeletedAction) {
			for (DeleteListener listener : deleteListeners) {
				((ObjectDeletedAction)change).notifyListener(listener);
			}
		} else if (change instanceof EnvironmentModifiedAction) {
			for (EnvironmentListener listener : environmentListeners) {
				((EnvironmentModifiedAction)change).notifyListener(listener);
			}
		} else {
			throw new AssertionError("?!");
		}
	}
	
	@Override
	public void close() throws IOException {
	}
	
	public void addAddListener(AddListener listener) {
		addListeners.add(listener);
	}
	
	public void addModifyListener(ModifyListener listener) {
		modifyListeners.add(listener);
	}
	
	public void addDeleteListener(DeleteListener listener) {
		deleteListeners.add(listener);
	}
	
	public void addEnvironmentListener(EnvironmentListener listener) {
		environmentListeners.add(listener);
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (Entry<String,WorldObject> object : objects.entrySet()) {
			sb.append(String.format(
				"%s:\n%s",
				object.getKey(),
				object.getValue()
			));
			
		}
		
		return sb.toString();
	}

	/** Don't modify that map directly! */
	public Map<String,WorldObject> getObjects() {
		return objects;
	}

	/**
	 * Check if the world states contains an object with
	 * these properties.
	 */
	public boolean containsMatchingObject(Map<String,String> properties) {
		if (properties.containsKey(WorldObject.FIELD_ID)) {
			String id = properties.get(WorldObject.FIELD_ID);
			if (!objects.containsKey(id)) {
				return false;
			}
			
			return objects.get(id).matchesProperties(properties);
		
		} else {
			for (Entry<String,WorldObject> entry : objects.entrySet()) {
				if (entry.getValue().matchesProperties(properties)) {
					return true;
				}
			}
			
			return false;
		}
	}
	
	public SubscriptionManager getSubscriptionManager() {
		return subscriptionManager;
	}
	
	public String getEnvironmentVariable(String key) {
		return environmentVariables.get(key);
	}
	
	public String getEnvironmentVariable(String key, String defaultValue) {
		return environmentVariables.containsKey(key) ? environmentVariables.get(key) : defaultValue;
	}
	
	/**
	 * Don't modify environment variables directly, modifications
	 * have to be sent to the game server using dialogEngine.addAction(ModifyObjectAction).
	 */
	public Map<String,String> getEnvironmentVariables() {
		return environmentVariables;
	}
	
	public WorldMaker getWorldMaker() {
		return worldMaker;
	}
	
	public void dumpObjects(PrintStream stream) {
		for (Entry<String,WorldObject> entry : objects.entrySet()) {
			stream.println(entry.getKey() + ":");
			stream.println("  " + entry.getValue());
		}
		stream.println("Total: " + objects.size() + " objects.");
	}
	
	public void dumpObjects() {
		dumpObjects(System.err);
	}
}
