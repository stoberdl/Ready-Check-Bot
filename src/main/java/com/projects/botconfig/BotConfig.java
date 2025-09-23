package com.projects.botconfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BotConfig {
  private static final Logger logger = LoggerFactory.getLogger(BotConfig.class);

  private BotConfig() {}

  public static String getBotToken() {
    return BotConfiguration.getInstance().getDiscordBotToken();
  }
}
