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

package de.uds.lsv.platon.script;

import groovy.transform.TypeChecked

import java.util.Collection;
import java.util.Map.Entry
import java.util.Set;
import java.util.regex.Pattern

import javax.script.Bindings

@TypeChecked
public class ListenableBindings implements Map<String,Object>, Bindings {
	public static interface ChangeListener {
		void bindingsVariableChanged(String key, Object value);
		void bindingsVariableRemoved(Object key);
	}
	
	public static interface FallbackListener {
		Object bindingsGetFallback(String key);
	}
	
	private Map<String,Object> bindings = new HashMap<>();
	
	private List<ChangeListener> changeListeners = new ArrayList<>();
	private FallbackListener fallbackListener = null;
	
	public void addChangeListener(ChangeListener listener) {
		changeListeners.add(listener);
	}
	
	public void setFallbackListener(FallbackListener listener) {
		fallbackListener = listener;
	}
	
	private static final Pattern identifierPattern = ~/[a-zA-Z_][a-zA-Z\d_]+/;
	private static final Set identifierBlacklist = new HashSet([
		// TODO: document illegal identifiers
		"out", "err", "context"
	]);
	
	@Override
	public boolean containsKey(Object key) {
		boolean result =
			bindings.containsKey(key) ||
			(
				(key instanceof String) &&
				!identifierBlacklist.contains(key) &&
				identifierPattern.matcher(key).matches()
			);
		return result;
	}
	
	public boolean containsKeyWithoutFallback(Object key) {
		return bindings.containsKey(key);
	}
	
	@Override
	public Object get(Object key) {
		if (bindings.containsKey(key)) {
			return bindings.get(key);
		}
		
		if (fallbackListener != null && containsKey(key)) {
			return fallbackListener.bindingsGetFallback((String)key);
		}
		
		return null;
	}
	
	@Override
	public Object put(String key, Object value) {
		Object result = bindings.put(key, value);
		for (ChangeListener listener : changeListeners) {
			listener.bindingsVariableChanged(key, value);
		}
		return result;
	}

	@Override
	public void putAll(Map<? extends String, ? extends Object> map) {
		bindings.putAll(map);
		
		for (Entry<? extends String, ? extends Object> entry : map.entrySet()) {
			for (ChangeListener listener : changeListeners) {
				listener.bindingsVariableChanged(entry.getKey(), entry.getValue());
			}
		}
	}

	@Override
	public Object remove(Object key) {
		Object result = bindings.remove(key);
		for (ChangeListener listener : changeListeners) {
			listener.bindingsVariableRemoved(key);
		}
		return result;
	}
	
	public Object putAt(String key, Object value) {
		for (ChangeListener listener : changeListeners) {
			listener.bindingsVariableRemoved(key);
		}
		bindings.put(key, value);
		return value;
	}

	@Override
	public void clear() {
		bindings.clear();
	}

	@Override
	public boolean containsValue(Object value) {
		return bindings.containsValue(value);
	}

	@Override
	public Set<java.util.Map.Entry<String, Object>> entrySet() {
		return bindings.entrySet();
	}

	@Override
	public boolean isEmpty() {
		return bindings.isEmpty();
	}

	@Override
	public Set<String> keySet() {
		return bindings.keySet();
	}

	@Override
	public int size() {
		return bindings.size();
	}

	@Override
	public Collection<Object> values() {
		return bindings.values();
	}	
}
