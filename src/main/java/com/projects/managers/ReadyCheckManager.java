package com.projects.managers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.awt.Color;
import java.io.*;
import java.lang.reflect.Type;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

public class ReadyCheckManager {
  private static final Map<String, ReadyCheck> activeReadyChecks = new ConcurrentHashMap<>();
  private static final Map<String, SavedReadyCheck> savedReadyChecks = new ConcurrentHashMap<>();
  private static final Map<String, Boolean> mentionPreferences = new ConcurrentHashMap<>();
  private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
  private static final String SAVE_FILE = "saved_ready_checks.json";
  private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
  private static final long TWO_HOURS_MS = 2 * 60 * 60 * 1000;
  private static JDA globalJDA;

  static {
    loadSavedConfigurations();
    startPeriodicUpdater();
  }

  public enum ReadyCheckStatus {
    ACTIVE,
    COMPLETED
  }

  private static void startPeriodicUpdater() {
    scheduler.scheduleWithFixedDelay(
        () -> {
          try {
            updateAllReadyCheckCountdowns();
          } catch (Exception e) {
            System.err.println("Error in periodic updater: " + e.getMessage());
          }
        },
        1,
        1,
        TimeUnit.MINUTES);
  }

  // Update all active ready checks with countdowns
  private static void updateAllReadyCheckCountdowns() {
    if (globalJDA == null) return;

    for (ReadyCheck readyCheck : activeReadyChecks.values()) {
      // Only update if there are scheduled users
      if (!readyCheck.getScheduledUsers().isEmpty()) {
        updateReadyCheckEmbed(readyCheck.getId(), globalJDA);
      }
    }
  }

  public static void setJDA(JDA jda) {
    globalJDA = jda;
  }

  public static void setMentionPreference(String readyCheckId, boolean mentionPeople) {
    mentionPreferences.put(readyCheckId, mentionPeople);
  }

  public static boolean getMentionPreference(String readyCheckId) {
    return mentionPreferences.getOrDefault(readyCheckId, true);
  }

  public static void createReadyCheckResponse(
      Object event,
      String readyCheckId,
      List<Member> targetMembers,
      Member initiator,
      String description) {
    ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    if (readyCheck == null) {
      return;
    }

    JDA jda = getJDAFromEvent(event);
    if (globalJDA == null && jda != null) {
      setJDA(jda);
    }

    String betterDescription = buildCheckDescription(initiator, targetMembers, description);
    readyCheck.setDescription(betterDescription);

    EmbedBuilder embed = buildReadyCheckEmbed(readyCheck, jda, betterDescription);
    List<Button> mainButtons = createMainButtons(readyCheckId);
    List<Button> saveButton = createSaveButton(readyCheckId);
    String mentions = createMentions(readyCheck, jda);

    if (event instanceof SlashCommandInteractionEvent) {
      handleSlashCommandResponse(
          (SlashCommandInteractionEvent) event,
          embed,
          mainButtons,
          saveButton,
          mentions,
          readyCheckId);
    } else if (event instanceof StringSelectInteractionEvent) {
      handleSelectMenuResponse(
          (StringSelectInteractionEvent) event,
          embed,
          mainButtons,
          saveButton,
          mentions,
          readyCheckId);
    }
  }

  private static String buildCheckDescription(
      Member initiator, List<Member> targetMembers, String originalDescription) {
    boolean initiatorInTargets = targetMembers.contains(initiator);
    int totalUsers = targetMembers.size() + (initiatorInTargets ? 0 : 1);

    if (originalDescription.contains("specific users")) {
      return "**"
          + initiator.getEffectiveName()
          + "** started a ready check for "
          + totalUsers
          + " users";
    }

    return originalDescription;
  }

  public static boolean isReadyCheckOngoing(String readyCheckId) {
    ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    if (readyCheck == null) {
      return false; // If it doesn't exist, consider it completed
    }

    return !allNonPassedReady(readyCheck);
  }

  public static String findExistingReadyCheck(
      String guildId, SavedReadyCheck savedCheck, String initiatorId) {
    return activeReadyChecks.values().stream()
        .filter(check -> check.getGuildId().equals(guildId))
        .filter(check -> isReadyCheckOngoing(check.getId())) // Only return non-completed checks
        .filter(
            check -> {
              if (savedCheck.isUserBased()) {
                Set<String> checkUserIds = new HashSet<>(check.getTargetUsers());
                checkUserIds.add(initiatorId);
                return checkUserIds.equals(new HashSet<>(savedCheck.getUserIds()));
              } else {
                return savedCheck.getRoleId().equals(check.getRoleId());
              }
            })
        .map(ReadyCheck::getId)
        .findFirst()
        .orElse(null);
  }

  public static String findExistingReadyCheck(
      String guildId, List<String> targetUserIds, String initiatorId) {
    return findExistingReadyCheckInternal(guildId, createUserSet(targetUserIds, initiatorId));
  }

  private static JDA getJDAFromEvent(Object event) {
    if (event instanceof SlashCommandInteractionEvent) {
      return ((SlashCommandInteractionEvent) event).getJDA();
    } else if (event instanceof StringSelectInteractionEvent) {
      return ((StringSelectInteractionEvent) event).getJDA();
    }
    return null;
  }

  public static String createUserReadyCheck(
      String guildId, String channelId, String initiatorId, List<Member> targetMembers) {
    return createReadyCheck(
        guildId, channelId, initiatorId, null, targetMembers, "**Ready Check** for specific users");
  }

  public static String createReadyCheck(
      String guildId,
      String channelId,
      String initiatorId,
      String roleId,
      List<Member> targetMembers) {
    return createReadyCheck(
        guildId, channelId, initiatorId, roleId, targetMembers, "**Ready Check** for role members");
  }

