package com.projects.readycheck;

import com.projects.readycheck.utils.ReadyCheckTimeParser;
import com.projects.readycheck.utils.ReadyCheckUtils;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReadyCheckScheduler {
  private static final Logger logger = LoggerFactory.getLogger(ReadyCheckScheduler.class);
  private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
  private static final ZoneId SYSTEM_TIMEZONE = ZoneId.systemDefault();
  private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm a");

  private ReadyCheckScheduler() {}

  public static ScheduledExecutorService getScheduler() {
    return scheduler;
  }

  public static void scheduleReadyAt(
      ReadyCheckManager.ReadyCheck readyCheck, String timeInput, String userId, JDA jda) {
    long delayMinutes = ReadyCheckTimeParser.parseTimeInputAsMinutes(timeInput);

    ensureUserInTargets(readyCheck, userId);
    cancelExistingScheduledUser(readyCheck, userId);

    Instant targetTime = Instant.now().plus(Duration.ofMinutes(delayMinutes));
    scheduleUserReady(readyCheck, userId, targetTime, jda);
  }

  public static String scheduleReadyAtSmart(
      ReadyCheckManager.ReadyCheck readyCheck, String timeInput, String userId, JDA jda) {
    LocalTime targetTime = ReadyCheckTimeParser.parseTargetTime(timeInput);
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
            () -> sendReadyReminder(readyCheck.getId(), userId, jda),
            delayMinutes,
            TimeUnit.MINUTES);

    ReadyCheckManager.ScheduledUser scheduledUser =
        new ReadyCheckManager.ScheduledUser(
            target.atZone(SYSTEM_TIMEZONE).toInstant().toEpochMilli(), reminderFuture);
    readyCheck.getScheduledUsers().put(userId, scheduledUser);

    return targetTime.format(TIME_FORMATTER);
  }

  public static String scheduleReadyUntil(
      ReadyCheckManager.ReadyCheck readyCheck, String timeInput, String userId, JDA jda) {
    LocalTime targetTime = ReadyCheckTimeParser.parseTargetTime(timeInput);
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
            () -> autoPassUser(readyCheck, userId, jda), delayMinutes, TimeUnit.MINUTES);

    readyCheck.getScheduledUntilFutures().put(userId, untilFuture);
    String discordTimestamp =
        "<t:" + target.atZone(SYSTEM_TIMEZONE).toInstant().getEpochSecond() + ":t>";
    readyCheck.getUserUntilTimes().put(userId, discordTimestamp);

    return discordTimestamp;
  }

  public static void startPeriodicUpdater() {
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

  public static void cleanupExpiredScheduledUsers(ReadyCheckManager.ReadyCheck readyCheck) {
    long currentTime = System.currentTimeMillis();
    Set<String> expiredUsers =
        readyCheck.getScheduledUsers().entrySet().stream()
            .filter(entry -> entry.getValue().readyTimestamp() <= currentTime)
            .map(java.util.Map.Entry::getKey)
            .collect(Collectors.toSet());

    expiredUsers.forEach(userId -> readyCheck.getScheduledUsers().remove(userId));
  }

  public static void cancelExistingScheduledUser(
      ReadyCheckManager.ReadyCheck readyCheck, String userId) {
    ReadyCheckManager.ScheduledUser existingScheduled =
        readyCheck.getScheduledUsers().remove(userId);
    if (existingScheduled != null) {
      existingScheduled.cancel();
    }

    ScheduledFuture<?> existingUntil = readyCheck.getScheduledUntilFutures().remove(userId);
    if (existingUntil != null && !existingUntil.isDone()) {
      existingUntil.cancel(false);
    }
  }

  private static void updateAllReadyCheckCountdowns() {
    JDA jda = ReadyCheckManager.getJDA();
    if (jda == null) return;

    for (ReadyCheckManager.ReadyCheck readyCheck :
        ReadyCheckManager.getActiveReadyChecks().values()) {
      if (readyCheck.getStatus() != ReadyCheckManager.ReadyCheckStatus.ACTIVE) continue;

      boolean embedNeedsUpdate = checkAndReadyUsersInVoice(readyCheck, jda);

      if (!readyCheck.getScheduledUsers().isEmpty() || embedNeedsUpdate) {
        ReadyCheckManager.updateReadyCheckEmbed(readyCheck.getId(), jda);
      }

      if (embedNeedsUpdate && ReadyCheckManager.checkIfAllReady(readyCheck.getId())) {
        ReadyCheckManager.notifyAllReady(readyCheck.getId(), jda);
      }
    }
  }

  private static boolean checkAndReadyUsersInVoice(
      ReadyCheckManager.ReadyCheck readyCheck, JDA jda) {
    Guild guild = jda.getGuildById(readyCheck.getGuildId());
    if (guild == null) return false;

    boolean anyChanges = false;
    Set<String> allUsers = ReadyCheckUtils.getAllUsers(readyCheck);

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

  private static void scheduleUserReady(
      ReadyCheckManager.ReadyCheck readyCheck, String userId, Instant targetTime, JDA jda) {
    readyCheck.getReadyUsers().remove(userId);
    readyCheck.getPassedUsers().remove(userId);

    Duration delay = Duration.between(Instant.now(), targetTime);
    long delayMinutes = Math.max(1, delay.toMinutes());

    ScheduledFuture<?> reminderFuture =
        scheduler.schedule(
            () -> sendReadyReminder(readyCheck.getId(), userId, jda),
            delayMinutes,
            TimeUnit.MINUTES);

    ReadyCheckManager.ScheduledUser scheduledUser =
        new ReadyCheckManager.ScheduledUser(targetTime.toEpochMilli(), reminderFuture);
    readyCheck.getScheduledUsers().put(userId, scheduledUser);
  }

  private static void sendReadyReminder(String readyCheckId, String userId, JDA jda) {
    ReadyCheckManager.ReadyCheck readyCheck = ReadyCheckManager.getActiveReadyCheck(readyCheckId);
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
      ReadyCheckManager.ReadyCheck readyCheck, String userId, String readyCheckId, JDA jda) {
    readyCheck.getScheduledUsers().remove(userId);
    readyCheck.getReadyUsers().add(userId);
    readyCheck.getPassedUsers().remove(userId);

    ReadyCheckManager.updateReadyCheckEmbed(readyCheckId, jda);

    if (ReadyCheckManager.checkIfAllReady(readyCheckId)) {
      ReadyCheckManager.notifyAllReady(readyCheckId, jda);
    }
  }

  private static void deleteOldMessageAndSendReminder(
      ReadyCheckManager.ReadyCheck readyCheck,
      String readyCheckId,
      Member member,
      TextChannel channel,
      JDA jda) {
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
    ReadyCheckManager.ReadyCheck readyCheck = ReadyCheckManager.getActiveReadyCheck(readyCheckId);
    if (readyCheck == null) return;

    Guild guild = jda.getGuildById(readyCheck.getGuildId());
    TextChannel channel = ReadyCheckUtils.getChannelFromGuild(guild, readyCheck.getChannelId());
    if (channel == null) return;

    var embed =
        ReadyCheckEmbedBuilder.buildReadyCheckEmbed(readyCheck, jda, readyCheck.getDescription());
    var mainButtons = ReadyCheckUtils.createMainButtons(readyCheckId);
    var saveButton = ReadyCheckUtils.createSaveButton(readyCheckId);
    String reminderText = "⏰ " + member.getAsMention() + " it's time to be ready!";

    channel
        .sendMessage(reminderText)
        .setEmbeds(embed.build())
        .setComponents(ActionRow.of(mainButtons), ActionRow.of(saveButton))
        .queue(newMessage -> readyCheck.setMessageId(newMessage.getId()));
  }

  private static void autoPassUser(
      ReadyCheckManager.ReadyCheck readyCheck, String userId, JDA jda) {
    readyCheck.getPassedUsers().add(userId);
    readyCheck.getReadyUsers().remove(userId);
    readyCheck.getUserUntilTimes().remove(userId);

    ReadyCheckManager.updateReadyCheckEmbed(readyCheck.getId(), jda);
  }

  private static void ensureUserInTargets(ReadyCheckManager.ReadyCheck readyCheck, String userId) {
    readyCheck.getTargetUsers().add(userId);
  }

  private static boolean isUserAlreadyProcessed(
      ReadyCheckManager.ReadyCheck readyCheck, String userId) {
    return readyCheck.getReadyUsers().contains(userId)
        || readyCheck.getPassedUsers().contains(userId);
  }

  private static boolean shouldAutoReadyUserInVoice(Member member) {
    return member.getVoiceState() != null
        && member.getVoiceState().inAudioChannel()
        && !member.getVoiceState().isDeafened();
  }

  private static void autoReadyUserInVoice(ReadyCheckManager.ReadyCheck readyCheck, String userId) {
    readyCheck.getReadyUsers().add(userId);
    cancelExistingScheduledUser(readyCheck, userId);
    readyCheck.getUserTimers().remove(userId);
    readyCheck.getUserUntilTimes().remove(userId);
  }
}
