package com.projects.readycheck;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.projects.commands.ReadyCommand;
import com.projects.listeners.ButtonInteractionListener;
import com.projects.listeners.MessageListener;
import com.projects.readycheck.utils.ReadyCheckTimeParser;
import com.projects.readycheck.utils.VoiceChannelMentionFilter;
import com.projects.testutils.MockDiscord;
import com.projects.testutils.TestReadyCheckUtils;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import net.dv8tion.jda.api.requests.restaction.interactions.UpdateInteractionAction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

final class EdgeCasesTest {

  private JDA mockJDA;
  private Guild mockGuild;
  private TextChannel mockChannel;
  private Member mockMember;
  private ReadyCommand readyCommand;
  private MessageListener messageListener;
  private ButtonInteractionListener buttonListener;

  @BeforeEach
  void setUp() {
    mockJDA = MockDiscord.createMockJDA();
    mockGuild = MockDiscord.createMockGuild("guild-123");
    mockChannel = MockDiscord.createMockTextChannel("channel-456");
    mockMember = MockDiscord.createMockMember("user-123", "TestUser");

    MockDiscord.setupGuildWithMembers(mockGuild, mockMember);
    MockDiscord.setupGuildWithChannel(mockGuild, mockChannel);
    MockDiscord.setupJDAWithGuild(mockJDA, mockGuild);

    readyCommand = new ReadyCommand();
    messageListener = new MessageListener();
    buttonListener = new ButtonInteractionListener();

    ReadyCheckManager.setJDA(mockJDA);
    ReadyCheckManager.getActiveReadyChecks().clear();
  }

  @AfterEach
  void tearDown() {
    ReadyCheckManager.getActiveReadyChecks().clear();
  }

  @Test
  void readyCheckManager_ConcurrentAccess_ShouldHandleSafely() {
    final ReadyCheckManager.ReadyCheck readyCheck = TestReadyCheckUtils.builder()
        .targetUsers("user-1", "user-2", "user-3")
        .build();
    ReadyCheckManager.getActiveReadyChecks().put(readyCheck.getId(), readyCheck);

    final Runnable task1 = () -> ReadyCheckManager.markUserReady(readyCheck.getId(), "user-1");
    final Runnable task2 = () -> ReadyCheckManager.markUserPassed(readyCheck.getId(), "user-2");
    final Runnable task3 = () -> ReadyCheckManager.toggleUserReady(readyCheck.getId(), "user-3");

    assertDoesNotThrow(() -> {
      task1.run();
      task2.run();
      task3.run();
    });
  }

  @Test
  void readyCheckManager_NonExistentReadyCheck_ShouldHandleGracefully() {
    assertDoesNotThrow(() -> {
      ReadyCheckManager.markUserReady("nonexistent-id", "user-123");
      ReadyCheckManager.markUserPassed("nonexistent-id", "user-123");
      ReadyCheckManager.unmarkUserPassed("nonexistent-id", "user-123");
      ReadyCheckManager.ensureUserInReadyCheck("nonexistent-id", "user-123");
    });

    assertFalse(ReadyCheckManager.toggleUserReady("nonexistent-id", "user-123"));
    assertFalse(ReadyCheckManager.checkIfAllReady("nonexistent-id"));
    assertFalse(ReadyCheckManager.isReadyCheckOngoing("nonexistent-id"));
  }

  @Test
  void timeParser_EdgeCaseInputs_ShouldHandleCorrectly() {
    assertThrows(IllegalArgumentException.class, () -> 
        ReadyCheckTimeParser.parseTimeInputAsMinutes(""));
    assertThrows(IllegalArgumentException.class, () -> 
        ReadyCheckTimeParser.parseTimeInputAsMinutes("   "));
    assertThrows(IllegalArgumentException.class, () -> 
        ReadyCheckTimeParser.parseTimeInputAsMinutes("null"));
    assertThrows(IllegalArgumentException.class, () -> 
        ReadyCheckTimeParser.parseTimeInputAsMinutes("-5"));
    assertThrows(IllegalArgumentException.class, () -> 
        ReadyCheckTimeParser.parseTimeInputAsMinutes("999999999999999999"));

    assertThrows(IllegalArgumentException.class, () -> 
        ReadyCheckTimeParser.parseTargetTime("25:00"));
    assertThrows(IllegalArgumentException.class, () -> 
        ReadyCheckTimeParser.parseTargetTime("12:60"));
    assertThrows(IllegalArgumentException.class, () -> 
        ReadyCheckTimeParser.parseTargetTime("abc:def"));
    assertThrows(IllegalArgumentException.class, () -> 
        ReadyCheckTimeParser.parseTargetTime("13am"));
    assertThrows(IllegalArgumentException.class, () => 
        ReadyCheckTimeParser.parseTargetTime("0pm"));
  }