  /**
   * Parse time input for "r in X" context - always treats numbers as minutes Examples: "5", "30",
   * "120"
   */
  private static long parseTimeInputAsMinutes(String timeInput) throws Exception {
    String input = timeInput.trim();

    if (input.matches("\\d+")) {
      long minutes = Long.parseLong(input);
      if (minutes >= 1 && minutes <= 1440) { // 1 minute to 24 hours
        return minutes;
      } else {
        throw new IllegalArgumentException("Minutes must be between 1 and 1440");
      }
    }

    throw new IllegalArgumentException(
        "For 'r in X', please use just the number of minutes (e.g., '5', '30')");
  }

  /**
   * Parse time input for "r at X" context - treats as time with smart AM/PM Examples: "5", "7:30",
   * "7pm", "19:30"
   */
  private static long parseTimeInputAsTime(String timeInput) throws Exception {
    return parseAsTime(timeInput.trim().toLowerCase());
  }

  /** Parse input as time with smart AM/PM detection */
  private static long parseAsTime(String input) throws Exception {
    LocalTime now = LocalTime.now();
    LocalTime targetTime;

    try {
      // Handle explicit AM/PM first
      if (input.contains("pm") || input.contains("am")) {
        targetTime = parseExplicitAmPm(input);
      }
      // Handle 24-hour format
      else if (input.contains(":") && is24HourFormat(input)) {
        targetTime = parse24HourFormat(input);
      }
      // Handle ambiguous formats with smart detection
      else {
        targetTime = parseWithSmartDetection(input, now);
      }

      return calculateMinutesUntil(now, targetTime);

    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Invalid time format: " + input);
    }
  }

  /** Parse explicit AM/PM formats like "7pm", "7:30am" */
  private static LocalTime parseExplicitAmPm(String input) throws Exception {
    String normalizedInput = input.toUpperCase().replaceAll("\\s+", "");

    if (normalizedInput.matches("\\d+(PM|AM)")) {
      // Simple hour like "7PM"
      int hour = Integer.parseInt(normalizedInput.replaceAll("[^0-9]", ""));
      boolean isPM = normalizedInput.contains("PM");

      if (hour >= 1 && hour <= 12) {
        if (isPM) {
          return LocalTime.of(hour == 12 ? 12 : hour + 12, 0);
        } else {
          return LocalTime.of(hour == 12 ? 0 : hour, 0);
        }
      } else {
        throw new IllegalArgumentException("Hour must be between 1 and 12");
      }
    } else {
      // Handle formats like "7:30PM"
      return LocalTime.parse(normalizedInput, DateTimeFormatter.ofPattern("h:mma"));
    }
  }

  /** Check if colon format is 24-hour (hour >= 13) */
  private static boolean is24HourFormat(String input) {
    String[] parts = input.split(":");
    if (parts.length >= 1) {
      try {
        int hour = Integer.parseInt(parts[0]);
        return hour >= 13 || hour == 0;
      } catch (NumberFormatException e) {
        return false;
      }
    }
    return false;
  }

  /** Parse 24-hour format like "14:30" or "0:30" */
  private static LocalTime parse24HourFormat(String input) throws Exception {
    String[] parts = input.split(":");
    if (parts.length == 2) {
      int hour = Integer.parseInt(parts[0]);
      int minute = Integer.parseInt(parts[1]);

      if (hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59) {
        return LocalTime.of(hour, minute);
      }
    }
    throw new IllegalArgumentException("Invalid 24-hour format");
  }

  /**
   * Parse ambiguous formats with smart AM/PM detection Handles: "7", "7:30" (no AM/PM specified)
   */
  private static LocalTime parseWithSmartDetection(String input, LocalTime now) throws Exception {
    int hour, minute = 0;

    if (input.contains(":")) {
      String[] parts = input.split(":");
      hour = Integer.parseInt(parts[0]);
      minute = Integer.parseInt(parts[1]);

      if (minute < 0 || minute > 59) {
        throw new IllegalArgumentException("Invalid minutes");
      }
    } else {
      // Simple number that passed the "isSimpleMinutes" check (1-12)
      hour = Integer.parseInt(input);
    }

    if (hour < 1 || hour > 12) {
      throw new IllegalArgumentException("Hour must be between 1 and 12 for ambiguous format");
    }

    return smartAmPmDetection(hour, minute, now);
  }

  /** Smart AM/PM detection with better boundary logic */
  private static LocalTime smartAmPmDetection(int hour, int minute, LocalTime now) {
    // Convert hour to both AM and PM versions
    LocalTime amTime = LocalTime.of(hour == 12 ? 0 : hour, minute);
    LocalTime pmTime = LocalTime.of(hour == 12 ? 12 : hour + 12, minute);

    // Current hour for logic
    int currentHour = now.getHour();

    // Special logic based on current time ranges
    if (currentHour >= 22 || currentHour <= 5) {
      // Late night (10pm-5am): default to next reasonable time
      // If they say "7" at 11pm, they probably mean 7am tomorrow
      return amTime.isAfter(now) ? amTime : pmTime;
    } else if (currentHour >= 6 && currentHour <= 11) {
      // Morning (6am-11am): prefer AM unless it's in the past
      return amTime.isAfter(now) ? amTime : pmTime;
    } else {
      // Afternoon/Evening (12pm-9pm): prefer PM unless it's in the past
      return pmTime.isAfter(now) ? pmTime : amTime.plusHours(24);
    }
  }

