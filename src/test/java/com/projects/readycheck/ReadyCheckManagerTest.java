package com.projects.readycheck;

import static org.junit.jupiter.api.Assertions.*;

import com.projects.testutils.MockDiscord;
import com.projects.testutils.TestReadyCheckUtils;
import java.util.List;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class ReadyCheckManagerTest {

  private JDA mockJDA;
  private Guild mockGuild;
  private TextChannel mockChannel;
  private Member mockInitiator;
  private Member mockUser1;
  private Member mockUser2;
  private Role mockRole;

  @BeforeEach
  void setUp() {
    mockJDA = MockDiscord.createMockJDA();
    mockGuild = MockDiscord.createMockGuild("guild-123");
    mockChannel = MockDiscord.createMockTextChannel("channel-456");
    mockInitiator = MockDiscord.createMockMember("user-initiator", "Initiator");
    mockUser1 = MockDiscord.createMockMember("user-1", "User1");
    mockUser2 = MockDiscord.createMockMember("user-2", "User2");
    mockRole = MockDiscord.createMockRole("role-123", "GameRole");

    MockDiscord.setupGuildWithMembers(mockGuild, mockInitiator, mockUser1, mockUser2);
    MockDiscord.setupGuildWithChannel(mockGuild, mockChannel);
    MockDiscord.setupGuildWithRoles(mockGuild, mockRole);
    MockDiscord.setupJDAWithGuild(mockJDA, mockGuild);

    ReadyCheckManager.setJDA(mockJDA);
    ReadyCheckManager.getActiveReadyChecks().clear();
  }

  @AfterEach
  void tearDown() {
    ReadyCheckManager.getActiveReadyChecks().clear();
  }

  @Test
  void createUserReadyCheck_ShouldCreateActiveReadyCheck() {
    final List<Member> targetMembers = List.of(mockUser1, mockUser2);

    final String readyCheckId =
        ReadyCheckManager.createUserReadyCheck(
            "guild-123", "channel-456", "user-initiator", targetMembers);

    assertNotNull(readyCheckId);
    final ReadyCheckManager.ReadyCheck readyCheck =
        ReadyCheckManager.getActiveReadyCheck(readyCheckId);

    assertNotNull(readyCheck);
    assertEquals("guild-123", readyCheck.getGuildId());
    assertEquals("channel-456", readyCheck.getChannelId());
    assertEquals("user-initiator", readyCheck.getInitiatorId());
    assertTrue(readyCheck.getTargetUsers().contains("user-1"));
    assertTrue(readyCheck.getTargetUsers().contains("user-2"));
    assertTrue(readyCheck.getReadyUsers().contains("user-initiator"));
  }

  @Test
  void createReadyCheck_WithRole_ShouldCreateRoleBasedCheck() {
    final List<Member> targetMembers = List.of(mockUser1, mockUser2);

    final String readyCheckId =
        ReadyCheckManager.createReadyCheck(
            "guild-123", "channel-456", "user-initiator", "role-123", targetMembers);

    final ReadyCheckManager.ReadyCheck readyCheck =
        ReadyCheckManager.getActiveReadyCheck(readyCheckId);

    assertNotNull(readyCheck);
    assertEquals("role-123", readyCheck.getRoleId());
  }

  @Test
  void markUserReady_ShouldAddToReadyUsers() {
    final ReadyCheckManager.ReadyCheck readyCheck =
        TestReadyCheckUtils.builder().targetUsers("user-1", "user-2").build();
    ReadyCheckManager.getActiveReadyChecks().put(readyCheck.getId(), readyCheck);

    ReadyCheckManager.markUserReady(readyCheck.getId(), "user-1");

    assertTrue(readyCheck.getReadyUsers().contains("user-1"));
    assertFalse(readyCheck.getPassedUsers().contains("user-1"));
  }

  @Test
  void markUserReady_WhenUserPassed_ShouldRemoveFromPassed() {
    final ReadyCheckManager.ReadyCheck readyCheck =
        TestReadyCheckUtils.builder().targetUsers("user-1").passedUsers("user-1").build();
    ReadyCheckManager.getActiveReadyChecks().put(readyCheck.getId(), readyCheck);

    ReadyCheckManager.markUserReady(readyCheck.getId(), "user-1");

    assertTrue(readyCheck.getReadyUsers().contains("user-1"));
    assertFalse(readyCheck.getPassedUsers().contains("user-1"));
  }

  @Test
  void toggleUserReady_WhenNotReady_ShouldMarkReady() {
    final ReadyCheckManager.ReadyCheck readyCheck =
        TestReadyCheckUtils.builder().targetUsers("user-1").build();
    ReadyCheckManager.getActiveReadyChecks().put(readyCheck.getId(), readyCheck);

    final boolean isNowReady = ReadyCheckManager.toggleUserReady(readyCheck.getId(), "user-1");

    assertTrue(isNowReady);
    assertTrue(readyCheck.getReadyUsers().contains("user-1"));
  }

  @Test
  void toggleUserReady_WhenReady_ShouldMarkNotReady() {
    final ReadyCheckManager.ReadyCheck readyCheck =
        TestReadyCheckUtils.builder().targetUsers("user-1").readyUsers("user-1").build();
    ReadyCheckManager.getActiveReadyChecks().put(readyCheck.getId(), readyCheck);

    final boolean isNowReady = ReadyCheckManager.toggleUserReady(readyCheck.getId(), "user-1");

    assertFalse(isNowReady);
    assertFalse(readyCheck.getReadyUsers().contains("user-1"));
  }

  @Test
  void markUserPassed_ShouldAddToPassedUsers() {
    final ReadyCheckManager.ReadyCheck readyCheck =
        TestReadyCheckUtils.builder().targetUsers("user-1").readyUsers("user-1").build();
    ReadyCheckManager.getActiveReadyChecks().put(readyCheck.getId(), readyCheck);

    ReadyCheckManager.markUserPassed(readyCheck.getId(), "user-1");

    assertTrue(readyCheck.getPassedUsers().contains("user-1"));
    assertFalse(readyCheck.getReadyUsers().contains("user-1"));
  }

  @Test
  void checkIfAllReady_WhenAllUsersReady_ShouldReturnTrue() {
    final ReadyCheckManager.ReadyCheck readyCheck =
        TestReadyCheckUtils.builder()
            .initiator("user-initiator")
            .targetUsers("user-1", "user-2")
            .readyUsers("user-initiator", "user-1", "user-2")
            .build();
    ReadyCheckManager.getActiveReadyChecks().put(readyCheck.getId(), readyCheck);

    final boolean allReady = ReadyCheckManager.checkIfAllReady(readyCheck.getId());

    assertTrue(allReady);
  }

  @Test
  void checkIfAllReady_WithPassedUsers_ShouldExcludePassedFromCount() {
    final ReadyCheckManager.ReadyCheck readyCheck =
        TestReadyCheckUtils.builder()
            .initiator("user-initiator")
            .targetUsers("user-1", "user-2")
            .readyUsers("user-initiator", "user-1")
            .passedUsers("user-2")
            .build();
    ReadyCheckManager.getActiveReadyChecks().put(readyCheck.getId(), readyCheck);

    final boolean allReady = ReadyCheckManager.checkIfAllReady(readyCheck.getId());

    assertTrue(allReady);
  }

  @Test
  void checkIfAllReady_WhenNotAllReady_ShouldReturnFalse() {
    final ReadyCheckManager.ReadyCheck readyCheck =
        TestReadyCheckUtils.builder()
            .initiator("user-initiator")
            .targetUsers("user-1", "user-2")
            .readyUsers("user-initiator", "user-1")
            .build();
    ReadyCheckManager.getActiveReadyChecks().put(readyCheck.getId(), readyCheck);

    final boolean allReady = ReadyCheckManager.checkIfAllReady(readyCheck.getId());

    assertFalse(allReady);
  }

  @Test
  void findActiveReadyCheckInChannel_ShouldFindExistingCheck() {
    final ReadyCheckManager.ReadyCheck readyCheck =
        TestReadyCheckUtils.builder()
            .guild("guild-123")
            .channel("channel-456")
            .targetUsers("user-1")
            .build();
    readyCheck.setStatus(ReadyCheckManager.ReadyCheckStatus.ACTIVE);
    ReadyCheckManager.getActiveReadyChecks().put(readyCheck.getId(), readyCheck);

    final String foundId =
        ReadyCheckManager.findActiveReadyCheckInChannel("guild-123", "channel-456");

    assertEquals(readyCheck.getId(), foundId);
  }

  @Test
  void findActiveReadyCheckInChannel_WhenCompleted_ShouldReturnNull() {
    final ReadyCheckManager.ReadyCheck readyCheck =
        TestReadyCheckUtils.builder()
            .guild("guild-123")
            .channel("channel-456")
            .targetUsers("user-1")
            .readyUsers("user-initiator", "user-1")
            .build();
    readyCheck.setStatus(ReadyCheckManager.ReadyCheckStatus.COMPLETED);
    ReadyCheckManager.getActiveReadyChecks().put(readyCheck.getId(), readyCheck);

    final String foundId =
        ReadyCheckManager.findActiveReadyCheckInChannel("guild-123", "channel-456");

    assertNull(foundId);
  }

  @Test
  void findActiveReadyCheckForUser_ShouldFindCheckWithUser() {
    final ReadyCheckManager.ReadyCheck readyCheck =
        TestReadyCheckUtils.builder().guild("guild-123").targetUsers("user-1").build();
    ReadyCheckManager.getActiveReadyChecks().put(readyCheck.getId(), readyCheck);

    final String foundId = ReadyCheckManager.findActiveReadyCheckForUser("guild-123", "user-1");

    assertEquals(readyCheck.getId(), foundId);
  }

  @Test
  void findActiveReadyCheckForUser_WhenUserNotInCheck_ShouldReturnNull() {
    final ReadyCheckManager.ReadyCheck readyCheck =
        TestReadyCheckUtils.builder().guild("guild-123").targetUsers("user-1").build();
    ReadyCheckManager.getActiveReadyChecks().put(readyCheck.getId(), readyCheck);

    final String foundId = ReadyCheckManager.findActiveReadyCheckForUser("guild-123", "user-2");

    assertNull(foundId);
  }

  @Test
  void ensureUserInReadyCheck_ShouldAddUserToTargets() {
    final ReadyCheckManager.ReadyCheck readyCheck = TestReadyCheckUtils.builder().build();
    ReadyCheckManager.getActiveReadyChecks().put(readyCheck.getId(), readyCheck);

    ReadyCheckManager.ensureUserInReadyCheck(readyCheck.getId(), "new-user");

    assertTrue(readyCheck.getTargetUsers().contains("new-user"));
  }

  @Test
  void setMentionPreference_ShouldStoreSetting() {
    ReadyCheckManager.setMentionPreference("test-id", false);

    final boolean mentionPeople = ReadyCheckManager.getMentionPreference("test-id");

    assertFalse(mentionPeople);
  }

  @Test
  void getMentionPreference_WhenNotSet_ShouldDefaultToTrue() {
    final boolean mentionPeople = ReadyCheckManager.getMentionPreference("nonexistent-id");

    assertTrue(mentionPeople);
  }

  @Test
  void findExistingReadyCheck_WithSameUserList_ShouldFindMatch() {
    final ReadyCheckManager.ReadyCheck readyCheck =
        TestReadyCheckUtils.builder()
            .guild("guild-123")
            .initiator("user-initiator")
            .targetUsers("user-1", "user-2")
            .build();
    ReadyCheckManager.getActiveReadyChecks().put(readyCheck.getId(), readyCheck);

    final String foundId =
        ReadyCheckManager.findExistingReadyCheck(
            "guild-123", List.of("user-1", "user-2"), "user-initiator");

    assertEquals(readyCheck.getId(), foundId);
  }

  @Test
  void findExistingReadyCheck_WithDifferentUserList_ShouldReturnNull() {
    final ReadyCheckManager.ReadyCheck readyCheck =
        TestReadyCheckUtils.builder()
            .guild("guild-123")
            .initiator("user-initiator")
            .targetUsers("user-1", "user-2")
            .build();
    ReadyCheckManager.getActiveReadyChecks().put(readyCheck.getId(), readyCheck);

    final String foundId =
        ReadyCheckManager.findExistingReadyCheck(
            "guild-123", List.of("user-3", "user-4"), "user-initiator");

    assertNull(foundId);
  }

  @Test
  void isReadyCheckOngoing_WhenNotAllReady_ShouldReturnTrue() {
    final ReadyCheckManager.ReadyCheck readyCheck =
        TestReadyCheckUtils.builder()
            .initiator("user-initiator")
            .targetUsers("user-1")
            .readyUsers("user-initiator")
            .build();
    ReadyCheckManager.getActiveReadyChecks().put(readyCheck.getId(), readyCheck);

    final boolean ongoing = ReadyCheckManager.isReadyCheckOngoing(readyCheck.getId());

    assertTrue(ongoing);
  }

  @Test
  void isReadyCheckOngoing_WhenAllReady_ShouldReturnFalse() {
    final ReadyCheckManager.ReadyCheck readyCheck =
        TestReadyCheckUtils.builder()
            .initiator("user-initiator")
            .targetUsers("user-1")
            .readyUsers("user-initiator", "user-1")
            .build();
    ReadyCheckManager.getActiveReadyChecks().put(readyCheck.getId(), readyCheck);

    final boolean ongoing = ReadyCheckManager.isReadyCheckOngoing(readyCheck.getId());

    assertFalse(ongoing);
  }

  @Test
  void unmarkUserPassed_ShouldRemoveFromPassedUsers() {
    final ReadyCheckManager.ReadyCheck readyCheck =
        TestReadyCheckUtils.builder().passedUsers("user-1").build();
    ReadyCheckManager.getActiveReadyChecks().put(readyCheck.getId(), readyCheck);

    ReadyCheckManager.unmarkUserPassed(readyCheck.getId(), "user-1");

    assertFalse(readyCheck.getPassedUsers().contains("user-1"));
  }

  @Test
  void setReadyCheckMessageId_ShouldUpdateMessageId() {
    final ReadyCheckManager.ReadyCheck readyCheck = TestReadyCheckUtils.builder().build();
    ReadyCheckManager.getActiveReadyChecks().put(readyCheck.getId(), readyCheck);

    ReadyCheckManager.setReadyCheckMessageId(readyCheck.getId(), "message-123");

    assertEquals("message-123", readyCheck.getMessageId());
  }
}
