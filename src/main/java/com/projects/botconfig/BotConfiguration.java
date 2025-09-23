package com.projects.botconfig;

import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BotConfiguration {
  private static final Logger logger = LoggerFactory.getLogger(BotConfiguration.class);

  public static final long EIGHT_HOURS_MS = TimeUnit.HOURS.toMillis(8);
  public static final long AVATAR_COOLDOWN_MS = TimeUnit.MINUTES.toMillis(10);
  public static final int MAX_MINUTES_COUNTDOWN = 1440;
  public static final int MIN_MINUTES_COUNTDOWN = 1;

  private final String discordBotToken;
  private final String supabaseUrl;
  private final String supabaseKey;

  private static BotConfiguration instance;

  private BotConfiguration() {
    this.discordBotToken = validateRequired("DISCORD_BOT_TOKEN");
    this.supabaseUrl = validateRequired("SUPABASE_URL");
    this.supabaseKey = validateRequired("SUPABASE_KEY");

    logger.info("Bot configuration initialized successfully");
  }

  public static BotConfiguration getInstance() {
    if (instance == null) {
      instance = new BotConfiguration();
    }
    return instance;
  }

  public String getDiscordBotToken() {
    return discordBotToken;
  }

  public String getSupabaseUrl() {
    return supabaseUrl;
  }

  public String getSupabaseKey() {
    return supabaseKey;
  }

  private String validateRequired(final String envVarName) {
    final String value = System.getenv(envVarName);
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalStateException(envVarName + " environment variable is required but not set");
    }
    return value.trim();
  }

  public void validateConfiguration() {
    logger.info("Validating bot configuration...");

    if (discordBotToken.isEmpty()) {
      throw new IllegalStateException("Discord bot token cannot be empty");
    }

    if (!supabaseUrl.startsWith("https://")) {
      throw new IllegalStateException("Supabase URL must start with https://");
    }

    if (supabaseKey.length() < 10) {
      throw new IllegalStateException("Supabase key appears to be invalid (too short)");
    }

    logger.info("Bot configuration validation completed successfully");
  }
}