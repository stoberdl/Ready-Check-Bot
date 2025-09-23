package com.projects.readycheck.exceptions;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

class ExceptionHierarchyTest {

  @Test
  @DisplayName("Exception messages should include relevant details")
  void testExceptionMessages() {
    ReadyCheckNotFoundException notFound = new ReadyCheckNotFoundException("abc-123");
    assertTrue(notFound.getMessage().contains("abc-123"));

    InvalidTimeFormatException invalidTime = new InvalidTimeFormatException("25:00");
    assertTrue(invalidTime.getMessage().contains("25:00"));

    DatabasePersistenceException dbError = new DatabasePersistenceException("save config", "timeout");
    assertTrue(dbError.getMessage().contains("save config"));
    assertTrue(dbError.getMessage().contains("timeout"));
  }

  @Test
  @DisplayName("Exceptions should preserve cause when provided")
  void testCausePreservation() {
    Throwable originalCause = new RuntimeException("Original cause");

    ReadyCheckException baseException = new ReadyCheckException("Test message", originalCause);
    assertEquals(originalCause, baseException.getCause());

    InvalidTimeFormatException formatException = new InvalidTimeFormatException("bad format", originalCause);
    assertEquals(originalCause, formatException.getCause());

    DatabasePersistenceException dbException = new DatabasePersistenceException("operation", originalCause);
    assertEquals(originalCause, dbException.getCause());
  }
}