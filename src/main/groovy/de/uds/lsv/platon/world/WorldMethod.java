package de.uds.lsv.platon.world;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks methods that should be called from dialog scripts.
 * They have to return an Iterable of Actions, their first 
 * argument is a Map of Closures (mapping string ids to
 * reactions).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface WorldMethod { }