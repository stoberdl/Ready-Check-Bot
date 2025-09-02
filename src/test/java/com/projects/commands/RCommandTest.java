package com.projects.commands;

import static org.mockito.Mockito.*;

import com.projects.readycheck.ReadyCheckManager;
import com.projects.readycheck.SupabasePersistence;
import com.projects.testutils.MockDiscord;
import com.projects.testutils.TestReadyCheckUtils;
import java.util.List;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

final class RCommandTest {

  private RCommand rCommand;
  private SlashCommandInteractionEvent mockEvent;
  private JDA mockJDA;
  private Guild mockGuild;
  private TextChannel mockChannel;
  private Member mockInitiator;
  private Member mockUser1;
  private Member mockUser2;
  private Role mockRole;
  private ReplyCallbackAction mockReply;

  @BeforeEach
  void setUp() {
    rCommand = new RCommand();

    mockJDA = MockDiscord.createMockJDA();
    mockGuild = MockDiscord.createMockGuild("guild-123");
    mockChannel = MockDiscord.createMockTextChannel("channel-456");
    mockInitiator = MockDiscord.createMockMember("user-initiator", "Initiator");
    mockUser1 = MockDiscord.createMockMember("user-1", "User1");
    mockUser2 = MockDiscord.createMockMember("user-2", "User2");
    mockRole = MockDiscord.createMockRole("role-123", "GameRole");

    mockEvent = mock(SlashCommandInteractionEvent.class);
    mockReply = mock(ReplyCallbackAction.class);

    when(mockEvent.getJDA()).thenReturn(mockJDA);
    when(mockEvent.getGuild()).thenReturn(mockGuild);
    when(mockEvent.getChannel()).thenReturn(mockChannel);
    when(mockEvent.getMember()).thenReturn(mockInitiator);
    when(mockEvent.reply(anyString())).thenReturn(mockReply);
    when(mockReply.setEphemeral(anyBoolean())).thenReturn(mockReply);
    when(mockReply.addActionRow(any())).thenReturn(mockReply);
    when(mockReply.queue()).thenReturn(null);

    MockDiscord.setupGuildWithMembers(mockGuild, mockInitiator, mockUser1, mockUser2);
    MockDiscord.setupGuildWithChannel(mockGuild, mockChannel);
    MockDiscord.setupGuildWithRoleMembers(mockGuild, mockRole, List.of(mockUser1, mockUser2));
    MockDiscord.setupJDAWithGuild(mockJDA, mockGuild);

    ReadyCheckManager.setJDA(mockJDA);
    ReadyCheckManager.getActiveReadyChecks().clear();
  }

  @AfterEach
  void tearDown() {
    ReadyCheckManager.getActiveReadyChecks().clear();
  }

  @Test
  void executeSlash_NoSavedConfigurations_ShouldShowErrorMessage() {
    try (MockedStatic<SupabasePersistence> mockedPersistence =
        mockStatic(SupabasePersistence.class)) {
      mockedPersistence
          .when(() -> SupabasePersistence.getSavedReadyChecks("guild-123"))
          .thenReturn(List.of());

      rCommand.executeSlash(mockEvent);

      verify(mockEvent).reply(contains("No saved ready check configurations found"));
      verify(mockReply).setEphemeral(true);
    }
  }

  @Test
  void executeSlash_SingleSavedConfiguration_ShouldStartDirectly() {
    final ReadyCheckManager.SavedReadyCheck savedCheck =
        TestReadyCheckUtils.createRoleBasedSavedCheck("role-123", true);

    try (MockedStatic<ReadyCheckManager> mockedManager = mockStatic(ReadyCheckManager.class);
        MockedStatic<SupabasePersistence> mockedPersistence =
            mockStatic(SupabasePersistence.class)) {

      mockedManager.when(() -> ReadyCheckManager.getJDA()).thenReturn(mockJDA);
      mockedPersistence
          .when(() -> SupabasePersistence.getSavedReadyChecks("guild-123"))
          .thenReturn(List.of(savedCheck));
      mockedManager
          .when(
              () ->
                  ReadyCheckManager.findExistingReadyCheck(
                      eq("guild-123"), eq(savedCheck), eq("user-initiator")))
          .thenReturn(null);

      rCommand.executeSlash(mockEvent);

      mockedManager.verify(
          () ->
              ReadyCheckManager.createReadyCheck(
                  eq("guild-123"), eq("channel-456"), eq("user-initiator"), eq("role-123"), any()));
    }
  }

