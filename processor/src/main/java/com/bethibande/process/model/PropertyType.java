package com.bethibande.process.model;

import javax.lang.model.type.TypeMirror;
import java.util.function.Supplier;

public sealed interface PropertyType permits PropertyType.EntityCollectionType, PropertyType.EntityType, PropertyType.DirectType {

    TypeMirror getType();

    final class EntityCollectionType implements PropertyType {

        private final TypeMirror root;
        private final TypeMirror entityType;
        private final Supplier<PersistenceUnit> supplier;

        public EntityCollectionType(final TypeMirror root,
                                    final TypeMirror entityType,
                                    final Supplier<PersistenceUnit> supplier) {
            this.root = root;
            this.entityType = entityType;
            this.supplier = supplier;
        }

        @Override
        public TypeMirror getType() {
            return root;
        }

        public TypeMirror getRoot() {
            return root;
        }

        public TypeMirror getEntityType() {
            return entityType;
        }

        public PersistenceUnit getTargetRef() {
            return supplier.get();
        }
    }

    final class EntityType implements PropertyType {

        private final TypeMirror type;
        private final Supplier<PersistenceUnit> supplier;

        public EntityType(final TypeMirror type, final Supplier<PersistenceUnit> supplier) {
            this.type = type;
            this.supplier = supplier;
        }

        @Override
        public TypeMirror getType() {
            return type;
        }

        public PersistenceUnit getTargetRef() {
            return supplier.get();
        }

    }

    final class DirectType implements PropertyType {

        private final TypeMirror type;

        public DirectType(final TypeMirror type) {
            this.type = type;
        }

        @Override
        public TypeMirror getType() {
            return type;
        }

    }

}
