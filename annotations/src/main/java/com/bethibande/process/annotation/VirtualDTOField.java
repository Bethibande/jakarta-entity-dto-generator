package com.bethibande.process.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A marker that tells the annotation processor to include a property marked with {@link jakarta.persistence.Transient}.
 * Virtual DTO fields can always be excluded from a generated DTO using {@link EntityDTO#excludeProperties()}.
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface VirtualDTOField {

}
