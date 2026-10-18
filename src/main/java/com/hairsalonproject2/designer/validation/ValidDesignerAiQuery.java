package com.hairsalonproject2.designer.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DesignerAiQueryValidator.class)
public @interface ValidDesignerAiQuery {
 String message() default "추천 인원을 올바르게 입력해 주세요.";
 Class<?>[] groups() default {};
 Class<? extends Payload>[] payload() default {};
}
