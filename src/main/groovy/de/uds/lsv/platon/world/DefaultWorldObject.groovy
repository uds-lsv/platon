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
