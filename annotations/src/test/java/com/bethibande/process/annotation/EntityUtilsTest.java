package com.bethibande.process.annotation;

import com.bethibande.process.util.EntityUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class EntityUtilsTest {

    static class DummyEntity {
        private final Long id;
        private final String customKey;
        private final String name;

        public DummyEntity(final Long id, final String customKey, final String name) {
            this.id = id;
            this.customKey = customKey;
            this.name = name;
        }

        public Long getId() {
            return id;
        }

        public String getCustomKey() {
            return customKey;
        }

        public String getName() {
            return name;
        }
    }

    @SuppressWarnings("unchecked")
    private EntityManager createMockEntityManager(final Map<String, Object> queryCalls, final List<DummyEntity> allEntities) {
        return (EntityManager) Proxy.newProxyInstance(
                EntityManager.class.getClassLoader(),
                new Class<?>[]{EntityManager.class},
                (proxy, method, args) -> {
                    if ("createQuery".equals(method.getName()) && args != null && args.length >= 2) {
                        final String qlString = (String) args[0];
                        queryCalls.put("qlString", qlString);
                        queryCalls.put("resultClass", args[1]);

                        final List<Object>[] param = new List[1];
                        return (TypedQuery<DummyEntity>) Proxy.newProxyInstance(
                                TypedQuery.class.getClassLoader(),
                                new Class<?>[]{TypedQuery.class},
                                (queryProxy, queryMethod, queryArgs) -> {
                                    if ("setParameter".equals(queryMethod.getName())) {
                                        queryCalls.put("paramName", queryArgs[0]);
                                        if (queryArgs[1] instanceof Collection<?> c) {
                                            param[0] = new ArrayList<>(c);
                                            queryCalls.put("paramValue", param[0]);
                                        }
                                        return queryProxy;
                                    }
                                    if ("getResultList".equals(queryMethod.getName())) {
                                        if (param[0] == null) return Collections.emptyList();
                                        final List<DummyEntity> results = new ArrayList<>();
                                        for (final DummyEntity e : allEntities) {
                                            if (param[0].contains(e.getId()) || param[0].contains(e.getCustomKey())) {
                                                results.add(e);
                                            }
                                        }
                                        return results;
                                    }
                                    return null;
                                }
                        );
                    }
                    return null;
                }
        );
    }

    @Test
    void testFindAll_EmptyOrNullInputs() {
        final Map<String, Object> queryCalls = new HashMap<>();
        final EntityManager em = createMockEntityManager(queryCalls, List.of());

        assertTrue(EntityUtils.findAll(null, DummyEntity.class, List.of(1L)).isEmpty());
        assertTrue(EntityUtils.findAll(em, null, List.of(1L)).isEmpty());
        assertTrue(EntityUtils.findAll(em, DummyEntity.class, (Collection<Long>) null).isEmpty());
        assertTrue(EntityUtils.findAll(em, DummyEntity.class, Collections.emptyList()).isEmpty());
        assertTrue(EntityUtils.findAll(em, DummyEntity.class, Collections.singletonList(null)).isEmpty());

        assertTrue(queryCalls.isEmpty());
    }

    @Test
    void testFindAll_WithDefaultIdAttribute() {
        final DummyEntity e1 = new DummyEntity(1L, "k1", "Entity 1");
        final DummyEntity e2 = new DummyEntity(2L, "k2", "Entity 2");
        final Map<String, Object> queryCalls = new HashMap<>();
        final EntityManager em = createMockEntityManager(queryCalls, List.of(e1, e2));

        final List<DummyEntity> result = EntityUtils.findAll(em, DummyEntity.class, Arrays.asList(1L, 2L, 1L, null));

        assertEquals("SELECT e FROM DummyEntity e WHERE e.id IN :ids", queryCalls.get("qlString"));
        assertEquals("ids", queryCalls.get("paramName"));
        assertEquals(List.of(1L, 2L), queryCalls.get("paramValue"));
        assertEquals(2, result.size());
        assertTrue(result.contains(e1));
        assertTrue(result.contains(e2));
    }

    @Test
    void testFindAll_WithCustomIdAttribute() {
        final DummyEntity e1 = new DummyEntity(1L, "k1", "Entity 1");
        final DummyEntity e2 = new DummyEntity(2L, "k2", "Entity 2");
        final Map<String, Object> queryCalls = new HashMap<>();
        final EntityManager em = createMockEntityManager(queryCalls, List.of(e1, e2));

        final List<DummyEntity> result = EntityUtils.findAll(em, DummyEntity.class, "customKey", List.of("k1"));

        assertEquals("SELECT e FROM DummyEntity e WHERE e.customKey IN :ids", queryCalls.get("qlString"));
        assertEquals("ids", queryCalls.get("paramName"));
        assertEquals(List.of("k1"), queryCalls.get("paramValue"));
        assertEquals(1, result.size());
        assertEquals(e1, result.get(0));
    }

    @Test
    void testFindAllById_EmptyOrNullInputs() {
        final Map<String, Object> queryCalls = new HashMap<>();
        final EntityManager em = createMockEntityManager(queryCalls, List.of());

        assertTrue(EntityUtils.findAllById(null, DummyEntity.class, DummyEntity::getId, List.of(1L)).isEmpty());
        assertTrue(EntityUtils.findAllById(em, null, DummyEntity::getId, List.of(1L)).isEmpty());
        assertTrue(EntityUtils.findAllById(em, DummyEntity.class, null, List.of(1L)).isEmpty());
        assertTrue(EntityUtils.findAllById(em, DummyEntity.class, DummyEntity::getId, null).isEmpty());
        assertTrue(EntityUtils.findAllById(em, DummyEntity.class, DummyEntity::getId, Collections.emptyList()).isEmpty());
        assertTrue(EntityUtils.findAllById(em, DummyEntity.class, DummyEntity::getId, Collections.singletonList(null)).isEmpty());

        assertTrue(queryCalls.isEmpty());
    }

    @Test
    void testFindAllById_MapResult() {
        final DummyEntity e1 = new DummyEntity(10L, "k10", "Ten");
        final DummyEntity e2 = new DummyEntity(20L, "k20", "Twenty");
        final Map<String, Object> queryCalls = new HashMap<>();
        final EntityManager em = createMockEntityManager(queryCalls, List.of(e1, e2));

        final Map<Long, DummyEntity> map = EntityUtils.findAllById(em, DummyEntity.class, DummyEntity::getId, List.of(10L, 20L, 30L));

        assertEquals(2, map.size());
        assertSame(e1, map.get(10L));
        assertSame(e2, map.get(20L));
        assertNull(map.get(30L));
    }

    @Test
    void testFindAllById_CustomAttribute() {
        final DummyEntity e1 = new DummyEntity(10L, "k10", "Ten");
        final DummyEntity e2 = new DummyEntity(20L, "k20", "Twenty");
        final Map<String, Object> queryCalls = new HashMap<>();
        final EntityManager em = createMockEntityManager(queryCalls, List.of(e1, e2));

        final Map<String, DummyEntity> map = EntityUtils.findAllById(em, DummyEntity.class, "customKey", DummyEntity::getCustomKey, List.of("k10", "k20"));

        assertEquals(2, map.size());
        assertSame(e1, map.get("k10"));
        assertSame(e2, map.get("k20"));
        assertEquals("SELECT e FROM DummyEntity e WHERE e.customKey IN :ids", queryCalls.get("qlString"));
    }
}
