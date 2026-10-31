package com.bethibande.process.example;

import jakarta.validation.constraints.CopiedValues;
import jakarta.validation.constraints.Pattern;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ValidationAnnotationTest {

    @Test
    void copiesValidationAnnotationValues() throws NoSuchFieldException {
        final Field token = ValidationAnnotationDTO.class.getDeclaredField("token");

        final Pattern pattern = token.getAnnotation(Pattern.class);
        assertNotNull(pattern);
        assertEquals("[a-zA-Z0-9._-]", pattern.regexp());
        assertArrayEquals(new Pattern.Flag[]{Pattern.Flag.CASE_INSENSITIVE, Pattern.Flag.UNICODE_CASE}, pattern.flags());

        final CopiedValues copiedValues = token.getAnnotation(CopiedValues.class);
        assertNotNull(copiedValues);
        assertEquals("quote: \" slash: \\", copiedValues.value());
        assertArrayEquals(new String[]{"line1\nline2", "tab\t"}, copiedValues.strings());
        assertArrayEquals(new CopiedValues.Mode[]{CopiedValues.Mode.FIRST, CopiedValues.Mode.SECOND}, copiedValues.modes());
        assertArrayEquals(new Class<?>[]{String.class, int[].class}, copiedValues.types());
        assertArrayEquals(new Class<?>[0], copiedValues.emptyTypes());
        assertTrue(copiedValues.enabled());
        assertEquals((byte) 1, copiedValues.byteValue());
        assertEquals((short) 2, copiedValues.shortValue());
        assertEquals(3, copiedValues.intValue());
        assertEquals(3000000000L, copiedValues.longValue());
        assertEquals(1.25F, copiedValues.floatValue());
        assertEquals(2.5, copiedValues.doubleValue());
        assertEquals('\t', copiedValues.charValue());
    }

}