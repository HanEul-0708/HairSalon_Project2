package com.hairsalonproject2.common.catalog;

/** A catalog change that would invalidate existing data or a member link. */
public class CatalogConflictException extends RuntimeException {
 private final String field;

 public CatalogConflictException(String field, String message) {
  super(message);
  this.field = field;
 }

 public String getField() { return field; }
}