  /** Calculate minutes until target time, handling day rollover and rounding properly */
  private static long calculateMinutesUntil(LocalTime now, LocalTime targetTime) {
    Duration duration = Duration.between(now, targetTime);

    long totalSeconds = duration.getSeconds();
    long minutesUntil = (totalSeconds + 59) / 60; // This rounds up

    // If time is in the past or now, assume they mean tomorrow
    if (minutesUntil <= 0) {
      duration = Duration.between(now, targetTime.plusHours(24));
      totalSeconds = duration.getSeconds();
      minutesUntil = (totalSeconds + 59) / 60; // Round up for tomorrow too
    }

    return minutesUntil;
  }

  /** Updated scheduleReadyAt to store exact target timestamp */
  public static String scheduleReadyAt(
      String readyCheckId, String timeInput, String userId, JDA jda) {
    try {
      long delayMinutes = parseTimeInputAsMinutes(timeInput); // For "r in X" - always minutes
      ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
      if (readyCheck == null) {
        throw new IllegalArgumentException("Ready check not found");
      }

      ensureUserInTargets(readyCheck, userId);

      // Calculate exact target timestamp (rounded to the minute)
      long currentTimeMs = System.currentTimeMillis();
      long targetTimeMs = currentTimeMs + (delayMinutes * 60 * 1000);

      // Round to the exact minute to avoid second-level precision issues
      targetTimeMs = (targetTimeMs / 60000) * 60000; // Truncate to minute boundary

      readyCheck.getScheduledUsers().put(userId, targetTimeMs);
      readyCheck.getReadyUsers().remove(userId);
      readyCheck.getPassedUsers().remove(userId);

      scheduler.schedule(
          () -> sendReadyReminder(readyCheckId, userId, jda), delayMinutes, TimeUnit.MINUTES);

      LocalTime readyTime = LocalTime.now().plusMinutes(delayMinutes);
      return formatTimeForDisplay(timeInput, delayMinutes, readyTime);
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid time format: " + timeInput);
    }
  }

  /** Updated scheduleReadyAtSmart to store exact target timestamp */
  public static String scheduleReadyAtSmart(
      String readyCheckId, String timeInput, String userId, JDA jda) throws Exception {
    ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    if (readyCheck == null) {
      throw new IllegalArgumentException("Ready check not found");
    }

    long minutesUntilReady = parseTimeInputAsTime(timeInput); // For "r at X" - smart time parsing

    if (minutesUntilReady <= 0) {
      throw new IllegalArgumentException("Time must be in the future");
    }

    readyCheck.getReadyUsers().remove(userId);
    readyCheck.getUserTimers().remove(userId);

    // Calculate exact target timestamp and round to minute boundary
    long currentTimeMs = System.currentTimeMillis();
    long targetTimeMs = currentTimeMs + (minutesUntilReady * 60 * 1000);
    targetTimeMs = (targetTimeMs / 60000) * 60000; // Round to exact minute

    readyCheck.getScheduledUsers().put(userId, targetTimeMs);

    scheduler.schedule(
        () -> sendReadyReminder(readyCheckId, userId, jda), minutesUntilReady, TimeUnit.MINUTES);

    // Return the exact target time for confirmation
    Instant targetInstant = Instant.ofEpochMilli(targetTimeMs);
    LocalTime targetTime = LocalTime.ofInstant(targetInstant, ZoneId.systemDefault());
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("h:mm a");
    return targetTime.format(formatter);
  }

  /** Updated scheduleReadyUntil to store timestamp for timezone conversion */
  public static String scheduleReadyUntil(
      String readyCheckId, String timeInput, String userId, JDA jda) {
    try {
      long delayMinutes = parseTimeInputAsTime(timeInput);
      ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
      if (readyCheck == null) {
        throw new IllegalArgumentException("Ready check not found");
      }

      ensureUserInTargets(readyCheck, userId);

      // Calculate exact "until" timestamp rounded to minute boundary
      long currentTimeMs = System.currentTimeMillis();
      long untilTimestamp = currentTimeMs + (delayMinutes * 60 * 1000);
      untilTimestamp = (untilTimestamp / 60000) * 60000; // Round to exact minute

      // Schedule user to automatically pass at that time
      scheduler.schedule(
          () -> autoPassUser(readyCheckId, userId, jda), delayMinutes, TimeUnit.MINUTES);

      // Store the timestamp for Discord formatting (convert to seconds)
      String discordTimestamp = "<t:" + (untilTimestamp / 1000) + ":t>";
      readyCheck.getUserUntilTimes().put(userId, discordTimestamp);

      return discordTimestamp;
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid time format: " + timeInput);
    }
  }

  /** Helper method to format timestamps for display consistency */
  private static String formatScheduledTime(long timestampMs, long minutesLeft) {
    if (minutesLeft >= 60) {
      // More than 1 hour: use Discord timestamp (timezone-aware)
      long discordTimestamp = timestampMs / 1000;
      return "<t:" + discordTimestamp + ":t>";
    } else {
      // Less than 1 hour: show minutes (no timezone issues)
      return minutesLeft + "min";
    }
  }

  public static void updateReadyCheckEmbed(String readyCheckId, JDA jda) {
    ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    if (readyCheck == null || readyCheck.getMessageId() == null) return;

    Guild guild = jda.getGuildById(readyCheck.getGuildId());
    if (guild == null) return;

    TextChannel channel = guild.getTextChannelById(readyCheck.getChannelId());
    if (channel == null) return;

    // Check if not all ready anymore and we have a completion message to delete
    boolean wasCompleted = readyCheck.getStatus() == ReadyCheckStatus.COMPLETED;
    boolean nowCompleted = allNonPassedReady(readyCheck);

    if (wasCompleted && !nowCompleted && readyCheck.getCompletionMessageId() != null) {
      channel
          .retrieveMessageById(readyCheck.getCompletionMessageId())
          .queue(message -> message.delete().queue(null, error -> {}), error -> {});
      readyCheck.setCompletionMessageId(null);
      readyCheck.setStatus(ReadyCheckStatus.ACTIVE);
    }

    cleanupExpiredScheduledUsers(readyCheck);

    channel
        .retrieveMessageById(readyCheck.getMessageId())
        .queue(
            message -> {
              EmbedBuilder embed =
                  buildReadyCheckEmbed(readyCheck, jda, readyCheck.getDescription());
              List<Button> mainButtons = createMainButtons(readyCheckId);
              List<Button> saveButton = createSaveButton(readyCheckId);

              message
                  .editMessageEmbeds(embed.build())
                  .setComponents(ActionRow.of(mainButtons), ActionRow.of(saveButton))
                  .queue();
            },
            error -> {
              // Message might have been deleted
            });
  }

