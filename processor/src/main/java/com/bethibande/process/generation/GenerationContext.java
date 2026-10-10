package com.bethibande.process.generation;

import com.bethibande.process.StringUtil;
import com.bethibande.process.annotation.FilterType;
import com.bethibande.process.model.PersistenceUnit;
import com.bethibande.process.model.Property;
import com.bethibande.process.model.PropertyType;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;

import java.util.*;

public class GenerationContext {

    private final GenerationContext parent;

    private final PersistenceUnit unit;
    private final FilterType filterType;
    private final String[] filterProperties;
    private final String[] expandProperties;

    private final ClassName className;

    private final Set<String> filterPropertiesLookup;
    private final Set<String> expandPropertiesLookup;

    private final Map<Property, GenerationContext> branches = new HashMap<>(0);

    public GenerationContext(final GenerationContext parent,
                             final PersistenceUnit unit,
                             final String nameOverride,
                             final FilterType filterType,
                             final String[] filterProperties,
                             final String[] expandProperties) {
        this.parent = parent;
        this.unit = unit;
        this.filterType = filterType != null ? filterType : FilterType.EXCLUDE;
        this.filterProperties = filterProperties != null ? filterProperties : new String[0];
        this.expandProperties = expandProperties != null ? expandProperties : new String[0];

        this.filterPropertiesLookup = Set.of(this.filterProperties);
        this.expandPropertiesLookup = Set.of(this.expandProperties);

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
                filterType,
                filterProperties,
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

    public boolean isPropertyIncluded(final Property prop) {
        final String path = prop.getPathFromRootString();

        // If property is marked as ignored by default, it is only included when
        // the filter type is INCLUDE and the property is explicitly listed.
        if (prop.ignoredByDefault()) {
            if (filterType == FilterType.INCLUDE) {
                return filterPropertiesLookup.contains(path)
                        || filterPropertiesLookup.stream().anyMatch(filterPath -> path.startsWith(filterPath + "."))
                        || filterPropertiesLookup.stream().anyMatch(filterPath -> filterPath.startsWith(path + "."));
            }
            return false;
        }

        if (filterType == FilterType.EXCLUDE) {
            final boolean isExcluded = filterPropertiesLookup.contains(path)
                    || filterPropertiesLookup.stream().anyMatch(filterPath -> path.startsWith(filterPath + "."));
            return !isExcluded;
        }

        if (filterType == FilterType.INCLUDE) {
            if (filterPropertiesLookup.isEmpty()) return false;
            return filterPropertiesLookup.contains(path)
                    || (prop.type() instanceof PropertyType.EntityType && !shouldExpand(prop) && filterPropertiesLookup.contains(path + "Id"))
                    || filterPropertiesLookup.stream().anyMatch(filterPath -> path.startsWith(filterPath + "."))
                    || filterPropertiesLookup.stream().anyMatch(filterPath -> filterPath.startsWith(path + "."));
        }

        return true;
    }

    public List<Property> getProperties() {
        return unit.properties()
                .stream()
                .filter(this::isPropertyIncluded)
                .toList();
    }

    public PersistenceUnit getUnit() {
        return unit;
    }

    public FilterType getFilterType() {
        return filterType;
    }

    public String[] getFilterProperties() {
        return filterProperties;
    }

    public String[] getExcludeProperties() {
        return filterType == FilterType.EXCLUDE ? filterProperties : new String[0];
    }

    public String[] getIncludeProperties() {
        return filterType == FilterType.INCLUDE ? filterProperties : new String[0];
    }

    public String[] getExpandProperties() {
        return expandProperties;
    }

    private String formatPropName(final String prop) {
        final StringBuilder sb = new StringBuilder();
        for (final String part : prop.split("\\.")) {
            sb.append(StringUtil.firstCharacterUpperCase(part));
        }
        return sb.toString();
    }

    private ClassName generateName() {
        final StringBuilder builder = new StringBuilder();
        builder.append(unit.getSimpleName());
        builder.append("DTO");

        if (filterProperties != null && filterProperties.length > 0) {
            final String prefix = filterType == FilterType.INCLUDE ? "With" : "Without";
            final List<String> matchingProps = new ArrayList<>();

            for (final String prop : filterProperties) {
                if (filterType == FilterType.EXCLUDE) {
                    if (prop.contains(".")) continue;
                    matchingProps.add(StringUtil.firstCharacterUpperCase(prop));
                } else {
                    if (parent != null) {
                        final String branchPrefix = unit.properties().isEmpty()
                                ? ""
                                : unit.properties().getFirst().getPathFromRoot().stream().findFirst().orElse("");
                        String relativeProp = prop;
                        if (!branchPrefix.isEmpty() && prop.startsWith(branchPrefix + ".")) {
                            relativeProp = prop.substring(branchPrefix.length() + 1);
                            matchingProps.add(formatPropName(relativeProp));
                        } else if (!prop.contains(".")) {
                            matchingProps.add(formatPropName(relativeProp));
                        }
                    } else {
                        matchingProps.add(formatPropName(prop));
                    }
                }
            }

            if (!matchingProps.isEmpty()) {
                builder.append(prefix);
                for (int i = 0; i < matchingProps.size(); i++) {
                    if (i > 0) {
                        builder.append("And");
                    }
                    builder.append(matchingProps.get(i));
                }
            }
        }

        final String baseName = builder.toString();
        if (parent == null) return ClassName.get(unit.getPackageName(), baseName);
        return parent.getClassName().nestedClass(baseName);
    }
}
