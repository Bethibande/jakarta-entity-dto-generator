package com.bethibande.process.model;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.TypeName;

import javax.lang.model.element.TypeElement;
import java.util.List;

public record PersistenceUnit(
        TypeElement element,
        Property idProperty,
        List<Property> properties
) {

    public PersistenceUnit copyAsMemberOf(final Property parent) {
        return new PersistenceUnit(
                element,
                idProperty.copyWithParent(parent),
                properties.stream().map(prop -> prop.copyWithParent(parent)).toList()
        );
    }

    public String getSimpleName() {
        return element.getSimpleName().toString();
    }

    public String getPackageName() {
        return ClassName.get(element).packageName();
    }

    public String getFullyQualifiedName() {
        return element.getQualifiedName().toString();
    }

    public TypeName asTypeName() {
        return TypeName.get(element.asType());
    }

}
