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

public class IdleJob implements Runnable, Cancellable {
	private boolean cancelled = false;
	private final Object callable;
	
	public IdleJob(Object callable) {
		this.callable = callable;
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
	}
}