  public static boolean markUserReady(String readyCheckId, String userId) {
    ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    if (readyCheck == null) {
      //      logger.warn("Attempted to mark user ready for non-existent ready check: {}",
      // readyCheckId);
      return false;
    }

    readyCheck.getUserTimers().remove(userId);
    readyCheck.getScheduledUsers().remove(userId);

    readyCheck.getReadyUsers().add(userId);

    boolean allReady =
        readyCheck.getTargetUsers().stream()
            .allMatch(targetUserId -> readyCheck.getReadyUsers().contains(targetUserId));

    if (allReady && readyCheck.getStatus() == ReadyCheckStatus.ACTIVE) {
      readyCheck.setStatus(ReadyCheckStatus.COMPLETED);
      // logger.info("Ready check completed: {}", readyCheckId);
    }

    //   logger.info("User {} marked as ready for ready check: {}", userId, readyCheckId);
    return allReady;
  }

  // Clean up expired scheduled users (when countdown reaches 0 or goes negative)
  private static void cleanupExpiredScheduledUsers(ReadyCheck readyCheck) {
    long currentTime = System.currentTimeMillis();
    Set<String> expiredUsers = new HashSet<>();

    for (Map.Entry<String, Long> entry : readyCheck.getScheduledUsers().entrySet()) {
      if (entry.getValue() <= currentTime) {
        expiredUsers.add(entry.getKey());
      }
    }
    for (String userId : expiredUsers) {
      readyCheck.getScheduledUsers().remove(userId);
    }
  }

  public static void notifyAllReady(String readyCheckId, JDA jda) {
    ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    if (readyCheck == null) return;

    Guild guild = jda.getGuildById(readyCheck.getGuildId());
    if (guild == null) return;

    TextChannel channel = guild.getTextChannelById(readyCheck.getChannelId());
    if (channel == null) return;

    readyCheck.setStatus(ReadyCheckStatus.COMPLETED);

    // Create mentions for all ready users (excluding passed users)
    Set<String> allUsers = new HashSet<>(readyCheck.getTargetUsers());
    allUsers.add(readyCheck.getInitiatorId());

    String readyUserMentions = "";
    if (getMentionPreference(readyCheckId)) {
      readyUserMentions =
          allUsers.stream()
              .filter(userId -> readyCheck.getReadyUsers().contains(userId))
              .filter(userId -> !readyCheck.getPassedUsers().contains(userId))
              .map(
                  userId -> {
                    Member member = guild.getMemberById(userId);
                    return member != null ? member.getAsMention() : null;
                  })
              .filter(Objects::nonNull)
              .collect(Collectors.joining(" "));
    }

    EmbedBuilder completedEmbed =
        new EmbedBuilder()
            .setTitle("🎉 Everyone is ready!")
            .setDescription(readyCheck.getDescription())
            .setColor(Color.GREEN)
            .setTimestamp(Instant.now());

    String messageContent = readyUserMentions.isEmpty() ? "" : readyUserMentions;

    channel
        .sendMessage(messageContent)
        .setEmbeds(completedEmbed.build())
        .queue(
            message -> {
              readyCheck.setCompletionMessageId(message.getId());
              scheduleMessageDeletion(message, 5);
            });

    updateReadyCheckEmbed(readyCheckId, jda);
  }

  public static void ensureUserInReadyCheck(String readyCheckId, String userId) {
    ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    if (readyCheck != null) {
      ensureUserInTargets(readyCheck, userId);
    }
  }

  public static boolean toggleUserReady(String readyCheckId, String userId) {
    ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    if (readyCheck == null) return false;

    ensureUserInTargets(readyCheck, userId);

    if (readyCheck.getReadyUsers().contains(userId)) {
      readyCheck.getReadyUsers().remove(userId);
      readyCheck.setLastInteractionTime(System.currentTimeMillis());
      return false;
    } else {
      readyCheck.getReadyUsers().add(userId);
      readyCheck.getPassedUsers().remove(userId);
      readyCheck.getScheduledUsers().remove(userId);
      readyCheck.getUserUntilTimes().remove(userId);
      readyCheck.setLastInteractionTime(System.currentTimeMillis());
      return true;
    }
  }

  public static void markUserPassed(String readyCheckId, String userId) {
    ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    if (readyCheck == null) return;

    ensureUserInTargets(readyCheck, userId);
    readyCheck.getPassedUsers().add(userId);
    readyCheck.getReadyUsers().remove(userId);
    readyCheck.getScheduledUsers().remove(userId);
    readyCheck.getUserUntilTimes().remove(userId);
    readyCheck.setLastInteractionTime(System.currentTimeMillis());
  }

  public static boolean checkIfAllReady(String readyCheckId) {
    ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    return readyCheck != null && allNonPassedReady(readyCheck);
  }

  public static void unmarkUserPassed(String readyCheckId, String userId) {
    ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    if (readyCheck == null) return;

    readyCheck.getPassedUsers().remove(userId);
    readyCheck.setLastInteractionTime(System.currentTimeMillis());
  }

