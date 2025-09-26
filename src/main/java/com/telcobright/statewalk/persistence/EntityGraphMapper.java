package com.telcobright.statewalk.persistence;

import com.telcobright.statewalk.annotation.*;
import com.telcobright.statemachine.StateMachineContextEntity;

import java.lang.invoke.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Maps entity object graphs and pre-compiles access methods at initialization time.
 * Avoids reflection in hot path by using MethodHandles and lambdas.
 */
public class EntityGraphMapper {

    private final Map<Class<?>, EntityMetadata> entityMetadataMap = new ConcurrentHashMap<>();
    private final Map<String, Object> singletonInstances = new ConcurrentHashMap<>();
    private final Map<Class<?>, List<FieldAccessor>> fieldAccessors = new ConcurrentHashMap<>();

    // MethodHandles for fast field access
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    /**
     * Metadata for an entity field
     */
    public static class EntityMetadata {
        public String fieldName;
        public Class<?> entityClass;
        public String tableName;
        public RelationType relationType;
        public boolean isSingleton;
        public boolean isLazy;
        public CascadeType[] cascadeTypes;
        public Function<Object, Object> getter;
        public BiConsumer<Object, Object> setter;
        public Class<?> collectionElementType; // For collections
    }

    /**
     * Fast field accessor using MethodHandles
     */
    public static class FieldAccessor {
        public final Field field;
        public final MethodHandle getter;
        public final MethodHandle setter;
        public final EntityMetadata metadata;

        public FieldAccessor(Field field, MethodHandle getter, MethodHandle setter, EntityMetadata metadata) {
            this.field = field;
            this.getter = getter;
            this.setter = setter;
            this.metadata = metadata;
        }
    }

    /**
     * Analyze the object graph starting from the root context class.
     * This is called at initialization time, not runtime.
     */
    public void analyzeGraph(Class<? extends StateMachineContextEntity<?>> rootClass) {
        System.out.println("[EntityGraphMapper] Analyzing object graph for: " + rootClass.getName());

        // Clear previous analysis
        entityMetadataMap.clear();
        fieldAccessors.clear();

        // Recursively analyze the class and its fields
        analyzeClass(rootClass, new HashSet<>());

        System.out.println("[EntityGraphMapper] Analysis complete. Found " + entityMetadataMap.size() + " entity types");
    }

    /**
     * Recursively analyze a class and its entity fields
     */
    private void analyzeClass(Class<?> clazz, Set<Class<?>> visited) {
        if (visited.contains(clazz) || clazz.isPrimitive() || clazz.getName().startsWith("java.")) {
            return;
        }
        visited.add(clazz);

        List<FieldAccessor> accessors = new ArrayList<>();

        // Process all declared fields
        for (Field field : clazz.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
                continue;
            }

            // Check for entity annotation
            Entity entityAnnotation = field.getAnnotation(Entity.class);
            Singleton singletonAnnotation = field.getAnnotation(Singleton.class);

            if (entityAnnotation != null || singletonAnnotation != null) {
                EntityMetadata metadata = createEntityMetadata(field, entityAnnotation, singletonAnnotation);
                entityMetadataMap.put(field.getType(), metadata);

                // Create fast field accessor
                FieldAccessor accessor = createFieldAccessor(field, metadata);
                accessors.add(accessor);

                // Recursively analyze the entity type
                if (!Collection.class.isAssignableFrom(field.getType())) {
                    analyzeClass(field.getType(), visited);
                } else if (metadata.collectionElementType != null) {
                    analyzeClass(metadata.collectionElementType, visited);
                }
            }
        }

