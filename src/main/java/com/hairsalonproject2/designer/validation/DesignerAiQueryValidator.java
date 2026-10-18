package com.hairsalonproject2.designer.validation;

import com.hairsalonproject2.designer.service.DesignerAiQueryParser;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class DesignerAiQueryValidator implements ConstraintValidator<ValidDesignerAiQuery, String> {
 @Override
 public boolean isValid(String value, ConstraintValidatorContext context) {
  String error = DesignerAiQueryParser.parse(value).validationError();
  if (error == null) return true;
  context.disableDefaultConstraintViolation();
  context.buildConstraintViolationWithTemplate(error).addConstraintViolation();
  return false;
 }
}