  /**
   * Updated findActiveReadyCheckForUser to include passed users This allows "r" commands to work
   * even if user was previously passed
   */
  public static String findActiveReadyCheckForUser(String guildId, String userId) {
    long twoHoursAgo = System.currentTimeMillis() - TWO_HOURS_MS;

    return activeReadyChecks.values().stream()
        .filter(readyCheck -> readyCheck.getGuildId().equals(guildId))
        .filter(readyCheck -> readyCheck.getCreatedTime() >= twoHoursAgo)
        .filter(readyCheck -> readyCheck.getStatus() == ReadyCheckStatus.ACTIVE)
        .filter(
            readyCheck -> {
              // Include user if they're in target users OR if they're passed (they can re-engage)
              return readyCheck.getTargetUsers().contains(userId)
                  || readyCheck.getPassedUsers().contains(userId)
                  || readyCheck.getInitiatorId().equals(userId);
            })
        .map(ReadyCheck::getId)
        .findFirst()
        .orElse(null);
  }

  /**
   * Updated saveReadyCheck to prioritize most recently saved configs Removes duplicates and adds
   * the new save to the end (most recent)
   */
  public static void saveReadyCheck(String readyCheckId) {
    ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    if (readyCheck == null) return;

    String guildId = readyCheck.getGuildId();
    boolean mentionPeople = getMentionPreference(readyCheckId);
    SavedReadyCheck newSavedCheck;

    if (readyCheck.getRoleId() != null) {
      newSavedCheck = new SavedReadyCheck(readyCheck.getRoleId(), false, mentionPeople);
    } else {
      List<String> allUserIds = new ArrayList<>(readyCheck.getTargetUsers());
      allUserIds.add(readyCheck.getInitiatorId());
      newSavedCheck = new SavedReadyCheck(allUserIds, true, mentionPeople);
    }

    String newKey =
        guildId
            + "_"
            + (newSavedCheck.isUserBased()
                ? newSavedCheck.getUserIds().hashCode()
                : newSavedCheck.getRoleId());

    savedReadyChecks.remove(newKey);

    // remove any other entries for this guild that match the same configuration
    savedReadyChecks
        .entrySet()
        .removeIf(
            entry -> {
              String key = entry.getKey();
              SavedReadyCheck savedCheck = entry.getValue();

              if (!key.startsWith(guildId + "_")) return false;

              if (savedCheck.isUserBased() == newSavedCheck.isUserBased()) {
                if (savedCheck.isUserBased()) {
                  Set<String> existingUsers = new HashSet<>(savedCheck.getUserIds());
                  Set<String> newUsers = new HashSet<>(newSavedCheck.getUserIds());
                  return existingUsers.equals(newUsers);
                } else {
                  return Objects.equals(savedCheck.getRoleId(), newSavedCheck.getRoleId());
                }
              }
              return false;
            });

    // Add the new configuration (this will be at the end, making it most recent)
    savedReadyChecks.put(newKey, newSavedCheck);
    saveSavedConfigurations();
  }

  /** Send ready check directly to channel (for message-based commands) */
  public static void sendReadyCheckToChannel(
      TextChannel channel,
      String readyCheckId,
      List<Member> targetMembers,
      Member initiator,
      String description,
      JDA jda) {
    ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    if (readyCheck == null) return;

    if (globalJDA == null) {
      setJDA(jda);
    }

    String betterDescription = buildCheckDescription(initiator, targetMembers, description);
    readyCheck.setDescription(betterDescription);

    EmbedBuilder embed = buildReadyCheckEmbed(readyCheck, jda, betterDescription);
    List<Button> mainButtons = createMainButtons(readyCheckId);
    List<Button> saveButton = createSaveButton(readyCheckId);
    String mentions = createMentions(readyCheck, jda);

    channel
        .sendMessage(mentions)
        .setEmbeds(embed.build())
        .setComponents(ActionRow.of(mainButtons), ActionRow.of(saveButton))
        .queue(
            message -> {
              readyCheck.setMessageId(message.getId());
            });
  }

  public static List<SavedReadyCheck> getSavedReadyChecks(String guildId) {
    List<SavedReadyCheck> checks =
        savedReadyChecks.entrySet().stream()
            .filter(entry -> entry.getKey().startsWith(guildId + "_"))
            .map(Map.Entry::getValue)
            .collect(Collectors.toList());

    // Reverse the list so most recently saved appears first
    Collections.reverse(checks);
    return checks;
  }

  public static void resendExistingReadyCheck(String readyCheckId, JDA jda) {
    ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    if (readyCheck == null) return;

    Guild guild = jda.getGuildById(readyCheck.getGuildId());
    if (guild == null) return;

    TextChannel channel = guild.getTextChannelById(readyCheck.getChannelId());
    if (channel == null) return;

    EmbedBuilder embed = buildReadyCheckEmbed(readyCheck, jda, readyCheck.getDescription());
    List<Button> mainButtons = createMainButtons(readyCheckId);
    List<Button> saveButton = createSaveButton(readyCheckId);

    String mentions = createMentions(readyCheck, jda);

    channel
        .sendMessage(mentions)
        .setEmbeds(embed.build())
        .setComponents(ActionRow.of(mainButtons), ActionRow.of(saveButton))
        .queue(
            message -> {
              readyCheck.setMessageId(message.getId());
            });
  }

  public static void setReadyCheckMessageId(String readyCheckId, String messageId) {
    ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    if (readyCheck != null) {
      readyCheck.setMessageId(messageId);
    }
  }

