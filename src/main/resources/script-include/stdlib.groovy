@groovy.transform.TypeChecked
public class Stdlib {
	private static Set<String> onceIdsAlreadyUsed = new HashSet<>();
	
	private final de.uds.lsv.platon.script.ScriptAdapter scriptAdapter;
	 
	public Stdlib(de.uds.lsv.platon.script.ScriptAdapter scriptAdapter) {
		this.scriptAdapter = scriptAdapter;
	}
	
	private static boolean classResolvable(String className) {
		try {
			Class.forName(className);
			return true;
		}
		catch (ClassNotFoundException e) {
			return false;
		}
	}
	
	private static List<StackTraceElement> filterStackTrace(StackTraceElement[] trace) {
		List<StackTraceElement> filtered = new ArrayList<>();
		for (StackTraceElement element : trace) {
			if (
				classResolvable(element.getClassName()) &&
				!element.getClassName().startsWith("org.codehaus.groovy.runtime.callsite.") &&
				!element.getClassName().equals('java_util_concurrent_Callable$call')
			) {
				filtered.add(element);
			}
		}
		return filtered;
	}

	/**
	 * Return some value when the method is called the first time,
	 * and another value for all following calls.
	 * 
	 * Example:
	 * tell user, "important message!", priority: Stdlib.once("high", "normal")
	 * 
	 * Note:
	 * Unless you use the id argument, the once call is identified by the first
	 * and then arguments, the current thread, and the stack trace, which contains
	 * classes, methods, and line numbers -- but no character positions within a 
	 * line. This means that we can not distinguish between two identical once
	 * calls on the same line.
	 * 
	 * @param first
	 *   the value to be returned for the first call
	 * @param then
	 *   the value to be returned for subsequent calls
	 *   (default: null)
	 * @param id
	 *   manually set the identifier used to determine which calls
	 *   belong together (thread local).
	 * @return
	 */
	public static def once(first, then=null, String id=null) {
		if (id == null) {
			id = String.format(
				"%d\"auto\"%s\"%s\"%s",
				Thread.currentThread().getId(),
				groovy.json.StringEscapeUtils.escapeJava(first == null ? "⟨null⟩" : first.toString()),
				groovy.json.StringEscapeUtils.escapeJava(then == null ? "⟨null⟩" : then.toString()),
				filterStackTrace(Thread.currentThread().getStackTrace()).toString()
			);
		} else {
			id = "" + Thread.currentThread().getId() + "\"manual\"" + id;
		}
		
		if (onceIdsAlreadyUsed.contains(id)) {
			return then;
		} else {
			onceIdsAlreadyUsed.add(id);
			return first;
		}
	}
	
	/**
	 * Run a closure on each world objects of a collection,
	 * but wait for an action to complete before starting the
	 * next one.
	 * The closure has to return a Then object (returned by
	 * all world methods, tell, ...)
	 * 
	 * Examples:
	 * Stdlib.forEachObjectWait(objects({ it.type == Switch.TYPE })) {
	 *     it.switchOn();
	 * }
	 * 
	 * Stdlib.forEachObjectWait(objects({ it.type == Switch.TYPE && it.isOn() })) {
	 *     tell user, "Switch ${it} is on.";
	 * }
	 * 
	 * @param objects
	 * @param closure
	 */
	public static void forEachObjectWait(
		Collection<de.uds.lsv.platon.world.WorldObject> objects,
		Closure closure
	) {
		Iterator<de.uds.lsv.platon.world.WorldObject> iterator = objects.iterator();
		
		Closure f;
		f = { ->
			if (iterator.hasNext()) {
				def then = closure(iterator.next());
				if (then == null) {
					return f();
				}
				
				if (!(then instanceof de.uds.lsv.platon.script.Then)) {
					throw new IllegalArgumentException("Closure has to return null or a Then object!");
				}
				
				((de.uds.lsv.platon.script.Then)then).then(f);
			}
		}
		f();
	}
	
	private static final Object noDefaultValue = new Object();
	
	/**
	 * Select the correct translation for the user's language from a map of
	 * options.
	 * 
	 * @param options
	 *   a Map language code -> translation
	 */
	public Object selectTranslation(Map<String,Object> options, Object defaultValue=noDefaultValue) {
		if (!options.containsKey(scriptAdapter.language)) {
			if (!noDefaultValue.is(defaultValue)) {
				return defaultValue;
			} else {
				throw new RuntimeException("No option for language ${scriptAdapter.language}!");
			}
		}
		
		return options.get(scriptAdapter.language);
	}
	
	/**
	 * Wait for input.
	 * If no input is registered during the timeout, run elseAction.
	 * If an input is made but it does not match the patterns, run elseAction.
	 * If a matching input is made, run action.
	 * 
	 * @param timeout
	 * @param action
	 * @param elseAction
	 *   If no elseAction is specified or elseAction is null, action is used instead.
	 */
	public void waitForInput(
		languagePatterns,
		groovy.time.TimeDuration timeout,
		Closure action,
		Closure elseAction=null
	) {
		if (elseAction == null) {
			elseAction = action;
		}
		
		def priorityInputAction = scriptAdapter.scriptBindings.input(
			languagePatterns,
			action,
			elseAction
		);
	
		scriptAdapter.scriptBindings.idle(timeout) {
			((de.uds.lsv.platon.script.PriorityInputAction)priorityInputAction).cancel();
			elseAction.call();
		}
	}	
}

stdlib = new Stdlib(scriptAdapter);