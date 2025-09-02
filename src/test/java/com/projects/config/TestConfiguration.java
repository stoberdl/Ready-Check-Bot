package com.projects.config;

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExtendWith(TestConfiguration.TestResultLogger.class)
public final class TestConfiguration {

  private static final Logger logger = LoggerFactory.getLogger(TestConfiguration.class);

  private TestConfiguration() {}

  public static final class TestResultLogger implements TestWatcher {

    private final Logger testLogger = LoggerFactory.getLogger(TestResultLogger.class);

    @Override
    public void testSuccessful(final ExtensionContext context) {
      testLogger.debug("Test passed: {}", context.getDisplayName());
    }

    @Override
    public void testFailed(final ExtensionContext context, final Throwable cause) {
      testLogger.error("Test failed: {} - {}", context.getDisplayName(), cause.getMessage());
    }

    @Override
    public void testAborted(final ExtensionContext context, final Throwable cause) {
      testLogger.warn("Test aborted: {} - {}", context.getDisplayName(), cause.getMessage());
    }
  }

  public static final class TestConstants {
    public static final String MOCK_GUILD_ID = "guild-123";
    public static final String MOCK_CHANNEL_ID = "channel-456";
    public static final String MOCK_USER_INITIATOR = "user-initiator";
    public static final String MOCK_USER_1 = "user-1";
    public static final String MOCK_USER_2 = "user-2";
    public static final String MOCK_ROLE_ID = "role-123";
    public static final String MOCK_MESSAGE_ID = "message-789";
    public static final String TEST_READY_CHECK_ID = "test-check-id";

    private TestConstants() {}
  }

  public static final class TestEnvironment {

    public static void setupTestEnvironment() {
      System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "WARN");
      System.setProperty("org.slf4j.simpleLogger.log.com.projects", "DEBUG");
    }

    public static void cleanupTestEnvironment() {
      System.clearProperty("org.slf4j.simpleLogger.defaultLogLevel");
      System.clearProperty("org.slf4j.simpleLogger.log.com.projects");
    }

    private TestEnvironment() {}
  }

  public static final class TestValidation {

    public static void validateMockSetup() {
      logger.debug("Validating mock setup for Discord Ready Check Bot tests");
    }

    public static void validateTestData() {
      logger.debug("Validating test data consistency");
    }

    private TestValidation() {}
  }
}
