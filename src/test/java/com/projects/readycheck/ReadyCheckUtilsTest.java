package com.projects.readycheck;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.projects.readycheck.utils.ReadyCheckUtils;
import com.projects.testutils.MockDiscord;
import com.projects.testutils.TestReadyCheckUtils;
import java.util.List;
import java.util.Set;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class ReadyCheckUtilsTest {

  private Guild mockGuild;
  private TextChannel mockChannel;
  private Member mockInitiator;
  private Member mockUser1;
  private Member mockUser2;

  @BeforeEach
  void setUp() {
    mockGuild = MockDiscord.createMockGuild("guild-123");
    mockChannel = MockDiscord.createMockTextChannel("channel-456");
    mockInitiator = MockDiscord.createMockMember("user-initiator", "Initiator");
    mockUser1 = MockDiscord.createMockMember("user-1", "User1");
    mockUser2 = MockDiscord.createMockMember("user-2", "User2");

    MockDiscord.setupGuildWithMembers(mockGuild, mockInitiator, mockUser1, mockUser2);
    MockDiscord.setupGuildWithChannel(mockGuild, mockChannel);
  }

  @Test
  void getAllUsers_ShouldIncludeTargetUsersAndInitiator() {
    final var readyCheck =
        TestReadyCheckUtils.builder()
            .initiator("user-initiator")
            .targetUsers("user-1", "user-2")
            .build();

    final Set<String> allUsers = ReadyCheckUtils.getAllUsers(readyCheck);

    assertEquals(3, allUsers.size());
    assertTrue(allUsers.contains("user-initiator"));
    assertTrue(allUsers.contains("user-1"));
    assertTrue(allUsers.contains("user-2"));
  }

  @Test
  void getAllUsers_WithDuplicateInitiator_ShouldNotDuplicate() {
    final var readyCheck =
        TestReadyCheckUtils.builder()
            .initiator("user-initiator")
            .targetUsers("user-initiator", "user-1")
            .build();

    final Set<String> allUsers = ReadyCheckUtils.getAllUsers(readyCheck);

    assertEquals(2, allUsers.size());
    assertTrue(allUsers.contains("user-initiator"));
    assertTrue(allUsers.contains("user-1"));
  }

  @Test
  void getChannelFromGuild_ValidChannel_ShouldReturnChannel() {
    final TextChannel channel = ReadyCheckUtils.getChannelFromGuild(mockGuild, "channel-456");

    assertEquals(mockChannel, channel);
  }

  @Test
  void getChannelFromGuild_InvalidChannel_ShouldReturnNull() {
    when(mockGuild.getTextChannelById("nonexistent")).thenReturn(null);

    final TextChannel channel = ReadyCheckUtils.getChannelFromGuild(mockGuild, "nonexistent");

    assertNull(channel);
  }

  @Test
  void getChannelFromGuild_NullGuild_ShouldReturnNull() {
    final TextChannel channel = ReadyCheckUtils.getChannelFromGuild(null, "channel-456");

    assertNull(channel);
  }

  @Test
  void createMainButtons_ShouldCreateThreeButtons() {
    final List<Button> buttons = ReadyCheckUtils.createMainButtons("test-check-id");

    assertEquals(3, buttons.size());
    assertEquals("toggle_ready_test-check-id", buttons.get(0).getId());
    assertEquals("ready_at_test-check-id", buttons.get(1).getId());
    assertEquals("pass_test-check-id", buttons.get(2).getId());
  }

  @Test
  void createSaveButton_ShouldCreateSingleButton() {
    final List<Button> buttons = ReadyCheckUtils.createSaveButton("test-check-id");

    assertEquals(1, buttons.size());
    assertEquals("save_ready_test-check-id", buttons.get(0).getId());
    assertEquals("💾", buttons.get(0).getLabel());
  }

  @Test
  void buildCheckDescription_InitiatorInTargets_ShouldNotAddExtraUser() {
    final List<Member> targetMembers = List.of(mockInitiator, mockUser1, mockUser2);
    final String originalDescription = "**Initiator** started a ready check for specific users";

    final String result =
        ReadyCheckUtils.buildCheckDescription(mockInitiator, targetMembers, originalDescription);

    assertEquals("**Initiator** started a ready check for 3 users", result);
  }

  @Test
  void buildCheckDescription_InitiatorNotInTargets_ShouldAddInitiator() {
    final List<Member> targetMembers = List.of(mockUser1, mockUser2);
    final String originalDescription = "**Initiator** started a ready check for specific users";

    final String result =
        ReadyCheckUtils.buildCheckDescription(mockInitiator, targetMembers, originalDescription);

    assertEquals("**Initiator** started a ready check for 3 users", result);
  }

  @Test
  void buildCheckDescription_NonSpecificUsersDescription_ShouldReturnOriginal() {
    final List<Member> targetMembers = List.of(mockUser1, mockUser2);
    final String originalDescription = "**Initiator** started a ready check for @GameRole";

    final String result =
        ReadyCheckUtils.buildCheckDescription(mockInitiator, targetMembers, originalDescription);

    assertEquals(originalDescription, result);
  }

  @Test
  void userCanEngageWithReadyCheck_TargetUser_ShouldReturnTrue() {
    final var readyCheck =
        TestReadyCheckUtils.builder()
            .initiator("user-initiator")
            .targetUsers("user-1", "user-2")
            .build();

    final boolean canEngage = ReadyCheckUtils.userCanEngageWithReadyCheck(readyCheck, "user-1");

    assertTrue(canEngage);
  }

  @Test
  void userCanEngageWithReadyCheck_InitiatorUser_ShouldReturnTrue() {
    final var readyCheck =
        TestReadyCheckUtils.builder()
            .initiator("user-initiator")
            .targetUsers("user-1", "user-2")
            .build();

    final boolean canEngage =
        ReadyCheckUtils.userCanEngageWithReadyCheck(readyCheck, "user-initiator");

    assertTrue(canEngage);
  }

  @Test
  void userCanEngageWithReadyCheck_PassedUser_ShouldReturnTrue() {
    final var readyCheck =
        TestReadyCheckUtils.builder()
            .initiator("user-initiator")
            .targetUsers("user-1")
            .passedUsers("user-2")
            .build();

    final boolean canEngage = ReadyCheckUtils.userCanEngageWithReadyCheck(readyCheck, "user-2");

    assertTrue(canEngage);
  }

  @Test
  void userCanEngageWithReadyCheck_UnrelatedUser_ShouldReturnFalse() {
    final var readyCheck =
        TestReadyCheckUtils.builder()
            .initiator("user-initiator")
            .targetUsers("user-1", "user-2")
            .build();

    final boolean canEngage = ReadyCheckUtils.userCanEngageWithReadyCheck(readyCheck, "user-3");

    assertFalse(canEngage);
  }

  @Test
  void createUserSet_ShouldCombineTargetsAndInitiator() {
    final List<String> targetUserIds = List.of("user-1", "user-2");
    final String initiatorId = "user-initiator";

    final Set<String> userSet = ReadyCheckUtils.createUserSet(targetUserIds, initiatorId);

    assertEquals(3, userSet.size());
    assertTrue(userSet.contains("user-1"));
    assertTrue(userSet.contains("user-2"));
    assertTrue(userSet.contains("user-initiator"));
  }

  @Test
  void createUserSet_InitiatorAlreadyInTargets_ShouldNotDuplicate() {
    final List<String> targetUserIds = List.of("user-initiator", "user-1");
    final String initiatorId = "user-initiator";

    final Set<String> userSet = ReadyCheckUtils.createUserSet(targetUserIds, initiatorId);

    assertEquals(2, userSet.size());
    assertTrue(userSet.contains("user-initiator"));
    assertTrue(userSet.contains("user-1"));
  }

  @Test
  void matchesSavedCheck_UserBasedMatch_ShouldReturnTrue() {
    final var readyCheck =
        TestReadyCheckUtils.builder()
            .initiator("user-initiator")
            .targetUsers("user-1", "user-2")
            .build();

    final var savedCheck =
        TestReadyCheckUtils.createUserBasedSavedCheck(
            List.of("user-initiator", "user-1", "user-2"), true);

    final boolean matches =
        ReadyCheckUtils.matchesSavedCheck(readyCheck, savedCheck, "user-initiator");

    assertTrue(matches);
  }

  @Test
  void matchesSavedCheck_UserBasedNoMatch_ShouldReturnFalse() {
    final var readyCheck =
        TestReadyCheckUtils.builder()
            .initiator("user-initiator")
            .targetUsers("user-1", "user-2")
            .build();

    final var savedCheck =
        TestReadyCheckUtils.createUserBasedSavedCheck(List.of("user-3", "user-4"), true);

    final boolean matches =
        ReadyCheckUtils.matchesSavedCheck(readyCheck, savedCheck, "user-initiator");

    assertFalse(matches);
  }

  @Test
  void matchesSavedCheck_RoleBasedMatch_ShouldReturnTrue() {
    final var readyCheck =
        TestReadyCheckUtils.builder().role("role-123").targetUsers("user-1", "user-2").build();

    final var savedCheck = TestReadyCheckUtils.createRoleBasedSavedCheck("role-123", true);

    final boolean matches =
        ReadyCheckUtils.matchesSavedCheck(readyCheck, savedCheck, "user-initiator");

    assertTrue(matches);
  }

  @Test
  void matchesSavedCheck_RoleBasedNoMatch_ShouldReturnFalse() {
    final var readyCheck =
        TestReadyCheckUtils.builder().role("role-123").targetUsers("user-1", "user-2").build();

    final var savedCheck = TestReadyCheckUtils.createRoleBasedSavedCheck("role-456", true);

    final boolean matches =
        ReadyCheckUtils.matchesSavedCheck(readyCheck, savedCheck, "user-initiator");

    assertFalse(matches);
  }

  @Test
  void matchesSavedCheck_RoleBasedWithNullRole_ShouldReturnFalse() {
    final var readyCheck = TestReadyCheckUtils.builder().targetUsers("user-1", "user-2").build();

    final var savedCheck = TestReadyCheckUtils.createRoleBasedSavedCheck("role-123", true);

    final boolean matches =
        ReadyCheckUtils.matchesSavedCheck(readyCheck, savedCheck, "user-initiator");

    assertFalse(matches);
  }
}