  @Test
  void executeSlash_SingleSavedWithExistingCheck_ShouldRefreshExisting() {
    final ReadyCheckManager.SavedReadyCheck savedCheck =
        TestReadyCheckUtils.createRoleBasedSavedCheck("role-123", true);

    final ReadyCheckManager.ReadyCheck existingCheck =
        TestReadyCheckUtils.builder()
            .id("existing-check")
            .guild("guild-123")
            .role("role-123")
            .build();

    try (MockedStatic<ReadyCheckManager> mockedManager = mockStatic(ReadyCheckManager.class);
        MockedStatic<SupabasePersistence> mockedPersistence =
            mockStatic(SupabasePersistence.class)) {

      mockedManager.when(() -> ReadyCheckManager.getJDA()).thenReturn(mockJDA);
      mockedPersistence
          .when(() -> SupabasePersistence.getSavedReadyChecks("guild-123"))
          .thenReturn(List.of(savedCheck));
      mockedManager
          .when(
              () ->
                  ReadyCheckManager.findExistingReadyCheck(
                      eq("guild-123"), eq(savedCheck), eq("user-initiator")))
          .thenReturn("existing-check");
      mockedManager
          .when(() -> ReadyCheckManager.isReadyCheckOngoing("existing-check"))
          .thenReturn(true);

      rCommand.executeSlash(mockEvent);

      mockedManager.verify(
          () -> ReadyCheckManager.resendExistingReadyCheck("existing-check", mockJDA));
      verify(mockEvent).reply(contains("Found an existing ready check"));
      verify(mockReply).setEphemeral(true);
    }
  }

  @Test
  void executeSlash_MultipleSavedConfigurations_ShouldShowMenu() {
    final ReadyCheckManager.SavedReadyCheck savedCheck1 =
        TestReadyCheckUtils.createRoleBasedSavedCheck("role-123", true);
    final ReadyCheckManager.SavedReadyCheck savedCheck2 =
        TestReadyCheckUtils.createUserBasedSavedCheck(List.of("user-1", "user-2"), false);

    try (MockedStatic<SupabasePersistence> mockedPersistence =
        mockStatic(SupabasePersistence.class)) {
      mockedPersistence
          .when(() -> SupabasePersistence.getSavedReadyChecks("guild-123"))
          .thenReturn(List.of(savedCheck1, savedCheck2));

      rCommand.executeSlash(mockEvent);

      verify(mockEvent).reply("Select a saved configuration:");
      verify(mockReply).addActionRow(any());
      verify(mockReply).setEphemeral(true);
    }
  }

  @Test
  void executeSlash_UserBasedSavedCheck_ShouldCreateUserReadyCheck() {
    final ReadyCheckManager.SavedReadyCheck savedCheck =
        TestReadyCheckUtils.createUserBasedSavedCheck(
            List.of("user-initiator", "user-1", "user-2"), true);

    try (MockedStatic<ReadyCheckManager> mockedManager = mockStatic(ReadyCheckManager.class);
        MockedStatic<SupabasePersistence> mockedPersistence =
            mockStatic(SupabasePersistence.class)) {

      mockedManager.when(() -> ReadyCheckManager.getJDA()).thenReturn(mockJDA);
      mockedPersistence
          .when(() -> SupabasePersistence.getSavedReadyChecks("guild-123"))
          .thenReturn(List.of(savedCheck));
      mockedManager
          .when(
              () ->
                  ReadyCheckManager.findExistingReadyCheck(
                      eq("guild-123"), eq(savedCheck), eq("user-initiator")))
          .thenReturn(null);

      rCommand.executeSlash(mockEvent);

      mockedManager.verify(
          () ->
              ReadyCheckManager.createUserReadyCheck(
                  eq("guild-123"), eq("channel-456"), eq("user-initiator"), any()));
    }
  }

  @Test
  void executeSlash_SavedRoleNotExists_ShouldShowError() {
    final ReadyCheckManager.SavedReadyCheck savedCheck =
        TestReadyCheckUtils.createRoleBasedSavedCheck("nonexistent-role", true);

    when(mockGuild.getRoleById("nonexistent-role")).thenReturn(null);

    try (MockedStatic<ReadyCheckManager> mockedManager = mockStatic(ReadyCheckManager.class);
        MockedStatic<SupabasePersistence> mockedPersistence =
            mockStatic(SupabasePersistence.class)) {

      mockedManager.when(() -> ReadyCheckManager.getJDA()).thenReturn(mockJDA);
      mockedPersistence
          .when(() -> SupabasePersistence.getSavedReadyChecks("guild-123"))
          .thenReturn(List.of(savedCheck));

      rCommand.executeSlash(mockEvent);

      verify(mockEvent).reply("The saved role no longer exists!");
      verify(mockReply).setEphemeral(true);
    }
  }

  @Test
  void executeSlash_SavedUsersNotInServer_ShouldShowError() {
    final ReadyCheckManager.SavedReadyCheck savedCheck =
        TestReadyCheckUtils.createUserBasedSavedCheck(List.of("nonexistent-user"), true);

    when(mockGuild.getMemberById("nonexistent-user")).thenReturn(null);

    try (MockedStatic<ReadyCheckManager> mockedManager = mockStatic(ReadyCheckManager.class);
        MockedStatic<SupabasePersistence> mockedPersistence =
            mockStatic(SupabasePersistence.class)) {

      mockedManager.when(() -> ReadyCheckManager.getJDA()).thenReturn(mockJDA);
      mockedPersistence
          .when(() -> SupabasePersistence.getSavedReadyChecks("guild-123"))
          .thenReturn(List.of(savedCheck));

      rCommand.executeSlash(mockEvent);

      verify(mockEvent).reply("No other saved users found or they are no longer in the server!");
      verify(mockReply).setEphemeral(true);
    }
  }

