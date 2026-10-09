package com.bethibande.process.annotation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PropertyFilterTest {

    @EntityDTO(
            filter = @PropertyFilter(type = FilterType.INCLUDE, properties = {"name", "email"})
    )
    static class DummyIncludeEntity {}

    @EntityDTO(
            filter = @PropertyFilter(type = FilterType.EXCLUDE, properties = {"password"})
    )
    static class DummyExcludeEntity {}

    @EntityDTO
    static class DummyDefaultEntity {}

    @Test
    void testFilterTypeEnum() {
        assertEquals(2, FilterType.values().length);
        assertEquals(FilterType.INCLUDE, FilterType.valueOf("INCLUDE"));
        assertEquals(FilterType.EXCLUDE, FilterType.valueOf("EXCLUDE"));
    }
}
