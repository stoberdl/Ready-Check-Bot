package com.projects.readycheck;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.projects.testutils.AsyncTestUtils;
import com.projects.testutils.MockDiscord;
import com.projects.testutils.TestReadyCheckUtils;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

final class ReadyCheckSchedulerTest {

  private JDA mockJDA;
  private Guild mockGuild;
  private TextChannel mockChannel;
  private Member mockMember;
  private ScheduledExecutorService mockScheduler;
  private ScheduledFuture<?> mockFuture;

  @BeforeEach
  void setUp() {
    mockJDA = MockDiscord.createMockJDA();
    mockGuild = MockDiscord.createMockGuild("guild-123");
    mockChannel = MockDiscord.createMockTextChannel("channel-456");
    mockMember = MockDiscord.createMockMember("user-123", "TestUser");
    mockScheduler = AsyncTestUtils.createMockScheduler();
    mockFuture = AsyncTestUtils.createMockScheduledFuture();

    MockDiscord.setupGuildWithMembers(mockGuild, mockMember);
    MockDiscord.setupGuildWithChannel(mockGuild, mockChannel);
    MockDiscord.setupJDAWithGuild(mockJDA, mockGuild);

    when(mockScheduler.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
        .thenReturn(mockFuture);

    ReadyCheckManager.setJDA(mockJDA);
    ReadyCheckManager.getActiveReadyChecks().clear();
  }

  @AfterEach
  void tearDown() {
    ReadyCheckManager.getActiveReadyChecks().clear();
  }

  @Test
  void scheduleReadyAt_ValidMinutes_ShouldScheduleUser() {
    final ReadyCheckManager.ReadyCheck readyCheck =
        TestReadyCheckUtils.builder().targetUsers("user-123").build();

    try (MockedStatic<ReadyCheckScheduler> mockedScheduler =
        mockStatic(ReadyCheckScheduler.class)) {
      mockedScheduler.when(() -> ReadyCheckScheduler.getScheduler()).thenReturn(mockScheduler);
      mockedScheduler
          .when(() -> ReadyCheckScheduler.scheduleReadyAt(readyCheck, "15", "user-123", mockJDA))
          .thenCallRealMethod();

      ReadyCheckScheduler.scheduleReadyAt(readyCheck, "15", "user-123", mockJDA);

      assertTrue(readyCheck.getTargetUsers().contains("user-123"));
      verify(mockScheduler).schedule(any(Runnable.class), eq(15L), eq(TimeUnit.MINUTES));
    }
  }

  @Test
  void scheduleReadyAt_InvalidTime_ShouldThrowException() {
    final ReadyCheckManager.ReadyCheck readyCheck = TestReadyCheckUtils.builder().build();

    assertThrows(
        IllegalArgumentException.class,
        () -> ReadyCheckScheduler.scheduleReadyAt(readyCheck, "invalid", "user-123", mockJDA));
  }

  @Test
  void scheduleReadyAtSmart_ValidTime_ShouldScheduleAndReturnFormattedTime() {
    final ReadyCheckManager.ReadyCheck readyCheck = TestReadyCheckUtils.builder().build();

    try (MockedStatic<ReadyCheckScheduler> mockedScheduler =
        mockStatic(ReadyCheckScheduler.class)) {
      mockedScheduler.when(() -> ReadyCheckScheduler.getScheduler()).thenReturn(mockScheduler);
      mockedScheduler
          .when(
              () ->
                  ReadyCheckScheduler.scheduleReadyAtSmart(readyCheck, "8pm", "user-123", mockJDA))
          .thenCallRealMethod();

      final String formattedTime =
          ReadyCheckScheduler.scheduleReadyAtSmart(readyCheck, "8pm", "user-123", mockJDA);

      assertEquals("8:00 PM", formattedTime);
      assertTrue(readyCheck.getScheduledUsers().containsKey("user-123"));
      assertFalse(readyCheck.getReadyUsers().contains("user-123"));
      verify(mockScheduler).schedule(any(Runnable.class), anyLong(), eq(TimeUnit.MINUTES));
    }
  }

  @Test
  void scheduleReadyAtSmart_TimeInPast_ShouldScheduleForNextDay() {
    final ReadyCheckManager.ReadyCheck readyCheck = TestReadyCheckUtils.builder().build();

    try (MockedStatic<ReadyCheckScheduler> mockedScheduler =
        mockStatic(ReadyCheckScheduler.class)) {
      mockedScheduler.when(() -> ReadyCheckScheduler.getScheduler()).thenReturn(mockScheduler);
      mockedScheduler
          .when(
              () ->
                  ReadyCheckScheduler.scheduleReadyAtSmart(readyCheck, "1am", "user-123", mockJDA))
          .thenCallRealMethod();

      assertDoesNotThrow(
          () -> ReadyCheckScheduler.scheduleReadyAtSmart(readyCheck, "1am", "user-123", mockJDA));

      verify(mockScheduler).schedule(any(Runnable.class), anyLong(), eq(TimeUnit.MINUTES));
    }
  }

  @Test
  void cleanupExpiredScheduledUsers_WithExpiredUsers_ShouldRemoveExpired() {
    final ReadyCheckManager.ReadyCheck readyCheck = TestReadyCheckUtils.builder().build();
    final long pastTime = System.currentTimeMillis() - 60000;
    final long futureTime = System.currentTimeMillis() + 60000;

    TestReadyCheckUtils.addScheduledUser(readyCheck, "expired-user", pastTime);
    TestReadyCheckUtils.addScheduledUser(readyCheck, "future-user", futureTime);

    ReadyCheckScheduler.cleanupExpiredScheduledUsers(readyCheck);

    assertFalse(readyCheck.getScheduledUsers().containsKey("expired-user"));
    assertTrue(readyCheck.getScheduledUsers().containsKey("future-user"));
  }

  @Test
  void cancelExistingScheduledUser_WithScheduledUser_ShouldCancelAndRemove() {
    final ReadyCheckManager.ReadyCheck readyCheck = TestReadyCheckUtils.builder().build();
    TestReadyCheckUtils.addScheduledUser(
        readyCheck, "user-123", System.currentTimeMillis() + 60000);

    ReadyCheckScheduler.cancelExistingScheduledUser(readyCheck, "user-123");

    assertFalse(readyCheck.getScheduledUsers().containsKey("user-123"));
  }

  @Test
  void cancelExistingScheduledUser_WithNonScheduledUser_ShouldNotThrow() {
    final ReadyCheckManager.ReadyCheck readyCheck = TestReadyCheckUtils.builder().build();

    assertDoesNotThrow(
        () -> ReadyCheckScheduler.cancelExistingScheduledUser(readyCheck, "nonexistent-user"));
  }

  @Test
  void sendReadyReminder_UserInVoice_ShouldAutoReady() {
    final ReadyCheckManager.ReadyCheck readyCheck =
        TestReadyCheckUtils.builder()
            .id("check-123")
            .guild("guild-123")
            .channel("channel-456")
            .build();
    ReadyCheckManager.getActiveReadyChecks().put("check-123", readyCheck);
    TestReadyCheckUtils.addScheduledUser(readyCheck, "user-123", System.currentTimeMillis());

    setupMemberVoiceState(mockMember, true, false);

    try (MockedStatic<ReadyCheckManager> mockedManager = mockStatic(ReadyCheckManager.class)) {
      mockedManager.when(() -> ReadyCheckManager.getJDA()).thenReturn(mockJDA);
      mockedManager
          .when(() -> ReadyCheckManager.getActiveReadyCheck("check-123"))
          .thenReturn(readyCheck);
      mockedManager
          .when(() -> ReadyCheckManager.getActiveReadyChecks())
          .thenReturn(ReadyCheckManager.getActiveReadyChecks());

      ReadyCheckScheduler.sendReadyReminder("check-123", "user-123", mockJDA);

      assertTrue(readyCheck.getReadyUsers().contains("user-123"));
      assertFalse(readyCheck.getScheduledUsers().containsKey("user-123"));
      mockedManager.verify(() -> ReadyCheckManager.updateReadyCheckEmbed("check-123", mockJDA));
    }
  }

  @Test
  void sendReadyReminder_UserNotInVoice_ShouldSendReminderMessage() {
    final ReadyCheckManager.ReadyCheck readyCheck =
        TestReadyCheckUtils.builder()
            .id("check-123")
            .guild("guild-123")
            .channel("channel-456")
            .build();
    ReadyCheckManager.getActiveReadyChecks().put("check-123", readyCheck);
    TestReadyCheckUtils.addScheduledUser(readyCheck, "user-123", System.currentTimeMillis());

    setupMemberVoiceState(mockMember, false, false);
    setupMessageSending(mockChannel);

    try (MockedStatic<ReadyCheckManager> mockedManager = mockStatic(ReadyCheckManager.class)) {
      mockedManager.when(() -> ReadyCheckManager.getJDA()).thenReturn(mockJDA);
      mockedManager
          .when(() -> ReadyCheckManager.getActiveReadyCheck("check-123"))
          .thenReturn(readyCheck);

      ReadyCheckScheduler.sendReadyReminder("check-123", "user-123", mockJDA);

      assertFalse(readyCheck.getScheduledUsers().containsKey("user-123"));
      verify(mockChannel).sendMessage(contains(mockMember.getAsMention()));
    }
  }

  @Test
  void sendReadyReminder_UserDeafened_ShouldSendReminderMessage() {
    final ReadyCheckManager.ReadyCheck readyCheck =
        TestReadyCheckUtils.builder()
            .id("check-123")
            .guild("guild-123")
            .channel("channel-456")
            .build();
    ReadyCheckManager.getActiveReadyChecks().put("check-123", readyCheck);
    TestReadyCheckUtils.addScheduledUser(readyCheck, "user-123", System.currentTimeMillis());

    setupMemberVoiceState(mockMember, true, true);
    setupMessageSending(mockChannel);

    try (MockedStatic<ReadyCheckManager> mockedManager = mockStatic(ReadyCheckManager.class)) {
      mockedManager.when(() -> ReadyCheckManager.getJDA()).thenReturn(mockJDA);
      mockedManager
          .when(() -> ReadyCheckManager.getActiveReadyCheck("check-123"))
          .thenReturn(readyCheck);

      ReadyCheckScheduler.sendReadyReminder("check-123", "user-123", mockJDA);

      verify(mockChannel).sendMessage(contains(mockMember.getAsMention()));
    }
  }

  @Test
  void sendReadyReminder_ReadyCheckNotFound_ShouldHandleGracefully() {
    try (MockedStatic<ReadyCheckManager> mockedManager = mockStatic(ReadyCheckManager.class)) {
      mockedManager
          .when(() -> ReadyCheckManager.getActiveReadyCheck("nonexistent"))
          .thenReturn(null);

      assertDoesNotThrow(
          () -> ReadyCheckScheduler.sendReadyReminder("nonexistent", "user-123", mockJDA));
    }
  }

  @Test
  void sendReadyReminder_GuildNotFound_ShouldHandleGracefully() {
    final ReadyCheckManager.ReadyCheck readyCheck =
        TestReadyCheckUtils.builder().id("check-123").guild("nonexistent-guild").build();

    when(mockJDA.getGuildById("nonexistent-guild")).thenReturn(null);

    try (MockedStatic<ReadyCheckManager> mockedManager = mockStatic(ReadyCheckManager.class)) {
      mockedManager
          .when(() -> ReadyCheckManager.getActiveReadyCheck("check-123"))
          .thenReturn(readyCheck);

      assertDoesNotThrow(
          () -> ReadyCheckScheduler.sendReadyReminder("check-123", "user-123", mockJDA));
    }
  }

  @Test
  void sendReadyReminder_MemberNotFound_ShouldHandleGracefully() {
    final ReadyCheckManager.ReadyCheck readyCheck =
        TestReadyCheckUtils.builder().id("check-123").guild("guild-123").build();

    when(mockGuild.getMemberById("nonexistent-user")).thenReturn(null);

    try (MockedStatic<ReadyCheckManager> mockedManager = mockStatic(ReadyCheckManager.class)) {
      mockedManager
          .when(() -> ReadyCheckManager.getActiveReadyCheck("check-123"))
          .thenReturn(readyCheck);

      assertDoesNotThrow(
          () -> ReadyCheckScheduler.sendReadyReminder("check-123", "nonexistent-user", mockJDA));
    }
  }

  @Test
  void sendReadyReminder_ChannelNotFound_ShouldHandleGracefully() {
    final ReadyCheckManager.ReadyCheck readyCheck =
        TestReadyCheckUtils.builder()
            .id("check-123")
            .guild("guild-123")
            .channel("nonexistent-channel")
            .build();

    when(mockGuild.getTextChannelById("nonexistent-channel")).thenReturn(null);

    try (MockedStatic<ReadyCheckManager> mockedManager = mockStatic(ReadyCheckManager.class)) {
      mockedManager
          .when(() -> ReadyCheckManager.getActiveReadyCheck("check-123"))
          .thenReturn(readyCheck);

      assertDoesNotThrow(
          () -> ReadyCheckScheduler.sendReadyReminder("check-123", "user-123", mockJDA));
    }
  }

  private void setupMemberVoiceState(
      final Member member, final boolean inVoice, final boolean deafened) {
    final net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel voiceChannel =
        mock(net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel.class);
    final Guild.VoiceState voiceState = mock(Guild.VoiceState.class);

    when(member.getVoiceState()).thenReturn(voiceState);
    when(voiceState.inAudioChannel()).thenReturn(inVoice);
    when(voiceState.isDeafened()).thenReturn(deafened);
    if (inVoice) {
      when(voiceState.getChannel()).thenReturn(voiceChannel);
    }
  }

  private void setupMessageSending(final TextChannel channel) {
    final MessageCreateAction createAction = mock(MessageCreateAction.class);
    when(channel.sendMessage(anyString())).thenReturn(createAction);
    when(createAction.setEmbeds(any())).thenReturn(createAction);
    when(createAction.setComponents(any())).thenReturn(createAction);
    when(createAction.queue(any())).thenReturn(null);
  }
}
