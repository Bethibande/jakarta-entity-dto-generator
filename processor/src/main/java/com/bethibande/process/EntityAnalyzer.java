package com.bethibande.process;

import com.palantir.javapoet.TypeName;
import com.bethibande.process.model.Accessor;
import com.bethibande.process.model.PersistenceUnit;
import com.bethibande.process.model.Property;
import com.bethibande.process.model.PropertyType;
import jakarta.persistence.*;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.lang.annotation.Annotation;
import java.util.*;
import java.util.function.Predicate;

public class EntityAnalyzer {

    public static final Set<Class<? extends Annotation>> ENTITY_ANNOTATIONS = Set.of(
            Entity.class
    );

    public static final Set<Class<? extends Annotation>> MAPPING_ANNOTATIONS = Set.of(
            Id.class,
            Basic.class,
            EmbeddedId.class,
            OneToOne.class,
            OneToMany.class,
            ManyToMany.class,
            ManyToOne.class,
            Column.class,
            JoinColumn.class,
            Embedded.class
    );

    protected final ProcessingEnvironment environment;

    protected final Map<TypeElement, PersistenceUnit> persistenceUnits = new HashMap<>();

    public EntityAnalyzer(final ProcessingEnvironment environment) {
        this.environment = environment;
    }

    protected boolean isEntity(final Element element) {
        for (final Class<? extends Annotation> entityAnnotation : ENTITY_ANNOTATIONS) {
            if (element.getAnnotation(entityAnnotation) != null) return true;
        }
        return false;
    }

    protected PropertyType toPropertyType(final TypeMirror type) {
        final Element typeSource = resolve(type);
        if (typeSource instanceof TypeElement typeElement && isEntity(typeElement)) {
            return new PropertyType.EntityType(type, () -> this.analyze(typeElement));
        }
        return new PropertyType.DirectType(type);
    }

    protected TypeElement resolve(final TypeMirror type) {
        return environment.getElementUtils().getTypeElement(TypeName.get(type).withoutAnnotations().toString());
    }

    protected Property toProperty(final VariableElement element, final Property parent) {
        final String name = element.getSimpleName().toString();
        final Accessor accessor = new Accessor.FieldAccessor(element);
        final PropertyType type = toPropertyType(element.asType());

        return new Property(
                name,
                accessor,
                type,
                parent
        );
    }

    protected AccessType toAccessType(final Element element) {
        if (element instanceof VariableElement) return AccessType.FIELD;
        return AccessType.PROPERTY;
    }

    protected String extractGetterName(final ExecutableElement element) {
        final String name = element.getSimpleName().toString();
        if (name.startsWith("get")) return StringUtil.firstCharacterLowerCase(name.substring(3));
        if (name.startsWith("is")) return StringUtil.firstCharacterLowerCase(name.substring(2));
        return name;
    }

    protected String extractSetterName(final ExecutableElement element) {
        final String name = element.getSimpleName().toString();
        if (name.startsWith("set")) return StringUtil.firstCharacterLowerCase(name.substring(3));
        return name;
    }

    protected boolean isGetter(final ExecutableElement element) {
        if (!element.getParameters().isEmpty()) return false;
        final TypeMirror returnType = element.getReturnType();
        return returnType != null && returnType.getKind() != TypeKind.VOID;
    }

    protected boolean isSetter(final ExecutableElement element) {
        return element.getParameters().size() == 1;
    }

    protected boolean filter(final Element element, final AccessType accessType) {
        if (accessType == AccessType.FIELD && element.getKind() != ElementKind.FIELD) return false;
        if (accessType == AccessType.PROPERTY && element.getKind() != ElementKind.METHOD) return false;
        if (accessType == AccessType.PROPERTY) {
            final ExecutableElement executableElement = (ExecutableElement) element;
            if (!isGetter(executableElement) && !isSetter(executableElement)) return false;
        }

        return !element.getModifiers().contains(Modifier.STATIC)
                && element.getAnnotation(Transient.class) == null;
    }

