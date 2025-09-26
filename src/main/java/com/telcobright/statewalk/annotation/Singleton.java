package com.telcobright.statewalk.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field as a singleton entity in the object graph.
 * Only one instance of this entity will exist across the entire graph.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Singleton {
    /**
     * Key to identify this singleton instance
     * If not specified, uses the field type as key
     */
    String key() default "";
}