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

package de.uds.lsv.platon.config;

import groovy.transform.CompileStatic
import java.lang.reflect.Field

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

import de.uds.lsv.platon.session.DialogEngine
import de.uds.lsv.platon.session.DialogSession
import de.uds.lsv.platon.session.User

@CompileStatic
public class Config {
	/*
	 * How dialog scripts are loaded:
	 * 1) If openDialogScript is not null, it is called.
	 *    It is expected to return a Reader.
	 * 2) Otherwise the script defined in dialogScript
	 *    is opened.
	 *  
	 * Note for Eclipse users:
	 * If you want to load dialog scripts from the class path,
	 * don't make your resources folder a source folder or
	 * Eclipse will not copy source files it recognizes.
	 */
	Closure<Reader> openDialogScript = null;
	
	
	public URL dialogScript = null;
	
	/**
	 * If using openDialogScript instead of setting
	 * dialogScript, this can be specified to define
	 * the location that is used for #include.
	 * Can be null, which will disable #include.
	 */
	public URI dialogScriptRoot = null;
	
	/**
	 * Create a DialogEngine for a user.
	 */
	public Closure<DialogEngine> createDialogEngine = {
		DialogSession dialogSession, User user ->
		if (openDialogScript != null) {
			return new DialogEngine(dialogSession, user, openDialogScript.call(), dialogScriptRoot);
		} else if (dialogScript != null) {
			return new DialogEngine(dialogSession, user, dialogScript);
		} else {
			throw new RuntimeException("No dialog script defined -- check configuration!");
		}
	}
	
	/**
	 * Wrap exceptions in DialogDispatcher so they are passed on to the game server.
	 */
	boolean wrapExceptions = true;
	
	/**
	 * Always respond to input, even if game is not active.
	 */
	boolean ignoreGameInactive = false;
	
	/**
	 * Additional definitions for the script
	 * (mainly for testing).
	 */
	Map<String,Object> scriptDefinitions = [:];
	
	public String scriptInit = null;
	
	/**
	 * Encoding for script files.
	 */
	public Charset scriptCharset = StandardCharsets.UTF_8;
	
	/**
	 * Maximum history size (i.e. all actions that occurred, not just verbal).
	 * Set to -1 to disable this limit.
	 */
	public int maxHistorySize = 100;
	
	/**
	 * Automatically make all input regular expression patterns
	 * case-insensitive.
	 */
	public boolean caseInsensitiveInputPatterns = true;
	
	public Closure serverExceptionHandler = null;
	
	/** Action run time in milliseconds after which a warning is displayed. */ 
	public long defaultActionTimeoutMillis = 5000l;
	
	/**
	 * Action run time in milliseconds after which a warning is displayed,
	 * for VerbalOutputActions.
	 */
	public long verbalActionTimeoutMillis = 25000l;
	
	/**
	 * Don't interrupt the current output when the user starts speaking.
	 */
	public boolean disableBargeIn = false;
	
	/**
	 * Set to a closure to dump the dialog script file 
	 * (passed to the closure as a String).
	 */
	public Closure dumpDialogScript = null;
	
	@Override
	public String toString() {
		Class<?> cls = getClass();
		List<Field> fields = Arrays.asList(cls.getFields());
		fields.sort {
			Field a, Field b ->
			a.getName() <=> b.getName() 
		};
		
		StringBuilder sb = new StringBuilder();
		for (Field field : fields) {
			if (field.getName().equals('__$stMC')) {
				continue;
			}
			
			sb.append(field.getName());
			sb.append(": ");
			sb.append(field.get(this));
			sb.append('\n');
		}
		
		return sb.toString();
	}
}
