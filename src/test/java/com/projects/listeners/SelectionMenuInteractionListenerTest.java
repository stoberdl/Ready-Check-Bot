package com.projects.listeners;

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
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

final class SelectionMenuInteractionListenerTest {

  private SelectionMenuInteractionListener menuListener;
  private StringSelectInteractionEvent mockEvent;
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
    menuListener = new SelectionMenuInteractionListener();

    mockJDA = MockDiscord.createMockJDA();
    mockGuild = MockDiscord.createMockGuild("guild-123");
    mockChannel = MockDiscord.createMockTextChannel("channel-456");
    mockInitiator = MockDiscord.createMockMember("user-initiator", "Initiator");
    mockUser1 = MockDiscord.createMockMember("user-1", "User1");
    mockUser2 = MockDiscord.createMockMember("user-2", "User2");
    mockRole = MockDiscord.createMockRole("role-123", "GameRole");

    mockEvent = mock(StringSelectInteractionEvent.class);
    mockReply = mock(ReplyCallbackAction.class);

    when(mockEvent.getJDA()).thenReturn(mockJDA);
    when(mockEvent.getGuild()).thenReturn(mockGuild);
    when(mockEvent.getChannel()).thenReturn(mockChannel);
    when(mockEvent.getMember()).thenReturn(mockInitiator);
    when(mockEvent.reply(anyString())).thenReturn(mockReply);
    when(mockReply.setEmbeds(any())).thenReturn(mockReply);
    when(mockReply.setComponents(any())).thenReturn(mockReply);
    when(mockReply.setEphemeral(anyBoolean())).thenReturn(mockReply);
    when(mockReply.queue()).thenReturn(null);
    when(mockReply.queue(any())).thenReturn(null);

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
  void onStringSelectInteraction_NonSavedReadyMenu_ShouldIgnore() {
    when(mockEvent.getComponentId()).thenReturn("other_menu_id");

    menuListener.onStringSelectInteraction(mockEvent);

    verifyNoMoreInteractions(mockEvent);
  }

  @Test
  void onStringSelectInteraction_RoleBasedSelection_ShouldCreateRoleReadyCheck() {
    when(mockEvent.getComponentId()).thenReturn("select_saved_ready_true");
    when(mockEvent.getValues()).thenReturn(List.of("role-123"));

    final ReadyCheckManager.SavedReadyCheck savedCheck =
        TestReadyCheckUtils.createRoleBasedSavedCheck("role-123", true);

    try (MockedStatic<ReadyCheckManager> mockedManager = mockStatic(ReadyCheckManager.class);
        MockedStatic<SupabasePersistence> mockedPersistence =
            mockStatic(SupabasePersistence.class)) {

      mockedManager.when(() -> ReadyCheckManager.getJDA()).thenReturn(mockJDA);
      mockedPersistence
          .when(() -> SupabasePersistence.getSavedReadyChecks("guild-123"))
          .thenReturn(List.of(savedCheck));

      menuListener.onStringSelectInteraction(mockEvent);

      mockedManager.verify(
          () ->
              ReadyCheckManager.createReadyCheck(
                  eq("guild-123"), eq("channel-456"), eq("user-initiator"), eq("role-123"), any()));
    }
  }

  @Test
  void onStringSelectInteraction_UserBasedSelection_ShouldCreateUserReadyCheck() {
    when(mockEvent.getComponentId()).thenReturn("select_saved_ready_false");
    when(mockEvent.getValues()).thenReturn(List.of("users_12345"));

    final ReadyCheckManager.SavedReadyCheck savedCheck =
        TestReadyCheckUtils.createUserBasedSavedCheck(
            List.of("user-initiator", "user-1", "user-2"), false);

    try (MockedStatic<ReadyCheckManager> mockedManager = mockStatic(ReadyCheckManager.class);
        MockedStatic<SupabasePersistence> mockedPersistence =
            mockStatic(SupabasePersistence.class)) {

      mockedManager.when(() -> ReadyCheckManager.getJDA()).thenReturn(mockJDA);
      mockedPersistence
          .when(() -> SupabasePersistence.getSavedReadyChecks("guild-123"))
          .thenReturn(List.of(savedCheck));

      menuListener.onStringSelectInteraction(mockEvent);

      mockedManager.verify(
          () ->
              ReadyCheckManager.createUserReadyCheck(
                  eq("guild-123"), eq("channel-456"), eq("user-initiator"), any()));
    }
  }

