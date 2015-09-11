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

import groovy.transform.TypeChecked

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Map.Entry

import de.uds.lsv.platon.session.DialogSession

@TypeChecked
public abstract class WorldObject implements Cloneable {
	public static final String FIELD_ID = "id";
	public static final String FIELD_TYPE = '$type';
	
	DialogSession session;

	// Don't add public here, we want Groovy to use get/set!
	String id;
	String type;
	
	public WorldObject() {
		WorldClass annotation = this.getClass().getAnnotation(WorldClass.class);
		if (annotation == null) {
			throw new RuntimeException("Missing WorldClass annotation!");
		}
		
		type = annotation.value();
	}
	
	public WorldObject(String type) {
		this.@type = type;
	}
	
	public void init(DialogSession session, Map<String,String> stringProperties) {
		id = stringProperties.get(FIELD_ID);
		if (id == null) {
			throw new IllegalArgumentException("Missing ${FIELD_ID} property!");
		}
		
		this.session = session;
		assert (!stringProperties.containsKey(FIELD_TYPE) || getType().equals(stringProperties.get(FIELD_TYPE)));
		
		modified(stringProperties);
	}
	
	public Map<String,Object> getProperties() {
		Map<String,Object> map = new HashMap<>();
		map.put(FIELD_ID, id);
		map.put(FIELD_TYPE, getType());
		
		for (Field field : this.getClass().getFields()) {
			if (field.getAnnotation(WorldField.class) != null) {
				map.put(
					field.getName(),
					getWorldField(field)
				);
			}
		}
		
		return map;
	}
	
	public Map<String,Object> getPropertiesWithInternalNames() {
		Map<String,Object> map = new HashMap<>();
		map.put("id", id);
		map.put("type", getType());
		
		for (Field field : this.getClass().getFields()) {
			if (field.getAnnotation(WorldField.class) != null) {
				map.put(
					field.getName(),
					getWorldField(field)
				);
			}
		}
		
		return map;
	}
	
	private Object getWorldField(Field field) {
		if (Modifier.isPublic(field.getModifiers())) {
			// Accessible field
			if (Byte.TYPE.equals(field.getType())) {
				return Byte.valueOf(field.getByte(this));
			} else if (Short.TYPE.equals(field.getType())) {
				return Short.valueOf(field.getShort(this));
			} else if (Integer.TYPE.equals(field.getType())) {
				return Integer.valueOf(field.getInt(this));
			} else if (Long.TYPE.equals(field.getType())) {
				return Long.valueOf(field.getLong(this));
			} else if (Float.TYPE.equals(field.getType())) {
				return Float.valueOf(field.getFloat(this));
			} else if (Double.TYPE.equals(field.getType())) {
				return Double.valueOf(field.getDouble(this));
			} else if (Boolean.TYPE.equals(field.getType())) {
				return Boolean.valueOf(field.getBoolean(this));
			} else if (Character.TYPE.equals(field.getType())) {
				return Character.valueOf(field.getChar(this));
			} else {
				return field.get(this);
			}
		
		} else {
			// Not accessible -- try setX (which should have been generated
			// by Groovy for fields without visibility modifiers)
			String methodName = field.getName();
			if (Character.isLowerCase(methodName.charAt(0))) {
				methodName = "" + Character.toUpperCase(methodName.charAt(0)) + methodName.substring(1);
			}
			methodName = "get" + methodName;
			
			Method method = this.getClass().getMethod(methodName);
			return method.invoke(this);
		}
	}
	
	private String getWorldFieldAsString(Field field) {
		if (Modifier.isPublic(field.getModifiers())) {
			// Accessible field
			if (Byte.TYPE.equals(field.getType())) {
				return Byte.toString(field.getByte(this));
			} else if (Short.TYPE.equals(field.getType())) {
				return Short.toString(field.getShort(this));
			} else if (Integer.TYPE.equals(field.getType())) {
				return Integer.toString(field.getInt(this));
			} else if (Long.TYPE.equals(field.getType())) {
				return Long.toString(field.getLong(this));
			} else if (Float.TYPE.equals(field.getType())) {
				return Float.toString(field.getFloat(this));
			} else if (Double.TYPE.equals(field.getType())) {
				return Double.toString(field.getDouble(this));
			} else if (Boolean.TYPE.equals(field.getType())) {
				return Boolean.toString(field.getBoolean(this));
			} else if (Character.TYPE.equals(field.getType())) {
				return Character.toString(field.getChar(this));
			} else {
				return field.get(this).toString();
			}
		
		} else {
			// Not accessible -- try setX (which should have been generated
			// by Groovy for fields without visibility modifiers)
			String methodName = field.getName();
			if (Character.isLowerCase(methodName.charAt(0))) {
				methodName = "" + Character.toUpperCase(methodName.charAt(0)) + methodName.substring(1);
			}
			methodName = "get" + methodName;
			
			Method method = this.getClass().getMethod(methodName);
			return method.invoke(this).toString();
		}
	}
	
