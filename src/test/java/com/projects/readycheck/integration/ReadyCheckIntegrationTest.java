package com.projects.readycheck.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.projects.commands.ReadyCommand;
import com.projects.listeners.ButtonInteractionListener;
import com.projects.listeners.MessageListener;
import com.projects.listeners.ModalInteractionListener;
import com.projects.readycheck.ReadyCheckManager;
import com.projects.readycheck.SupabasePersistence;
import com.projects.testutils.MockDiscord;
import com.projects.testutils.TestReadyCheckUtils;
import java.util.List;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import net.dv8tion.jda.api.requests.restaction.interactions.UpdateInteractionAction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

final class ReadyCheckIntegrationTest {

  private JDA mockJDA;
  private Guild mockGuild;
  private TextChannel mockChannel;
  private Member mockInitiator;
  private Member mockUser1;
  private Member mockUser2;
  private Role mockRole;
  
  private ReadyCommand readyCommand;
  private MessageListener messageListener;
  private ButtonInteractionListener buttonListener;
  private ModalInteractionListener modalListener;

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
    MockDiscord.setupGuildWithRoleMembers(mockGuild, mockRole, List.of(mockUser1, mockUser2));
    MockDiscord.setupJDAWithGuild(mockJDA, mockGuild);

    readyCommand = new ReadyCommand();
    messageListener = new MessageListener();
    buttonListener = new ButtonInteractionListener();
    modalListener = new ModalInteractionListener();