  @Test
  void onStringSelectInteraction_ExistingReadyCheck_ShouldRefreshExisting() {
    when(mockEvent.getComponentId()).thenReturn("select_saved_ready_true");
    when(mockEvent.getValues()).thenReturn(List.of("role-123"));

    final ReadyCheckManager.SavedReadyCheck savedCheck =
        TestReadyCheckUtils.createRoleBasedSavedCheck("role-123", true);
    final String existingCheckId = "existing-check";

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
          .thenReturn(existingCheckId);
      mockedManager
          .when(() -> ReadyCheckManager.isReadyCheckOngoing(existingCheckId))
          .thenReturn(true);

      menuListener.onStringSelectInteraction(mockEvent);

      mockedManager.verify(
          () -> ReadyCheckManager.resendExistingReadyCheck(existingCheckId, mockJDA));
      verify(mockEvent).reply(contains("Found an existing ready check"));
      verify(mockReply).setEphemeral(true);
    }
  }

  @Test
  void onStringSelectInteraction_RoleNoLongerExists_ShouldShowError() {
    when(mockEvent.getComponentId()).thenReturn("select_saved_ready_true");
    when(mockEvent.getValues()).thenReturn(List.of("nonexistent-role"));

    when(mockGuild.getRoleById("nonexistent-role")).thenReturn(null);

    menuListener.onStringSelectInteraction(mockEvent);

    verify(mockEvent).reply("The selected role no longer exists!");
    verify(mockReply).setEphemeral(true);
  }

  @Test
  void onStringSelectInteraction_NoRoleMembers_ShouldShowError() {
    when(mockEvent.getComponentId()).thenReturn("select_saved_ready_true");
    when(mockEvent.getValues()).thenReturn(List.of("role-123"));

    when(mockGuild.getMembersWithRoles(mockRole)).thenReturn(List.of(mockInitiator));

    final ReadyCheckManager.SavedReadyCheck savedCheck =
        TestReadyCheckUtils.createRoleBasedSavedCheck("role-123", true);

    try (MockedStatic<SupabasePersistence> mockedPersistence =
        mockStatic(SupabasePersistence.class)) {
      mockedPersistence
          .when(() -> SupabasePersistence.getSavedReadyChecks("guild-123"))
          .thenReturn(List.of(savedCheck));

      menuListener.onStringSelectInteraction(mockEvent);

      verify(mockEvent).reply(contains("No other members found with the role"));
      verify(mockReply).setEphemeral(true);
    }
  }

  @Test
  void onStringSelectInteraction_UserBasedNoValidMembers_ShouldShowError() {
    when(mockEvent.getComponentId()).thenReturn("select_saved_ready_false");
    when(mockEvent.getValues()).thenReturn(List.of("users_12345"));

    final ReadyCheckManager.SavedReadyCheck savedCheck =
        TestReadyCheckUtils.createUserBasedSavedCheck(List.of("nonexistent-user"), false);

    try (MockedStatic<SupabasePersistence> mockedPersistence =
        mockStatic(SupabasePersistence.class)) {
      mockedPersistence
          .when(() -> SupabasePersistence.getSavedReadyChecks("guild-123"))
          .thenReturn(List.of(savedCheck));

      menuListener.onStringSelectInteraction(mockEvent);

      verify(mockEvent).reply("No other saved users found or they are no longer in the server!");
      verify(mockReply).setEphemeral(true);
    }
  }

  @Test
  void onStringSelectInteraction_InvalidUserBasedFormat_ShouldShowError() {
    when(mockEvent.getComponentId()).thenReturn("select_saved_ready_true");
    when(mockEvent.getValues()).thenReturn(List.of("users_"));

    menuListener.onStringSelectInteraction(mockEvent);

    verify(mockEvent).reply("The selected saved configuration is invalid!");
    verify(mockReply).setEphemeral(true);
  }

  @Test
  void onStringSelectInteraction_SavedCheckNotFound_ShouldShowError() {
    when(mockEvent.getComponentId()).thenReturn("select_saved_ready_true");
    when(mockEvent.getValues()).thenReturn(List.of("users_99999"));

    try (MockedStatic<SupabasePersistence> mockedPersistence =
        mockStatic(SupabasePersistence.class)) {
      mockedPersistence
          .when(() -> SupabasePersistence.getSavedReadyChecks("guild-123"))
          .thenReturn(List.of());

      menuListener.onStringSelectInteraction(mockEvent);

      verify(mockEvent).reply("The selected saved configuration no longer exists!");
      verify(mockReply).setEphemeral(true);
    }
  }

  @Test
  void onStringSelectInteraction_MentionPreferenceFromComponent_ShouldUseCorrectSetting() {
    when(mockEvent.getComponentId()).thenReturn("select_saved_ready_false");
    when(mockEvent.getValues()).thenReturn(List.of("role-123"));

    final ReadyCheckManager.SavedReadyCheck savedCheck =
        TestReadyCheckUtils.createRoleBasedSavedCheck("role-123", true);

    try (MockedStatic<ReadyCheckManager> mockedManager = mockStatic(ReadyCheckManager.class);
        MockedStatic<SupabasePersistence> mockedPersistence =
            mockStatic(SupabasePersistence.class)) {

      mockedManager.when(() -> ReadyCheckManager.getJDA()).thenReturn(mockJDA);
      mockedPersistence
          .when(() -> SupabasePersistence.getSavedReadyChecks("guild-123"))
          .thenReturn(List.of(savedCheck));

      menuListener.onStringSelectInteraction(mockEvent);

      mockedManager.verify(() -> ReadyCheckManager.setMentionPreference(anyString(), eq(false)));
    }
  }

  @Test
  void onStringSelectInteraction_SavedCheckMentionPreference_ShouldUseSavedSetting() {
    when(mockEvent.getComponentId()).thenReturn("select_saved_ready");
    when(mockEvent.getValues()).thenReturn(List.of("role-123"));

    final ReadyCheckManager.SavedReadyCheck savedCheck =
        TestReadyCheckUtils.createRoleBasedSavedCheck("role-123", false);

    try (MockedStatic<ReadyCheckManager> mockedManager = mockStatic(ReadyCheckManager.class);
        MockedStatic<SupabasePersistence> mockedPersistence =
            mockStatic(SupabasePersistence.class)) {

      mockedManager.when(() -> ReadyCheckManager.getJDA()).thenReturn(mockJDA);
      mockedPersistence
          .when(() -> SupabasePersistence.getSavedReadyChecks("guild-123"))
          .thenReturn(List.of(savedCheck));

      menuListener.onStringSelectInteraction(mockEvent);

      mockedManager.verify(() -> ReadyCheckManager.setMentionPreference(anyString(), eq(false)));
    }
  }

  @Test
  void onStringSelectInteraction_UserBasedExcludesInitiator_ShouldFilterCorrectly() {
    when(mockEvent.getComponentId()).thenReturn("select_saved_ready_true");
    when(mockEvent.getValues()).thenReturn(List.of("users_12345"));

    final ReadyCheckManager.SavedReadyCheck savedCheck =
        TestReadyCheckUtils.createUserBasedSavedCheck(List.of("user-initiator", "user-1"), true);

    try (MockedStatic<ReadyCheckManager> mockedManager = mockStatic(ReadyCheckManager.class);
        MockedStatic<SupabasePersistence> mockedPersistence =
            mockStatic(SupabasePersistence.class)) {

      mockedManager.when(() -> ReadyCheckManager.getJDA()).thenReturn(mockJDA);
      mockedPersistence
          .when(() -> SupabasePersistence.getSavedReadyChecks("guild-123"))
          .thenReturn(List.of(savedCheck));

      menuListener.onStringSelectInteraction(mockEvent);

      mockedManager.verify(
          () ->
              ReadyCheckManager.createUserReadyCheck(
                  eq("guild-123"),
                  eq("channel-456"),
                  eq("user-initiator"),
                  argThat(members -> members.size() == 1 && members.get(0).equals(mockUser1))));
    }
  }

  @Test
  void onStringSelectInteraction_RoleBasedExcludesInitiator_ShouldFilterCorrectly() {
    when(mockEvent.getComponentId()).thenReturn("select_saved_ready_true");
    when(mockEvent.getValues()).thenReturn(List.of("role-123"));

    when(mockGuild.getMembersWithRoles(mockRole))
        .thenReturn(List.of(mockInitiator, mockUser1, mockUser2));

    final ReadyCheckManager.SavedReadyCheck savedCheck =
        TestReadyCheckUtils.createRoleBasedSavedCheck("role-123", true);

    try (MockedStatic<ReadyCheckManager> mockedManager = mockStatic(ReadyCheckManager.class);
        MockedStatic<SupabasePersistence> mockedPersistence =
            mockStatic(SupabasePersistence.class)) {

      mockedManager.when(() -> ReadyCheckManager.getJDA()).thenReturn(mockJDA);
      mockedPersistence
          .when(() -> SupabasePersistence.getSavedReadyChecks("guild-123"))
          .thenReturn(List.of(savedCheck));

      menuListener.onStringSelectInteraction(mockEvent);

      mockedManager.verify(
          () ->
              ReadyCheckManager.createReadyCheck(
                  eq("guild-123"),
                  eq("channel-456"),
                  eq("user-initiator"),
                  eq("role-123"),
                  argThat(
                      members ->
                          members.size() == 2
                              && members.contains(mockUser1)
                              && members.contains(mockUser2)
                              && !members.contains(mockInitiator))));
    }
  }
}
