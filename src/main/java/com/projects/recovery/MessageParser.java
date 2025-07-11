package com.projects.recovery;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MessageParser {
  private static final Logger logger = LoggerFactory.getLogger(MessageParser.class);

  private static final Pattern INITIATOR_PATTERN =
      Pattern.compile("\\*\\*([^*]{1,50})\\*\\* started a ready check");

  private static final Pattern USER_STATUS_PATTERN =
      Pattern.compile("([❌✅⏰🚫]) ([^\\n\\r]{1,100})(?:\\s+\\([^)]{1,50}\\))?", Pattern.MULTILINE);

  private static final Pattern READY_COUNT_PATTERN = Pattern.compile("(\\d{1,3})/(\\d{1,3}) ready");

  private MessageParser() {}

  public static RecoveredReadyCheckData parseEmbedContent(final String description) {
    if (description == null || description.trim().isEmpty()) {
      logger.debug("Empty or null embed description");
      return null;
    }

    try {
      final String initiatorName = extractInitiatorName(description);
      final List<UserState> userStates = extractUserStates(description);

      if (initiatorName == null || userStates.isEmpty()) {
        logger.debug(
            "Could not extract initiator ({}) or user states ({})",
            initiatorName,
            userStates.size());
        return null;
      }

      if (!validateParsedData(initiatorName, userStates, description)) {
        return null;
      }

      logger.debug("Parsed ready check: initiator={}, users={}", initiatorName, userStates.size());
      return new RecoveredReadyCheckData(initiatorName, userStates);

    } catch (final Exception e) {
      logger.debug("Exception parsing embed content: {}", e.getMessage());
      return null;
    }
  }

  private static String extractInitiatorName(final String description) {
    Matcher matcher = INITIATOR_PATTERN.matcher(description);
    if (matcher.find()) {
      final String name = matcher.group(1).trim();
      logger.debug("Found initiator: {}", name);
      return name;
    }

    final Pattern recoveryPattern = Pattern.compile("([^']{1,50})'s ready check\\(🔄️?\\)");
    matcher = recoveryPattern.matcher(description);
    if (matcher.find()) {
      final String name = matcher.group(1).trim();
      logger.debug("Found recovery initiator: {}", name);
      return name;
    }

    final Pattern simpleRecoveryPattern = Pattern.compile("([^']{1,50})'s ready check");
    matcher = simpleRecoveryPattern.matcher(description);
    if (matcher.find()) {
      final String name = matcher.group(1).trim();
      logger.debug("Found simple recovery initiator: {}", name);
      return name;
    }

    logger.debug("No initiator found in description");
    return null;
  }

  private static List<UserState> extractUserStates(final String description) {
    final List<UserState> userStates = new ArrayList<>();
    final Matcher matcher = USER_STATUS_PATTERN.matcher(description);

    while (matcher.find()) {
      final String status = matcher.group(1);
      String displayName = matcher.group(2).trim();

      displayName = cleanDisplayName(displayName);

      if (!displayName.isEmpty()) {
        userStates.add(new UserState(displayName, status));
        logger.debug("Found user state: {} -> {}", status, displayName);
      }
    }

    logger.debug("Extracted {} user states", userStates.size());
    return userStates;
  }

  private static String cleanDisplayName(String displayName) {
    displayName = displayName.replace("**", "").replace("*", "");
    displayName = displayName.replace("~~", "");
    displayName = displayName.trim();

    if (displayName.contains(" (")) {
      final int parenIndex = displayName.indexOf(" (");
      displayName = displayName.substring(0, parenIndex).trim();
    }

    return displayName;
  }

  private static boolean validateParsedData(
      final String initiatorName, final List<UserState> userStates, final String description) {

    if (initiatorName.length() > 100) {
      logger.debug("Initiator name too long: {}", initiatorName.length());
      return false;
    }

    if (userStates.size() > 50) {
      logger.debug("Too many users: {}", userStates.size());
      return false;
    }

    final Matcher readyCountMatcher = READY_COUNT_PATTERN.matcher(description);
    if (readyCountMatcher.find()) {
      final int expectedTotal = Integer.parseInt(readyCountMatcher.group(2));
      final int actualTotal =
          (int) userStates.stream().filter(state -> !state.status().equals("🚫")).count();

      if (Math.abs(expectedTotal - actualTotal) > 2) {
        logger.debug("User count mismatch: expected ~{}, found {}", expectedTotal, actualTotal);
      }
    }

    final boolean initiatorFound =
        userStates.stream().anyMatch(state -> state.displayName().equalsIgnoreCase(initiatorName));

    if (!initiatorFound) {
      logger.debug("Initiator {} not found in user states", initiatorName);
    }

    return true;
  }
}
