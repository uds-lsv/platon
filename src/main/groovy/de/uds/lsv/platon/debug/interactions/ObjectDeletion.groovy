package de.uds.lsv.platon.debug.interactions

import groovy.transform.TupleConstructor
import groovy.transform.TypeChecked

@TypeChecked
@TupleConstructor
public class ObjectDeletion implements Interaction {
	String objectId;
}
