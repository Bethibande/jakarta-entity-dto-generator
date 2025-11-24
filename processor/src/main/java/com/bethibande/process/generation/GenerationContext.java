package com.bethibande.process.generation;

import com.bethibande.process.StringUtil;
import com.bethibande.process.model.PersistenceUnit;
import com.bethibande.process.model.Property;
import com.bethibande.process.model.PropertyType;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;

import java.util.*;

public class GenerationContext {

    private final GenerationContext parent;

    private final PersistenceUnit unit;
    private final String[] excludeProperties;
    private final String[] expandProperties;

    private final ClassName className;

    private final Set<String> excludePropertiesLookup;
    private final Set<String> expandPropertiesLookup;

    private final Map<Property, GenerationContext> branches = new HashMap<>(0);

    public GenerationContext(final GenerationContext parent,
                             final PersistenceUnit unit,
                             final String nameOverride,
                             final String[] excludeProperties,
                             final String[] expandProperties) {
        this.parent = parent;
        this.unit = unit;
        this.excludeProperties = excludeProperties;
        this.expandProperties = expandProperties;

        this.excludePropertiesLookup = Set.of(excludeProperties);
        this.expandPropertiesLookup = Set.of(expandProperties);

        this.className = nameOverride != null
                ? ClassName.get(unit.getPackageName(), nameOverride)
                : generateName();
    }

    public SequencedCollection<CodeBlock> read(final Property property) {
        final Deque<CodeBlock> blocks = new ArrayDeque<>();
        Property current = property;

        while (current != null) {
            blocks.addFirst(current.accessor().read());
            current = current.parent();
            if (current != null
                    && (current.type() instanceof PropertyType.EntityType || current.type() instanceof PropertyType.EntityCollectionType)
                    && shouldExpand(current)) {
                break;
            }
        }

        return blocks;
    }

    public Collection<GenerationContext> getBranches() {
        return branches.values();
    }

    public PersistenceUnit extractEntityType(final Property property) {
        if (property.type() instanceof PropertyType.EntityType entityType) return entityType.getTargetRef();
        if (property.type() instanceof PropertyType.EntityCollectionType collectionType) {
            return collectionType.getTargetRef();
        }
        return null;
    }

    public GenerationContext branch(final Property property) {
        if (branches.containsKey(property)) return branches.get(property);

        final PersistenceUnit target = extractEntityType(property);
        if (target == null) return null;

        final PersistenceUnit branchedProperty = target.copyAsMemberOf(property);

        final GenerationContext branch = new GenerationContext(
                this,
                branchedProperty,
                null,
                excludeProperties,
                expandProperties
        );

        this.branches.put(property, branch);

        return branch;
    }

    public GenerationContext getParent() {
        return parent;
    }

    public ClassName getClassName() {
        return className;
    }

    public boolean shouldExpand(final Property property) {
        return expandPropertiesLookup.contains(property.getPathFromRootString());
    }

    public List<Property> getProperties() {
        return unit.properties()
                .stream()
                .filter(prop -> !excludePropertiesLookup.contains(prop.getPathFromRootString()))
                .toList();
    }

    public PersistenceUnit getUnit() {
        return unit;
    }

    private ClassName generateName() {
        final StringBuilder builder = new StringBuilder();
        builder.append(unit.getSimpleName());
        builder.append("DTO");

        if (excludeProperties != null && excludeProperties.length > 0) {
            builder.append("Without");
            for (int i = 0; i < excludeProperties.length; i++) {
                final String prop = excludeProperties[i];
                if (prop.contains(".")) continue;

                if (i > 0) {
                    builder.append("And");
                }

                builder.append(StringUtil.firstCharacterUpperCase(prop));
            }
        }

        final String baseName = builder.toString();
        if (parent == null) return ClassName.get(unit.getPackageName(), baseName);
        return parent.getClassName().nestedClass(baseName);
    }
}
