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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReadyCheckManager {
  private static final Logger logger = LoggerFactory.getLogger(ReadyCheckManager.class);
  private static final Map<String, ReadyCheck> activeReadyChecks = new ConcurrentHashMap<>();
  private static final Map<String, SavedReadyCheck> savedReadyChecks = new ConcurrentHashMap<>();
  private static final Map<String, Boolean> mentionPreferences = new ConcurrentHashMap<>();
  private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
  private static final String SAVE_FILE = getConfigFilePath();
  private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
  private static final long TWO_HOURS_MS = TimeUnit.HOURS.toMillis(2);
  private static final ZoneId SYSTEM_TIMEZONE = ZoneId.systemDefault();
  private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm a");
  private static JDA globalJDA;

  static {
    loadSavedConfigurations();
    startPeriodicUpdater();

    scheduler.schedule(
        () -> {
          if (globalJDA != null) {
            ReadyCheckRecoveryManager.recoverReadyChecksFromMessages(globalJDA);
          } else {
            logger.warn("JDA not ready for recovery, skipping message recovery");
          }
        },
        10,
        TimeUnit.SECONDS);
  }

  public enum ReadyCheckStatus {
    ACTIVE,
    COMPLETED
  }

  public static void setJDA(JDA jda) {
    globalJDA = jda;
  }

  public static boolean hasActiveReadyCheck(String readyCheckId) {
    return activeReadyChecks.containsKey(readyCheckId);
  }

  public static void addRecoveredReadyCheck(String readyCheckId, ReadyCheck readyCheck) {
    activeReadyChecks.put(readyCheckId, readyCheck);
    logger.info("Added recovered ready check: {}", readyCheckId);
  }

  public static ReadyCheck createRecoveredReadyCheck(
      String readyCheckId,
      String guildId,
      String channelId,
      String initiatorId,
      String roleId,
      Set<String> targetUsers,
      Set<String> readyUsers,
      Set<String> passedUsers,
      String messageId) {

    ReadyCheck recoveredCheck =
        new ReadyCheck(
            readyCheckId, guildId, channelId, initiatorId, roleId, new ArrayList<>(targetUsers));

    recoveredCheck.getTargetUsers().clear();
    recoveredCheck.getTargetUsers().addAll(targetUsers);
    recoveredCheck.getReadyUsers().addAll(readyUsers);
    recoveredCheck.getPassedUsers().addAll(passedUsers);
    recoveredCheck.setMessageId(messageId);

    return recoveredCheck;
  }

  public static EmbedBuilder buildReadyCheckEmbedForRecovery(
      ReadyCheck readyCheck, JDA jda, String description) {
    return buildReadyCheckEmbed(readyCheck, jda, description);
  }

  public static JDA getJDA() {
    return globalJDA;
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
    if (readyCheck == null) return;

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

    if (event instanceof SlashCommandInteractionEvent slashEvent) {
      handleSlashCommandResponse(
          slashEvent, embed, mainButtons, saveButton, mentions, readyCheckId);
    } else if (event instanceof StringSelectInteractionEvent selectEvent) {
      handleSelectMenuResponse(selectEvent, embed, mainButtons, saveButton, mentions, readyCheckId);
    }
  }

  public static boolean isReadyCheckOngoing(String readyCheckId) {
    ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    return readyCheck != null && !allNonPassedReady(readyCheck);
  }

  public static String findExistingReadyCheck(
      String guildId, SavedReadyCheck savedCheck, String initiatorId) {
    return activeReadyChecks.values().stream()
        .filter(check -> check.getGuildId().equals(guildId))
        .filter(check -> isReadyCheckOngoing(check.getId()))
        .filter(check -> matchesSavedCheck(check, savedCheck, initiatorId))
        .map(ReadyCheck::getId)
        .findFirst()
        .orElse(null);
  }

  public static String findExistingReadyCheck(
      String guildId, List<String> targetUserIds, String initiatorId) {
    return findExistingReadyCheckInternal(guildId, createUserSet(targetUserIds, initiatorId));
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

  public static void scheduleReadyAt(
      String readyCheckId, String timeInput, String userId, JDA jda) {
    long delayMinutes = parseTimeInputAsMinutes(timeInput);
    ReadyCheck readyCheck = getReadyCheckOrThrow(readyCheckId);

    ensureUserInTargets(readyCheck, userId);
    cancelExistingScheduledUser(readyCheck, userId);

    Instant targetTime = Instant.now().plus(Duration.ofMinutes(delayMinutes));
    scheduleUserReady(readyCheck, userId, targetTime, jda);
  }

  public static String scheduleReadyAtSmart(
      String readyCheckId, String timeInput, String userId, JDA jda) {
    ReadyCheck readyCheck = getReadyCheckOrThrow(readyCheckId);

    LocalTime targetTime = parseTargetTime(timeInput);
    cancelExistingScheduledUser(readyCheck, userId);
    readyCheck.getReadyUsers().remove(userId);

    LocalDateTime now = LocalDateTime.now(SYSTEM_TIMEZONE);
    LocalDateTime target = LocalDateTime.of(now.toLocalDate(), targetTime);

    if (!target.isAfter(now)) {
      target = target.plusDays(1);
    }

    Duration delay = Duration.between(now, target);
    long delayMinutes = delay.toMinutes();
    if (delay.toSecondsPart() > 0) {
      delayMinutes++;
    }

    ScheduledFuture<?> reminderFuture =
        scheduler.schedule(
            () -> sendReadyReminder(readyCheckId, userId, jda), delayMinutes, TimeUnit.MINUTES);

    ScheduledUser scheduledUser =
        new ScheduledUser(
            target.atZone(SYSTEM_TIMEZONE).toInstant().toEpochMilli(), reminderFuture);
    readyCheck.getScheduledUsers().put(userId, scheduledUser);

    return targetTime.format(TIME_FORMATTER);
  }

  public static String scheduleReadyUntil(
      String readyCheckId, String timeInput, String userId, JDA jda) {
    LocalTime targetTime = parseTargetTime(timeInput);
    ReadyCheck readyCheck = getReadyCheckOrThrow(readyCheckId);
    ensureUserInTargets(readyCheck, userId);

    LocalDateTime now = LocalDateTime.now(SYSTEM_TIMEZONE);
    LocalDateTime target = LocalDateTime.of(now.toLocalDate(), targetTime);

    if (!target.isAfter(now)) {
      target = target.plusDays(1);
    }

    Duration delay = Duration.between(now, target);
    long delayMinutes = delay.toMinutes();
    if (delay.toSecondsPart() > 0) {
      delayMinutes++;
    }

    ScheduledFuture<?> untilFuture =
        scheduler.schedule(
            () -> autoPassUser(readyCheckId, userId, jda), delayMinutes, TimeUnit.MINUTES);

    readyCheck.getScheduledUntilFutures().put(userId, untilFuture);
    String discordTimestamp =
        "<t:" + target.atZone(SYSTEM_TIMEZONE).toInstant().getEpochSecond() + ":t>";
    readyCheck.getUserUntilTimes().put(userId, discordTimestamp);

    return discordTimestamp;
  }

  public static void updateReadyCheckEmbed(String readyCheckId, JDA jda) {
    ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    if (readyCheck == null || readyCheck.getMessageId() == null) return;

    Guild guild = jda.getGuildById(readyCheck.getGuildId());
    if (guild == null) return;

    TextChannel channel = guild.getTextChannelById(readyCheck.getChannelId());
    if (channel == null) return;

    handleStatusTransition(readyCheck, channel);
    cleanupExpiredScheduledUsers(readyCheck);
    updateMessage(readyCheck, channel, readyCheckId);
  }

  public static boolean markUserReady(String readyCheckId, String userId) {
    ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    if (readyCheck == null) {
      logger.warn(
          "Attempted to mark user {} ready for non-existent ready check: {}", userId, readyCheckId);
      return false;
    }

    cancelExistingScheduledUser(readyCheck, userId);
    readyCheck.getUserTimers().remove(userId);
    readyCheck.getReadyUsers().add(userId);

    boolean allReady = readyCheck.getReadyUsers().containsAll(readyCheck.getTargetUsers());
    if (allReady && readyCheck.getStatus() == ReadyCheckStatus.ACTIVE) {
      readyCheck.setStatus(ReadyCheckStatus.COMPLETED);
      logger.info("Ready check completed: {}", readyCheckId);
    }

    logger.debug("User {} marked as ready for ready check: {}", userId, readyCheckId);
    return allReady;
  }

  public static void notifyAllReady(String readyCheckId, JDA jda) {
    ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    if (readyCheck == null) return;

    Guild guild = jda.getGuildById(readyCheck.getGuildId());
    TextChannel channel = getChannelFromGuild(guild, readyCheck.getChannelId());
    if (channel == null) return;

    readyCheck.setStatus(ReadyCheckStatus.COMPLETED);
    Set<String> allUsers = getAllUsers(readyCheck);
    List<String> readyUserNames = getReadyUserNames(readyCheck, allUsers, guild);
    String readyUserMentions = createReadyUserMentions(readyCheck, allUsers, guild, readyCheckId);

    replaceReadyCheckWithSummary(readyCheckId, jda, readyUserNames, readyUserMentions);
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
      return false;
    } else {
      cancelExistingScheduledUser(readyCheck, userId);
      readyCheck.getReadyUsers().add(userId);
      readyCheck.getPassedUsers().remove(userId);
      readyCheck.getUserUntilTimes().remove(userId);
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
  }

  public static boolean checkIfAllReady(String readyCheckId) {
    ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    return readyCheck != null && allNonPassedReady(readyCheck);
  }

  public static void unmarkUserPassed(String readyCheckId, String userId) {
    ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    if (readyCheck != null) {
      readyCheck.getPassedUsers().remove(userId);
    }
  }

  public static String findActiveReadyCheckForUser(String guildId, String userId) {
    long twoHoursAgo = System.currentTimeMillis() - TWO_HOURS_MS;

    return activeReadyChecks.values().stream()
        .filter(readyCheck -> readyCheck.getGuildId().equals(guildId))
        .filter(readyCheck -> readyCheck.getCreatedTime() >= twoHoursAgo)
        .filter(readyCheck -> readyCheck.getStatus() == ReadyCheckStatus.ACTIVE)
        .filter(readyCheck -> userCanEngageWithReadyCheck(readyCheck, userId))
        .map(ReadyCheck::getId)
        .findFirst()
        .orElse(null);
  }

  public static void saveReadyCheck(String readyCheckId) {
    ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    if (readyCheck == null) {
      logger.warn("Attempted to save non-existent ready check: {}", readyCheckId);
      return;
    }

    String guildId = readyCheck.getGuildId();
    boolean mentionPeople = getMentionPreference(readyCheckId);
    SavedReadyCheck newSavedCheck = createSavedCheck(readyCheck, mentionPeople);

    String newKey = createSavedCheckKey(guildId, newSavedCheck);
    removeDuplicateConfigurations(guildId, newSavedCheck);

    savedReadyChecks.put(newKey, newSavedCheck);
    saveSavedConfigurations();

    logger.info(
        "Saved ready check configuration for guild: {}, type: {}",
        guildId,
        newSavedCheck.isUserBased() ? "user-based" : "role-based");
  }

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
        .queue(message -> readyCheck.setMessageId(message.getId()));
  }

  public static List<SavedReadyCheck> getSavedReadyChecks(String guildId) {
    List<SavedReadyCheck> checks =
        savedReadyChecks.entrySet().stream()
            .filter(entry -> entry.getKey().startsWith(guildId + "_"))
            .map(Map.Entry::getValue)
            .collect(Collectors.toList());

    Collections.reverse(checks);
    return checks;
  }

  public static void resendExistingReadyCheck(String readyCheckId, JDA jda) {
    ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    if (readyCheck == null) return;

    Guild guild = jda.getGuildById(readyCheck.getGuildId());
    TextChannel channel = getChannelFromGuild(guild, readyCheck.getChannelId());
    if (channel == null) return;

    EmbedBuilder embed = buildReadyCheckEmbed(readyCheck, jda, readyCheck.getDescription());
    List<Button> mainButtons = createMainButtons(readyCheckId);
    List<Button> saveButton = createSaveButton(readyCheckId);
    String mentions = createMentions(readyCheck, jda);

    channel
        .sendMessage(mentions)
        .setEmbeds(embed.build())
        .setComponents(ActionRow.of(mainButtons), ActionRow.of(saveButton))
        .queue(message -> readyCheck.setMessageId(message.getId()));
  }

  public static void setReadyCheckMessageId(String readyCheckId, String messageId) {
    ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    if (readyCheck != null) {
      readyCheck.setMessageId(messageId);
    }
  }

  private static void startPeriodicUpdater() {
    scheduler.scheduleWithFixedDelay(
        () -> {
          try {
            updateAllReadyCheckCountdowns();
          } catch (Exception e) {
            logger.error("Error in periodic updater: {}", e.getMessage(), e);
          }
        },
        1,
        1,
        TimeUnit.MINUTES);
  }

  private static void updateAllReadyCheckCountdowns() {
    if (globalJDA == null) return;

    for (ReadyCheck readyCheck : activeReadyChecks.values()) {
      if (readyCheck.getStatus() != ReadyCheckStatus.ACTIVE) continue;

      boolean embedNeedsUpdate = checkAndReadyUsersInVoice(readyCheck);

      if (!readyCheck.getScheduledUsers().isEmpty() || embedNeedsUpdate) {
        updateReadyCheckEmbed(readyCheck.getId(), globalJDA);
      }

      if (embedNeedsUpdate && checkIfAllReady(readyCheck.getId())) {
        notifyAllReady(readyCheck.getId(), globalJDA);
      }
    }
  }

  private static boolean checkAndReadyUsersInVoice(ReadyCheck readyCheck) {
    Guild guild = globalJDA.getGuildById(readyCheck.getGuildId());
    if (guild == null) return false;

    boolean anyChanges = false;
    Set<String> allUsers = getAllUsers(readyCheck);

    for (String userId : allUsers) {
      if (isUserAlreadyProcessed(readyCheck, userId)) continue;

      Member member = guild.getMemberById(userId);
      if (member == null) continue;

      if (shouldAutoReadyUserInVoice(member)) {
        autoReadyUserInVoice(readyCheck, userId);
        anyChanges = true;
      }
    }

    return anyChanges;
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

  private static JDA getJDAFromEvent(Object event) {
    return switch (event) {
      case SlashCommandInteractionEvent slashEvent -> slashEvent.getJDA();
      case StringSelectInteractionEvent selectEvent -> selectEvent.getJDA();
      default -> null;
    };
  }

  private static long parseTimeInputAsMinutes(String timeInput) {
    String input = timeInput.trim();

    if (input.matches("\\d+")) {
      long minutes = Long.parseLong(input);
      if (minutes >= 1 && minutes <= 1440) {
        return minutes;
      } else {
        throw new IllegalArgumentException("Minutes must be between 1 and 1440");
      }
    }

    throw new IllegalArgumentException(
        "For 'r in X', please use just the number of minutes (e.g., '5', '30')");
  }

  private static LocalTime parseTargetTime(String timeInput) {
    String input = timeInput.trim().toLowerCase();
    LocalTime now = LocalTime.now(SYSTEM_TIMEZONE);

    if (input.contains("pm") || input.contains("am")) {
      return parseExplicitAmPm(input);
    }

    if (input.contains(":") && is24HourFormat(input)) {
      return parse24HourFormat(input);
    }

    if (input.contains(":")) {
      return parseTimeWithColon(input, now);
    }

    if (input.matches("\\d{3,4}")) {
      return parseCompactTime(input, now);
    }

    return parseWithSmartDetection(input, now);
  }

  private static LocalTime parseTimeWithColon(String input, LocalTime now) {
    return parseTimeComponents(input, now);
  }

  private static LocalTime parseTimeComponents(String input, LocalTime now) {
    String[] parts = input.split(":");
    if (parts.length != 2) {
      throw new IllegalArgumentException("Invalid time format");
    }

    try {
      int hour = Integer.parseInt(parts[0]);
      int minute = Integer.parseInt(parts[1]);

      validateMinutes(minute);
      validateHour(hour);

      return smartAmPmDetection(hour, minute, now);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Invalid time format");
    }
  }

  private static void validateMinutes(int minute) {
    if (minute < 0 || minute > 59) {
      throw new IllegalArgumentException("Invalid minutes");
    }
  }

  private static void validateHour(int hour) {
    if (hour < 1 || hour > 12) {
      throw new IllegalArgumentException("Hour must be between 1 and 12 for ambiguous format");
    }
  }

  private static LocalTime parseCompactTime(String input, LocalTime now) {
    try {
      if (input.length() == 3) {
        return parseThreeDigitTime(input, now);
      } else if (input.length() == 4) {
        return parseFourDigitTime(input, now);
      }

      throw new IllegalArgumentException("Invalid time format");
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Invalid time format");
    }
  }

  private static LocalTime parseThreeDigitTime(String input, LocalTime now) {
    int hour = Integer.parseInt(input.substring(0, 1));
    int minute = Integer.parseInt(input.substring(1, 3));

    validateMinutes(minute);

    if (hour >= 1 && hour <= 9) {
      return smartAmPmDetection(hour, minute, now);
    }
    throw new IllegalArgumentException("Invalid hour in time format");
  }

  private static LocalTime parseFourDigitTime(String input, LocalTime now) {
    int hour = Integer.parseInt(input.substring(0, 2));
    int minute = Integer.parseInt(input.substring(2, 4));

    validateMinutes(minute);

    if (hour >= 1 && hour <= 12) {
      return smartAmPmDetection(hour, minute, now);
    }
    throw new IllegalArgumentException("Invalid hour in time format");
  }

  private static LocalTime parseExplicitAmPm(String input) {
    String normalizedInput = input.toUpperCase().replaceAll("\\s+", "");

    if (normalizedInput.matches("\\d+(PM|AM)")) {
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
      return LocalTime.parse(normalizedInput, DateTimeFormatter.ofPattern("h:mma"));
    }
  }

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

  private static LocalTime parse24HourFormat(String input) {
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

  private static LocalTime parseWithSmartDetection(String input, LocalTime now) {
    int hour = Integer.parseInt(input);

    if (hour < 1 || hour > 12) {
      throw new IllegalArgumentException("Hour must be between 1 and 12 for ambiguous format");
    }

    return smartAmPmDetection(hour, 0, now);
  }

  private static LocalTime smartAmPmDetection(int hour, int minute, LocalTime now) {
    LocalTime amTime = LocalTime.of(hour == 12 ? 0 : hour, minute);
    LocalTime pmTime = LocalTime.of(hour == 12 ? 12 : hour + 12, minute);

    int currentHour = now.getHour();

    if (currentHour >= 22 || currentHour <= 5) {
      return amTime.isAfter(now) ? amTime : pmTime;
    }

    if (currentHour <= 11) {
      return amTime.isAfter(now) ? amTime : pmTime;
    }

    return pmTime.isAfter(now) ? pmTime : amTime;
  }

  private static void cancelExistingScheduledUser(ReadyCheck readyCheck, String userId) {
    ScheduledUser existingScheduled = readyCheck.getScheduledUsers().remove(userId);
    if (existingScheduled != null) {
      existingScheduled.cancel();
    }

    ScheduledFuture<?> existingUntil = readyCheck.getScheduledUntilFutures().remove(userId);
    if (existingUntil != null && !existingUntil.isDone()) {
      existingUntil.cancel(false);
    }
  }

  private static ReadyCheck getReadyCheckOrThrow(String readyCheckId) {
    ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    if (readyCheck == null) {
      throw new IllegalArgumentException("Ready check not found");
    }
    return readyCheck;
  }

  private static void scheduleUserReady(
      ReadyCheck readyCheck, String userId, Instant targetTime, JDA jda) {
    readyCheck.getReadyUsers().remove(userId);
    readyCheck.getPassedUsers().remove(userId);

    Duration delay = Duration.between(Instant.now(), targetTime);
    long delayMinutes = Math.max(1, delay.toMinutes());

    ScheduledFuture<?> reminderFuture =
        scheduler.schedule(
            () -> sendReadyReminder(readyCheck.getId(), userId, jda),
            delayMinutes,
            TimeUnit.MINUTES);

    ScheduledUser scheduledUser = new ScheduledUser(targetTime.toEpochMilli(), reminderFuture);
    readyCheck.getScheduledUsers().put(userId, scheduledUser);
  }

  private static void handleStatusTransition(ReadyCheck readyCheck, TextChannel channel) {
    boolean wasCompleted = readyCheck.getStatus() == ReadyCheckStatus.COMPLETED;
    boolean nowCompleted = allNonPassedReady(readyCheck);

    if (wasCompleted && !nowCompleted && readyCheck.getCompletionMessageId() != null) {
      channel
          .retrieveMessageById(readyCheck.getCompletionMessageId())
          .queue(message -> message.delete().queue(null, error -> {}), error -> {});
      readyCheck.setCompletionMessageId(null);
      readyCheck.setStatus(ReadyCheckStatus.ACTIVE);
    }
  }

  private static void updateMessage(
      ReadyCheck readyCheck, TextChannel channel, String readyCheckId) {
    channel
        .retrieveMessageById(readyCheck.getMessageId())
        .queue(
            message -> {
              EmbedBuilder embed =
                  buildReadyCheckEmbed(readyCheck, globalJDA, readyCheck.getDescription());
              List<Button> mainButtons = createMainButtons(readyCheckId);
              List<Button> saveButton = createSaveButton(readyCheckId);

              message
                  .editMessageEmbeds(embed.build())
                  .setComponents(ActionRow.of(mainButtons), ActionRow.of(saveButton))
                  .queue();
            },
            error -> {});
  }

  private static void cleanupExpiredScheduledUsers(ReadyCheck readyCheck) {
    long currentTime = System.currentTimeMillis();
    Set<String> expiredUsers =
        readyCheck.getScheduledUsers().entrySet().stream()
            .filter(entry -> entry.getValue().readyTimestamp() <= currentTime)
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());

    expiredUsers.forEach(userId -> readyCheck.getScheduledUsers().remove(userId));
  }

  private static TextChannel getChannelFromGuild(Guild guild, String channelId) {
    return guild != null ? guild.getTextChannelById(channelId) : null;
  }

  private static Set<String> getAllUsers(ReadyCheck readyCheck) {
    Set<String> allUsers = new HashSet<>(readyCheck.getTargetUsers());
    allUsers.add(readyCheck.getInitiatorId());
    return allUsers;
  }

  private static List<String> getReadyUserNames(
      ReadyCheck readyCheck, Set<String> allUsers, Guild guild) {
    return allUsers.stream()
        .filter(userId -> readyCheck.getReadyUsers().contains(userId))
        .filter(userId -> !readyCheck.getPassedUsers().contains(userId))
        .map(
            userId -> {
              Member member = guild.getMemberById(userId);
              return member != null ? member.getEffectiveName() : null;
            })
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  private static String createReadyUserMentions(
      ReadyCheck readyCheck, Set<String> allUsers, Guild guild, String readyCheckId) {
    if (!getMentionPreference(readyCheckId)) {
      return "";
    }

    return allUsers.stream()
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

  private static void replaceReadyCheckWithSummary(
      String readyCheckId, JDA jda, List<String> readyUserNames, String mentions) {
    ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    if (readyCheck == null || readyCheck.getMessageId() == null) return;

    Guild guild = jda.getGuildById(readyCheck.getGuildId());
    TextChannel channel = getChannelFromGuild(guild, readyCheck.getChannelId());
    if (channel == null) return;

    StringBuilder memberList = new StringBuilder();
    readyUserNames.forEach(userName -> memberList.append("✅ ").append(userName).append("\n"));

    EmbedBuilder summaryEmbed =
        new EmbedBuilder()
            .setTitle("✅ Ready Check Complete")
            .setDescription(readyCheck.getDescription() + "\n\n" + memberList)
            .setColor(Color.GREEN)
            .setTimestamp(Instant.now());

    channel
        .retrieveMessageById(readyCheck.getMessageId())
        .queue(
            oldMessage -> {
              oldMessage.delete().queue(null, error -> {});
              channel
                  .sendMessage(mentions)
                  .setEmbeds(summaryEmbed.build())
                  .queue(newMessage -> readyCheck.setMessageId(newMessage.getId()));
            },
            error ->
                channel
                    .sendMessage(mentions)
                    .setEmbeds(summaryEmbed.build())
                    .queue(newMessage -> readyCheck.setMessageId(newMessage.getId())));
  }

  private static boolean userCanEngageWithReadyCheck(ReadyCheck readyCheck, String userId) {
    return readyCheck.getTargetUsers().contains(userId)
        || readyCheck.getPassedUsers().contains(userId)
        || readyCheck.getInitiatorId().equals(userId);
  }

  private static SavedReadyCheck createSavedCheck(ReadyCheck readyCheck, boolean mentionPeople) {
    if (readyCheck.getRoleId() != null) {
      return new SavedReadyCheck(readyCheck.getRoleId(), false, mentionPeople);
    } else {
      List<String> allUserIds = new ArrayList<>(readyCheck.getTargetUsers());
      allUserIds.add(readyCheck.getInitiatorId());
      return new SavedReadyCheck(allUserIds, true, mentionPeople);
    }
  }

  private static String createSavedCheckKey(String guildId, SavedReadyCheck savedCheck) {
    return guildId
        + "_"
        + (savedCheck.isUserBased() ? savedCheck.getUserIds().hashCode() : savedCheck.getRoleId());
  }

  private static void removeDuplicateConfigurations(String guildId, SavedReadyCheck newSavedCheck) {
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
  }

  private static boolean matchesSavedCheck(
      ReadyCheck check, SavedReadyCheck savedCheck, String initiatorId) {
    if (savedCheck.isUserBased()) {
      Set<String> checkUserIds = new HashSet<>(check.getTargetUsers());
      checkUserIds.add(initiatorId);
      return checkUserIds.equals(new HashSet<>(savedCheck.getUserIds()));
    } else {
      return savedCheck.getRoleId().equals(check.getRoleId());
    }
  }

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

    readyCheck.getReadyUsers().add(initiatorId);
    readyCheck.getTargetUsers().add(initiatorId);

    activeReadyChecks.put(readyCheckId, readyCheck);
    return readyCheckId;
  }

  private static void ensureUserInTargets(ReadyCheck readyCheck, String userId) {
    readyCheck.getTargetUsers().add(userId);
  }

  private static void autoPassUser(String readyCheckId, String userId, JDA jda) {
    ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    if (readyCheck == null) return;

    readyCheck.getPassedUsers().add(userId);
    readyCheck.getReadyUsers().remove(userId);
    readyCheck.getUserUntilTimes().remove(userId);

    updateReadyCheckEmbed(readyCheckId, jda);
  }

  private static void sendReadyReminder(String readyCheckId, String userId, JDA jda) {
    ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    if (readyCheck == null) return;

    Guild guild = jda.getGuildById(readyCheck.getGuildId());
    if (guild == null) return;

    Member member = guild.getMemberById(userId);
    if (member == null) return;

    if (shouldAutoReadyUser(member)) {
      autoReadyUserFromReminder(readyCheck, userId, readyCheckId, jda);
      return;
    }

    readyCheck.getScheduledUsers().remove(userId);
    TextChannel channel = guild.getTextChannelById(readyCheck.getChannelId());
    if (channel == null) return;

    deleteOldMessageAndSendReminder(readyCheck, readyCheckId, member, channel, jda);
  }

  private static boolean shouldAutoReadyUser(Member member) {
    return member.getVoiceState() != null
        && member.getVoiceState().inAudioChannel()
        && !member.getVoiceState().isDeafened();
  }

  private static void autoReadyUserFromReminder(
      ReadyCheck readyCheck, String userId, String readyCheckId, JDA jda) {
    readyCheck.getScheduledUsers().remove(userId);
    readyCheck.getReadyUsers().add(userId);
    readyCheck.getPassedUsers().remove(userId);

    updateReadyCheckEmbed(readyCheckId, jda);

    if (checkIfAllReady(readyCheckId)) {
      notifyAllReady(readyCheckId, jda);
    }
  }

  private static void deleteOldMessageAndSendReminder(
      ReadyCheck readyCheck, String readyCheckId, Member member, TextChannel channel, JDA jda) {
    if (readyCheck.getMessageId() != null) {
      channel
          .retrieveMessageById(readyCheck.getMessageId())
          .queue(
              oldMessage -> {
                oldMessage.delete().queue(null, error -> {});
                sendUpdatedReadyCheckWithReminder(readyCheckId, member, jda);
              },
              error -> sendUpdatedReadyCheckWithReminder(readyCheckId, member, jda));
    } else {
      sendUpdatedReadyCheckWithReminder(readyCheckId, member, jda);
    }
  }

  private static void sendUpdatedReadyCheckWithReminder(
      String readyCheckId, Member member, JDA jda) {
    ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    if (readyCheck == null) return;

    Guild guild = jda.getGuildById(readyCheck.getGuildId());
    TextChannel channel = getChannelFromGuild(guild, readyCheck.getChannelId());
    if (channel == null) return;

    EmbedBuilder embed = buildReadyCheckEmbed(readyCheck, jda, readyCheck.getDescription());
    List<Button> mainButtons = createMainButtons(readyCheckId);
    List<Button> saveButton = createSaveButton(readyCheckId);
    String reminderText = "⏰ " + member.getAsMention() + " it's time to be ready!";

    channel
        .sendMessage(reminderText)
        .setEmbeds(embed.build())
        .setComponents(ActionRow.of(mainButtons), ActionRow.of(saveButton))
        .queue(newMessage -> readyCheck.setMessageId(newMessage.getId()));
  }

  private static boolean isUserAlreadyProcessed(ReadyCheck readyCheck, String userId) {
    return readyCheck.getReadyUsers().contains(userId)
        || readyCheck.getPassedUsers().contains(userId);
  }

  private static boolean shouldAutoReadyUserInVoice(Member member) {
    return member.getVoiceState() != null
        && member.getVoiceState().inAudioChannel()
        && !member.getVoiceState().isDeafened();
  }

  private static void autoReadyUserInVoice(ReadyCheck readyCheck, String userId) {
    readyCheck.getReadyUsers().add(userId);
    cancelExistingScheduledUser(readyCheck, userId);
    readyCheck.getUserTimers().remove(userId);
    readyCheck.getUserUntilTimes().remove(userId);
  }

  private static Set<String> createUserSet(List<String> targetUserIds, String initiatorId) {
    Set<String> allUserIds = new HashSet<>(targetUserIds);
    allUserIds.add(initiatorId);
    return allUserIds;
  }

  private static String findExistingReadyCheckInternal(String guildId, Set<String> targetUsers) {
    return activeReadyChecks.values().stream()
        .filter(check -> check.getGuildId().equals(guildId))
        .filter(check -> isReadyCheckOngoing(check.getId()))
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
    Set<String> allUsers = getAllUsers(readyCheck);
    return allUsers.stream()
        .map(userId -> buildMemberStatus(readyCheck, userId, jda))
        .collect(Collectors.joining("\n"));
  }

  private static String buildMemberStatus(ReadyCheck readyCheck, String userId, JDA jda) {
    String displayName = getDisplayName(readyCheck, userId, jda);

    if (isUserPassed(readyCheck, userId)) {
      return buildPassedStatus(displayName);
    }

    if (isUserReady(readyCheck, userId)) {
      return buildReadyStatus(readyCheck, userId, displayName);
    }

    if (isUserScheduled(readyCheck, userId)) {
      return buildScheduledStatus(readyCheck, userId, displayName);
    }

    return buildNotReadyStatus(displayName);
  }

  private static String getDisplayName(ReadyCheck readyCheck, String userId, JDA jda) {
    Guild guild = jda.getGuildById(readyCheck.getGuildId());
    Member member = guild != null ? guild.getMemberById(userId) : null;
    return member != null ? member.getEffectiveName() : "Unknown User";
  }

  private static boolean isUserPassed(ReadyCheck readyCheck, String userId) {
    return readyCheck.getPassedUsers().contains(userId);
  }

  private static boolean isUserReady(ReadyCheck readyCheck, String userId) {
    return readyCheck.getReadyUsers().contains(userId);
  }

  private static boolean isUserScheduled(ReadyCheck readyCheck, String userId) {
    return readyCheck.getScheduledUsers().containsKey(userId);
  }

  private static String buildPassedStatus(String displayName) {
    return "🚫 ~~" + displayName + "~~";
  }

  private static String buildReadyStatus(ReadyCheck readyCheck, String userId, String displayName) {
    if (hasUntilTime(readyCheck, userId)) {
      return buildReadyUntilStatus(readyCheck, userId, displayName);
    }

    if (hasTimer(readyCheck, userId)) {
      return buildReadyWithTimerStatus(readyCheck, userId, displayName);
    }

    return "✅ " + displayName;
  }

  private static boolean hasUntilTime(ReadyCheck readyCheck, String userId) {
    return readyCheck.getUserUntilTimes().containsKey(userId);
  }

  private static boolean hasTimer(ReadyCheck readyCheck, String userId) {
    return readyCheck.getUserTimers().containsKey(userId);
  }

  private static String buildReadyUntilStatus(
      ReadyCheck readyCheck, String userId, String displayName) {
    String untilTime = readyCheck.getUserUntilTimes().get(userId);
    return "✅ " + displayName + " (until " + untilTime + ")";
  }

  private static String buildReadyWithTimerStatus(
      ReadyCheck readyCheck, String userId, String displayName) {
    Integer timerMinutes = readyCheck.getUserTimers().get(userId);
    return "✅ " + displayName + " (auto-unready in " + timerMinutes + "min)";
  }

  private static String buildScheduledStatus(
      ReadyCheck readyCheck, String userId, String displayName) {
    ScheduledUser scheduledUser = readyCheck.getScheduledUsers().get(userId);
    long readyTimeMs = scheduledUser.readyTimestamp();
    long minutesLeft = calculateMinutesLeft(readyTimeMs);

    if (minutesLeft <= 0) {
      return "⏰ " + displayName + " (ready now!)";
    }

    String timeDisplay = formatScheduledTime(readyTimeMs, minutesLeft);
    String timePrefix = minutesLeft >= 60 ? "ready " : "ready in ";
    return "⏰ " + displayName + " (" + timePrefix + timeDisplay + ")";
  }

  private static long calculateMinutesLeft(long readyTimeMs) {
    long currentTimeMs = System.currentTimeMillis();
    return Math.max(0, (readyTimeMs - currentTimeMs) / 60000);
  }

  private static String formatScheduledTime(long timestampMs, long minutesLeft) {
    if (minutesLeft >= 60) {
      long discordTimestamp = timestampMs / 1000;
      return "<t:" + discordTimestamp + ":t>";
    } else if (minutesLeft >= 1) {
      return minutesLeft + "min";
    } else {
      return "ready now!";
    }
  }

  private static String buildNotReadyStatus(String displayName) {
    return "❌ " + displayName;
  }

  public static List<Button> createMainButtons(String readyCheckId) {
    return Arrays.asList(
        Button.success("toggle_ready_" + readyCheckId, "Toggle Ready"),
        Button.primary("ready_at_" + readyCheckId, "Ready At..."),
        Button.primary("ready_until_" + readyCheckId, "Ready Until..."),
        Button.danger("pass_" + readyCheckId, "Pass"));
  }

  public static List<Button> createSaveButton(String readyCheckId) {
    return Collections.singletonList(Button.secondary("save_ready_" + readyCheckId, "💾"));
  }

  private static String createMentions(ReadyCheck readyCheck, JDA jda) {
    if (!getMentionPreference(readyCheck.getId())) {
      return "";
    }

    Set<String> allUsers = getAllUsers(readyCheck);
    Guild guild = jda.getGuildById(readyCheck.getGuildId());
    if (guild == null) return "";

    return allUsers.stream()
        .filter(userId -> !readyCheck.getReadyUsers().contains(userId))
        .filter(userId -> !readyCheck.getPassedUsers().contains(userId))
        .filter(userId -> !readyCheck.getScheduledUsers().containsKey(userId))
        .filter(userId -> !readyCheck.getUserUntilTimes().containsKey(userId))
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
            response ->
                response
                    .retrieveOriginal()
                    .queue(message -> setReadyCheckMessageId(readyCheckId, message.getId())));
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
            response ->
                response
                    .retrieveOriginal()
                    .queue(message -> setReadyCheckMessageId(readyCheckId, message.getId())));
  }

  private static int getReadyCount(ReadyCheck readyCheck) {
    Set<String> allUsers = getAllUsers(readyCheck);
    return (int)
        allUsers.stream()
            .filter(userId -> !readyCheck.getPassedUsers().contains(userId))
            .filter(userId -> readyCheck.getReadyUsers().contains(userId))
            .count();
  }

  private static int getNonPassedCount(ReadyCheck readyCheck) {
    Set<String> allUsers = getAllUsers(readyCheck);
    return (int)
        allUsers.stream().filter(userId -> !readyCheck.getPassedUsers().contains(userId)).count();
  }

  private static boolean allNonPassedReady(ReadyCheck readyCheck) {
    Set<String> allUsers = getAllUsers(readyCheck);
    return allUsers.stream()
        .filter(userId -> !readyCheck.getPassedUsers().contains(userId))
        .allMatch(userId -> readyCheck.getReadyUsers().contains(userId));
  }

  private static void saveSavedConfigurations() {
    try (FileWriter writer = new FileWriter(SAVE_FILE)) {
      gson.toJson(savedReadyChecks, writer);
      logger.debug("Saved {} ready check configurations to file", savedReadyChecks.size());
    } catch (IOException e) {
      logger.error("Failed to save configurations to {}: {}", SAVE_FILE, e.getMessage(), e);
    }
  }

  private static void loadSavedConfigurations() {
    File file = new File(SAVE_FILE);

    File parentDir = file.getParentFile();
    if (parentDir != null && !parentDir.exists()) {
      boolean created = parentDir.mkdirs();
      if (created) {
        logger.info("Created config directory: {}", parentDir.getAbsolutePath());
      }
    }

    if (!file.exists()) {
      logger.info("No existing configuration file found at: {}", file.getAbsolutePath());
      return;
    }

    try (FileReader reader = new FileReader(file)) {
      Type type = new TypeToken<Map<String, SavedReadyCheck>>() {}.getType();
      Map<String, SavedReadyCheck> loaded = gson.fromJson(reader, type);
      if (loaded != null) {
        savedReadyChecks.putAll(loaded);
        logger.info(
            "Loaded {} saved ready check configurations from {}",
            loaded.size(),
            file.getAbsolutePath());
      }
    } catch (IOException e) {
      logger.error(
          "Failed to load configurations from {}: {}", file.getAbsolutePath(), e.getMessage(), e);
    }
  }

  private static String getConfigFilePath() {
    String configDir = System.getProperty("bot.config.dir", "/app/data");
    File dockerPath = new File(configDir);
    if (dockerPath.exists() && dockerPath.isDirectory()) {
      return configDir + "/saved_ready_checks.json";
    }
    return "saved_ready_checks.json";
  }

  public record ScheduledUser(long readyTimestamp, ScheduledFuture<?> reminderFuture) {
    public void cancel() {
      if (reminderFuture != null && !reminderFuture.isDone()) {
        reminderFuture.cancel(false);
      }
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
    private final Map<String, ScheduledUser> scheduledUsers;
    private final Map<String, ScheduledFuture<?>> scheduledUntilFutures;
    private final Map<String, Integer> userTimers;
    private final Map<String, String> userUntilTimes;
    private final Set<String> passedUsers;
    private final long createdTime;
    private String messageId;
    private String completionMessageId;
    private ReadyCheckStatus status;
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
      this.userTimers = new HashMap<>();
      this.userUntilTimes = new HashMap<>();
      this.passedUsers = new HashSet<>();
      this.status = ReadyCheckStatus.ACTIVE;
      this.createdTime = System.currentTimeMillis();
      this.scheduledUsers = new HashMap<>();
      this.scheduledUntilFutures = new HashMap<>();
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

    public Map<String, ScheduledUser> getScheduledUsers() {
      return scheduledUsers;
    }

    public Map<String, ScheduledFuture<?>> getScheduledUntilFutures() {
      return scheduledUntilFutures;
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

    public void setDescription(String description) {
      this.description = description;
    }
  }

  public static class SavedReadyCheck {
    private final String roleId;
    private final List<String> userIds;
    private final boolean userBased;
    private final boolean mentionPeople;

    public SavedReadyCheck(String roleId, boolean userBased, boolean mentionPeople) {
      this.roleId = roleId;
      this.userIds = null;
      this.userBased = userBased;
      this.mentionPeople = mentionPeople;
    }

    public SavedReadyCheck(List<String> userIds, boolean userBased, boolean mentionPeople) {
      this.roleId = null;
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
  }
}
