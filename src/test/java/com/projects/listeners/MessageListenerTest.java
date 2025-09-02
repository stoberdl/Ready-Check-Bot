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
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

final class MessageListenerTest {

  private MessageListener messageListener;
  private MessageReceivedEvent mockEvent;
  private JDA mockJDA;
  private Guild mockGuild;
  private TextChannel mockChannel;
  private Member mockMember;
  private User mockUser;
  private Message mockMessage;
  private Role mockRole;

  @BeforeEach
  void setUp() {
    messageListener = new MessageListener();

    mockJDA = MockDiscord.createMockJDA();
    mockGuild = MockDiscord.createMockGuild("guild-123");
    mockChannel = MockDiscord.createMockTextChannel("channel-456");
    mockMember = MockDiscord.createMockMember("user-123", "TestUser");
    mockUser = mockMember.getUser();
    mockMessage = mock(Message.class);
    mockRole = MockDiscord.createMockRole("role-123", "GameRole");

    mockEvent = mock(MessageReceivedEvent.class);
    when(mockEvent.getJDA()).thenReturn(mockJDA);
    when(mockEvent.getGuild()).thenReturn(mockGuild);
    when(mockEvent.getChannel()).thenReturn(mockChannel);
    when(mockEvent.getMember()).thenReturn(mockMember);
    when(mockEvent.getAuthor()).thenReturn(mockUser);
    when(mockEvent.getMessage()).thenReturn(mockMessage);
    when(mockEvent.isFromGuild()).thenReturn(true);

    MockDiscord.setupGuildWithMembers(mockGuild, mockMember);
    MockDiscord.setupGuildWithChannel(mockGuild, mockChannel);
    MockDiscord.setupGuildWithRoleMembers(mockGuild, mockRole, List.of(mockMember));
    MockDiscord.setupJDAWithGuild(mockJDA, mockGuild);

    ReadyCheckManager.setJDA(mockJDA);
    ReadyCheckManager.getActiveReadyChecks().clear();
  }

  @AfterEach
  void tearDown() {
    ReadyCheckManager.getActiveReadyChecks().clear();
  }

  @Test
  void onMessageReceived_BotMessage_ShouldIgnore() {
    when(mockUser.isBot()).thenReturn(true);
    when(mockMessage.getContentRaw()).thenReturn("r");

    messageListener.onMessageReceived(mockEvent);

    verify(mockEvent, never()).getGuild();
  }

  @Test
  void onMessageReceived_NonGuildMessage_ShouldIgnore() {
    when(mockEvent.isFromGuild()).thenReturn(false);
    when(mockMessage.getContentRaw()).thenReturn("r");

    messageListener.onMessageReceived(mockEvent);

    verify(mockEvent, never()).getGuild();
  }

  @Test
  void onMessageReceived_TooLongMessage_ShouldIgnore() {
    final String longMessage = "r".repeat(101);
    when(mockMessage.getContentRaw()).thenReturn(longMessage);

    messageListener.onMessageReceived(mockEvent);

    verify(mockEvent, times(1)).getAuthor();
    verify(mockEvent, times(1)).isFromGuild();
    verify(mockEvent, times(1)).getMessage();
    verifyNoMoreInteractions(mockEvent);
  }

  @Test
  void onMessageReceived_SimpleR_WithExistingActiveCheck_ShouldMarkReady() {
    when(mockMessage.getContentRaw()).thenReturn("r");

    final ReadyCheckManager.ReadyCheck existingCheck =
        TestReadyCheckUtils.builder().guild("guild-123").targetUsers("user-123").build();
    ReadyCheckManager.getActiveReadyChecks().put(existingCheck.getId(), existingCheck);

    try (MockedStatic<ReadyCheckManager> mockedManager = mockStatic(ReadyCheckManager.class)) {
      mockedManager.when(() -> ReadyCheckManager.getJDA()).thenReturn(mockJDA);
      mockedManager
          .when(() -> ReadyCheckManager.findActiveReadyCheckForUser("guild-123", "user-123"))
          .thenReturn(existingCheck.getId());
      mockedManager
          .when(() -> ReadyCheckManager.markUserReady(existingCheck.getId(), "user-123"))
          .thenCallRealMethod();

      messageListener.onMessageReceived(mockEvent);

      mockedManager.verify(
          () -> ReadyCheckManager.markUserReady(existingCheck.getId(), "user-123"));
    }
  }

