package com.projects.recovery;

import java.util.Set;

public record ResolvedUserStates(
    Set<String> targetUsers,
    Set<String> readyUsers,
    Set<String> passedUsers,
    Set<String> scheduledUsers,
    int unresolvedCount) {

  public int getTotalResolved() {
    return targetUsers.size();
  }

  public boolean hasUnresolvedUsers() {
    return unresolvedCount > 0;
  }

  public double getResolutionRate() {
    int total = targetUsers.size() + unresolvedCount;
    return total == 0 ? 1.0 : (double) targetUsers.size() / total;
  }
}
