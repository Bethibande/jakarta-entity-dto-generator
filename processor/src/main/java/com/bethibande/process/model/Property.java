package com.bethibande.process.model;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.SequencedCollection;

public record Property(
        String name,
        Accessor accessor,
        PropertyType type,
        Property parent
) {

    public Property copyWithParent(final Property parent) {
        return new Property(
                name,
                accessor,
                type,
                this.parent == null ? parent : this.parent.copyWithParent(parent)
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
