package de.uds.lsv.platon.debug.interactions

import groovy.transform.TupleConstructor
import groovy.transform.TypeChecked

@TypeChecked
@TupleConstructor
public class ObjectModification implements Interaction {
	Map<String,String> properties;
}
