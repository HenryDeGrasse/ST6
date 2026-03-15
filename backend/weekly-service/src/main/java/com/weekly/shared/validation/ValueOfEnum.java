package com.weekly.shared.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a string matches one of the constants from the supplied enum.
 */
@Documented
@Constraint(validatedBy = ValueOfEnumValidator.class)
@Target({
        ElementType.FIELD,
        ElementType.PARAMETER,
        ElementType.RECORD_COMPONENT,
        ElementType.TYPE_USE
})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValueOfEnum {

    Class<? extends Enum<?>> enumClass();

    String message() default "must be one of the allowed values";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
