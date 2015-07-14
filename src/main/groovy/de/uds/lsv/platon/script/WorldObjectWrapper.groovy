package de.uds.lsv.platon.script

import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode

import java.lang.reflect.Method

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

import de.uds.lsv.platon.action.Action
import de.uds.lsv.platon.action.FailureAction
import de.uds.lsv.platon.action.ModifyObjectAction
import de.uds.lsv.platon.session.DialogEngine;
import de.uds.lsv.platon.world.WorldException
import de.uds.lsv.platon.world.WorldMethod
import de.uds.lsv.platon.world.WorldObject

/**
 * Wrap world objects for use in scripts.
 * Methods annotated with \@WorldMethod get ReactionTrigger as additional
 * argument, and the actions they return (as an Iterable of Actions) are
 * added to the dialog engine's action queue.
 * 
 * @author mgropp
 */
@TypeChecked
class WorldObjectWrapper extends GroovyObjectSupport {
	private static final Log logger = LogFactory.getLog(WorldObjectWrapper.class.getName());
	
	private WorldObject realObject;
	private DialogEngine dialogEngine;
	private List<Method> wrappedMethods = [];
		
	public WorldObjectWrapper(WorldObject realObject, DialogEngine dialogEngine) {
		this.@realObject = realObject;
		this.@dialogEngine = dialogEngine;

		for (Method method : realObject.getClass().getMethods()) {
			if (method.getAnnotation(WorldMethod.class) != null) {
				if (!List.class.equals(method.getReturnType())) {
					// TODO: exception type?
					throw new RuntimeException("Wrong return type for method ${method}! Expected: List<Action>");
				}
				this.@wrappedMethods.add(method);
			}
		}
	}
	
	@Override
	@TypeChecked(TypeCheckingMode.SKIP)
	public Object getProperty(String property) {
		return this.@realObject.getProperty(property);
	}
	
	@Override
	public void setProperty(String property, Object newValue) {
		if (!this.@realObject.isWritableField(property)) {
			throw new IllegalAccessException("Property »${property}« is not writable for scripts!");
		}
		
		this.@dialogEngine?.addAction(
			new ModifyObjectAction(
				this.@dialogEngine?.session,
				[
					(WorldObject.FIELD_ID): this.@realObject.getId(),
					(property): newValue.toString()
				]
			)
		);
	}
	
	@Override
	public Object invokeMethod(String name, Object args) {
		// Pretend we have setX methods for writable world properties
		// if they don't exist.
		if (name.startsWith("set") && name.length() > 3) {
			if (!this.@realObject.getClass().getDeclaredMethods().any({
				Method method -> method.getName().equals(name)
			})) {
				String propertyName = "" + Character.toLowerCase(name.charAt(3)) + name.substring(4);
				
				if (this.@realObject.isWritableField(propertyName)) {
					Action action =
						new ModifyObjectAction(
							this.@dialogEngine.session,
							[
								(WorldObject.FIELD_ID): this.@realObject.getId(),
								(propertyName): ((Object[])args)[0].toString()
							]
						);
					
					this.@dialogEngine?.addAction(action);
					
					return new Then(action, dialogEngine);
				}
			}
		}
		
		// Invoke real method
		Object result = null;
		try {
			result = this.@realObject.invokeMethod(name, args);
		}
		catch (WorldException e) {
			logger.debug("World method threw a WorldException with error code: " + e.getErrorCode());
			result = [
				new FailureAction(
					this.@dialogEngine.session,
					e.getErrorCode()
				)
			];
		}
			
		// TODO: check parameter types
		// add actions and return Then if this was a wrapped method
		Action lastAction = null;
		if (this.@wrappedMethods.find({ it.getName() == name }) != null) {
			Iterator<Action> actionsIterator = ((Iterable<Action>)result).iterator();
			while (actionsIterator.hasNext()) {
				Action action = actionsIterator.next();
				this.@dialogEngine?.addAction(action);
				lastAction = action;
			}
			
			return new Then(lastAction, dialogEngine);
		}
		
		return result;
	}
	
	@Override
	public String toString() {
		return "[script object wrapper for ${this.@realObject}]"
	}
}