    protected AccessType getAccessorType(final TypeElement element) {
        if (element.getAnnotation(Access.class) != null) {
            return element.getAnnotation(Access.class).value();
        }

        for (final Element enclosedElement : element.getEnclosedElements()) {
            for (final Class<? extends Annotation> mappingAnnotation : MAPPING_ANNOTATIONS) {
                if (enclosedElement.getAnnotation(mappingAnnotation) != null) return toAccessType(enclosedElement);
            }
        }

        return AccessType.FIELD;
    }

    protected <T> T find(final List<T> elements, final Predicate<T> predicate) {
        return elements.stream()
                .filter(predicate)
                .findFirst()
                .orElse(null);
    }

    protected Property toProperty(final List<ExecutableElement> elements, final Property parent) {
        final ExecutableElement getter = find(elements, this::isGetter);
        final ExecutableElement setter = find(elements, this::isSetter);

        if (getter == null) {
            environment.getMessager().printError("Could not find getter for property", setter);
            return null;
        }

        final String name = extractGetterName(getter);
        final Accessor accessor = new Accessor.MethodAccessor(getter, setter);
        final PropertyType type = toPropertyType(getter.getReturnType());

        return new Property(
                name,
                accessor,
                type,
                parent
        );
    }

    protected List<Property> toProperties(final List<ExecutableElement> elements, final Property parent) {
        final Map<String, List<ExecutableElement>> propertiesByName = new HashMap<>();
        for (final ExecutableElement element : elements) {
            final String key = isGetter(element) ? extractGetterName(element) : extractSetterName(element);
            propertiesByName.computeIfAbsent(key, _ -> new ArrayList<>()).add(element);
        }

        return propertiesByName.values()
                .stream()
                .map(els -> toProperty(els, parent))
                .toList();
    }

    protected void flatMapEmbeddedProperties(final List<Property> properties) {
        final List<Property> embeddedProperties = properties.stream()
                .filter(prop -> prop.accessor().getRootElement().getAnnotation(Embedded.class) != null)
                .toList();
        properties.removeAll(embeddedProperties);

        properties.addAll(embeddedProperties.stream()
                .map(property -> collectProperties(resolve(property.type().getType()), property))
                .flatMap(List::stream)
                .toList());
    }

    protected List<Property> collectProperties(final TypeElement element, final Property parent) {
        final AccessType accessType = getAccessorType(element);
        final List<? extends Element> elements = element.getEnclosedElements()
                .stream()
                .filter(el -> filter(el, accessType))
                .toList();

        final List<Property> properties = new ArrayList<>();

        if (accessType == AccessType.FIELD) {
            properties.addAll(elements.stream()
                    .map(el -> (VariableElement) el)
                    .map(el -> toProperty(el, parent))
                    .toList());
        } else {
            properties.addAll(toProperties(
                    elements.stream()
                            .map(el -> (ExecutableElement) el)
                            .toList(),
                    parent
            ));
        }

        flatMapEmbeddedProperties(properties);

        if (element.getSuperclass() != null) {
            final TypeMirror superclass = element.getSuperclass();
            final TypeElement superType = resolve(superclass);

            if (superType != null && superType.getAnnotation(MappedSuperclass.class) != null) {
                properties.addAll(collectProperties(superType, parent));
            }
        }

        return properties;
    }

    protected boolean isIdProperty(final Property property) {
        return property.accessor().getRootElement().getAnnotation(Id.class) != null;
    }

    public PersistenceUnit analyze(final TypeElement element) {
        if (this.persistenceUnits.containsKey(element)) return this.persistenceUnits.get(element);
        if (!isEntity(element)) return null;

        final List<Property> properties = collectProperties(element, null);
        final Property idProperty = find(properties, this::isIdProperty);

        final PersistenceUnit unit = new PersistenceUnit(
                element,
                idProperty,
                properties
        );

        this.persistenceUnits.put(element, unit);
        return unit;
    }

}
