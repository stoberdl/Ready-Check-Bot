package com.projects.botConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BotConfig {
  private static final Logger logger = LoggerFactory.getLogger(BotConfig.class);

  private BotConfig() {}

  public static String getBotToken() {
    final String token = System.getenv("DISCORD_BOT_TOKEN");
    if (token == null || token.isEmpty()) {
      logger.error("DISCORD_BOT_TOKEN environment variable not set!");
      throw new IllegalStateException("Bot token not configured");
    }
    return token;
  }
}
