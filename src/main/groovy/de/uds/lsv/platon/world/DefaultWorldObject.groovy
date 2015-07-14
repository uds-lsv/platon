package de.uds.lsv.platon.world

/**
 * A fallback world object class that can't be modified
 * by scripts.
 */
public class DefaultWorldObject extends WorldObject {
	private final Map<String,String> properties = new HashMap<String,String>();
	
	public DefaultWorldObject() {
		this(null);
	}
	
	public DefaultWorldObject(String type) {
		super(type ?: DefaultWorldObject.class.getName());
	}
	
	@Override
	public Object getProperty(String property) {
		if ("type".equals(property)) {
			return this.getType();
		}
		if ("id".equals(property)) {
			return this.getId();
		}
		
		if (!this.@properties.containsKey(property)) {
			throw new MissingPropertyException("No such property: " + property);
		}
		
		return this.@properties.get(property);
	}
	
	@Override
	public Map<String,Object> getProperties() {
		return (Map)this.@properties;
	}
	
	@Override
	public boolean matchesProperties(Map<String,String> stringProperties) {
		return stringProperties.every({ this.@properties[it.key] == it.value })
	}
	
	@Override
	public void modified(Map<String,String> stringProperties) {
		this.@properties.putAll(stringProperties);
	}
	
	@Override
	public boolean isWorldField(String name) {
		return this.@properties.containsKey(name);
	}
	
	@Override
	public boolean isWritableField(String name) {
		return this.@properties.containsKey(name);
	}
	
	@Override
	public String toString() {
		return "DefaultWorldObject(" + getType() + ")";
	}
}