  @Test
  void onMessageReceived_RInTime_WithExistingCheck_ShouldScheduleReady() {
    when(mockMessage.getContentRaw()).thenReturn("r in 15");

    final ReadyCheckManager.ReadyCheck existingCheck =
        TestReadyCheckUtils.builder().guild("guild-123").targetUsers("user-123").build();
    ReadyCheckManager.getActiveReadyChecks().put(existingCheck.getId(), existingCheck);

    try (MockedStatic<ReadyCheckManager> mockedManager = mockStatic(ReadyCheckManager.class)) {
      mockedManager.when(() -> ReadyCheckManager.getJDA()).thenReturn(mockJDA);
      mockedManager
          .when(() -> ReadyCheckManager.findActiveReadyCheckForUser("guild-123", "user-123"))
          .thenReturn(existingCheck.getId());

      messageListener.onMessageReceived(mockEvent);

      mockedManager.verify(
          () ->
              ReadyCheckManager.scheduleReadyAt(
                  eq(existingCheck.getId()), eq("15"), eq("user-123"), eq(mockJDA)));
    }
  }

  @Test
  void onMessageReceived_RAtTime_WithExistingCheck_ShouldScheduleReadyAtSmart() {
    when(mockMessage.getContentRaw()).thenReturn("r at 7pm");

    final ReadyCheckManager.ReadyCheck existingCheck =
        TestReadyCheckUtils.builder().guild("guild-123").targetUsers("user-123").build();
    ReadyCheckManager.getActiveReadyChecks().put(existingCheck.getId(), existingCheck);

    try (MockedStatic<ReadyCheckManager> mockedManager = mockStatic(ReadyCheckManager.class)) {
      mockedManager.when(() -> ReadyCheckManager.getJDA()).thenReturn(mockJDA);
      mockedManager
          .when(() -> ReadyCheckManager.findActiveReadyCheckForUser("guild-123", "user-123"))
          .thenReturn(existingCheck.getId());

      messageListener.onMessageReceived(mockEvent);

      mockedManager.verify(
          () ->
              ReadyCheckManager.scheduleReadyAtSmart(
                  eq(existingCheck.getId()), eq("7pm"), eq("user-123"), eq(mockJDA)));
    }
  }

  @Test
  void onMessageReceived_SimpleR_WithChannelCheck_ShouldEnsureUserAndMarkReady() {
    when(mockMessage.getContentRaw()).thenReturn("r");

    final ReadyCheckManager.ReadyCheck channelCheck =
        TestReadyCheckUtils.builder()
            .guild("guild-123")
            .channel("channel-456")
            .targetUsers("other-user")
            .build();
    ReadyCheckManager.getActiveReadyChecks().put(channelCheck.getId(), channelCheck);

    try (MockedStatic<ReadyCheckManager> mockedManager = mockStatic(ReadyCheckManager.class)) {
      mockedManager.when(() -> ReadyCheckManager.getJDA()).thenReturn(mockJDA);
      mockedManager
          .when(() -> ReadyCheckManager.findActiveReadyCheckForUser("guild-123", "user-123"))
          .thenReturn(null);
      mockedManager
          .when(() -> ReadyCheckManager.findActiveReadyCheckInChannel("guild-123", "channel-456"))
          .thenReturn(channelCheck.getId());

      messageListener.onMessageReceived(mockEvent);

      mockedManager.verify(
          () -> ReadyCheckManager.ensureUserInReadyCheck(channelCheck.getId(), "user-123"));
      mockedManager.verify(
          () -> ReadyCheckManager.refreshReadyCheckMessage(channelCheck.getId(), mockJDA));
      mockedManager.verify(() -> ReadyCheckManager.markUserReady(channelCheck.getId(), "user-123"));
    }
  }

  @Test
  void onMessageReceived_SimpleR_NoExistingChecks_WithSavedChecks_ShouldCreateFromSaved() {
    when(mockMessage.getContentRaw()).thenReturn("r");

    final ReadyCheckManager.SavedReadyCheck savedCheck =
        TestReadyCheckUtils.createRoleBasedSavedCheck("role-123", false);

    try (MockedStatic<ReadyCheckManager> mockedManager = mockStatic(ReadyCheckManager.class);
        MockedStatic<SupabasePersistence> mockedPersistence =
            mockStatic(SupabasePersistence.class)) {

      mockedManager.when(() -> ReadyCheckManager.getJDA()).thenReturn(mockJDA);
      mockedManager
          .when(() -> ReadyCheckManager.findActiveReadyCheckForUser("guild-123", "user-123"))
          .thenReturn(null);
      mockedManager
          .when(() -> ReadyCheckManager.findActiveReadyCheckInChannel("guild-123", "channel-456"))
          .thenReturn(null);

      mockedPersistence
          .when(() -> SupabasePersistence.getSavedReadyChecks("guild-123"))
          .thenReturn(List.of(savedCheck));

      messageListener.onMessageReceived(mockEvent);

      mockedManager.verify(
          () ->
              ReadyCheckManager.createReadyCheck(
                  eq("guild-123"), eq("channel-456"), eq("user-123"), eq("role-123"), any()));
    }
  }

