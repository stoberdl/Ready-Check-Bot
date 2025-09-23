package com.projects.readycheck.exceptions;

public class ReadyCheckNotFoundException extends ReadyCheckException {
  public ReadyCheckNotFoundException(final String readyCheckId) {
    super("Ready check not found: " + readyCheckId);
  }
}