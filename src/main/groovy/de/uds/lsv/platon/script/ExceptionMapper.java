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

import java.util.ArrayList;
import java.util.List;

import javax.script.ScriptException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.codehaus.groovy.control.messages.ExceptionMessage;
import org.codehaus.groovy.control.messages.Message;
import org.codehaus.groovy.control.messages.SimpleMessage;
import org.codehaus.groovy.control.messages.SyntaxErrorMessage;
import org.codehaus.groovy.syntax.SyntaxException;

import de.uds.lsv.platon.script.IncludeReader.CodeLocation;

public class ExceptionMapper {
	private static final Log logger = LogFactory.getLog(ExceptionMapper.class.getName());
	
	private IncludeReader includeReader;
	private ClassLoader scriptClassLoader;
	
	public ExceptionMapper(IncludeReader reader, ClassLoader scriptClassLoader) {
		this.includeReader = reader;
		this.scriptClassLoader = scriptClassLoader;
	}
	
	/**
	 * Try to find the source code location for e and
	 * wrap it in a DialogScriptException.
	 * If that's not possible, return e.
	 * 
	 * @param e
	 * @return
	 */
	public Throwable translateException(Throwable e) {
		Throwable t = tryTranslateException(e);
		if (t == null) {
			// We don't know the exception type, but maybe there's
			// something useful in the stack trace?
			CodeLocation location = getLocationFromTrace(e);
			if (location != null) {
				return wrapException(e, location);
			}
			
			logger.debug("Unknown throwable, no script location found in trace: " + e);
			return e;
		} else {
			return t;
		}
	}
	
	/**
	 * @param line
	 *   line number (0-based!)
	 * @return
	 */
	private CodeLocation translateLocation(int line) {
		if (includeReader != null) {
			return includeReader.translateLocation(line);
		} else {
			return new CodeLocation(null, line);
		}
	}
	
	/**
	 * Translate exception if type is supported.
	 * 
	 * @param e
	 * @return
	 */
	private DialogScriptException tryTranslateException(Throwable e) {
		if (e instanceof DialogScriptException) {
			return (DialogScriptException)e;
		} else if (e instanceof ScriptException) {
			return translateScriptException((ScriptException)e);
		} else if (e instanceof MultipleCompilationErrorsException) {
			return translateMultipleCompilationErrorsException((MultipleCompilationErrorsException)e);
		} else if (e instanceof SyntaxException) {
			return translateSyntaxException((SyntaxException)e);
		}
		
		return null;
	}
	
	public DialogScriptException translateScriptException(ScriptException e) {
		Throwable unpacked = unpackScriptException(e);
		
		if (unpacked != null) {
			if (unpacked instanceof ScriptException) {
				if (((ScriptException)unpacked).getLineNumber() > 0) {
					CodeLocation location = translateLocation(e.getLineNumber() - 1);
					return wrapException(e, location);
				}
			}
			
			// let's see if we have unpacked something useful...
			DialogScriptException t = tryTranslateException(unpacked);
			if (t != null) {
				return t;
			}
		}
		
		// try to guess a line number from the stack trace
		CodeLocation location = getLocationFromTrace(e);
		if (location != null) {
			return wrapException(e, location);
		}
		
		// give up
		return null;
	}
	
	/**
	 * Unpack a ScriptException, until we find
	 * either a ScriptException with a line number
	 * or a different exception type.
	 * 
	 * @param e
	 * @return
	 */
	private Throwable unpackScriptException(Throwable t) {
		for (Throwable e = t; e != null; e = e.getCause()) {
			if (e instanceof ScriptException) {
				if (((ScriptException)e).getLineNumber() > 0) {
					return e;
				}
			} else {
				return e;
			}
		}
		
		// this would mean we have a ScriptException with
		// only ScriptExceptions
		return null;
	}
	
