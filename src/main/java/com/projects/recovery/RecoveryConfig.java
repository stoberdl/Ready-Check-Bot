package com.projects.recovery;

public class RecoveryConfig {
  public static final int DEFAULT_RECOVERY_HOURS = 2;
  public static final int DEFAULT_MAX_MESSAGES = 10;
  public static final int MAX_USERS_PER_CHECK = 50;
  public static final int MAX_NAME_LENGTH = 100;

  public static final long MESSAGE_SCAN_DELAY_MS = 100;
  public static final int MAX_CHANNELS_PER_GUILD = 50;

  private RecoveryConfig() {}
}
