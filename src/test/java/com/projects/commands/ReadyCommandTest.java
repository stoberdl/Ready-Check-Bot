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
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

final class ReadyCommandTest {

  private ReadyCommand readyCommand;
  private SlashCommandInteractionEvent mockEvent;
  private JDA mockJDA;
  private Guild mockGuild;
  private TextChannel mockChannel;
  private Member mockInitiator;
  private Member mockUser1;
  private Member mockUser2;
  private Role mockRole;
  private OptionMapping mockTargetsOption;
  private OptionMapping mockPeopleOption;
  private ReplyCallbackAction mockReply;

  @BeforeEach
  void setUp() {
    readyCommand = new ReadyCommand();

    mockJDA = MockDiscord.createMockJDA();
    mockGuild = MockDiscord.createMockGuild("guild-123");
    mockChannel = MockDiscord.createMockTextChannel("channel-456");
    mockInitiator = MockDiscord.createMockMember("user-initiator", "Initiator");
    mockUser1 = MockDiscord.createMockMember("user-1", "User1");
    mockUser2 = MockDiscord.createMockMember("user-2", "User2");
    mockRole = MockDiscord.createMockRole("role-123", "GameRole");

    mockEvent = mock(SlashCommandInteractionEvent.class);
    mockTargetsOption = mock(OptionMapping.class);
    mockPeopleOption = mock(OptionMapping.class);
    mockReply = mock(ReplyCallbackAction.class);

    when(mockEvent.getJDA()).thenReturn(mockJDA);
    when(mockEvent.getGuild()).thenReturn(mockGuild);
    when(mockEvent.getChannel()).thenReturn(mockChannel);
    when(mockEvent.getMember()).thenReturn(mockInitiator);
    when(mockEvent.reply(anyString())).thenReturn(mockReply);
    when(mockReply.setEphemeral(anyBoolean())).thenReturn(mockReply);
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
  void executeSlash_NoTargetsWithSavedChecks_ShouldShowSingleSavedCheck() {
    when(mockEvent.getOption("targets")).thenReturn(null);
    when(mockEvent.getOption("people", true, OptionMapping::getAsBoolean)).thenReturn(true);

    final ReadyCheckManager.SavedReadyCheck savedCheck =
        TestReadyCheckUtils.createRoleBasedSavedCheck("role-123", true);

    try (MockedStatic<ReadyCheckManager> mockedManager = mockStatic(ReadyCheckManager.class);
        MockedStatic<SupabasePersistence> mockedPersistence =
            mockStatic(SupabasePersistence.class)) {

      mockedManager.when(() -> ReadyCheckManager.getJDA()).thenReturn(mockJDA);
      mockedPersistence
          .when(() -> SupabasePersistence.getSavedReadyChecks("guild-123"))
          .thenReturn(List.of(savedCheck));

      readyCommand.executeSlash(mockEvent);

      mockedManager.verify(
          () ->
              ReadyCheckManager.createReadyCheck(
                  eq("guild-123"), eq("channel-456"), eq("user-initiator"), eq("role-123"), any()));
    }
  }

  @Test
  void executeSlash_NoTargetsNoSavedChecks_ShouldShowNoConfigsMessage() {
    when(mockEvent.getOption("targets")).thenReturn(null);
    when(mockEvent.getOption("people", true, OptionMapping::getAsBoolean)).thenReturn(true);

    try (MockedStatic<SupabasePersistence> mockedPersistence =
        mockStatic(SupabasePersistence.class)) {
      mockedPersistence
          .when(() -> SupabasePersistence.getSavedReadyChecks("guild-123"))
          .thenReturn(List.of());

      readyCommand.executeSlash(mockEvent);

      verify(mockEvent).reply(contains("No saved ready check configurations found"));
      verify(mockReply).setEphemeral(true);
    }
  }

  @Test
  void executeSlash_WithRoleMention_ShouldCreateRoleBasedCheck() {
    when(mockEvent.getOption("targets")).thenReturn(mockTargetsOption);
    when(mockTargetsOption.getAsString()).thenReturn("<@&role-123>");
    when(mockEvent.getOption("people", true, OptionMapping::getAsBoolean)).thenReturn(true);

    try (MockedStatic<ReadyCheckManager> mockedManager = mockStatic(ReadyCheckManager.class)) {
      mockedManager.when(() -> ReadyCheckManager.getJDA()).thenReturn(mockJDA);

      readyCommand.executeSlash(mockEvent);

      mockedManager.verify(
          () ->
              ReadyCheckManager.createReadyCheck(
                  eq("guild-123"), eq("channel-456"), eq("user-initiator"), eq("role-123"), any()));
    }
  }

  @Test
  void executeSlash_WithUserMentions_ShouldCreateUserBasedCheck() {
    when(mockEvent.getOption("targets")).thenReturn(mockTargetsOption);
    when(mockTargetsOption.getAsString()).thenReturn("<@user-1> <@user-2>");
    when(mockEvent.getOption("people", true, OptionMapping::getAsBoolean)).thenReturn(true);

    try (MockedStatic<ReadyCheckManager> mockedManager = mockStatic(ReadyCheckManager.class)) {
      mockedManager.when(() -> ReadyCheckManager.getJDA()).thenReturn(mockJDA);

      readyCommand.executeSlash(mockEvent);

      mockedManager.verify(
          () ->
              ReadyCheckManager.createUserReadyCheck(
                  eq("guild-123"), eq("channel-456"), eq("user-initiator"), any()));
    }
  }

  @Test
  void executeSlash_WithMixedTargets_ShouldCreateMixedCheck() {
    when(mockEvent.getOption("targets")).thenReturn(mockTargetsOption);
    when(mockTargetsOption.getAsString()).thenReturn("<@&role-123> <@user-1>");
    when(mockEvent.getOption("people", true, OptionMapping::getAsBoolean)).thenReturn(true);

    try (MockedStatic<ReadyCheckManager> mockedManager = mockStatic(ReadyCheckManager.class)) {
      mockedManager.when(() -> ReadyCheckManager.getJDA()).thenReturn(mockJDA);

      readyCommand.executeSlash(mockEvent);

      mockedManager.verify(
          () ->
              ReadyCheckManager.createUserReadyCheck(
                  eq("guild-123"), eq("channel-456"), eq("user-initiator"), any()));
    }
  }

  @Test
  void executeSlash_NoValidTargets_ShouldShowErrorMessage() {
    when(mockEvent.getOption("targets")).thenReturn(mockTargetsOption);
    when(mockTargetsOption.getAsString()).thenReturn("<@&nonexistent-role>");
    when(mockEvent.getOption("people", true, OptionMapping::getAsBoolean)).thenReturn(true);

    readyCommand.executeSlash(mockEvent);

    verify(mockEvent).reply(contains("No valid targets found"));
    verify(mockReply).setEphemeral(true);
  }

  @Test
  void executeSlash_OnlyInitiatorTarget_ShouldShowSelfOnlyError() {
    when(mockEvent.getOption("targets")).thenReturn(mockTargetsOption);
    when(mockTargetsOption.getAsString()).thenReturn("<@user-initiator>");
    when(mockEvent.getOption("people", true, OptionMapping::getAsBoolean)).thenReturn(true);

    readyCommand.executeSlash(mockEvent);

    verify(mockEvent).reply(contains("You can't start a ready check with only yourself"));
    verify(mockReply).setEphemeral(true);
  }

  @Test
  void executeSlash_ExistingReadyCheck_ShouldRefreshExisting() {
    when(mockEvent.getOption("targets")).thenReturn(mockTargetsOption);
    when(mockTargetsOption.getAsString()).thenReturn("<@user-1> <@user-2>");
    when(mockEvent.getOption("people", true, OptionMapping::getAsBoolean)).thenReturn(true);

    final ReadyCheckManager.ReadyCheck existingCheck =
        TestReadyCheckUtils.builder()
            .guild("guild-123")
            .initiator("user-initiator")
            .targetUsers("user-1", "user-2")
            .build();
    ReadyCheckManager.getActiveReadyChecks().put(existingCheck.getId(), existingCheck);

    try (MockedStatic<ReadyCheckManager> mockedManager = mockStatic(ReadyCheckManager.class)) {
      mockedManager.when(() -> ReadyCheckManager.getJDA()).thenReturn(mockJDA);
      mockedManager
          .when(
              () ->
                  ReadyCheckManager.findExistingReadyCheck(
                      eq("guild-123"), any(List.class), eq("user-initiator")))
          .thenReturn(existingCheck.getId());

      readyCommand.executeSlash(mockEvent);

      mockedManager.verify(
          () -> ReadyCheckManager.resendExistingReadyCheck(existingCheck.getId(), mockJDA));
      verify(mockEvent).reply(contains("Found an existing ready check"));
      verify(mockReply).setEphemeral(true);
    }
  }

  @Test
  void executeSlash_WithNameParsing_ShouldFindUsersByName() {
    when(mockEvent.getOption("targets")).thenReturn(mockTargetsOption);
    when(mockTargetsOption.getAsString()).thenReturn("User1 User2");
    when(mockEvent.getOption("people", true, OptionMapping::getAsBoolean)).thenReturn(true);

    when(mockGuild.getMembersByEffectiveName("User1", true)).thenReturn(List.of(mockUser1));
    when(mockGuild.getMembersByEffectiveName("User2", true)).thenReturn(List.of(mockUser2));

    try (MockedStatic<ReadyCheckManager> mockedManager = mockStatic(ReadyCheckManager.class)) {
      mockedManager.when(() -> ReadyCheckManager.getJDA()).thenReturn(mockJDA);

      readyCommand.executeSlash(mockEvent);

      mockedManager.verify(
          () ->
              ReadyCheckManager.createUserReadyCheck(
                  eq("guild-123"), eq("channel-456"), eq("user-initiator"), any()));
    }
  }

  @Test
  void executeSlash_MultipleSavedChecks_ShouldShowSelectionMenu() {
    when(mockEvent.getOption("targets")).thenReturn(null);
    when(mockEvent.getOption("people", true, OptionMapping::getAsBoolean)).thenReturn(true);

    final ReadyCheckManager.SavedReadyCheck savedCheck1 =
        TestReadyCheckUtils.createRoleBasedSavedCheck("role-123", true);
    final ReadyCheckManager.SavedReadyCheck savedCheck2 =
        TestReadyCheckUtils.createUserBasedSavedCheck(List.of("user-1", "user-2"), false);

    try (MockedStatic<SupabasePersistence> mockedPersistence =
        mockStatic(SupabasePersistence.class)) {
      mockedPersistence
          .when(() -> SupabasePersistence.getSavedReadyChecks("guild-123"))
          .thenReturn(List.of(savedCheck1, savedCheck2));

      when(mockEvent.reply(anyString())).thenReturn(mockReply);
      when(mockReply.addActionRow(any())).thenReturn(mockReply);

      readyCommand.executeSlash(mockEvent);

      verify(mockEvent).reply("Select a saved ready check configuration:");
      verify(mockReply).addActionRow(any());
      verify(mockReply).setEphemeral(true);
    }
  }

  @Test
  void executeSlash_SavedRoleNoLongerExists_ShouldShowError() {
    when(mockEvent.getOption("targets")).thenReturn(null);
    when(mockEvent.getOption("people", true, OptionMapping::getAsBoolean)).thenReturn(true);

    final ReadyCheckManager.SavedReadyCheck savedCheck =
        TestReadyCheckUtils.createRoleBasedSavedCheck("nonexistent-role", true);

    try (MockedStatic<SupabasePersistence> mockedPersistence =
        mockStatic(SupabasePersistence.class)) {
      mockedPersistence
          .when(() -> SupabasePersistence.getSavedReadyChecks("guild-123"))
          .thenReturn(List.of(savedCheck));

      readyCommand.executeSlash(mockEvent);

      verify(mockEvent).reply("The saved role no longer exists!");
      verify(mockReply).setEphemeral(true);
    }
  }

  @Test
  void executeSlash_SavedUsersNoLongerInServer_ShouldShowError() {
    when(mockEvent.getOption("targets")).thenReturn(null);
    when(mockEvent.getOption("people", true, OptionMapping::getAsBoolean)).thenReturn(true);

    final ReadyCheckManager.SavedReadyCheck savedCheck =
        TestReadyCheckUtils.createUserBasedSavedCheck(List.of("nonexistent-user"), true);

    try (MockedStatic<SupabasePersistence> mockedPersistence =
        mockStatic(SupabasePersistence.class)) {
      mockedPersistence
          .when(() -> SupabasePersistence.getSavedReadyChecks("guild-123"))
          .thenReturn(List.of(savedCheck));

      readyCommand.executeSlash(mockEvent);

      verify(mockEvent).reply("No other saved users found or they are no longer in the server!");
      verify(mockReply).setEphemeral(true);
    }
  }

  @Test
  void executeSlash_PeopleOptionFalse_ShouldSetNoMentions() {
    when(mockEvent.getOption("targets")).thenReturn(mockTargetsOption);
    when(mockTargetsOption.getAsString()).thenReturn("<@user-1>");
    when(mockEvent.getOption("people", true, OptionMapping::getAsBoolean)).thenReturn(false);

    try (MockedStatic<ReadyCheckManager> mockedManager = mockStatic(ReadyCheckManager.class)) {
      mockedManager.when(() -> ReadyCheckManager.getJDA()).thenReturn(mockJDA);

      readyCommand.executeSlash(mockEvent);

      mockedManager.verify(() -> ReadyCheckManager.setMentionPreference(anyString(), eq(false)));
    }
  }
}