        if (!accessors.isEmpty()) {
            fieldAccessors.put(clazz, accessors);
        }
    }

    /**
     * Create entity metadata from field annotations
     */
    private EntityMetadata createEntityMetadata(Field field, Entity entityAnnotation, Singleton singletonAnnotation) {
        EntityMetadata metadata = new EntityMetadata();
        metadata.fieldName = field.getName();
        metadata.entityClass = field.getType();
        metadata.isSingleton = singletonAnnotation != null;

        if (entityAnnotation != null) {
            metadata.tableName = entityAnnotation.table().isEmpty() ?
                deriveTableName(field.getName()) : entityAnnotation.table();
            metadata.relationType = entityAnnotation.relation();
            metadata.isLazy = entityAnnotation.lazy();
            metadata.cascadeTypes = entityAnnotation.cascade();
        } else {
            // Singleton without Entity annotation
            metadata.tableName = deriveTableName(field.getName());
            metadata.relationType = RelationType.ONE_TO_ONE;
            metadata.cascadeTypes = new CascadeType[]{CascadeType.ALL};
        }

        // Handle collection types
        if (Collection.class.isAssignableFrom(field.getType())) {
            Type genericType = field.getGenericType();
            if (genericType instanceof ParameterizedType) {
                Type[] typeArgs = ((ParameterizedType) genericType).getActualTypeArguments();
                if (typeArgs.length > 0 && typeArgs[0] instanceof Class) {
                    metadata.collectionElementType = (Class<?>) typeArgs[0];
                }
            }
        }

        return metadata;
    }

    /**
     * Create fast field accessor using MethodHandles
     */
    private FieldAccessor createFieldAccessor(Field field, EntityMetadata metadata) {
        try {
            field.setAccessible(true);

            // Create MethodHandles for direct field access
            MethodHandle getter = LOOKUP.unreflectGetter(field);
            MethodHandle setter = LOOKUP.unreflectSetter(field);

            // Convert to lambdas for easier use
            metadata.getter = createGetterLambda(getter);
            metadata.setter = createSetterLambda(setter);

            return new FieldAccessor(field, getter, setter, metadata);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to create field accessor for: " + field, e);
        }
    }

    /**
     * Create getter lambda from MethodHandle
     */
    private Function<Object, Object> createGetterLambda(MethodHandle getter) {
        return obj -> {
            try {
                return getter.invoke(obj);
            } catch (Throwable e) {
                throw new RuntimeException("Failed to get field value", e);
            }
        };
    }

    /**
     * Create setter lambda from MethodHandle
     */
    private BiConsumer<Object, Object> createSetterLambda(MethodHandle setter) {
        return (obj, value) -> {
            try {
                setter.invoke(obj, value);
            } catch (Throwable e) {
                throw new RuntimeException("Failed to set field value", e);
            }
        };
    }

    /**
     * Derive table name from field name
     */
    private String deriveTableName(String fieldName) {
        // Convert camelCase to snake_case
        return fieldName.replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase() + "s";
    }

    /**
     * Compile persistence paths for optimized access
     * This pre-generates all necessary SQL and access patterns
     */
    public void compilePersistencePaths() {
        System.out.println("[EntityGraphMapper] Compiling persistence paths for " + entityMetadataMap.size() + " entities");

        // This would generate prepared statements, indexes, etc.
        // For now, we just validate the structure
        for (Map.Entry<Class<?>, EntityMetadata> entry : entityMetadataMap.entrySet()) {
            EntityMetadata metadata = entry.getValue();
            System.out.println("  - Entity: " + metadata.entityClass.getSimpleName() +
                              " -> Table: " + metadata.tableName +
                              " (Singleton: " + metadata.isSingleton + ")");
        }
    }

    /**
     * Persist an object graph using pre-compiled accessors
     */
    public void persistGraph(Object rootContext, SplitVerseGraphAdapter adapter) {
        if (rootContext == null) {
            return;
        }

        Map<Object, String> processedEntities = new IdentityHashMap<>();
        persistEntity(rootContext, rootContext.getClass(), adapter, processedEntities);
    }

    /**
     * Recursively persist an entity and its relationships
     */
    private void persistEntity(Object entity, Class<?> entityClass, SplitVerseGraphAdapter adapter,
                              Map<Object, String> processedEntities) {
        if (entity == null || processedEntities.containsKey(entity)) {
            return;
        }

        // Mark as processed
        String entityId = UUID.randomUUID().toString();
        processedEntities.put(entity, entityId);

        // Get field accessors for this entity class
        List<FieldAccessor> accessors = fieldAccessors.get(entityClass);
        if (accessors != null) {
            for (FieldAccessor accessor : accessors) {
                Object fieldValue = accessor.metadata.getter.apply(entity);

                if (fieldValue != null) {
                    if (accessor.metadata.isSingleton) {
                        // Handle singleton instance
                        fieldValue = getOrCreateSingleton(fieldValue, accessor.metadata);
                    }

                    // Handle different relationship types
                    if (accessor.metadata.relationType == RelationType.ONE_TO_MANY && fieldValue instanceof Collection) {
                        for (Object item : (Collection<?>) fieldValue) {
                            persistEntity(item, item.getClass(), adapter, processedEntities);
                        }
                    } else {
                        persistEntity(fieldValue, fieldValue.getClass(), adapter, processedEntities);
                    }
                }
            }
        }

        // Persist the entity itself
        adapter.persistEntity(entityClass, entity);
    }

    /**
     * Get or create a singleton instance
     */
    private Object getOrCreateSingleton(Object instance, EntityMetadata metadata) {
        String key = metadata.entityClass.getName();
        return singletonInstances.computeIfAbsent(key, k -> instance);
    }

    /**
     * Load an object graph from persistence
     */
    public <T> T loadGraph(String id, Class<T> rootClass, SplitVerseGraphAdapter adapter) {
        Map<String, Object> loadedEntities = new HashMap<>();
        return loadEntity(id, rootClass, adapter, loadedEntities);
    }

    /**
     * Recursively load an entity and its relationships
     */
    @SuppressWarnings("unchecked")
    private <T> T loadEntity(String id, Class<T> entityClass, SplitVerseGraphAdapter adapter,
                            Map<String, Object> loadedEntities) {
        String cacheKey = entityClass.getName() + ":" + id;

        // Check if already loaded
        if (loadedEntities.containsKey(cacheKey)) {
            return (T) loadedEntities.get(cacheKey);
        }

        // Load from adapter
        T entity = adapter.loadEntity(id, entityClass);
        if (entity == null) {
            return null;
        }

        loadedEntities.put(cacheKey, entity);

        // Load relationships
        List<FieldAccessor> accessors = fieldAccessors.get(entityClass);
        if (accessors != null) {
            for (FieldAccessor accessor : accessors) {
                if (!accessor.metadata.isLazy) {
                    // Load related entities eagerly
                    loadRelatedEntities(entity, accessor, adapter, loadedEntities);
                }
            }
        }

        return entity;
    }

    /**
     * Load related entities for a field
     */
    private void loadRelatedEntities(Object entity, FieldAccessor accessor, SplitVerseGraphAdapter adapter,
                                    Map<String, Object> loadedEntities) {
        // Implementation would load related entities based on relationship type
        // This is simplified for brevity
    }

    /**
     * Get all entity metadata
     */
    public Collection<EntityMetadata> getAllMetadata() {
        return entityMetadataMap.values();
    }

    /**
     * Get metadata for a specific class
     */
    public EntityMetadata getMetadata(Class<?> clazz) {
        return entityMetadataMap.get(clazz);
    }

    /**
     * Get field accessors for a class
     */
    public List<FieldAccessor> getAccessors(Class<?> clazz) {
        return fieldAccessors.getOrDefault(clazz, Collections.emptyList());
    }

    /**
     * Clear singleton instances (useful for testing)
     */
    public void clearSingletons() {
        singletonInstances.clear();
    }
}