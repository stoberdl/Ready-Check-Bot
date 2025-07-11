package com.projects.recovery;

public record UserState(String displayName, String status) {

  public boolean isReady() {
    return "✅".equals(status);
  }

  @Override
  public String toString() {
    return String.format("%s %s", status, displayName);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null || getClass() != obj.getClass()) return false;
    UserState userState = (UserState) obj;
    return displayName.equals(userState.displayName) && status.equals(userState.status);
  }
}
