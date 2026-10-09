package com.bethibande.process.annotation;

import com.bethibande.process.exception.EntityNotFoundException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EntityNotFoundExceptionTest {

    @Test
    void testEntityNotFoundExceptionProperties() {
        final EntityNotFoundException exception = new EntityNotFoundException("user.address", String.class, 123L);

        assertEquals("user.address", exception.getFieldPath());
        assertEquals("user.address", exception.fieldPath());
        assertEquals(String.class, exception.getType());
        assertEquals(String.class, exception.type());
        assertEquals(123L, exception.getId());
        assertEquals(123L, exception.id());
        assertEquals("Entity of type 'java.lang.String' with id '123' not found for field 'user.address'", exception.getMessage());
    }

    @Test
    void testCustomMessage() {
        final EntityNotFoundException exception = new EntityNotFoundException("Custom message", "field", Long.class, 1L);

        assertEquals("Custom message", exception.getMessage());
        assertEquals("field", exception.getFieldPath());
        assertEquals(Long.class, exception.getType());
        assertEquals(1L, exception.getId());
    }
}
