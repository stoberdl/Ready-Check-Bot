package com.projects.recovery;

import java.util.Set;

public record ResolvedUserStates(
    Set<String> targetUsers,
    Set<String> readyUsers,
    Set<String> passedUsers,
    Set<String> scheduledUsers,
    int unresolvedCount) {}