  // Private Helper Methods
  private static String createReadyCheck(
      String guildId,
      String channelId,
      String initiatorId,
      String roleId,
      List<Member> targetMembers,
      String description) {
    String readyCheckId = UUID.randomUUID().toString();

    List<String> targetUserIds =
        targetMembers.stream().map(Member::getId).collect(Collectors.toList());

    ReadyCheck readyCheck =
        new ReadyCheck(readyCheckId, guildId, channelId, initiatorId, roleId, targetUserIds);
    readyCheck.setDescription(description);

    // Mark initiator as ready
    readyCheck.getReadyUsers().add(initiatorId);
    readyCheck.getTargetUsers().add(initiatorId);

    activeReadyChecks.put(readyCheckId, readyCheck);
    return readyCheckId;
  }

  private static void ensureUserInTargets(ReadyCheck readyCheck, String userId) {
    readyCheck.getTargetUsers().add(userId);
  }

  private static void scheduleAutoUnready(
      String readyCheckId, String userId, int minutes, JDA jda) {
    ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    if (readyCheck != null) {
      readyCheck.getUserTimers().put(userId, minutes);
    }

    scheduler.schedule(() -> autoUnreadyUser(readyCheckId, userId, jda), minutes, TimeUnit.MINUTES);
  }

  private static void autoUnreadyUser(String readyCheckId, String userId, JDA jda) {
    ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    if (readyCheck == null) return;

    readyCheck.getReadyUsers().remove(userId);
    readyCheck.getUserTimers().remove(userId);
    readyCheck.getUserUntilTimes().remove(userId);

    updateReadyCheckEmbed(readyCheckId, jda);

    Guild guild = jda.getGuildById(readyCheck.getGuildId());
    if (guild == null) return;

    TextChannel channel = guild.getTextChannelById(readyCheck.getChannelId());
    if (channel == null) return;

    Member member = guild.getMemberById(userId);
    if (member == null) return;

    String messageLink = buildMessageLink(readyCheck);
    String messageText =
        getMentionPreference(readyCheckId)
            ? "⏰ " + member.getAsMention() + " your ready time has expired! " + messageLink
            : "⏰ **" + member.getEffectiveName() + "** your ready time has expired! " + messageLink;

    channel.sendMessage(messageText).queue(message -> scheduleMessageDeletion(message, 5));
  }

  // Auto pass user when "ready until" time expires - NO PUBLIC MESSAGE
  private static void autoPassUser(String readyCheckId, String userId, JDA jda) {
    ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    if (readyCheck == null) return;

    readyCheck.getPassedUsers().add(userId);
    readyCheck.getReadyUsers().remove(userId);
    readyCheck.getUserUntilTimes().remove(userId);

    updateReadyCheckEmbed(readyCheckId, jda);
    // No public message - user is silently marked as passed
  }

  private static void sendReadyReminder(String readyCheckId, String userId, JDA jda) {
    ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    if (readyCheck == null) return;

    Guild guild = jda.getGuildById(readyCheck.getGuildId());
    if (guild == null) return;

    TextChannel channel = guild.getTextChannelById(readyCheck.getChannelId());
    if (channel == null) return;

    Member member = guild.getMemberById(userId);
    if (member == null) return;

    readyCheck.getScheduledUsers().remove(userId);

    String messageLink = buildMessageLink(readyCheck);
    String messageText =
        getMentionPreference(readyCheckId)
            ? "⏰ " + member.getAsMention() + " it's time to be ready! " + messageLink
            : "⏰ **" + member.getEffectiveName() + "** it's time to be ready! " + messageLink;

    channel.sendMessage(messageText).queue(message -> scheduleMessageDeletion(message, 3));
  }

  private static String buildMessageLink(ReadyCheck readyCheck) {
    if (readyCheck.getMessageId() == null) return "";
    return String.format(
        "https://discord.com/channels/%s/%s/%s",
        readyCheck.getGuildId(), readyCheck.getChannelId(), readyCheck.getMessageId());
  }

  private static void scheduleMessageDeletion(Message message, int minutes) {
    scheduler.schedule(
        () -> {
          message.delete().queue(null, error -> {});
        },
        minutes,
        TimeUnit.MINUTES);
  }

  private static Set<String> createUserSet(List<String> targetUserIds, String initiatorId) {
    Set<String> allUserIds = new HashSet<>(targetUserIds);
    allUserIds.add(initiatorId);
    return allUserIds;
  }

  private static String findExistingReadyCheckInternal(String guildId, Set<String> targetUsers) {
    return activeReadyChecks.values().stream()
        .filter(check -> check.getGuildId().equals(guildId))
        .filter(check -> isReadyCheckOngoing(check.getId())) // Only return non-completed checks
        .filter(
            check -> {
              Set<String> checkUsers = new HashSet<>(check.getTargetUsers());
              checkUsers.add(check.getInitiatorId());
              return checkUsers.equals(targetUsers);
            })
        .map(ReadyCheck::getId)
        .findFirst()
        .orElse(null);
  }

  // UI Building Methods
  private static EmbedBuilder buildReadyCheckEmbed(
      ReadyCheck readyCheck, JDA jda, String description) {
    String status;
    Color color;

    if (allNonPassedReady(readyCheck)) {
      status = "🎉 Everyone is ready!";
      color = Color.GREEN;
    } else {
      int readyCount = getReadyCount(readyCheck);
      int totalCount = getNonPassedCount(readyCheck);
      status = String.format("⏳ %d/%d ready", readyCount, totalCount);
      color = Color.ORANGE;
    }

    return new EmbedBuilder()
        .setTitle(status)
        .setDescription(description + "\n\n" + buildMemberList(readyCheck, jda))
        .setColor(color)
        .setTimestamp(Instant.now());
  }

