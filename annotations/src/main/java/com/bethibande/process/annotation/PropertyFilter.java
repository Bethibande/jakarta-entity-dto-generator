package com.bethibande.process.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.CLASS)
@Target({})
public @interface PropertyFilter {

    /**
     * The filter type, either {@link FilterType#INCLUDE} or {@link FilterType#EXCLUDE}.
     */
    FilterType type() default FilterType.EXCLUDE;

    /**
     * The property paths to filter.
     * The processor expects the fields listed here to be property paths.
     * For embedded properties you can specify either the parent property or the full path (e.g. "timestamp.created").
     */
    String[] properties() default {};

}
