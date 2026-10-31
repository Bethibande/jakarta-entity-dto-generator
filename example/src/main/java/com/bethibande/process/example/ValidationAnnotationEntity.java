package com.bethibande.process.example;

import com.bethibande.process.annotation.EntityDTO;
import jakarta.persistence.Entity;
import jakarta.validation.constraints.CopiedValues;
import jakarta.validation.constraints.Pattern;

@Entity
@EntityDTO(name = "ValidationAnnotationDTO")
public class ValidationAnnotationEntity extends EntityBase {

    @Pattern(regexp = "[a-zA-Z0-9._-]", flags = {Pattern.Flag.CASE_INSENSITIVE, Pattern.Flag.UNICODE_CASE})
    @CopiedValues(
            value = "quote: \" slash: \\",
            strings = {"line1\nline2", "tab\t"},
            modes = {CopiedValues.Mode.FIRST, CopiedValues.Mode.SECOND},
            types = {String.class, int[].class},
            emptyTypes = {},
            enabled = true,
            byteValue = 1,
            shortValue = 2,
            intValue = 3,
            longValue = 3000000000L,
            floatValue = 1.25F,
            doubleValue = 2.5,
            charValue = '\t'
    )
    public String token;

}