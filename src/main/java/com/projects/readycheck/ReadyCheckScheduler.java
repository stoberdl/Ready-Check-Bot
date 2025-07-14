package com.projects.readycheck;

import com.projects.readycheck.utils.ReadyCheckTimeParser;
import com.projects.readycheck.utils.ReadyCheckUtils;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
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

public final class ReadyCheckScheduler {
  private static final Logger logger = LoggerFactory.getLogger(ReadyCheckScheduler.class);
  private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
  private static final ZoneId SYSTEM_TIMEZONE = ZoneId.systemDefault();
  private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm a");

  private ReadyCheckScheduler() {}

  public static ScheduledExecutorService getScheduler() {
    return scheduler;
  }

  public static void scheduleReadyAt(
      final ReadyCheckManager.ReadyCheck readyCheck,
      final String timeInput,
      final String userId,
      final JDA jda) {
    final long delayMinutes = ReadyCheckTimeParser.parseTimeInputAsMinutes(timeInput);

    ensureUserInTargets(readyCheck, userId);
    cancelExistingScheduledUser(readyCheck, userId);

    final Instant targetTime = Instant.now().plus(Duration.ofMinutes(delayMinutes));
    scheduleUserReady(readyCheck, userId, targetTime, jda);
  }

  public static String scheduleReadyAtSmart(
      final ReadyCheckManager.ReadyCheck readyCheck,
      final String timeInput,
      final String userId,
      final JDA jda) {
    final LocalTime targetTime = ReadyCheckTimeParser.parseTargetTime(timeInput);
    cancelExistingScheduledUser(readyCheck, userId);
    readyCheck.getReadyUsers().remove(userId);

    final LocalDateTime now = LocalDateTime.now(SYSTEM_TIMEZONE);
    LocalDateTime target = LocalDateTime.of(now.toLocalDate(), targetTime);

    if (!target.isAfter(now)) {
      target = target.plusDays(1);
    }

    final Duration delay = Duration.between(now, target);
    long delayMinutes = delay.toMinutes();
    if (delay.toSecondsPart() > 0) {
      delayMinutes++;
    }

    final ScheduledFuture<?> reminderFuture =
        scheduler.schedule(
            () -> sendReadyReminder(readyCheck.getId(), userId, jda),
            delayMinutes,
            TimeUnit.MINUTES);

    final ReadyCheckManager.ScheduledUser scheduledUser =
        new ReadyCheckManager.ScheduledUser(
            target.atZone(SYSTEM_TIMEZONE).toInstant().toEpochMilli(), reminderFuture);
    readyCheck.getScheduledUsers().put(userId, scheduledUser);

    return targetTime.format(TIME_FORMATTER);
  }

  public static String scheduleReadyUntil(
      final ReadyCheckManager.ReadyCheck readyCheck,
      final String timeInput,
      final String userId,
      final JDA jda) {
    final LocalTime targetTime = ReadyCheckTimeParser.parseTargetTime(timeInput);
    ensureUserInTargets(readyCheck, userId);

    final LocalDateTime now = LocalDateTime.now(SYSTEM_TIMEZONE);
    LocalDateTime target = LocalDateTime.of(now.toLocalDate(), targetTime);

    if (!target.isAfter(now)) {
      target = target.plusDays(1);
    }

    final Duration delay = Duration.between(now, target);
    long delayMinutes = delay.toMinutes();
    if (delay.toSecondsPart() > 0) {
      delayMinutes++;
    }

    final ScheduledFuture<?> untilFuture =
        scheduler.schedule(
            () -> autoPassUser(readyCheck, userId, jda), delayMinutes, TimeUnit.MINUTES);

    readyCheck.getScheduledUntilFutures().put(userId, untilFuture);
    final String discordTimestamp =
        "<t:" + target.atZone(SYSTEM_TIMEZONE).toInstant().getEpochSecond() + ":t>";
    readyCheck.getUserUntilTimes().put(userId, discordTimestamp);

    return discordTimestamp;
  }

  public static void startPeriodicUpdater() {
    scheduler.scheduleWithFixedDelay(
        () -> {
          try {
            updateAllReadyCheckCountdowns();
          } catch (final Exception e) {
            logger.error("Error in periodic updater: {}", e.getMessage(), e);
          }
        },
        1,
        1,
        TimeUnit.MINUTES);
  }

  public static void cleanupExpiredScheduledUsers(final ReadyCheckManager.ReadyCheck readyCheck) {
    final long currentTime = System.currentTimeMillis();
    final Set<String> expiredUsers =
        readyCheck.getScheduledUsers().entrySet().stream()
            .filter(entry -> entry.getValue().readyTimestamp() <= currentTime)
            .map(java.util.Map.Entry::getKey)
            .collect(Collectors.toSet());

    expiredUsers.forEach(userId -> readyCheck.getScheduledUsers().remove(userId));
  }

