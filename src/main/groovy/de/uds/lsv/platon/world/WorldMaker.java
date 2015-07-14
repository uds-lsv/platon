package de.uds.lsv.platon.world;

import groovy.lang.GroovyClassLoader;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import de.uds.lsv.platon.session.DialogSession;

public class WorldMaker {
	private static final Log logger = LogFactory.getLog(WorldMaker.class.getName());

	public static Map<String,Class<? extends WorldObject>> baseTypeRegistry =
		new HashMap<>();
	
	private Map<String,Class<? extends WorldObject>> typeRegistry =
		new HashMap<>(baseTypeRegistry);
	
	private void doRegisterType(String typeId, Class<? extends WorldObject> typeClass) {
		if (typeRegistry.containsKey(typeId)) {
			logger.debug(String.format("Type id »%s« already registered! Overwriting!", typeId));
		}
		typeRegistry.put(typeId, typeClass);
	}
	
	public void registerType(String typeId, Class<? extends WorldObject> typeClass) {
		logger.debug(String.format(
			"Registering world object type: %s => %s",
			typeId,
			typeClass.getName()
		));
		
		doRegisterType(typeId, typeClass);
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void registerTypes(GroovyClassLoader classLoader) {
		for (Class<?> cls : classLoader.getLoadedClasses()) {
			if (WorldObject.class.isAssignableFrom(cls)) {
				WorldClass annotation = cls.getAnnotation(WorldClass.class);
				if (annotation != null) {
					String typeId = annotation.value();
					logger.debug("Auto-registering world object type: " + typeId + " => " + cls.getName());
					registerType(typeId, (Class)cls);
				}
			}
		}
	}
	
	public WorldObject create(DialogSession session, Map<String,String> properties) {
		String type = properties.get(WorldObject.FIELD_TYPE);
		if (type == null) {
			//throw new IllegalArgumentException("Missing type property!");
			logger.warn("Missing type property in " + properties);
		}
		
		WorldObject worldObject;
		if (type == null || !typeRegistry.containsKey(type)) {
			if (type != null) {
				logger.warn("Unknown type: " + type);
			}
		
			worldObject = new DefaultWorldObject(type);
		
		} else {
			Class<? extends WorldObject> typeClass = typeRegistry.get(type);
			Constructor<? extends WorldObject> constructor;
			try {
				constructor = typeClass.getConstructor();
			}
			catch (NoSuchMethodException | SecurityException e) {
				throw new RuntimeException("Cannot access constructor " + typeClass.getSimpleName() + "(DialogSession, Map)", e);
			}
	
			try {
				worldObject = constructor.newInstance();
			}
			catch (InstantiationException | IllegalAccessException| IllegalArgumentException | InvocationTargetException e) {
				throw new RuntimeException("Could not create an instance of class " + typeClass, e);
			}
		}
		
		worldObject.init(session, properties);
		
		return worldObject;
	}
}
