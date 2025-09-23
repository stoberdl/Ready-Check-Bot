package com.projects.readycheck.exceptions;

public class ReadyCheckException extends Exception {
  public ReadyCheckException(final String message) {
    super(message);
  }

  public ReadyCheckException(final String message, final Throwable cause) {
    super(message, cause);
  }
}