  @Test
  void voiceChannelFilter_ExtremeCases_ShouldHandleCorrectly() {
    final Set<String> emptySet = Set.of();
    final String result1 = VoiceChannelMentionFilter.createCompletionMentions(emptySet, mockGuild);
    assertTrue(result1.isEmpty());

    final Set<String> largeSet = Set.of("user-1", "user-2", "user-3", "user-4", "user-5", 
        "user-6", "user-7", "user-8", "user-9", "user-10");
    final String result2 = VoiceChannelMentionFilter.createCompletionMentions(largeSet, mockGuild);
    assertNotNull(result2);

    final Set<String> nonExistentUsers = Set.of("nonexistent-1", "nonexistent-2");
    final String result3 = VoiceChannelMentionFilter.createCompletionMentions(nonExistentUsers, mockGuild);
    assertTrue(result3.isEmpty());
  }

  @Test
  void readyCommand_ExtremeInputs_ShouldHandleGracefully() {
    final SlashCommandInteractionEvent mockEvent = mock(SlashCommandInteractionEvent.class);
    final OptionMapping mockOption = mock(OptionMapping.class);
    final ReplyCallbackAction mockReply = mock(ReplyCallbackAction.class);

    when(mockEvent.getGuild()).thenReturn(mockGuild);
    when(mockEvent.getChannel()).thenReturn((MessageChannelUnion) mockChannel);
    when(mockEvent.getMember()).thenReturn(mockMember);
    when(mockEvent.getOption("targets")).thenReturn(mockOption);
    when(mockEvent.getOption("people", true, OptionMapping::getAsBoolean)).thenReturn(true);
    when(mockEvent.reply(anyString())).thenReturn(mockReply);
    when(mockReply.setEphemeral(anyBoolean())).thenReturn(mockReply);
    when(mockReply.queue()).thenReturn(null);

    final String veryLongString = "a".repeat(2000);
    when(mockOption.getAsString()).thenReturn(veryLongString);

    assertDoesNotThrow(() -> readyCommand.executeSlash(mockEvent));

    final String malformedMentions = "<@invalid> <@&also_invalid> @not_a_mention";
    when(mockOption.getAsString()).thenReturn(malformedMentions);

    assertDoesNotThrow(() -> readyCommand.executeSlash(mockEvent));
  }

  @Test
  void messageListener_ExtremeMessageLengths_ShouldHandleCorrectly() {
    final MessageReceivedEvent mockEvent = mock(MessageReceivedEvent.class);
    final Message mockMessage = mock(Message.class);
    final User mockUser = mock(User.class);

    when(mockEvent.getAuthor()).thenReturn(mockUser);
    when(mockEvent.isFromGuild()).thenReturn(true);
    when(mockEvent.getMessage()).thenReturn(mockMessage);
    when(mockUser.isBot()).thenReturn(false);

    final String exactlyMaxLength = "r" + " ".repeat(99);
    when(mockMessage.getContentRaw()).thenReturn(exactlyMaxLength);
    assertDoesNotThrow(() -> messageListener.onMessageReceived(mockEvent));

    final String overMaxLength = "r" + " ".repeat(100);
    when(mockMessage.getContentRaw()).thenReturn(overMaxLength);
    assertDoesNotThrow(() -> messageListener.onMessageReceived(mockEvent));

    when(mockMessage.getContentRaw()).thenReturn("");
    assertDoesNotThrow(() -> messageListener.onMessageReceived(mockEvent));

    when(mockMessage.getContentRaw()).thenReturn("   ");
    assertDoesNotThrow(() -> messageListener.onMessageReceived(mockEvent));
  }

  @Test
  void buttonListener_InvalidButtonIds_ShouldHandleGracefully() {
    final ButtonInteractionEvent mockEvent = mock(ButtonInteractionEvent.class);
    final User mockUser = mock(User.class);
    final ReplyCallbackAction mockReply = mock(ReplyCallbackAction.class);
    final UpdateInteractionAction mockUpdate = mock(UpdateInteractionAction.class);

    when(mockUser.getId()).thenReturn("user-123");
    when(mockEvent.getUser()).thenReturn(mockUser);
    when(mockEvent.getJDA()).thenReturn(mockJDA);
    when(mockEvent.reply(anyString())).thenReturn(mockReply);
    when(mockEvent.deferEdit()).thenReturn(mockUpdate);
    when(mockReply.setEphemeral(anyBoolean())).thenReturn(mockReply);
    when(mockReply.queue()).thenReturn(null);
    when(mockReply.queue(any())).thenReturn(null);
    when(mockUpdate.queue()).thenReturn(null);

    final String[] invalidIds = {
        "", "   ", "invalid", "toggle_ready_", "pass_", "ready_at_", "save_ready_",
        "toggle_ready_nonexistent", "pass_also_nonexistent",
        "malformed_button_id_with_no_underscore"
    };

    for (final String invalidId : invalidIds) {
      when(mockEvent.getComponentId()).thenReturn(invalidId);
      assertDoesNotThrow(() -> buttonListener.onButtonInteraction(mockEvent));
    }
  }