  private static String buildMemberList(ReadyCheck readyCheck, JDA jda) {
    List<String> memberStatuses = new ArrayList<>();

    Set<String> allUsers = new HashSet<>(readyCheck.getTargetUsers());
    allUsers.add(readyCheck.getInitiatorId());

    for (String userId : allUsers) {
      memberStatuses.add(buildMemberStatus(readyCheck, userId, jda));
    }

    return String.join("\n", memberStatuses);
  }

  private static String buildMemberStatus(ReadyCheck readyCheck, String userId, JDA jda) {
    Guild guild = jda.getGuildById(readyCheck.getGuildId());
    Member member = guild != null ? guild.getMemberById(userId) : null;
    String displayName = member != null ? member.getEffectiveName() : "Unknown User";

    if (readyCheck.getPassedUsers().contains(userId)) {
      return "🚫 ~~" + displayName + "~~";
    } else if (readyCheck.getReadyUsers().contains(userId)) {
      if (readyCheck.getUserUntilTimes().containsKey(userId)) {
        // For "ready until" times, also use Discord timestamps if stored as timestamp
        return "✅ " + displayName + " (until " + readyCheck.getUserUntilTimes().get(userId) + ")";
      } else if (readyCheck.getUserTimers().containsKey(userId)) {
        return "✅ "
            + displayName
            + " (auto-unready in "
            + readyCheck.getUserTimers().get(userId)
            + "min)";
      } else {
        return "✅ " + displayName;
      }
    } else if (readyCheck.getScheduledUsers().containsKey(userId)) {
      long readyTimeMs = readyCheck.getScheduledUsers().get(userId);
      long minutesLeft = (readyTimeMs - System.currentTimeMillis()) / 60000;

      if (minutesLeft <= 0) {
        return "⏰ " + displayName + " (ready now!)";
      } else {
        // Use the exact stored timestamp for display instead of recalculating
        String timeDisplay = formatScheduledTime(readyTimeMs, minutesLeft);
        if (minutesLeft >= 60) {
          return "⏰ " + displayName + " (ready " + timeDisplay + ")";
        } else {
          return "⏰ " + displayName + " (ready in " + timeDisplay + ")";
        }
      }
    } else {
      return "❌ " + displayName;
    }
  }

  private static List<Button> createMainButtons(String readyCheckId) {
    return Arrays.asList(
        Button.success("toggle_ready_" + readyCheckId, "Toggle Ready"),
        Button.primary("ready_at_" + readyCheckId, "Ready At..."),
        Button.primary("ready_until_" + readyCheckId, "Ready Until..."),
        Button.danger("pass_" + readyCheckId, "Pass"));
  }

  private static List<Button> createSaveButton(String readyCheckId) {
    return Collections.singletonList(Button.secondary("save_ready_" + readyCheckId, "💾"));
  }

  private static String createMentions(ReadyCheck readyCheck, JDA jda) {
    if (!getMentionPreference(readyCheck.getId())) {
      return "";
    }

    Set<String> allUsers = new HashSet<>(readyCheck.getTargetUsers());
    allUsers.add(readyCheck.getInitiatorId());

    Guild guild = jda.getGuildById(readyCheck.getGuildId());
    if (guild == null) return "";

    return allUsers.stream()
        .filter(userId -> !readyCheck.getReadyUsers().contains(userId)) // Not ready
        .filter(userId -> !readyCheck.getPassedUsers().contains(userId)) // Not passed
        .filter(userId -> !readyCheck.getScheduledUsers().containsKey(userId)) // Not scheduled
        .filter(userId -> !readyCheck.getUserUntilTimes().containsKey(userId)) // Not ready until
        .map(
            userId -> {
              Member member = guild.getMemberById(userId);
              return member != null ? member.getAsMention() : null;
            })
        .filter(Objects::nonNull)
        .collect(Collectors.joining(" "));
  }

  private static void handleSlashCommandResponse(
      SlashCommandInteractionEvent event,
      EmbedBuilder embed,
      List<Button> mainButtons,
      List<Button> saveButton,
      String mentions,
      String readyCheckId) {
    event
        .reply(mentions)
        .setEmbeds(embed.build())
        .setComponents(ActionRow.of(mainButtons), ActionRow.of(saveButton))
        .queue(
            response -> {
              response
                  .retrieveOriginal()
                  .queue(
                      message -> {
                        setReadyCheckMessageId(readyCheckId, message.getId());
                      });
            });
  }

  private static void handleSelectMenuResponse(
      StringSelectInteractionEvent event,
      EmbedBuilder embed,
      List<Button> mainButtons,
      List<Button> saveButton,
      String mentions,
      String readyCheckId) {
    event
        .reply(mentions)
        .setEmbeds(embed.build())
        .setComponents(ActionRow.of(mainButtons), ActionRow.of(saveButton))
        .queue(
            response -> {
              response
                  .retrieveOriginal()
                  .queue(
                      message -> {
                        setReadyCheckMessageId(readyCheckId, message.getId());
                      });
            });
  }

  private static int getReadyCount(ReadyCheck readyCheck) {
    Set<String> allUsers = new HashSet<>(readyCheck.getTargetUsers());
    allUsers.add(readyCheck.getInitiatorId());

    return (int)
        allUsers.stream()
            .filter(userId -> !readyCheck.getPassedUsers().contains(userId))
            .filter(userId -> readyCheck.getReadyUsers().contains(userId))
            .count();
  }

  private static int getNonPassedCount(ReadyCheck readyCheck) {
    Set<String> allUsers = new HashSet<>(readyCheck.getTargetUsers());
    allUsers.add(readyCheck.getInitiatorId());

    return (int)
        allUsers.stream().filter(userId -> !readyCheck.getPassedUsers().contains(userId)).count();
  }

