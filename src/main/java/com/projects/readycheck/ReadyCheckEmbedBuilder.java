package com.projects.readycheck;

import java.awt.Color;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;

public class ReadyCheckEmbedBuilder {

  private ReadyCheckEmbedBuilder() {}

  public static EmbedBuilder buildReadyCheckEmbed(
      ReadyCheckManager.ReadyCheck readyCheck, JDA jda, String description) {
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

    EmbedBuilder embedBuilder =
        new EmbedBuilder()
            .setTitle(status)
            .setDescription(description + "\n\n" + buildMemberList(readyCheck, jda))
            .setColor(color)
            .setTimestamp(Instant.now());

    if (readyCheck.isRecovered()) {
      embedBuilder.setFooter("♻️");
    }

    return embedBuilder;
  }

  private static String buildMemberList(ReadyCheckManager.ReadyCheck readyCheck, JDA jda) {
    Set<String> allUsers = getAllUsers(readyCheck);
    return allUsers.stream()
        .map(userId -> buildMemberStatus(readyCheck, userId, jda))
        .collect(Collectors.joining("\n"));
  }

  private static String buildMemberStatus(
      ReadyCheckManager.ReadyCheck readyCheck, String userId, JDA jda) {
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

  private static String getDisplayName(
      ReadyCheckManager.ReadyCheck readyCheck, String userId, JDA jda) {
    Guild guild = jda.getGuildById(readyCheck.getGuildId());
    Member member = guild != null ? guild.getMemberById(userId) : null;
    return member != null ? member.getEffectiveName() : "Unknown User";
  }

  private static boolean isUserPassed(ReadyCheckManager.ReadyCheck readyCheck, String userId) {
    return readyCheck.getPassedUsers().contains(userId);
  }

  private static boolean isUserReady(ReadyCheckManager.ReadyCheck readyCheck, String userId) {
    return readyCheck.getReadyUsers().contains(userId);
  }

  private static boolean isUserScheduled(ReadyCheckManager.ReadyCheck readyCheck, String userId) {
    return readyCheck.getScheduledUsers().containsKey(userId);
  }

  private static String buildPassedStatus(String displayName) {
    return "🚫 ~~" + displayName + "~~";
  }

  private static String buildReadyStatus(
      ReadyCheckManager.ReadyCheck readyCheck, String userId, String displayName) {
    if (hasTimer(readyCheck, userId)) {
      return buildReadyWithTimerStatus(readyCheck, userId, displayName);
    }

    return "✅ " + displayName;
  }

  private static boolean hasTimer(ReadyCheckManager.ReadyCheck readyCheck, String userId) {
    return readyCheck.getUserTimers().containsKey(userId);
  }

  private static String buildReadyWithTimerStatus(
      ReadyCheckManager.ReadyCheck readyCheck, String userId, String displayName) {
    Integer timerMinutes = readyCheck.getUserTimers().get(userId);
    return "✅ " + displayName + " (auto-unready in " + timerMinutes + "min)";
  }

  private static String buildScheduledStatus(
      ReadyCheckManager.ReadyCheck readyCheck, String userId, String displayName) {
    ReadyCheckManager.ScheduledUser scheduledUser = readyCheck.getScheduledUsers().get(userId);
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

  private static Set<String> getAllUsers(ReadyCheckManager.ReadyCheck readyCheck) {
    Set<String> allUsers = new java.util.HashSet<>(readyCheck.getTargetUsers());
    allUsers.add(readyCheck.getInitiatorId());
    return allUsers;
  }

  private static int getReadyCount(ReadyCheckManager.ReadyCheck readyCheck) {
    Set<String> allUsers = getAllUsers(readyCheck);
    return (int)
        allUsers.stream()
            .filter(userId -> !readyCheck.getPassedUsers().contains(userId))
            .filter(userId -> readyCheck.getReadyUsers().contains(userId))
            .count();
  }

  private static int getNonPassedCount(ReadyCheckManager.ReadyCheck readyCheck) {
    Set<String> allUsers = getAllUsers(readyCheck);
    return (int)
        allUsers.stream().filter(userId -> !readyCheck.getPassedUsers().contains(userId)).count();
  }

  private static boolean allNonPassedReady(ReadyCheckManager.ReadyCheck readyCheck) {
    Set<String> allUsers = getAllUsers(readyCheck);
    return allUsers.stream()
        .filter(userId -> !readyCheck.getPassedUsers().contains(userId))
        .allMatch(userId -> readyCheck.getReadyUsers().contains(userId));
  }

  public static String createMentions(
      ReadyCheckManager.ReadyCheck readyCheck, JDA jda, String readyCheckId) {
    boolean mentionPeople = ReadyCheckManager.getMentionPreference(readyCheckId);
    if (!mentionPeople) {
      return "";
    }

    Set<String> allUsers = getAllUsers(readyCheck);
    Guild guild = jda.getGuildById(readyCheck.getGuildId());
    if (guild == null) return "";

    return allUsers.stream()
        .filter(userId -> !readyCheck.getReadyUsers().contains(userId))
        .filter(userId -> !readyCheck.getPassedUsers().contains(userId))
        .filter(userId -> !readyCheck.getScheduledUsers().containsKey(userId))
        .map(
            userId -> {
              Member member = guild.getMemberById(userId);
              return member != null ? member.getAsMention() : null;
            })
        .filter(Objects::nonNull)
        .collect(Collectors.joining(" "));
  }
}