  public static void cancelExistingScheduledUser(
      final ReadyCheckManager.ReadyCheck readyCheck, final String userId) {
    final ReadyCheckManager.ScheduledUser existingScheduled =
        readyCheck.getScheduledUsers().remove(userId);
    if (existingScheduled != null) {
      existingScheduled.cancel();
    }

    final ScheduledFuture<?> existingUntil = readyCheck.getScheduledUntilFutures().remove(userId);
    if (existingUntil != null && !existingUntil.isDone()) {
      existingUntil.cancel(false);
    }
  }

  private static void updateAllReadyCheckCountdowns() {
    final JDA jda = ReadyCheckManager.getJDA();
    if (jda == null) return;

    for (final ReadyCheckManager.ReadyCheck readyCheck :
        ReadyCheckManager.getActiveReadyChecks().values()) {

      if (readyCheck.getStatus() != ReadyCheckManager.ReadyCheckStatus.ACTIVE) {
        continue;
      }

      final boolean botsRemoved = removeBotsFromReadyCheck(readyCheck, jda);
      final boolean embedNeedsUpdate = checkAndReadyUsersInVoice(readyCheck, jda);
      final boolean hasScheduledUsers = !readyCheck.getScheduledUsers().isEmpty();

      if (hasScheduledUsers || embedNeedsUpdate || botsRemoved) {
        ReadyCheckManager.updateReadyCheckEmbed(readyCheck.getId(), jda);
      }

      if (embedNeedsUpdate && ReadyCheckManager.checkIfAllReady(readyCheck.getId())) {
        ReadyCheckManager.notifyAllReady(readyCheck.getId(), jda);
      }
    }
  }

  private static boolean removeBotsFromReadyCheck(
      final ReadyCheckManager.ReadyCheck readyCheck, final JDA jda) {
    final Guild guild = jda.getGuildById(readyCheck.getGuildId());
    if (guild == null) return false;

    final Set<String> allUsers = ReadyCheckUtils.getAllUsers(readyCheck);
    final Set<String> botUsers = new HashSet<>();

    for (final String userId : allUsers) {
      final Member member = guild.getMemberById(userId);
      if (member != null && member.getUser().isBot()) {
        botUsers.add(userId);
      }
    }

    if (botUsers.isEmpty()) return false;

    for (final String botUserId : botUsers) {
      readyCheck.getTargetUsers().remove(botUserId);
      readyCheck.getReadyUsers().remove(botUserId);
      readyCheck.getPassedUsers().remove(botUserId);
      readyCheck.getScheduledUsers().remove(botUserId);
      readyCheck.getUserTimers().remove(botUserId);
      readyCheck.getUserUntilTimes().remove(botUserId);

      final ScheduledFuture<?> untilFuture =
          readyCheck.getScheduledUntilFutures().remove(botUserId);
      if (untilFuture != null && !untilFuture.isDone()) {
        untilFuture.cancel(false);
      }
    }

    return true;
  }

  private static boolean checkAndReadyUsersInVoice(
      final ReadyCheckManager.ReadyCheck readyCheck, final JDA jda) {
    final Guild guild = jda.getGuildById(readyCheck.getGuildId());
    if (guild == null) return false;

    boolean anyChanges = false;
    final Set<String> allUsers = ReadyCheckUtils.getAllUsers(readyCheck);

    for (final String userId : allUsers) {
      if (isUserAlreadyProcessed(readyCheck, userId)) {
        continue;
      }

      final Member member = guild.getMemberById(userId);
      if (member == null) {
        continue;
      }

      if (shouldAutoReadyUserInVoice(member)) {
        autoReadyUserInVoice(readyCheck, userId);
        anyChanges = true;
      }
    }

    return anyChanges;
  }

  private static void scheduleUserReady(
      final ReadyCheckManager.ReadyCheck readyCheck,
      final String userId,
      final Instant targetTime,
      final JDA jda) {
    readyCheck.getReadyUsers().remove(userId);
    readyCheck.getPassedUsers().remove(userId);

    final Duration delay = Duration.between(Instant.now(), targetTime);
    final long delayMinutes = Math.max(1, delay.toMinutes());

    final ScheduledFuture<?> reminderFuture =
        scheduler.schedule(
            () -> sendReadyReminder(readyCheck.getId(), userId, jda),
            delayMinutes,
            TimeUnit.MINUTES);

    final ReadyCheckManager.ScheduledUser scheduledUser =
        new ReadyCheckManager.ScheduledUser(targetTime.toEpochMilli(), reminderFuture);
    readyCheck.getScheduledUsers().put(userId, scheduledUser);
  }

