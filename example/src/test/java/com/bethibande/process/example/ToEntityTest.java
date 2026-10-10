package com.bethibande.process.example;

import com.bethibande.process.exception.EntityNotFoundException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class ToEntityTest {

    private EntityManager createEntityManager(final Map<Object, Object> registry) {
        return (EntityManager) Proxy.newProxyInstance(
                EntityManager.class.getClassLoader(),
                new Class<?>[]{EntityManager.class},
                (proxy, method, args) -> {
                    if ("find".equals(method.getName()) && args != null && args.length >= 2) {
                        return registry.get(args[1]);
                    }
                    if ("createQuery".equals(method.getName()) && args != null && args.length >= 2) {
                        return createTypedQuery((Class<?>) args[1], registry);
                    }
                    return null;
                }
        );
    }

    @SuppressWarnings("unchecked")
    private <T> jakarta.persistence.TypedQuery<T> createTypedQuery(final Class<T> resultClass, final Map<Object, Object> registry) {
        final List<Object>[] parameterHolder = new List[1];
        return (jakarta.persistence.TypedQuery<T>) Proxy.newProxyInstance(
                jakarta.persistence.TypedQuery.class.getClassLoader(),
                new Class<?>[]{jakarta.persistence.TypedQuery.class},
                (proxy, method, args) -> {
                    if ("setParameter".equals(method.getName())) {
                        if (args != null && args.length >= 2 && args[1] instanceof Collection<?> c) {
                            parameterHolder[0] = new ArrayList<>(c);
                        }
                        return proxy;
                    }
                    if ("getResultList".equals(method.getName())) {
                        if (parameterHolder[0] == null) {
                            return Collections.emptyList();
                        }
                        final List<T> results = new ArrayList<>();
                        for (final Object id : parameterHolder[0]) {
                            final Object obj = registry.get(id);
                            if (obj != null && resultClass.isInstance(obj)) {
                                results.add(resultClass.cast(obj));
                            }
                        }
                        return results;
                    }
                    return null;
                }
        );
    }

    @Test
    void testEntityWithMethodAccessors() {
        final EntityWithMethodAccessorsDTO dto = new EntityWithMethodAccessorsDTO(42L);
        final EntityWithMethodAccessors existing = new EntityWithMethodAccessors();

        final EntityWithMethodAccessors result = dto.toEntity(existing);
        assertSame(existing, result);
        assertEquals(42L, result.getId());

        assertNull(dto.toEntity((EntityWithMethodAccessors) null));
    }

    @Test
    void testOneToManyEntityDTOWithoutEntities() {
        final OneToManyEntityDTOWithoutEntities dto = new OneToManyEntityDTOWithoutEntities(5, 100L);
        final OneToManyEntity existing = new OneToManyEntity();

        final OneToManyEntity result = dto.toEntity(existing);
        assertSame(existing, result);
        assertEquals(100L, result.id);

        assertNull(dto.toEntity((OneToManyEntity) null));
    }

    @Test
    void testExampleEntityDTOWithoutId() {
        final LocalDateTime now = LocalDateTime.now();
        final ExampleEntityDTOWithoutId.ReferencedEntityDTOWithoutId refDto =
                new ExampleEntityDTOWithoutId.ReferencedEntityDTOWithoutId("testName");
        final ExampleEntityDTOWithoutId dto = new ExampleEntityDTOWithoutId(
                "hello",
                refDto,
                List.of("a", "b"),
                now,
                now.plusDays(1)
        );

        final ExampleEntity result = dto.toEntity();
        assertNotNull(result);
        assertNull(result.id);
        assertEquals("hello", result.someString);
        assertEquals(List.of("a", "b"), result.list);
        assertNotNull(result.timestamp);
        assertEquals(now, result.timestamp.created);
        assertEquals(now.plusDays(1), result.timestamp.updated);
        assertNotNull(result.entity);
        assertEquals("testName", result.entity.name);
        assertNull(result.entity.id);
    }

    @Test
    void testExampleEntityDTOWithEntityManager_Success() throws EntityNotFoundException {
        final UUID refId = UUID.randomUUID();
        final ReferencedEntity existingRef = new ReferencedEntity();
        existingRef.id = refId;
        existingRef.name = "oldName";

        final EntityManager entityManager = createEntityManager(Map.of(refId, existingRef));

        final ExampleEntityDTO.ReferencedEntityDTO refDto = new ExampleEntityDTO.ReferencedEntityDTO(refId, "newName");
        final LocalDateTime now = LocalDateTime.now();
        final ExampleEntityDTO dto = new ExampleEntityDTO(
                "updatedString",
                refDto,
                List.of("x"),
                now,
                now,
                10L
        );

        final ExampleEntity existing = new ExampleEntity();
        final ExampleEntity result = dto.toEntity(existing, entityManager);

        assertSame(existing, result);
        assertEquals(10L, result.id);
        assertEquals("updatedString", result.someString);
        assertEquals(List.of("x"), result.list);
        assertNotNull(result.timestamp);
        assertEquals(now, result.timestamp.created);
        assertEquals(now, result.timestamp.updated);
        assertSame(existingRef, result.entity);
        assertEquals("newName", result.entity.name);
        assertEquals(refId, result.entity.id);

        assertNull(dto.toEntity(null, entityManager));
    }

    @Test
    void testExampleEntityDTOWithEntityManager_NotFound() {
        final UUID missingRefId = UUID.randomUUID();
        final EntityManager entityManager = createEntityManager(Map.of());

        final ExampleEntityDTO.ReferencedEntityDTO refDto = new ExampleEntityDTO.ReferencedEntityDTO(missingRefId, "newName");
        final LocalDateTime now = LocalDateTime.now();
        final ExampleEntityDTO dto = new ExampleEntityDTO(
                "updatedString",
                refDto,
                List.of("x"),
                now,
                now,
                10L
        );

        final ExampleEntity existing = new ExampleEntity();
        final EntityNotFoundException exception = assertThrows(
                EntityNotFoundException.class,
                () -> dto.toEntity(existing, entityManager)
        );

        assertEquals("entity", exception.getFieldPath());
        assertEquals("entity", exception.fieldPath());
        assertEquals(ReferencedEntity.class, exception.getType());
        assertEquals(ReferencedEntity.class, exception.type());
        assertEquals(missingRefId, exception.getId());
        assertEquals(missingRefId, exception.id());
    }

    @Test
    void testManyToOneEntityDTO_NestedCollectionAndRecursion() throws EntityNotFoundException {
        final OneToManyEntity existingOneToMany = new OneToManyEntity();
        existingOneToMany.id = 200L;

        final ManyToOneEntity child1 = new ManyToOneEntity();
        child1.id = 1L;
        final ManyToOneEntity child2 = new ManyToOneEntity();
        child2.id = 2L;

        final Map<Object, Object> registry = new HashMap<>();
        registry.put(200L, existingOneToMany);
        registry.put(1L, child1);
        registry.put(2L, child2);

        final EntityManager entityManager = createEntityManager(registry);

        final ManyToOneEntityDTO.OneToManyEntityDTO oneToManyDto =
                new ManyToOneEntityDTO.OneToManyEntityDTO(2, List.of(1L, 2L), 200L);
        final ManyToOneEntityDTO dto = new ManyToOneEntityDTO(oneToManyDto, 500L);

        final ManyToOneEntity existing = new ManyToOneEntity();
        final ManyToOneEntity result = dto.toEntity(existing, entityManager);

        assertSame(existing, result);
        assertEquals(500L, result.id);
        assertSame(existingOneToMany, result.entity);
        assertEquals(List.of(child1, child2), result.entity.getEntities());
    }

    @Test
    void testManyToOneEntityDTO_NestedCollection_NotFound() {
        final OneToManyEntity existingOneToMany = new OneToManyEntity();
        existingOneToMany.id = 200L;

        final EntityManager entityManager = createEntityManager(Map.of(200L, existingOneToMany));

        final ManyToOneEntityDTO.OneToManyEntityDTO oneToManyDto =
                new ManyToOneEntityDTO.OneToManyEntityDTO(1, List.of(999L), 200L);
        final ManyToOneEntityDTO dto = new ManyToOneEntityDTO(oneToManyDto, 500L);

        final ManyToOneEntity existing = new ManyToOneEntity();
        final EntityNotFoundException exception = assertThrows(
                EntityNotFoundException.class,
                () -> dto.toEntity(existing, entityManager)
        );

        assertEquals("entity.entities", exception.getFieldPath());
        assertEquals(ManyToOneEntity.class, exception.getType());
        assertEquals(999L, exception.getId());
    }

    @Test
    void testExampleEntityDTO_ReusesExistingEntityWhenIdsEqual() throws EntityNotFoundException {
        final UUID refId = UUID.randomUUID();
        final ReferencedEntity existingRef = new ReferencedEntity();
        existingRef.id = refId;
        existingRef.name = "oldName";

        // EntityManager has empty registry - if find() is called, it would return null and throw EntityNotFoundException
        final EntityManager entityManager = createEntityManager(Map.of());

        final ExampleEntityDTO.ReferencedEntityDTO refDto = new ExampleEntityDTO.ReferencedEntityDTO(refId, "newName");
        final LocalDateTime now = LocalDateTime.now();
        final ExampleEntityDTO dto = new ExampleEntityDTO(
                "str",
                refDto,
                List.of(),
                now,
                now,
                10L
        );

        final ExampleEntity existing = new ExampleEntity();
        existing.entity = existingRef;

        final ExampleEntity result = dto.toEntity(existing, entityManager);

        assertSame(existingRef, result.entity);
        assertEquals(refId, result.entity.id);
        assertEquals("newName", result.entity.name);
    }

    @Test
    void testExampleEntityDTO_FetchesWhenExistingEntityHasDifferentId() throws EntityNotFoundException {
        final UUID oldRefId = UUID.randomUUID();
        final UUID newRefId = UUID.randomUUID();

        final ReferencedEntity oldRef = new ReferencedEntity();
        oldRef.id = oldRefId;
        oldRef.name = "old";

        final ReferencedEntity newRef = new ReferencedEntity();
        newRef.id = newRefId;
        newRef.name = "fetchedOld";

        final EntityManager entityManager = createEntityManager(Map.of(newRefId, newRef));

        final ExampleEntityDTO.ReferencedEntityDTO refDto = new ExampleEntityDTO.ReferencedEntityDTO(newRefId, "fetchedNew");
        final LocalDateTime now = LocalDateTime.now();
        final ExampleEntityDTO dto = new ExampleEntityDTO(
                "str",
                refDto,
                List.of(),
                now,
                now,
                10L
        );

        final ExampleEntity existing = new ExampleEntity();
        existing.entity = oldRef;

        final ExampleEntity result = dto.toEntity(existing, entityManager);

        assertSame(newRef, result.entity);
        assertEquals(newRefId, result.entity.id);
        assertEquals("fetchedNew", result.entity.name);
    }

    @Test
    void testExampleEntityDTO_ToEntityEntityManager_Success() throws EntityNotFoundException {
        final Long rootId = 100L;
        final UUID refId = UUID.randomUUID();

        final ExampleEntity rootEntity = new ExampleEntity();
        rootEntity.id = rootId;

        final ReferencedEntity refEntity = new ReferencedEntity();
        refEntity.id = refId;
        refEntity.name = "refName";

        final Map<Object, Object> registry = new HashMap<>();
        registry.put(rootId, rootEntity);
        registry.put(refId, refEntity);

        final EntityManager entityManager = createEntityManager(registry);

        final LocalDateTime now = LocalDateTime.now();
        final ExampleEntityDTO.ReferencedEntityDTO refDto = new ExampleEntityDTO.ReferencedEntityDTO(refId, "updatedRefName");
        final ExampleEntityDTO dto = new ExampleEntityDTO(
                "rootString",
                refDto,
                List.of("val"),
                now,
                now,
                rootId
        );

        final ExampleEntity result = dto.toEntity(entityManager);

        assertSame(rootEntity, result);
        assertEquals(rootId, result.id);
        assertEquals("rootString", result.someString);
        assertSame(refEntity, result.entity);
        assertEquals("updatedRefName", result.entity.name);
    }

    @Test
    void testExampleEntityDTO_ToEntityEntityManager_NotFound() {
        final Long missingRootId = 999L;
        final EntityManager entityManager = createEntityManager(Map.of());

        final LocalDateTime now = LocalDateTime.now();
        final ExampleEntityDTO dto = new ExampleEntityDTO(
                "rootString",
                null,
                List.of(),
                now,
                now,
                missingRootId
        );

        final EntityNotFoundException exception = assertThrows(
                EntityNotFoundException.class,
                () -> dto.toEntity(entityManager)
        );

        assertEquals("id", exception.getFieldPath());
        assertEquals(ExampleEntity.class, exception.getType());
        assertEquals(missingRootId, exception.getId());
    }

    @Test
    void testExampleEntityDTO_ToEntityEntityManager_NullId() throws EntityNotFoundException {
        final EntityManager entityManager = createEntityManager(Map.of());

        final LocalDateTime now = LocalDateTime.now();
        final ExampleEntityDTO dto = new ExampleEntityDTO(
                "rootString",
                null,
                List.of(),
                now,
                now,
                null
        );

        final ExampleEntity result = dto.toEntity(entityManager);
        assertNull(result);
    }

    @Test
    void testEntityWithMethodAccessors_ToEntityEntityManager() throws EntityNotFoundException {
        final EntityWithMethodAccessors entity = new EntityWithMethodAccessors();
        entity.setId(42L);

        final EntityManager entityManager = createEntityManager(Map.of(42L, entity));

        final EntityWithMethodAccessorsDTO dto = new EntityWithMethodAccessorsDTO(42L);
        final EntityWithMethodAccessors result = dto.toEntity(entityManager);

        assertSame(entity, result);
        assertEquals(42L, result.getId());
    }

    @Test
    void testReferencedEntityDTO_ToEntityEntityManager() throws EntityNotFoundException {
        final UUID id = UUID.randomUUID();
        final ReferencedEntity entity = new ReferencedEntity();
        entity.id = id;
        entity.name = "initial";

        final EntityManager entityManager = createEntityManager(Map.of(id, entity));

        final ExampleEntityDTO.ReferencedEntityDTO dto = new ExampleEntityDTO.ReferencedEntityDTO(id, "updated");
        final ReferencedEntity result = dto.toEntity(entityManager);

        assertSame(entity, result);
        assertEquals(id, result.id);
        assertEquals("updated", result.name);
    }

    @Test
    void testExampleEntityDTOWithIdAndSomeString() {
        final ExampleEntity entity = new ExampleEntity();
        entity.id = 55L;
        entity.someString = "includedString";
        entity.list = List.of("x", "y");

        final ExampleEntityDTOWithIdAndSomeString dto = ExampleEntityDTOWithIdAndSomeString.from(entity);
        assertNotNull(dto);
        assertEquals(55L, dto.id());
        assertEquals("includedString", dto.someString());

        final ExampleEntity existing = new ExampleEntity();
        final ExampleEntity converted = dto.toEntity(existing);
        assertSame(existing, converted);
        assertEquals(55L, converted.id);
        assertEquals("includedString", converted.someString);
    }

    @Test
    void testOneToManyEntityDTOWithIdAndEntityCount() {
        final OneToManyEntity entity = new OneToManyEntity();
        entity.id = 789L;
        entity.setEntities(List.of(new ManyToOneEntity(), new ManyToOneEntity()));

        final OneToManyEntityDTOWithIdAndEntityCount dto = OneToManyEntityDTOWithIdAndEntityCount.from(entity);
        assertNotNull(dto);
        assertEquals(789L, dto.id());
        assertEquals(2, dto.entityCount());

        final OneToManyEntity existing = new OneToManyEntity();
        final OneToManyEntity converted = dto.toEntity(existing);
        assertSame(existing, converted);
        assertEquals(789L, converted.id);
    }
}
