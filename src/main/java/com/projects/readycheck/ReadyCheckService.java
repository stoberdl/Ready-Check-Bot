package com.projects.readycheck;

import com.projects.readycheck.exceptions.ReadyCheckNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Member;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReadyCheckService {
  private static final Logger logger = LoggerFactory.getLogger(ReadyCheckService.class);
  private static final long EIGHT_HOURS_MS = TimeUnit.HOURS.toMillis(8);

  private final Map<String, ReadyCheckManager.ReadyCheck> activeReadyChecks = new ConcurrentHashMap<>();
  private final Map<String, Boolean> mentionPreferences = new ConcurrentHashMap<>();

  public ReadyCheckManager.ReadyCheck getActiveReadyCheck(final String readyCheckId) {
    return activeReadyChecks.get(readyCheckId);
  }

  public Map<String, ReadyCheckManager.ReadyCheck> getActiveReadyChecks() {
    return activeReadyChecks;
  }

  public String createUserReadyCheck(
      final String guildId,
      final String channelId,
      final String initiatorId,
      final List<Member> targetMembers) {
    return createReadyCheck(
        guildId, channelId, initiatorId, null, targetMembers, "**Ready Check** for specific users");
  }

  public String createReadyCheck(
      final String guildId,
      final String channelId,
      final String initiatorId,
      final String roleId,
      final List<Member> targetMembers) {
    return createReadyCheck(
        guildId, channelId, initiatorId, roleId, targetMembers, "**Ready Check** for role members");
  }

  private String createReadyCheck(
      final String guildId,
      final String channelId,
      final String initiatorId,
      final String roleId,
      final List<Member> targetMembers,
      final String description) {

    final String readyCheckId = java.util.UUID.randomUUID().toString();
    final List<String> targetUserIds = targetMembers.stream().map(Member::getId).toList();

    final ReadyCheckManager.ReadyCheck readyCheck =
        new ReadyCheckManager.ReadyCheck(readyCheckId, guildId, channelId, initiatorId, roleId, targetUserIds);
    readyCheck.setDescription(description);
    readyCheck.getReadyUsers().add(initiatorId);
    readyCheck.getTargetUsers().add(initiatorId);

    activeReadyChecks.put(readyCheckId, readyCheck);
    return readyCheckId;
  }

  public void setMentionPreference(final String readyCheckId, final boolean mentionPeople) {
    mentionPreferences.put(readyCheckId, mentionPeople);
  }

  public boolean getMentionPreference(final String readyCheckId) {
    return mentionPreferences.getOrDefault(readyCheckId, true);
  }

  public void markUserReady(final String readyCheckId, final String userId) {
    final ReadyCheckManager.ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    if (readyCheck == null) {
      logger.warn(
          "Attempted to mark user {} ready for non-existent ready check: {}", userId, readyCheckId);
      return;
    }

    ReadyCheckScheduler.cancelExistingScheduledUser(readyCheck, userId);
    readyCheck.getUserTimers().remove(userId);
    readyCheck.getReadyUsers().add(userId);
    readyCheck.getPassedUsers().remove(userId);
  }

  public boolean toggleUserReady(final String readyCheckId, final String userId) {
    final ReadyCheckManager.ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    if (readyCheck == null) return false;

    readyCheck.getTargetUsers().add(userId);

    if (readyCheck.getReadyUsers().contains(userId)) {
      readyCheck.getReadyUsers().remove(userId);
      return false;
    }

    markUserAsReady(readyCheck, userId);
    return true;
  }

  public void markUserPassed(final String readyCheckId, final String userId) {
    final ReadyCheckManager.ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    if (readyCheck == null) return;

    readyCheck.getTargetUsers().add(userId);
    readyCheck.getPassedUsers().add(userId);
    readyCheck.getReadyUsers().remove(userId);
    readyCheck.getScheduledUsers().remove(userId);
  }

  public void ensureUserInReadyCheck(final String readyCheckId, final String userId) {
    final ReadyCheckManager.ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    if (readyCheck != null) {
      readyCheck.getTargetUsers().add(userId);
    }
  }

  public void scheduleReadyAt(
      final String readyCheckId, final String timeInput, final String userId, final JDA jda)
      throws ReadyCheckNotFoundException {
    final ReadyCheckManager.ReadyCheck readyCheck = getReadyCheckOrThrow(readyCheckId);
    ReadyCheckScheduler.scheduleReadyAt(readyCheck, timeInput, userId, jda);
  }

  public boolean checkIfAllReady(final String readyCheckId) {
    final ReadyCheckManager.ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    return readyCheck != null && allNonPassedReady(readyCheck);
  }

  public boolean isReadyCheckOngoing(final String readyCheckId) {
    final ReadyCheckManager.ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    return readyCheck != null && !allNonPassedReady(readyCheck);
  }

  public String findActiveReadyCheckInChannel(final String guildId, final String channelId) {
    final long eightHoursAgo = System.currentTimeMillis() - EIGHT_HOURS_MS;

    return activeReadyChecks.values().stream()
        .filter(readyCheck -> readyCheck.getGuildId().equals(guildId))
        .filter(readyCheck -> readyCheck.getChannelId().equals(channelId))
        .filter(readyCheck -> readyCheck.getCreatedTime() >= eightHoursAgo)
        .filter(readyCheck -> readyCheck.getStatus() == ReadyCheckManager.ReadyCheckStatus.ACTIVE)
        .map(ReadyCheckManager.ReadyCheck::getId)
        .filter(this::isReadyCheckOngoing)
        .findFirst()
        .orElse(null);
  }

  private ReadyCheckManager.ReadyCheck getReadyCheckOrThrow(final String readyCheckId) throws ReadyCheckNotFoundException {
    final ReadyCheckManager.ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    if (readyCheck == null) {
      throw new ReadyCheckNotFoundException(readyCheckId);
    }
    return readyCheck;
  }

  private void markUserAsReady(final ReadyCheckManager.ReadyCheck readyCheck, final String userId) {
    ReadyCheckScheduler.cancelExistingScheduledUser(readyCheck, userId);
    readyCheck.getUserTimers().remove(userId);
    readyCheck.getReadyUsers().add(userId);
    readyCheck.getPassedUsers().remove(userId);
  }

  private boolean allNonPassedReady(final ReadyCheckManager.ReadyCheck readyCheck) {
    final java.util.Set<String> allUsers = com.projects.readycheck.utils.ReadyCheckUtils.getAllUsers(readyCheck);
    return allUsers.stream()
        .filter(userId -> !readyCheck.getPassedUsers().contains(userId))
        .allMatch(userId -> readyCheck.getReadyUsers().contains(userId));
  }
}