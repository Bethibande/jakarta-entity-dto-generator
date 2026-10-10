package com.bethibande.process.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a property as always ignored by default, unless a DTO has a property filter of type
 * {@link FilterType#INCLUDE} that explicitly includes the property.
 * <p>
 * When this annotation is present on a field or getter method, the property will be excluded
 * from all generated DTOs unless the {@link EntityDTO#filter()} explicitly lists the property
 * with {@link FilterType#INCLUDE}.
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface IgnoreProperty {

}
