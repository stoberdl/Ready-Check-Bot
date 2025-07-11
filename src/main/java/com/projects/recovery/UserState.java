package com.projects.recovery;

/**
 * @param status ❌, ✅, ⏰, 🚫
 */
public record UserState(String displayName, String status) {

  public boolean isReady() {
    return "✅".equals(status);
  }

  public boolean isPassed() {
    return "🚫".equals(status);
  }

  public boolean isScheduled() {
    return "⏰".equals(status);
  }

  public boolean isNotReady() {
    return "❌".equals(status);
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