  private static void sendReadyReminder(
      final String readyCheckId, final String userId, final JDA jda) {
    final ReadyCheckManager.ReadyCheck readyCheck =
        ReadyCheckManager.getActiveReadyCheck(readyCheckId);
    if (readyCheck == null) return;

    final Guild guild = jda.getGuildById(readyCheck.getGuildId());
    if (guild == null) return;

    final Member member = guild.getMemberById(userId);
    if (member == null) return;

    if (shouldAutoReadyUser(member)) {
      autoReadyUserFromReminder(readyCheck, userId, readyCheckId, jda);
      return;
    }

    readyCheck.getScheduledUsers().remove(userId);
    final TextChannel channel = guild.getTextChannelById(readyCheck.getChannelId());
    if (channel == null) return;

    deleteOldMessageAndSendReminder(readyCheck, readyCheckId, member, channel, jda);
  }

  private static boolean shouldAutoReadyUser(final Member member) {
    return member.getVoiceState() != null
        && member.getVoiceState().inAudioChannel()
        && !member.getVoiceState().isDeafened();
  }

  private static void autoReadyUserFromReminder(
      final ReadyCheckManager.ReadyCheck readyCheck,
      final String userId,
      final String readyCheckId,
      final JDA jda) {
    readyCheck.getScheduledUsers().remove(userId);
    readyCheck.getReadyUsers().add(userId);
    readyCheck.getPassedUsers().remove(userId);

    ReadyCheckManager.updateReadyCheckEmbed(readyCheckId, jda);

    if (ReadyCheckManager.checkIfAllReady(readyCheckId)) {
      ReadyCheckManager.notifyAllReady(readyCheckId, jda);
    }
  }

  private static void deleteOldMessageAndSendReminder(
      final ReadyCheckManager.ReadyCheck readyCheck,
      final String readyCheckId,
      final Member member,
      final TextChannel channel,
      final JDA jda) {
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
      final String readyCheckId, final Member member, final JDA jda) {
    final ReadyCheckManager.ReadyCheck readyCheck =
        ReadyCheckManager.getActiveReadyCheck(readyCheckId);
    if (readyCheck == null) return;

    final Guild guild = jda.getGuildById(readyCheck.getGuildId());
    final TextChannel channel =
        ReadyCheckUtils.getChannelFromGuild(guild, readyCheck.getChannelId());
    if (channel == null) return;

    final var embed =
        ReadyCheckEmbedBuilder.buildReadyCheckEmbed(readyCheck, jda, readyCheck.getDescription());
    final var mainButtons = ReadyCheckUtils.createMainButtons(readyCheckId);
    final var saveButton = ReadyCheckUtils.createSaveButton(readyCheckId);
    final String reminderText = "⏰ " + member.getAsMention() + " it's time to be ready!";

    channel
        .sendMessage(reminderText)
        .setEmbeds(embed.build())
        .setComponents(ActionRow.of(mainButtons), ActionRow.of(saveButton))
        .queue(newMessage -> readyCheck.setMessageId(newMessage.getId()));
  }

  private static void autoPassUser(
      final ReadyCheckManager.ReadyCheck readyCheck, final String userId, final JDA jda) {
    readyCheck.getPassedUsers().add(userId);
    readyCheck.getReadyUsers().remove(userId);
    readyCheck.getUserUntilTimes().remove(userId);

    ReadyCheckManager.updateReadyCheckEmbed(readyCheck.getId(), jda);
  }

  private static void ensureUserInTargets(
      final ReadyCheckManager.ReadyCheck readyCheck, final String userId) {
    readyCheck.getTargetUsers().add(userId);
  }

  private static boolean isUserAlreadyProcessed(
      final ReadyCheckManager.ReadyCheck readyCheck, final String userId) {
    return readyCheck.getReadyUsers().contains(userId)
        || readyCheck.getPassedUsers().contains(userId);
  }

  private static boolean shouldAutoReadyUserInVoice(final Member member) {
    return member.getVoiceState() != null
        && member.getVoiceState().inAudioChannel()
        && !member.getVoiceState().isDeafened();
  }

  private static void autoReadyUserInVoice(
      final ReadyCheckManager.ReadyCheck readyCheck, final String userId) {
    readyCheck.getReadyUsers().add(userId);
    cancelExistingScheduledUser(readyCheck, userId);
    readyCheck.getUserTimers().remove(userId);
    readyCheck.getUserUntilTimes().remove(userId);
  }
}