	@SuppressWarnings("unchecked")
	public DialogScriptException translateMultipleCompilationErrorsException(MultipleCompilationErrorsException e) {
		List<DialogScriptException> exceptions = new ArrayList<>();
		
		for (Message m : (List<Message>)e.getErrorCollector().getErrors()) {
			if (m instanceof SyntaxErrorMessage) {
				exceptions.add(translateSyntaxErrorMessage((SyntaxErrorMessage)m));
			} else if (m instanceof ExceptionMessage) {
				exceptions.add(translateExceptionMessage((ExceptionMessage)m));
			} else if (m instanceof SimpleMessage) {
				exceptions.add(translateSimpleMessage((SimpleMessage)m));
			} else {
				throw new AssertionError("Unknown message type: " + m.getClass() + ": " + m);
			}
		}
		
		if (exceptions.size() == 1) {
			return exceptions.get(0);
		} else {
			return new MultiException(e, exceptions);
		}
	}
	
	public DialogScriptException translateSimpleMessage(SimpleMessage m) {
		// No idea what I'm supposed to make of this one...
		return new DialogScriptException(new RuntimeException("SimpleMessage: " + m.getMessage()));
	}
	
	public DialogScriptException translateExceptionMessage(ExceptionMessage m) {
		Throwable e = m.getCause();
		DialogScriptException dse = tryTranslateException(e);
		if (dse != null) {
			return dse;
		}
		
		// no information found -- still wrap exception in a DSE
		return new DialogScriptException(e);
	}
	
	public DialogScriptException translateSyntaxErrorMessage(SyntaxErrorMessage m) {
		return translateSyntaxException(m.getCause());
	}
	
	public DialogScriptException translateSyntaxException(SyntaxException e) {
		CodeLocation startLocation = translateLocation(e.getStartLine() - 1);
		CodeLocation endLocation = translateLocation(e.getEndLine() - 1);
		
		assert (startLocation.url == endLocation.url || startLocation.url.equals(endLocation.url));

		DialogScriptException dse = new DialogScriptException(e);
		dse.sourceUrl = startLocation.url;
		dse.startLine = startLocation.startLine + 1;
		dse.endLine = endLocation.startLine + 1;
		dse.startColumn = e.getStartColumn();
		dse.endColumn = e.getEndColumn();
		
		return dse;
	}
	
	/**
	 * True, if we think the class might be our groovy
	 * script from the groovy script engine.
	 * 
	 * @param className
	 * @return
	 */
	private boolean couldBeScriptClass(String className) {
		// The script class loader has to know the class
		Class<?> cls; 
		try {
			cls = scriptClassLoader.loadClass(className);
		}
		catch (ClassNotFoundException e) {
			return false;
		}
		
		// The script class is loaded by the script class loader,
		// so our class loader should not know it.
		try {
			this.getClass().getClassLoader().loadClass(className);
			return false;
		}
		catch (ClassNotFoundException e) {
		}
		
		return couldBeScriptClassHelper(cls);
	}
	
	/**
	 * @param cls
	 *   A class from the script class loader.
	 * @return
	 */
	private boolean couldBeScriptClassHelper(Class<?> cls) {
		// If we have a contained class, check the
		// parent class
		Class<?> encl = cls.getEnclosingClass();
		if (encl != null) {
			if (couldBeScriptClassHelper(encl)) {
				return true;
			}
		}
		
		// The class has to be a groovy.lang.Script
		if (!groovy.lang.Script.class.isAssignableFrom(cls)) {
			return false;
		}
		
		return true;
	}
	
	/**
	 * The Groovy Script Engine does not (seem to?) expose the
	 * class a script is compiled to.
	 * So all we can do is GUESS!
	 * 
	 * @param e
	 * @return
	 */
	public CodeLocation getLocationFromTrace(Throwable e) {
		for (Throwable x = e; x != null; x = x.getCause()) {
			for (StackTraceElement ste : x.getStackTrace()) {
				if (couldBeScriptClass(ste.getClassName())) {
					return translateLocation(ste.getLineNumber() - 1);
				}
			}
		}
		
		return null;
	}
	
	private static DialogScriptException wrapException(Throwable t, CodeLocation location) {
		DialogScriptException dse = new DialogScriptException(t);
		dse.sourceUrl = location.url;
		dse.startLine = location.startLine + 1;
		return dse;
	}
}
