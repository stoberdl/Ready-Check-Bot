package com.projects.models.recovery;

import java.util.List;

public record RecoveredReadyCheckData(String initiatorName, List<UserState> userStates) {

  @Override
  public String toString() {
    return String.format(
        "RecoveredReadyCheckData{initiator='%s', users=%d}", initiatorName, userStates.size());
  }
}