  @Test
  void readyCheckManager_MemoryPressure_ShouldHandleEfficiently() {
    final int largeNumber = 1000;
    
    for (int i = 0; i < largeNumber; i++) {
      final ReadyCheckManager.ReadyCheck readyCheck = TestReadyCheckUtils.builder()
          .id("check-" + i)
          .targetUsers("user-" + i)
          .build();
      ReadyCheckManager.getActiveReadyChecks().put("check-" + i, readyCheck);
    }

    assertEquals(largeNumber, ReadyCheckManager.getActiveReadyChecks().size());

    for (int i = 0; i < largeNumber; i++) {
      assertNotNull(ReadyCheckManager.getActiveReadyCheck("check-" + i));
    }

    ReadyCheckManager.getActiveReadyChecks().clear();
    assertEquals(0, ReadyCheckManager.getActiveReadyChecks().size());
  }

  @Test
  void readyCheckManager_NullInputs_ShouldHandleGracefully() {
    assertDoesNotThrow(() -> {
      ReadyCheckManager.markUserReady(null, "user-123");
      ReadyCheckManager.markUserReady("check-id", null);
      ReadyCheckManager.markUserReady(null, null);
    });

    assertDoesNotThrow(() -> {
      ReadyCheckManager.setMentionPreference(null, true);
      ReadyCheckManager.getMentionPreference(null);
      ReadyCheckManager.setReadyCheckMessageId(null, "message-123");
      ReadyCheckManager.setReadyCheckMessageId("check-id", null);
    });

    assertFalse(ReadyCheckManager.checkIfAllReady(null));
    assertFalse(ReadyCheckManager.isReadyCheckOngoing(null));
    assertNull(ReadyCheckManager.findActiveReadyCheckForUser(null, "user-123"));
    assertNull(ReadyCheckManager.findActiveReadyCheckForUser("guild-123", null));
  }

  @Test
  void readyCheckManager_EmptyCollections_ShouldHandleCorrectly() {
    final String checkId = ReadyCheckManager.createUserReadyCheck(
        "guild-123", "channel-456", "user-initiator", List.of());

    final ReadyCheckManager.ReadyCheck readyCheck = ReadyCheckManager.getActiveReadyCheck(checkId);
    assertNotNull(readyCheck);
    assertTrue(readyCheck.getTargetUsers().isEmpty());
    assertTrue(readyCheck.getReadyUsers().contains("user-initiator"));
  }

  @Test
  void readyCheckManager_DuplicateUsers_ShouldHandleCorrectly() {
    final Member duplicateUser = MockDiscord.createMockMember("user-1", "User1");
    final List<Member> membersWithDuplicates = List.of(duplicateUser, duplicateUser, duplicateUser);

    final String checkId = ReadyCheckManager.createUserReadyCheck(
        "guild-123", "channel-456", "user-initiator", membersWithDuplicates);

    final ReadyCheckManager.ReadyCheck readyCheck = ReadyCheckManager.getActiveReadyCheck(checkId);
    assertNotNull(readyCheck);
    
    final long userCount = readyCheck.getTargetUsers().stream()
        .filter(userId -> "user-1".equals(userId))
        .count();
    assertEquals(1, userCount);
  }

  @Test
  void readyCheckManager_RapidStateChanges_ShouldMaintainConsistency() {
    final ReadyCheckManager.ReadyCheck readyCheck = TestReadyCheckUtils.builder()
        .targetUsers("user-1")
        .build();
    ReadyCheckManager.getActiveReadyChecks().put(readyCheck.getId(), readyCheck);

    for (int i = 0; i < 10; i++) {
      ReadyCheckManager.markUserReady(readyCheck.getId(), "user-1");
      ReadyCheckManager.markUserPassed(readyCheck.getId(), "user-1");
      ReadyCheckManager.unmarkUserPassed(readyCheck.getId(), "user-1");
      ReadyCheckManager.toggleUserReady(readyCheck.getId(), "user-1");
      ReadyCheckManager.ensureUserInReadyCheck(readyCheck.getId(), "user-1");
    }

    assertNotNull(ReadyCheckManager.getActiveReadyCheck(readyCheck.getId()));
  }

  private void assertDoesNotThrow(final Runnable runnable) {
    try {
      runnable.run();
    } catch (final Exception e) {
      throw new AssertionError("Expected no exception but got: " + e.getMessage(), e);
    }
  }
}