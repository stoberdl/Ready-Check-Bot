package com.projects.recovery;

import java.time.Instant;
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
      Pattern.compile(
          "([❌✅⏰🚫])\\s+([^\\n\\r(]{1,100})(?:\\s*\\(([^)]{1,200})\\))?", Pattern.MULTILINE);

  private static final Pattern READY_COUNT_PATTERN = Pattern.compile("(\\d{1,3})/(\\d{1,3}) ready");

  private static final Pattern DISCORD_TIMESTAMP_PATTERN = Pattern.compile("<t:(\\d{1,15}):[tT]>");
  private static final Pattern READY_IN_PATTERN = Pattern.compile("ready in (\\d{1,4})min");
  private static final Pattern READY_AT_PATTERN = Pattern.compile("ready at (.+)");
  private static final Pattern UNTIL_PATTERN = Pattern.compile("until (.+)");
  private static final Pattern AUTO_UNREADY_PATTERN =
      Pattern.compile("auto-unready in (\\d{1,4})min");

  private MessageParser() {}

  public static RecoveredReadyCheckData parseEmbedContent(
      final String description, final Instant embedTimestamp) {
    if (description == null || description.trim().isEmpty()) {
      logger.debug("Empty or null embed description");
      return null;
    }

    try {
      final String initiatorName = extractInitiatorName(description);
      final List<EnhancedUserState> userStates =
          extractEnhancedUserStates(description, embedTimestamp);

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

  private static List<EnhancedUserState> extractEnhancedUserStates(
      final String description, final Instant embedTimestamp) {
    final List<EnhancedUserState> userStates = new ArrayList<>();
    final Matcher matcher = USER_STATUS_PATTERN.matcher(description);

    logger.debug("Full embed description for timing extraction:\n{}", description);

    while (matcher.find()) {
      final String status = matcher.group(1);
      String displayName = matcher.group(2).trim();
      final String timingInfo = matcher.group(3);

      logger.debug(
          "Raw user line matched - Status: '{}', Name: '{}', Timing: '{}'",
          status,
          displayName,
          timingInfo);

      displayName = cleanDisplayName(displayName);

      if (!displayName.isEmpty()) {
        final TimingData timing = parseTimingInfo(timingInfo, embedTimestamp);
        userStates.add(new EnhancedUserState(displayName, status, timing));
        logger.debug(
            "Found enhanced user state: {} -> {} with timing: {}", status, displayName, timing);
      }
    }

    logger.debug("Extracted {} enhanced user states", userStates.size());
    return userStates;
  }

  private static TimingData parseTimingInfo(final String timingInfo, final Instant embedTimestamp) {
    if (timingInfo == null || timingInfo.trim().isEmpty()) {
      return new TimingData(TimingType.NONE, null, null);
    }

    final String cleaned = timingInfo.trim();
    logger.debug("Parsing timing info: '{}'", cleaned);

    Matcher matcher = DISCORD_TIMESTAMP_PATTERN.matcher(cleaned);
    if (matcher.find()) {
      final long timestamp = Long.parseLong(matcher.group(1));
      final Instant targetTime = Instant.ofEpochSecond(timestamp);
      logger.debug("Found Discord timestamp: {} -> {}", timestamp, targetTime);

      if (UNTIL_PATTERN.matcher(cleaned).find()) {
        logger.debug("Classified as READY_UNTIL");
        return new TimingData(TimingType.READY_UNTIL, targetTime, null);
      } else {
        logger.debug("Classified as READY_AT");
        return new TimingData(TimingType.READY_AT, targetTime, null);
      }
    }

    matcher = READY_IN_PATTERN.matcher(cleaned);
    if (matcher.find()) {
      final int minutes = Integer.parseInt(matcher.group(1));
      final Instant targetTime = embedTimestamp.plusSeconds(minutes * 60L);
      logger.debug("Found 'ready in {}min' -> target time: {}", minutes, targetTime);
      return new TimingData(TimingType.READY_AT, targetTime, null);
    }

    matcher = AUTO_UNREADY_PATTERN.matcher(cleaned);
    if (matcher.find()) {
      final int minutes = Integer.parseInt(matcher.group(1));
      logger.debug("Found 'auto-unready in {}min'", minutes);
      return new TimingData(TimingType.AUTO_UNREADY, null, minutes);
    }

    matcher = UNTIL_PATTERN.matcher(cleaned);
    if (matcher.find()) {
      logger.debug("Found generic 'until' pattern: {}", cleaned);
      return new TimingData(TimingType.READY_UNTIL, null, cleaned);
    }

    matcher = READY_AT_PATTERN.matcher(cleaned);
    if (matcher.find()) {
      logger.debug("Found generic 'ready at' pattern: {}", cleaned);
      return new TimingData(TimingType.READY_AT, null, cleaned);
    }

    logger.debug("No timing patterns matched for: '{}'", cleaned);
    return new TimingData(TimingType.NONE, null, null);
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
      final String initiatorName,
      final List<EnhancedUserState> userStates,
      final String description) {

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

  public enum TimingType {
    NONE,
    READY_AT,
    READY_UNTIL,
    AUTO_UNREADY
  }

  public record TimingData(TimingType type, Instant targetTime, Object data) {
    @Override
    public String toString() {
      return String.format("TimingData{type=%s, targetTime=%s, data=%s}", type, targetTime, data);
    }
  }

  public record EnhancedUserState(String displayName, String status, TimingData timing) {
    public boolean isReady() {
      return "✅".equals(status);
    }

    @Override
    public String toString() {
      return String.format("%s %s %s", status, displayName, timing);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null || getClass() != obj.getClass()) return false;
      EnhancedUserState userState = (EnhancedUserState) obj;
      return displayName.equals(userState.displayName) && status.equals(userState.status);
    }
  }
}
