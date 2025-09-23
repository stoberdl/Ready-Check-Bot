package com.projects.botconfig;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

class BotConfigurationTest {

  @BeforeEach
  @AfterEach
  void resetBotConfigurationSingleton() throws Exception {
    // Reset the singleton instance for clean test isolation
    Field instanceField = BotConfiguration.class.getDeclaredField("instance");
    instanceField.setAccessible(true);
    instanceField.set(null, null);
  }

  @Test
  @DisplayName("Should maintain singleton pattern")
  void testSingletonPattern() {
    // Skip this test if environment variables are not set
    assumeTrue(System.getenv("DISCORD_BOT_TOKEN") != null, "DISCORD_BOT_TOKEN not set");
    assumeTrue(System.getenv("SUPABASE_URL") != null, "SUPABASE_URL not set");
    assumeTrue(System.getenv("SUPABASE_KEY") != null, "SUPABASE_KEY not set");

    BotConfiguration config1 = BotConfiguration.getInstance();
    BotConfiguration config2 = BotConfiguration.getInstance();

    assertSame(config1, config2);
  }
}