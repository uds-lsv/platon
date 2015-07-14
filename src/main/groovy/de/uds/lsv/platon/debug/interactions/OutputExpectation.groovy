package de.uds.lsv.platon.debug.interactions

import groovy.transform.TypeChecked

import java.util.regex.Pattern

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

import de.uds.lsv.platon.debug.ValidatingDialogClient.OutputEvent
import de.uds.lsv.platon.script.Wildcard

@TypeChecked
public class OutputExpectation implements Interaction {
	private static final Log logger = LogFactory.getLog(OutputExpectation.class.getName());
	
	def user = Wildcard.INSTANCE;
	def ioType = Wildcard.INSTANCE;

	private Object outputValue = null;
	private Pattern outputPattern = null;
	private Closure<Boolean> outputClosure = null;

	public OutputExpectation(user=Wildcard.INSTANCE, ioType=Wildcard.INSTANCE) {
		this.user = user;
		this.ioType = ioType;
	}

	public void setExpectedOutput(String value) {
		this.outputValue = value;
	}

	public void setExpectedOutput(Pattern pattern) {
		this.outputPattern = pattern;
	}

	public void setExpectedOutput(Closure closure) {
		this.outputClosure = closure;
	}

	boolean matches(OutputEvent output) {
		if (user != Wildcard.INSTANCE && output.user != user) {
			logger.debug("User not matched. Expected: ${user} Found: ${output.user}");
			return false;
		}

		if (ioType != Wildcard.INSTANCE && output.ioType != ioType) {
			logger.debug("IO type not matched. Expected: ${ioType} Found: ${output.ioType}");
			return false;
		}

		if (outputValue != null && outputValue.equals(output.text)) {
			logger.debug("Output matched (value): ${outputValue}");
			return true;
		}

		if (
		outputPattern != null &&
		(output.text instanceof CharSequence) &&
		outputPattern.matcher(output.text).matches()
		) {
			logger.debug("Output matched (regex): ${outputPattern}");
			return true;
		}

		if (outputClosure != null && outputClosure.call(output.text)) {
			logger.debug("Output matched (closure): ${outputClosure}");
			return true;
		}

		logger.debug("Output text not matched.");
		return false;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("Expectation: Output: ");
		if (outputValue != null) {
			sb.append("»${outputValue}«");
		}
		if (outputPattern != null) {
			sb.append("/${outputPattern}/");
		}
		if (outputClosure != null) {
			sb.append("(closure)")
		}
		return sb.toString();
	}
}