  private static boolean allNonPassedReady(ReadyCheck readyCheck) {
    Set<String> allUsers = new HashSet<>(readyCheck.getTargetUsers());
    allUsers.add(readyCheck.getInitiatorId());

    return allUsers.stream()
        .filter(userId -> !readyCheck.getPassedUsers().contains(userId))
        .allMatch(userId -> readyCheck.getReadyUsers().contains(userId));
  }

  private static String formatTimeForDisplay(
      String originalInput, long delayMinutes, LocalTime readyTime) {
    if (originalInput.matches("\\d+")) {
      return delayMinutes + " minutes";
    } else {
      return readyTime.format(DateTimeFormatter.ofPattern("h:mm a"));
    }
  }

  // File I/O utilities
  private static void saveSavedConfigurations() {
    try (FileWriter writer = new FileWriter(SAVE_FILE)) {
      gson.toJson(savedReadyChecks, writer);
    } catch (IOException e) {
      System.err.println("Failed to save configurations: " + e.getMessage());
    }
  }

  private static void loadSavedConfigurations() {
    File file = new File(SAVE_FILE);
    if (!file.exists()) {
      return;
    }

    try (FileReader reader = new FileReader(file)) {
      Type type = new TypeToken<Map<String, SavedReadyCheck>>() {}.getType();
      Map<String, SavedReadyCheck> loaded = gson.fromJson(reader, type);
      if (loaded != null) {
        savedReadyChecks.putAll(loaded);
      }
    } catch (IOException e) {
      System.err.println("Failed to load configurations: " + e.getMessage());
    }
  }

  public static class ReadyCheck {
    private final String id;
    private final String guildId;
    private final String channelId;
    private final String initiatorId;
    private final String roleId;
    private final Set<String> targetUsers;
    private final Set<String> readyUsers;
    private final Map<String, Long> scheduledUsers;
    private final Map<String, Integer> userTimers;
    private final Map<String, String> userUntilTimes;
    private final Set<String> passedUsers;
    private String messageId;
    private String completionMessageId;
    private ReadyCheckStatus status;
    private long lastInteractionTime;
    private final long createdTime;
    private String description;

    public ReadyCheck(
        String id,
        String guildId,
        String channelId,
        String initiatorId,
        String roleId,
        List<String> targetUserIds) {
      this.id = id;
      this.guildId = guildId;
      this.channelId = channelId;
      this.initiatorId = initiatorId;
      this.roleId = roleId;
      this.targetUsers = new HashSet<>(targetUserIds);
      this.readyUsers = new HashSet<>();
      this.scheduledUsers = new HashMap<>();
      this.userTimers = new HashMap<>();
      this.userUntilTimes = new HashMap<>();
      this.passedUsers = new HashSet<>();
      this.status = ReadyCheckStatus.ACTIVE;
      this.createdTime = System.currentTimeMillis();
      this.lastInteractionTime = System.currentTimeMillis();
    }

    public String getId() {
      return id;
    }

    public String getGuildId() {
      return guildId;
    }

    public String getChannelId() {
      return channelId;
    }

    public String getInitiatorId() {
      return initiatorId;
    }

    public String getRoleId() {
      return roleId;
    }

    public Set<String> getTargetUsers() {
      return targetUsers;
    }

    public Set<String> getReadyUsers() {
      return readyUsers;
    }

    public Map<String, Long> getScheduledUsers() {
      return scheduledUsers;
    }

    public Map<String, Integer> getUserTimers() {
      return userTimers;
    }

    public Map<String, String> getUserUntilTimes() {
      return userUntilTimes;
    }

    public Set<String> getPassedUsers() {
      return passedUsers;
    }

    public String getMessageId() {
      return messageId;
    }

    public String getCompletionMessageId() {
      return completionMessageId;
    }

    public ReadyCheckStatus getStatus() {
      return status;
    }

    public long getLastInteractionTime() {
      return lastInteractionTime;
    }

    public long getCreatedTime() {
      return createdTime;
    }

    public String getDescription() {
      return description;
    }

    public void setMessageId(String messageId) {
      this.messageId = messageId;
    }

    public void setCompletionMessageId(String completionMessageId) {
      this.completionMessageId = completionMessageId;
    }

    public void setStatus(ReadyCheckStatus status) {
      this.status = status;
    }

    public void setLastInteractionTime(long lastInteractionTime) {
      this.lastInteractionTime = lastInteractionTime;
    }

    public void setDescription(String description) {
      this.description = description;
    }
  }

  public static class SavedReadyCheck {
    private String roleId;
    private List<String> userIds;
    private final boolean userBased;
    private boolean mentionPeople = true;

    public SavedReadyCheck(String roleId, boolean userBased) {
      this.roleId = roleId;
      this.userBased = userBased;
    }

    public SavedReadyCheck(List<String> userIds, boolean userBased) {
      this.userIds = userIds;
      this.userBased = userBased;
    }

    public SavedReadyCheck(String roleId, boolean userBased, boolean mentionPeople) {
      this.roleId = roleId;
      this.userBased = userBased;
      this.mentionPeople = mentionPeople;
    }

    public SavedReadyCheck(List<String> userIds, boolean userBased, boolean mentionPeople) {
      this.userIds = userIds;
      this.userBased = userBased;
      this.mentionPeople = mentionPeople;
    }

    public String getRoleId() {
      return roleId;
    }

    public List<String> getUserIds() {
      return userIds;
    }

    public boolean isUserBased() {
      return userBased;
    }

    public boolean getMentionPeople() {
      return mentionPeople;
    }

    public void setMentionPeople(boolean mentionPeople) {
      this.mentionPeople = mentionPeople;
    }
  }
}
