package com.telcobright.statewalk.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field as an entity to be persisted in the object graph
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Entity {

    /**
     * Table name for this entity
     * If not specified, derived from field name
     */
    String table() default "";

    /**
     * Relationship type
     */
    RelationType relation() default RelationType.ONE_TO_ONE;

    /**
     * Whether this entity is lazy loaded
     */
    boolean lazy() default false;

    /**
     * Cascade operations
     */
    CascadeType[] cascade() default {CascadeType.ALL};
}