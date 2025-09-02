package com.projects.testutils;

import com.projects.readycheck.ReadyCheckManager;
import java.util.List;
import java.util.Set;

public final class TestReadyCheckUtils {

  private TestReadyCheckUtils() {}

  public static ReadyCheckManager.ReadyCheck createTestReadyCheck(
      final String id,
      final String guildId,
      final String channelId,
      final String initiatorId,
      final List<String> targetUserIds) {
    return new ReadyCheckManager.ReadyCheck(
        id, guildId, channelId, initiatorId, null, targetUserIds);
  }

  public static ReadyCheckManager.ReadyCheck createRoleBasedReadyCheck(
      final String id,
      final String guildId,
      final String channelId,
      final String initiatorId,
      final String roleId,
      final List<String> targetUserIds) {
    return new ReadyCheckManager.ReadyCheck(
        id, guildId, channelId, initiatorId, roleId, targetUserIds);
  }

  public static ReadyCheckManager.SavedReadyCheck createUserBasedSavedCheck(
      final List<String> userIds, final boolean mentionPeople) {
    return new ReadyCheckManager.SavedReadyCheck(userIds, true, mentionPeople);
  }

  public static ReadyCheckManager.SavedReadyCheck createRoleBasedSavedCheck(
      final String roleId, final boolean mentionPeople) {
    return new ReadyCheckManager.SavedReadyCheck(roleId, false, mentionPeople);
  }

  public static void markUserReady(
      final ReadyCheckManager.ReadyCheck readyCheck, final String userId) {
    readyCheck.getReadyUsers().add(userId);
    readyCheck.getPassedUsers().remove(userId);
  }

  public static void markUserPassed(
      final ReadyCheckManager.ReadyCheck readyCheck, final String userId) {
    readyCheck.getPassedUsers().add(userId);
    readyCheck.getReadyUsers().remove(userId);
  }

  public static void addScheduledUser(
      final ReadyCheckManager.ReadyCheck readyCheck, final String userId, final long timestamp) {
    final ReadyCheckManager.ScheduledUser scheduledUser =
        new ReadyCheckManager.ScheduledUser(timestamp, null);
    readyCheck.getScheduledUsers().put(userId, scheduledUser);
  }

  public static boolean isUserReady(
      final ReadyCheckManager.ReadyCheck readyCheck, final String userId) {
    return readyCheck.getReadyUsers().contains(userId);
  }

  public static boolean isUserPassed(
      final ReadyCheckManager.ReadyCheck readyCheck, final String userId) {
    return readyCheck.getPassedUsers().contains(userId);
  }

  public static boolean isUserScheduled(
      final ReadyCheckManager.ReadyCheck readyCheck, final String userId) {
    return readyCheck.getScheduledUsers().containsKey(userId);
  }

  public static int getReadyCount(final ReadyCheckManager.ReadyCheck readyCheck) {
    final Set<String> allUsers = getAllUsers(readyCheck);
    return (int)
        allUsers.stream()
            .filter(userId -> !readyCheck.getPassedUsers().contains(userId))
            .filter(userId -> readyCheck.getReadyUsers().contains(userId))
            .count();
  }

  public static int getNonPassedCount(final ReadyCheckManager.ReadyCheck readyCheck) {
    final Set<String> allUsers = getAllUsers(readyCheck);
    return (int)
        allUsers.stream().filter(userId -> !readyCheck.getPassedUsers().contains(userId)).count();
  }

  public static boolean areAllUsersReady(final ReadyCheckManager.ReadyCheck readyCheck) {
    final Set<String> allUsers = getAllUsers(readyCheck);
    return allUsers.stream()
        .filter(userId -> !readyCheck.getPassedUsers().contains(userId))
        .allMatch(userId -> readyCheck.getReadyUsers().contains(userId));
  }

  public static Set<String> getAllUsers(final ReadyCheckManager.ReadyCheck readyCheck) {
    final Set<String> allUsers = new java.util.HashSet<>(readyCheck.getTargetUsers());
    allUsers.add(readyCheck.getInitiatorId());
    return allUsers;
  }

  public static TestReadyCheckBuilder builder() {
    return new TestReadyCheckBuilder();
  }

  public static final class TestReadyCheckBuilder {
    private String id = "test-id";
    private String guildId = "guild-123";
    private String channelId = "channel-456";
    private String initiatorId = "user-initiator";
    private String roleId;
    private final List<String> targetUserIds = new java.util.ArrayList<>();
    private final List<String> readyUserIds = new java.util.ArrayList<>();
    private final List<String> passedUserIds = new java.util.ArrayList<>();

    public TestReadyCheckBuilder id(final String id) {
      this.id = id;
      return this;
    }

    public TestReadyCheckBuilder guild(final String guildId) {
      this.guildId = guildId;
      return this;
    }

    public TestReadyCheckBuilder channel(final String channelId) {
      this.channelId = channelId;
      return this;
    }

    public TestReadyCheckBuilder initiator(final String initiatorId) {
      this.initiatorId = initiatorId;
      return this;
    }

    public TestReadyCheckBuilder role(final String roleId) {
      this.roleId = roleId;
      return this;
    }

    public TestReadyCheckBuilder targetUsers(final String... userIds) {
      this.targetUserIds.addAll(List.of(userIds));
      return this;
    }

    public TestReadyCheckBuilder readyUsers(final String... userIds) {
      this.readyUserIds.addAll(List.of(userIds));
      return this;
    }

    public TestReadyCheckBuilder passedUsers(final String... userIds) {
      this.passedUserIds.addAll(List.of(userIds));
      return this;
    }

    public ReadyCheckManager.ReadyCheck build() {
      final ReadyCheckManager.ReadyCheck readyCheck =
          new ReadyCheckManager.ReadyCheck(
              id, guildId, channelId, initiatorId, roleId, targetUserIds);

      readyUserIds.forEach(userId -> readyCheck.getReadyUsers().add(userId));
      passedUserIds.forEach(userId -> readyCheck.getPassedUsers().add(userId));

      return readyCheck;
    }
  }
}
