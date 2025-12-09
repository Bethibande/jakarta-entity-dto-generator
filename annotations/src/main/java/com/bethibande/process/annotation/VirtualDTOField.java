package com.bethibande.process.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A marker that tells the annotation processor to include a property marked with {@link jakarta.persistence.Transient}.
 * Please note, this annotation only works on methods. If the access mode of an entity is set to {@link jakarta.persistence.AccessType#FIELD},
 * methods annotated with this annotation will be used regardles.
 * Virtual DTO fields can always be excluded from a generated DTO using {@link EntityDTO#excludeProperties()}.
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD})
public @interface VirtualDTOField {

}
