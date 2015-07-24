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

public class CancellableJob implements Runnable, Cancellable {
	private volatile boolean cancelled = false;
	private final Object callable;
	private final Object cancelCallable;
	
	public CancellableJob(Object callable, Object cancelCallable=null) {
		this.callable = callable;
		this.cancelCallable = cancelCallable;
	}
	
	@Override
	public void run() {
		if (!cancelled) {
			callable();
		}
	}
	
	public void call() {
		if (!cancelled) {
			callable();
		}
	}

	@Override
	public void cancel() {
		cancelled = true;
		if (cancelCallable != null) {
			cancelCallable();
		}
	}
}
