package com.bethibande.process.model;

import javax.lang.model.element.AnnotationMirror;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.SequencedCollection;

public record Property(
        String name,
        Accessor accessor,
        PropertyType type,
        boolean optional,
        Property parent,
        List<AnnotationMirror> validationAnnotations
) {

    public Property(
            String name,
            Accessor accessor,
            PropertyType type,
            boolean optional,
            Property parent
    ) {
        this(name, accessor, type, optional, parent, List.of());
    }

    public Property copyWithParent(final Property parent) {
        return new Property(
                name,
                accessor,
                type,
                optional,
                this.parent == null ? parent : this.parent.copyWithParent(parent),
                validationAnnotations
        );
    }

    public String getPathFromRootString() {
        return String.join(".", getPathFromRoot());
    }

    public SequencedCollection<String> getPathFromRoot() {
        final Deque<String> path = new ArrayDeque<>();

        Property current = this;
        while (current != null) {
            path.addFirst(current.name);
            current = current.parent;
        }
        return path;
    }

}
