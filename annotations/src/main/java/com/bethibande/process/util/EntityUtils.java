package com.bethibande.process.util;

import jakarta.persistence.EntityManager;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public final class EntityUtils {

    private EntityUtils() {
    }

    public static <T, ID> List<T> findAll(
            final EntityManager entityManager,
            final Class<T> entityClass,
            final Collection<ID> ids
    ) {
        return findAll(entityManager, entityClass, "id", ids);
    }

    public static <T, ID> List<T> findAll(
            final EntityManager entityManager,
            final Class<T> entityClass,
            final String idAttribute,
            final Collection<ID> ids
    ) {
        if (entityManager == null || entityClass == null || idAttribute == null || ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        final List<ID> filteredIds = ids.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (filteredIds.isEmpty()) {
            return Collections.emptyList();
        }
        final String query = String.format("SELECT e FROM %s e WHERE e.%s IN :ids", entityClass.getSimpleName(), idAttribute);
        return entityManager.createQuery(query, entityClass)
                .setParameter("ids", filteredIds)
                .getResultList();
    }

    public static <T, ID> Map<ID, T> findAllById(
            final EntityManager entityManager,
            final Class<T> entityClass,
            final Function<T, ID> idExtractor,
            final Collection<ID> ids
    ) {
        return findAllById(entityManager, entityClass, "id", idExtractor, ids);
    }

    public static <T, ID> Map<ID, T> findAllById(
            final EntityManager entityManager,
            final Class<T> entityClass,
            final String idAttribute,
            final Function<T, ID> idExtractor,
            final Collection<ID> ids
    ) {
        if (entityManager == null || entityClass == null || idAttribute == null || idExtractor == null || ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        final List<T> results = findAll(entityManager, entityClass, idAttribute, ids);
        final Map<ID, T> map = new HashMap<>(results.size());
        for (final T entity : results) {
            if (entity != null) {
                final ID id = idExtractor.apply(entity);
                if (id != null) {
                    map.put(id, entity);
                }
            }
        }
        return map;
    }
}
