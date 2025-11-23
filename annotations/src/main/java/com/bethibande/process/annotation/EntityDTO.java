package com.bethibande.process.annotation;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.CLASS)
@Target({java.lang.annotation.ElementType.TYPE})
@Repeatable(EntityDTO.Repeat.class)
public @interface EntityDTO {

    /**
     * Overrides the generated default class name.
     * This value must be a valid java class name.
     */
    String name() default "";

    /**
     * You can list properties you want to exclude from the resulting dto.
     * The processor expects the fields listed here to be property paths.
     * For embedded properties you'll need to include the property marked as embedded in the path.
     */
    String[] excludeProperties() default {};

    /**
     * You can list entity properties you want to expand.
     * By default, all referenced entities are compacted to an id field like "someEntityId".
     * Listing these properties here will expand them and include all of their fields, unless they are excluded by {@link #excludeProperties()}.
     */
    String[] expandProperties() default {};

    @Retention(RetentionPolicy.CLASS)
    @Target({java.lang.annotation.ElementType.TYPE})
    @interface Repeat {
        EntityDTO[] value();
    }

}
