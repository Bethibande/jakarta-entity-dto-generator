package com.bethibande.process.model;

import javax.lang.model.type.TypeMirror;
import java.util.function.Supplier;

public sealed interface PropertyType permits PropertyType.EntityType, PropertyType.DirectType {

    TypeMirror getType();

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