  @Test
  void onMessageReceived_SimpleR_NoExistingOrSavedChecks_ShouldDoNothing() {
    when(mockMessage.getContentRaw()).thenReturn("r");

    try (MockedStatic<ReadyCheckManager> mockedManager = mockStatic(ReadyCheckManager.class);
        MockedStatic<SupabasePersistence> mockedPersistence =
            mockStatic(SupabasePersistence.class)) {

      mockedManager.when(() -> ReadyCheckManager.getJDA()).thenReturn(mockJDA);
      mockedManager
          .when(() -> ReadyCheckManager.findActiveReadyCheckForUser("guild-123", "user-123"))
          .thenReturn(null);
      mockedManager
          .when(() -> ReadyCheckManager.findActiveReadyCheckInChannel("guild-123", "channel-456"))
          .thenReturn(null);

      mockedPersistence
          .when(() -> SupabasePersistence.getSavedReadyChecks("guild-123"))
          .thenReturn(List.of());

      messageListener.onMessageReceived(mockEvent);

      mockedManager.verify(
          () ->
              ReadyCheckManager.createReadyCheck(
                  anyString(), anyString(), anyString(), anyString(), any()),
          never());
    }
  }

  @Test
  void onMessageReceived_InvalidTimeFormat_ShouldMarkReadyImmediately() {
    when(mockMessage.getContentRaw()).thenReturn("r in abc");

    final ReadyCheckManager.ReadyCheck existingCheck =
        TestReadyCheckUtils.builder().guild("guild-123").targetUsers("user-123").build();
    ReadyCheckManager.getActiveReadyChecks().put(existingCheck.getId(), existingCheck);

    try (MockedStatic<ReadyCheckManager> mockedManager = mockStatic(ReadyCheckManager.class)) {
      mockedManager.when(() -> ReadyCheckManager.getJDA()).thenReturn(mockJDA);
      mockedManager
          .when(() -> ReadyCheckManager.findActiveReadyCheckForUser("guild-123", "user-123"))
          .thenReturn(existingCheck.getId());
      mockedManager
          .when(
              () -> ReadyCheckManager.scheduleReadyAt(anyString(), anyString(), anyString(), any()))
          .thenThrow(new IllegalArgumentException("Invalid time"));

      messageListener.onMessageReceived(mockEvent);

      mockedManager.verify(
          () -> ReadyCheckManager.markUserReady(existingCheck.getId(), "user-123"));
    }
  }

  @Test
  void onMessageReceived_VariousRFormats_ShouldParseCorrectly() {
    final String[] validFormats = {"r", "R", "r in 15", "R in 30", "r at 7pm", "R AT 8:30"};

    for (final String format : validFormats) {
      when(mockMessage.getContentRaw()).thenReturn(format);

      try (MockedStatic<ReadyCheckManager> mockedManager = mockStatic(ReadyCheckManager.class);
          MockedStatic<SupabasePersistence> mockedPersistence =
              mockStatic(SupabasePersistence.class)) {

        mockedManager.when(() -> ReadyCheckManager.getJDA()).thenReturn(mockJDA);
        mockedManager
            .when(() -> ReadyCheckManager.findActiveReadyCheckForUser(anyString(), anyString()))
            .thenReturn(null);
        mockedManager
            .when(() -> ReadyCheckManager.findActiveReadyCheckInChannel(anyString(), anyString()))
            .thenReturn(null);
        mockedPersistence
            .when(() -> SupabasePersistence.getSavedReadyChecks(anyString()))
            .thenReturn(List.of());

        messageListener.onMessageReceived(mockEvent);
      }
    }
  }

  @Test
  void onMessageReceived_NonMatchingMessage_ShouldIgnore() {
    when(mockMessage.getContentRaw()).thenReturn("hello world");

    messageListener.onMessageReceived(mockEvent);

    verify(mockEvent, times(1)).getAuthor();
    verify(mockEvent, times(1)).isFromGuild();
    verify(mockEvent, times(1)).getMessage();
    verifyNoMoreInteractions(mockEvent);
  }

  @Test
  void onMessageReceived_NullMember_ShouldHandleGracefully() {
    when(mockEvent.getMember()).thenReturn(null);
    when(mockMessage.getContentRaw()).thenReturn("r");

    messageListener.onMessageReceived(mockEvent);

    verify(mockEvent).getMember();
  }
}
