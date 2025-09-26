package com.telcobright.statewalk.annotation;

/**
 * Defines cascade operations for related entities
 */
public enum CascadeType {
    ALL,
    PERSIST,
    MERGE,
    REMOVE,
    REFRESH,
    DETACH
}