    ReadyCheckManager.setJDA(mockJDA);
    ReadyCheckManager.getActiveReadyChecks().clear();
  }

  @AfterEach
  void tearDown() {
    ReadyCheckManager.getActiveReadyChecks().clear();
  }

  @Test
  void completeReadyCheckFlow_SlashCommandToCompletion_ShouldWork() {
    try (MockedStatic<ReadyCheckManager> mockedManager = mockStatic(ReadyCheckManager.class);
         MockedStatic<SupabasePersistence> mockedPersistence = mockStatic(SupabasePersistence.class)) {
      
      setupManagerMocks(mockedManager);
      
      final SlashCommandInteractionEvent slashEvent = createSlashCommandEvent();
      final String readyCheckId = "test-check-id";
      
      final ReadyCheckManager.ReadyCheck readyCheck = TestReadyCheckUtils.builder()
          .id(readyCheckId)
          .guild("guild-123")
          .initiator("user-initiator")
          .targetUsers("user-1", "user-2")
          .readyUsers("user-initiator")
          .build();
      ReadyCheckManager.getActiveReadyChecks().put(readyCheckId, readyCheck);

      mockedManager.when(() -> ReadyCheckManager.createUserReadyCheck(
          eq("guild-123"), eq("channel-456"), eq("user-initiator"), any()))
          .thenReturn(readyCheckId);
      mockedPersistence.when(() -> SupabasePersistence.getSavedReadyChecks(anyString()))
          .thenReturn(List.of());

      readyCommand.executeSlash(slashEvent);

      final ButtonInteractionEvent buttonEvent1 = createButtonEvent("toggle_ready_" + readyCheckId, "user-1");
      mockedManager.when(() -> ReadyCheckManager.toggleUserReady(readyCheckId, "user-1"))
          .thenReturn(true);
      mockedManager.when(() -> ReadyCheckManager.checkIfAllReady(readyCheckId))
          .thenReturn(false);

      buttonListener.onButtonInteraction(buttonEvent1);

      final ButtonInteractionEvent buttonEvent2 = createButtonEvent("toggle_ready_" + readyCheckId, "user-2");
      mockedManager.when(() -> ReadyCheckManager.toggleUserReady(readyCheckId, "user-2"))
          .thenReturn(true);
      mockedManager.when(() -> ReadyCheckManager.checkIfAllReady(readyCheckId))
          .thenReturn(true);

      buttonListener.onButtonInteraction(buttonEvent2);

      mockedManager.verify(() -> ReadyCheckManager.createUserReadyCheck(anyString(), anyString(), anyString(), any()));
      mockedManager.verify(() -> ReadyCheckManager.toggleUserReady(readyCheckId, "user-1"));
      mockedManager.verify(() -> ReadyCheckManager.toggleUserReady(readyCheckId, "user-2"));
      mockedManager.verify(() -> ReadyCheckManager.notifyAllReady(readyCheckId, mockJDA));
    }
  }

  @Test
  void messageBasedReadyCheckFlow_ShouldCreateAndProgress() {
    try (MockedStatic<ReadyCheckManager> mockedManager = mockStatic(ReadyCheckManager.class);
         MockedStatic<SupabasePersistence> mockedPersistence = mockStatic(SupabasePersistence.class)) {
      
      setupManagerMocks(mockedManager);
      
      final ReadyCheckManager.SavedReadyCheck savedCheck = 
          TestReadyCheckUtils.createRoleBasedSavedCheck("role-123", false);
      mockedPersistence.when(() -> SupabasePersistence.getSavedReadyChecks("guild-123"))
          .thenReturn(List.of(savedCheck));

      final MessageReceivedEvent messageEvent = createMessageEvent("r");
      final String readyCheckId = "message-check-id";
      
      mockedManager.when(() -> ReadyCheckManager.findActiveReadyCheckForUser("guild-123", "user-initiator"))
          .thenReturn(null);
      mockedManager.when(() -> ReadyCheckManager.findActiveReadyCheckInChannel("guild-123", "channel-456"))
          .thenReturn(null);
      mockedManager.when(() -> ReadyCheckManager.createReadyCheck(
          eq("guild-123"), eq("channel-456"), eq("user-initiator"), eq("role-123"), any()))
          .thenReturn(readyCheckId);

      messageListener.onMessageReceived(messageEvent);

      mockedManager.verify(() -> ReadyCheckManager.createReadyCheck(
          eq("guild-123"), eq("channel-456"), eq("user-initiator"), eq("role-123"), any()));
      mockedManager.verify(() -> ReadyCheckManager.setMentionPreference(readyCheckId, false));
      mockedManager.verify(() -> ReadyCheckManager.markUserReady(readyCheckId, "user-initiator"));
    }
  }

  @Test
  void messageBasedScheduling_ShouldScheduleUser() {
    try (MockedStatic<ReadyCheckManager> mockedManager = mockStatic(ReadyCheckManager.class)) {
      
      setupManagerMocks(mockedManager);
      
      final String existingCheckId = "existing-check";
      final ReadyCheckManager.ReadyCheck existingCheck = TestReadyCheckUtils.builder()
          .id(existingCheckId)
          .guild("guild-123")
          .targetUsers("user-initiator")
          .build();
      ReadyCheckManager.getActiveReadyChecks().put(existingCheckId, existingCheck);

      mockedManager.when(() -> ReadyCheckManager.findActiveReadyCheckForUser("guild-123", "user-initiator"))
          .thenReturn(existingCheckId);

      final MessageReceivedEvent messageEvent = createMessageEvent("r in 15");

      messageListener.onMessageReceived(messageEvent);

      mockedManager.verify(() -> ReadyCheckManager.unmarkUserPassed(existingCheckId, "user-initiator"));
      mockedManager.verify(() -> ReadyCheckManager.scheduleReadyAt(existingCheckId, "15", "user-initiator", mockJDA));
      mockedManager.verify(() -> ReadyCheckManager.updateReadyCheckEmbed(existingCheckId, mockJDA));
    }
  }

  @Test
  void passAndUnpassFlow_ShouldToggleCorrectly() {
    try (MockedStatic<ReadyCheckManager> mockedManager = mockStatic(ReadyCheckManager.class)) {
      
      setupManagerMocks(mockedManager);
      
      final String readyCheckId = "pass-test-id";
      final ReadyCheckManager.ReadyCheck readyCheck = TestReadyCheckUtils.builder()
          .id(readyCheckId)
          .targetUsers("user-1")
          .build();
      ReadyCheckManager.getActiveReadyChecks().put(readyCheckId, readyCheck);

      final ButtonInteractionEvent passEvent = createButtonEvent("pass_" + readyCheckId, "user-1");
      buttonListener.onButtonInteraction(passEvent);

      mockedManager.verify(() -> ReadyCheckManager.markUserPassed(readyCheckId, "user-1"));

      final ButtonInteractionEvent readyAtEvent = createButtonEvent("ready_at_" + readyCheckId, "user-1");
      buttonListener.onButtonInteraction(readyAtEvent);

      mockedManager.verify(() -> ReadyCheckManager.unmarkUserPassed(readyCheckId, "user-1"));
    }
  }

  @Test
  void saveAndReuseFlow_ShouldPersistConfiguration() {
    try (MockedStatic<ReadyCheckManager> mockedManager = mockStatic(ReadyCheckManager.class);
         MockedStatic<SupabasePersistence> mockedPersistence = mockStatic(SupabasePersistence.class)) {
      
      setupManagerMocks(mockedManager);
      
      final String readyCheckId = "save-test-id";
      final ReadyCheckManager.ReadyCheck readyCheck = TestReadyCheckUtils.builder()
          .id(readyCheckId)
          .build();
      ReadyCheckManager.getActiveReadyChecks().put(readyCheckId, readyCheck);

      final ButtonInteractionEvent saveEvent = createButtonEvent("save_ready_" + readyCheckId, "user-initiator");
      buttonListener.onButtonInteraction(saveEvent);

      mockedManager.verify(() -> ReadyCheckManager.saveReadyCheck(readyCheckId));
    }
  }

  @Test
  void duplicateReadyCheckDetection_ShouldReuseExisting() {
    try (MockedStatic<ReadyCheckManager> mockedManager = mockStatic(ReadyCheckManager.class)) {
      
      setupManagerMocks(mockedManager);
      
      final String existingCheckId = "existing-duplicate";
      mockedManager.when(() -> ReadyCheckManager.findExistingReadyCheck(
          eq("guild-123"), any(List.class), eq("user-initiator")))
          .thenReturn(existingCheckId);

      final SlashCommandInteractionEvent slashEvent = createSlashCommandEvent();
      readyCommand.executeSlash(slashEvent);

      mockedManager.verify(() -> ReadyCheckManager.resendExistingReadyCheck(existingCheckId, mockJDA));
    }
  }

  @Test
  void concurrentUserInteractions_ShouldHandleCorrectly() {
    try (MockedStatic<ReadyCheckManager> mockedManager = mockStatic(ReadyCheckManager.class)) {
      
      setupManagerMocks(mockedManager);
      
      final String readyCheckId = "concurrent-test";
      final ReadyCheckManager.ReadyCheck readyCheck = TestReadyCheckUtils.builder()
          .id(readyCheckId)
          .targetUsers("user-1", "user-2")
          .build();
      ReadyCheckManager.getActiveReadyChecks().put(readyCheckId, readyCheck);

      final ButtonInteractionEvent button1 = createButtonEvent("toggle_ready_" + readyCheckId, "user-1");
      final ButtonInteractionEvent button2 = createButtonEvent("toggle_ready_" + readyCheckId, "user-2");

      mockedManager.when(() -> ReadyCheckManager.toggleUserReady(readyCheckId, "user-1"))
          .thenReturn(true);
      mockedManager.when(() -> ReadyCheckManager.toggleUserReady(readyCheckId, "user-2"))
          .thenReturn(true);

      buttonListener.onButtonInteraction(button1);
      buttonListener.onButtonInteraction(button2);

      mockedManager.verify(() -> ReadyCheckManager.ensureUserInReadyCheck(readyCheckId, "user-1"));
      mockedManager.verify(() -> ReadyCheckManager.ensureUserInReadyCheck(readyCheckId, "user-2"));
      mockedManager.verify(() -> ReadyCheckManager.toggleUserReady(readyCheckId, "user-1"));
      mockedManager.verify(() => ReadyCheckManager.toggleUserReady(readyCheckId, "user-2"));
      mockedManager.verify(() -> ReadyCheckManager.updateReadyCheckEmbed(readyCheckId, mockJDA), times(2));
    }
  }

  private void setupManagerMocks(final MockedStatic<ReadyCheckManager> mockedManager) {
    mockedManager.when(() -> ReadyCheckManager.getJDA()).thenReturn(mockJDA);
    mockedManager.when(() -> ReadyCheckManager.getActiveReadyChecks())
        .thenReturn(ReadyCheckManager.getActiveReadyChecks());
    mockedManager.when(() -> ReadyCheckManager.getActiveReadyCheck(anyString()))
        .thenAnswer(invocation -> ReadyCheckManager.getActiveReadyChecks().get(invocation.getArgument(0)));
  }

  private SlashCommandInteractionEvent createSlashCommandEvent() {
    final SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
    final OptionMapping targetsOption = mock(OptionMapping.class);
    final ReplyCallbackAction reply = mock(ReplyCallbackAction.class);
    
    when(event.getJDA()).thenReturn(mockJDA);
    when(event.getGuild()).thenReturn(mockGuild);
    when(event.getChannel()).thenReturn(mockChannel);
    when(event.getMember()).thenReturn(mockInitiator);
    when(event.getOption("targets")).thenReturn(targetsOption);
    when(event.getOption("people", true, OptionMapping::getAsBoolean)).thenReturn(true);
    when(targetsOption.getAsString()).thenReturn("<@user-1> <@user-2>");
    when(event.reply(anyString())).thenReturn(reply);
    when(reply.setEmbeds(any())).thenReturn(reply);
    when(reply.setComponents(any())).thenReturn(reply);
    when(reply.queue(any())).thenReturn(null);
    
    return event;
  }

  private MessageReceivedEvent createMessageEvent(final String content) {
    final MessageReceivedEvent event = mock(MessageReceivedEvent.class);
    final Message message = mock(Message.class);
    final User user = mockInitiator.getUser();
    
    when(event.getJDA()).thenReturn(mockJDA);
    when(event.getGuild()).thenReturn(mockGuild);
    when(event.getChannel()).thenReturn(mockChannel);
    when(event.getMember()).thenReturn(mockInitiator);
    when(event.getAuthor()).thenReturn(user);
    when(event.getMessage()).thenReturn(message);
    when(event.isFromGuild()).thenReturn(true);
    when(message.getContentRaw()).thenReturn(content);
    
    return event;
  }

  private ButtonInteractionEvent createButtonEvent(final String componentId, final String userId) {
    final ButtonInteractionEvent event = mock(ButtonInteractionEvent.class);
    final User user = mock(User.class);
    final UpdateInteractionAction update = mock(UpdateInteractionAction.class);
    final ReplyCallbackAction reply = mock(ReplyCallbackAction.class);
    
    when(user.getId()).thenReturn(userId);
    when(event.getJDA()).thenReturn(mockJDA);
    when(event.getUser()).thenReturn(user);
    when(event.getComponentId()).thenReturn(componentId);
    when(event.deferEdit()).thenReturn(update);
    when(event.reply(anyString())).thenReturn(reply);
    when(reply.setEphemeral(anyBoolean())).thenReturn(reply);
    when(update.queue()).thenReturn(null);
    when(reply.queue()).thenReturn(null);
    when(reply.queue(any())).thenReturn(null);
    
    return event;
  }
}