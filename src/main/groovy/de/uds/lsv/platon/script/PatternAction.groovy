package de.uds.lsv.platon.script

import groovy.transform.TypeChecked

import java.util.regex.Matcher
import java.util.regex.Pattern

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

@TypeChecked
public class PatternAction implements Comparable<PatternAction> {
	private static final Log logger = LogFactory.getLog(PatternAction.class.getName());
	
	public final Object pattern;
	public final AgentCallable action;

	/** set to Double.POSITIVE_INFINITY for an instant match */
	public final double priority;

	public PatternAction(Object pattern, AgentCallable action, double priority) {
		this.pattern = pattern;
		this.action = action;
		this.priority = priority;
	}

	/**
	 * @param input
	 * @return
	 *   != null if input is accepted.
	 */
	public Object matches(Object input, Object details=null) {
		// Wildcard
		if (Wildcard.INSTANCE.is(pattern)) {
			logger.debug(String.format("Accepted »%s«: wildcard", input));
			return Wildcard.INSTANCE;
		}
		
		// Regex Pattern
		if (pattern instanceof Pattern) {
			if (!(input instanceof CharSequence)) {
				return null;
			}
			
			Matcher matcher = ((Pattern)pattern).matcher((CharSequence)input);
			if (matcher.matches()) {
				logger.debug(String.format("Accepted »%s«: regex %s", input, pattern));
				return matcher;
			}
			
			return null;
		}
		
		// Closure
		if (pattern instanceof Closure) {
			Closure filter = (Closure)pattern;

			Class[] parameterTypes = filter.getParameterTypes();
			if (parameterTypes.length == 0) {
				return null;
			}
			if (!parameterTypes[0].isAssignableFrom(input.getClass())) {
				return null;
			}
			
			def result;
			if (filter.maximumNumberOfParameters == 1) {
				result = filter(input);
			} else {
				result = filter(input, details);
			}
			
			if (result != null && result) {
				logger.debug(String.format("Accepted »%s«: closure %s", input, filter));
				return result;
			}
			
			return null;
		}

		// Class
		if (pattern instanceof Class) {
			Class cls = (Class)pattern;
			
			if (cls.isAssignableFrom(input.getClass())) {
				logger.debug(String.format("Accepted »%s«: class %s", input, cls));
				return cls;
			}
			
			return null;
		}
		
		// .equals
		if (pattern.equals(input)) {
			logger.debug(String.format("Accepted »%s«: equals %s", input, pattern));
			return pattern;
		}
		
		return null;
	}

	@Override
	public int compareTo(PatternAction other) {
		return Double.compare(
			this.priority,
			other.priority
		);
	}
		
	@Override
	public String toString() {
		return String.format(
			"PatternAction(%s, %s)",
			pattern,
			action
		);
	}
}