  @Test
  void executeSlash_RoleWithNoMembers_ShouldShowError() {
    final ReadyCheckManager.SavedReadyCheck savedCheck =
        TestReadyCheckUtils.createRoleBasedSavedCheck("role-123", true);

    when(mockGuild.getMembersWithRoles(mockRole)).thenReturn(List.of(mockInitiator));

    try (MockedStatic<ReadyCheckManager> mockedManager = mockStatic(ReadyCheckManager.class);
        MockedStatic<SupabasePersistence> mockedPersistence =
            mockStatic(SupabasePersistence.class)) {

      mockedManager.when(() -> ReadyCheckManager.getJDA()).thenReturn(mockJDA);
      mockedPersistence
          .when(() -> SupabasePersistence.getSavedReadyChecks("guild-123"))
          .thenReturn(List.of(savedCheck));

      rCommand.executeSlash(mockEvent);

      verify(mockEvent).reply(contains("No other members found with the role"));
      verify(mockReply).setEphemeral(true);
    }
  }

  @Test
  void executeSlash_ShouldSetMentionPreference() {
    final ReadyCheckManager.SavedReadyCheck savedCheck =
        TestReadyCheckUtils.createRoleBasedSavedCheck("role-123", false);

    try (MockedStatic<ReadyCheckManager> mockedManager = mockStatic(ReadyCheckManager.class);
        MockedStatic<SupabasePersistence> mockedPersistence =
            mockStatic(SupabasePersistence.class)) {

      mockedManager.when(() -> ReadyCheckManager.getJDA()).thenReturn(mockJDA);
      mockedPersistence
          .when(() -> SupabasePersistence.getSavedReadyChecks("guild-123"))
          .thenReturn(List.of(savedCheck));
      mockedManager
          .when(
              () ->
                  ReadyCheckManager.findExistingReadyCheck(
                      eq("guild-123"), eq(savedCheck), eq("user-initiator")))
          .thenReturn(null);

      rCommand.executeSlash(mockEvent);

      mockedManager.verify(() -> ReadyCheckManager.setMentionPreference(anyString(), eq(false)));
    }
  }

  @Test
  void executeSlash_MenuOptionsForUserGroups_ShouldHandleHashCollisions() {
    final List<String> userGroup1 = List.of("user-1", "user-2");
    final List<String> userGroup2 = List.of("user-3", "user-4");

    final ReadyCheckManager.SavedReadyCheck savedCheck1 =
        TestReadyCheckUtils.createUserBasedSavedCheck(userGroup1, true);
    final ReadyCheckManager.SavedReadyCheck savedCheck2 =
        TestReadyCheckUtils.createUserBasedSavedCheck(userGroup2, false);

    try (MockedStatic<SupabasePersistence> mockedPersistence =
        mockStatic(SupabasePersistence.class)) {
      mockedPersistence
          .when(() -> SupabasePersistence.getSavedReadyChecks("guild-123"))
          .thenReturn(List.of(savedCheck1, savedCheck2));

      rCommand.executeSlash(mockEvent);

      verify(mockEvent).reply("Select a saved configuration:");
      verify(mockReply).addActionRow(any());
      verify(mockReply).setEphemeral(true);
    }
  }

  @Test
  void executeSlash_UserBasedSavedCheckExcludesInitiator_ShouldFilterCorrectly() {
    final ReadyCheckManager.SavedReadyCheck savedCheck =
        TestReadyCheckUtils.createUserBasedSavedCheck(List.of("user-initiator", "user-1"), true);

    try (MockedStatic<ReadyCheckManager> mockedManager = mockStatic(ReadyCheckManager.class);
        MockedStatic<SupabasePersistence> mockedPersistence =
            mockStatic(SupabasePersistence.class)) {

      mockedManager.when(() -> ReadyCheckManager.getJDA()).thenReturn(mockJDA);
      mockedPersistence
          .when(() -> SupabasePersistence.getSavedReadyChecks("guild-123"))
          .thenReturn(List.of(savedCheck));
      mockedManager
          .when(
              () ->
                  ReadyCheckManager.findExistingReadyCheck(
                      eq("guild-123"), eq(savedCheck), eq("user-initiator")))
          .thenReturn(null);

      rCommand.executeSlash(mockEvent);

      mockedManager.verify(
          () ->
              ReadyCheckManager.createUserReadyCheck(
                  eq("guild-123"),
                  eq("channel-456"),
                  eq("user-initiator"),
                  argThat(members -> members.size() == 1 && members.get(0).equals(mockUser1))));
    }
  }
}
