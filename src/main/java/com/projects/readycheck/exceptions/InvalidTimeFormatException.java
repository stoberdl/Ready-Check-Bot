package com.projects.readycheck.exceptions;

public class InvalidTimeFormatException extends ReadyCheckException {
  public InvalidTimeFormatException(final String message) {
    super("Invalid time format: " + message);
  }

  public InvalidTimeFormatException(final String message, final Throwable cause) {
    super("Invalid time format: " + message, cause);
  }
}