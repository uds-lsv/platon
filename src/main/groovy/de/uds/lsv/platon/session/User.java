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

public class User {
	public final int id;
	public final String name;
	public final String language;
	public final String region;
	
	private boolean speaking = false;
	
	public User(int id, String name, String language, String region) {
		this.id = id;
		this.name = name;
		this.language = language;
		this.region = region;
	}
	
	public boolean isSpeaking() {
		return speaking;
	}
	
	public void setSpeaking(boolean value) {
		this.speaking = value;
	}
	
	@Override
	public String toString() {
		return String.format(
			"[User »%s« id=%d language=%s region=%s]",
			name, id, language, region
		);
	}
}