	private void setWorldField(Field field, String value) {
		if (field.getAnnotation(WorldField.class) == null) {
			throw new IllegalAccessException("You can only modify fields annotated with @WorldField.");
		}
		
		if (Modifier.isPublic(field.getModifiers())) {
			// Accessible field
			if (Byte.TYPE.equals(field.getType())) {
				field.setByte(this, Byte.parseByte(value));
			} else if (Short.TYPE.equals(field.getType())) {
				field.setShort(this, Short.parseShort(value));
			} else if (Integer.TYPE.equals(field.getType())) {
				field.setInt(this, Integer.parseInt(value));
			} else if (Long.TYPE.equals(field.getType())) {
				field.setLong(this, Long.parseLong(value));
			} else if (Float.TYPE.equals(field.getType())) {
				field.setFloat(this, Float.parseFloat(value));
			} else if (Double.TYPE.equals(field.getType())) {
				field.setDouble(this, Double.parseDouble(value));
			} else if (Boolean.TYPE.equals(field.getType())) {
				field.setBoolean(this, Boolean.parseBoolean(value));
			} else if (Character.TYPE.equals(field.getType())) {
				if (value.length() != 1) {
					throw new RuntimeException("Invalid char value: »" + value + "«");
				}
				field.setChar(this, value.charAt(0));
			} else if (String.class.equals(field.getType())) {
				field.set(this, value);
			} else {
				throw new RuntimeException("Unsupported type in field " + field.getName() + ": " + field.getType());
			}
		
		} else {
			// Not accessible -- try setX (which should have been generated
			// by Groovy for fields without visibility modifiers)
			String methodName = field.getName();
			if (Character.isLowerCase(methodName.charAt(0))) {
				methodName = "" + Character.toUpperCase(methodName.charAt(0)) + methodName.substring(1);
			}
			methodName = "set" + methodName;
			
			if (Byte.TYPE.equals(field.getType())) {
				Method method = this.getClass().getMethod(methodName, Byte.TYPE);
				method.invoke(this, Byte.parseByte(value));
			} else if (Short.TYPE.equals(field.getType())) {
				Method method = this.getClass().getMethod(methodName, Short.TYPE);
				method.invoke(this, Short.parseShort(value));
			} else if (Integer.TYPE.equals(field.getType())) {
				Method method = this.getClass().getMethod(methodName, Integer.TYPE);
				method.invoke(this, Integer.parseInt(value));
			} else if (Long.TYPE.equals(field.getType())) {
				Method method = this.getClass().getMethod(methodName, Long.TYPE);
				method.invoke(this, Long.parseLong(value));
			} else if (Float.TYPE.equals(field.getType())) {
				Method method = this.getClass().getMethod(methodName, Float.TYPE);
				method.invoke(this, Float.parseFloat(value));
			} else if (Double.TYPE.equals(field.getType())) {
				Method method = this.getClass().getMethod(methodName, Double.TYPE);
				method.invoke(this, Double.parseDouble(value));
			} else if (Boolean.TYPE.equals(field.getType())) {
				Method method = this.getClass().getMethod(methodName, Boolean.TYPE);
				method.invoke(this, Boolean.parseBoolean(value));
			} else if (Character.TYPE.equals(field.getType())) {
				Method method = this.getClass().getMethod(methodName, Character.TYPE);
				if (value.length() != 1) {
					throw new RuntimeException("Invalid char value: »" + value + "«");
				}
				method.invoke(this, value.charAt(0));
			} else if (String.class.equals(field.getType())) {
				Method method = this.getClass().getMethod(methodName, String.class);
				method.invoke(this, value);
			} else {
				throw new RuntimeException("Unsupported type in field " + field.getName() + ": " + field.getType());
			}
		}
	}
	
	public boolean matchesProperties(Map<String,String> stringProperties) {
		for (Entry<String,String> entry : stringProperties.entrySet()) {
			if (FIELD_ID.equals(entry.getKey())) {
				if (!id.equals(entry.getValue())) {
					return false;
				}
			} else if (FIELD_TYPE.equals(entry.getKey())) {
				if (!type.equals(entry.getValue())) {
					return false;
				}
			} else {
				Field field;
				try {
					field = this.getClass().getField(entry.getKey());
				}
				catch (NoSuchFieldException e) {
					return false;
				}
				
				if (!entry.getValue().equals(getWorldFieldAsString(field))) {
					return false;
				}
			}
		}
		
		return true;
	}
	
	/**
	 * True iff the field is part of the object state
	 * transferred between dialog engine end world server.
	 * @param name
	 * @return
	 */
	public boolean isWorldField(String name) {
		Field field = getClass().getField(name);
		if (field == null) {
			return false;
		}
		
		WorldField annotation = field.getAnnotation(WorldField.class);
		return (annotation != null);
	}
	
	/**
	 * Returns true iff a (world) field should be directly writable/assignable
	 * by scripts. WorldObjectWrapper then generates ModifyObjectActions
	 * accordingly for these properties.
	 * 
	 * @param property
	 * @return
	 */
	public boolean isWritableField(String name) {
		Field field = getClass().getField(name);
		if (field == null) {
			return false;
		}
		
		WorldField annotation = field.getAnnotation(WorldField.class);
		if (annotation == null) {
			return false;
		}
		
		return annotation.writable();
	}
	
	public void modified(Map<String,String> stringProperties) {
		assert id.equals(stringProperties.get(FIELD_ID));
		
		for (Entry<String,String> entry : stringProperties.entrySet()) {
			if (FIELD_ID.equals(entry.getKey()) || FIELD_TYPE.equals(entry.getKey())) {
				// ignore special fields
				continue;
			}
			
			setWorldField(
				this.getClass().getField(entry.getKey()),
				entry.getValue()
			);
		}
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(getClass().getSimpleName());
		sb.append("(\n");
		
		for (Entry<String,Object> entry : getProperties().entrySet()) {
			sb.append('\t');
			sb.append(entry.getKey());
			sb.append('=');
			sb.append(entry.getValue());
			sb.append(", ");
		}
		
		sb.append(')');
		return sb.toString();
	}
	
	public String getId() {
		return id;
	}
	
	public String getType() {
		return type;
	}
}
