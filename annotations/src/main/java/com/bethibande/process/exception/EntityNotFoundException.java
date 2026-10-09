package com.bethibande.process.exception;

public class EntityNotFoundException extends Exception {

    private final String fieldPath;
    private final Class<?> type;
    private final Object id;

    public EntityNotFoundException(final String fieldPath, final Class<?> type, final Object id) {
        super(String.format("Entity of type '%s' with id '%s' not found for field '%s'", type != null ? type.getName() : "unknown", id, fieldPath));
        this.fieldPath = fieldPath;
        this.type = type;
        this.id = id;
    }

    public EntityNotFoundException(final String message, final String fieldPath, final Class<?> type, final Object id) {
        super(message);
        this.fieldPath = fieldPath;
        this.type = type;
        this.id = id;
    }

    public String getFieldPath() {
        return fieldPath;
    }

    public Class<?> getType() {
        return type;
    }

    public Object getId() {
        return id;
    }

    public String fieldPath() {
        return fieldPath;
    }

    public Class<?> type() {
        return type;
    }

    public Object id() {
        return id;
    }
}
