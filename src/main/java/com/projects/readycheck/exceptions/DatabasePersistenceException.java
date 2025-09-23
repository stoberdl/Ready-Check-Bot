package com.projects.readycheck.exceptions;

public class DatabasePersistenceException extends ReadyCheckException {
  public DatabasePersistenceException(final String operation, final Throwable cause) {
    super("Database operation failed: " + operation, cause);
  }

  public DatabasePersistenceException(final String operation, final String details) {
    super("Database operation failed: " + operation + " - " + details);
  }
}