package com.hairsalonproject2.salon.service;

/** Internal signal: retry the complete write in a new transaction. */
class ExternalSalonWriteConflictException extends RuntimeException {
 ExternalSalonWriteConflictException(String message) { super(message); }
 ExternalSalonWriteConflictException(String message, Throwable cause) { super(message, cause); }
}
