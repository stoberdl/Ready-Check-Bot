package com.projects.readycheck;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.projects.testutils.MockDiscord;
import com.projects.testutils.TestReadyCheckUtils;
import java.awt.Color;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

final class ReadyCheckEmbedBuilderTest {

  private JDA mockJDA;
  private Guild mockGuild;
  private Member mockInitiator;
  private Member mockUser1;
  private Member mockUser2;

  @BeforeEach
  void setUp() {
    mockJDA = MockDiscord.createMockJDA();
    mockGuild = MockDiscord.createMockGuild("guild-123");
    mockInitiator = MockDiscord.createMockMember("user-initiator", "Initiator");
    mockUser1 = MockDiscord.createMockMember("user-1", "User1");
    mockUser2 = MockDiscord.createMockMember("user-2", "User2");

    MockDiscord.setupGuildWithMembers(mockGuild, mockInitiator, mockUser1, mockUser2);
    MockDiscord.setupJDAWithGuild(mockJDA, mockGuild);
  }

  @Test
  void buildReadyCheckEmbed_AllUsersReady_ShouldShowCompletedStatus() {
    final ReadyCheckManager.ReadyCheck readyCheck =
        TestReadyCheckUtils.builder()
            .guild("guild-123")
            .initiator("user-initiator")
            .targetUsers("user-1")
            .readyUsers("user-initiator", "user-1")
            .build();
    readyCheck.setDescription("Test ready check");

    final EmbedBuilder embed =
        ReadyCheckEmbedBuilder.buildReadyCheckEmbed(readyCheck, mockJDA, "Test ready check");

    final MessageEmbed built = embed.build();
    assertEquals("🎉 Everyone is ready!", built.getTitle());
    assertEquals(Color.GREEN.getRGB(), built.getColorRaw());
    assertNotNull(built.getDescription());
    assertTrue(built.getDescription().contains("Test ready check"));
  }

  @Test
  void buildReadyCheckEmbed_PartiallyReady_ShouldShowProgress() {
    final ReadyCheckManager.ReadyCheck readyCheck =
        TestReadyCheckUtils.builder()
            .guild("guild-123")
            .initiator("user-initiator")
            .targetUsers("user-1", "user-2")
            .readyUsers("user-initiator")
            .build();
    readyCheck.setDescription("Test ready check");

    final EmbedBuilder embed =
        ReadyCheckEmbedBuilder.buildReadyCheckEmbed(readyCheck, mockJDA, "Test ready check");

    final MessageEmbed built = embed.build();
    assertEquals("⏳ 1/3 ready", built.getTitle());
    assertEquals(Color.ORANGE.getRGB(), built.getColorRaw());
  }

  @Test
  void buildReadyCheckEmbed_WithPassedUsers_ShouldExcludeFromCount() {
    final ReadyCheckManager.ReadyCheck readyCheck =
        TestReadyCheckUtils.builder()
            .guild("guild-123")
            .initiator("user-initiator")
            .targetUsers("user-1", "user-2")
            .readyUsers("user-initiator")
            .passedUsers("user-2")
            .build();
    readyCheck.setDescription("Test ready check");

    final EmbedBuilder embed =
        ReadyCheckEmbedBuilder.buildReadyCheckEmbed(readyCheck, mockJDA, "Test ready check");

    final MessageEmbed built = embed.build();
    assertEquals("⏳ 1/2 ready", built.getTitle());
    assertTrue(built.getDescription().contains("~~User2~~"));
  }

  @Test
  void buildReadyCheckEmbed_WithScheduledUsers_ShouldShowScheduledStatus() {
    final ReadyCheckManager.ReadyCheck readyCheck =
        TestReadyCheckUtils.builder()
            .guild("guild-123")
            .initiator("user-initiator")
            .targetUsers("user-1")
            .readyUsers("user-initiator")
            .build();
    readyCheck.setDescription("Test ready check");

    final long futureTime = System.currentTimeMillis() + 300000;
    TestReadyCheckUtils.addScheduledUser(readyCheck, "user-1", futureTime);

    final EmbedBuilder embed =
        ReadyCheckEmbedBuilder.buildReadyCheckEmbed(readyCheck, mockJDA, "Test ready check");

    final MessageEmbed built = embed.build();
    assertTrue(built.getDescription().contains("⏰ User1"));
  }

  @Test
  void buildReadyCheckEmbed_WithUserTimers_ShouldShowTimerInfo() {
    final ReadyCheckManager.ReadyCheck readyCheck =
        TestReadyCheckUtils.builder()
            .guild("guild-123")
            .initiator("user-initiator")
            .targetUsers("user-1")
            .readyUsers("user-initiator", "user-1")
            .build();
    readyCheck.setDescription("Test ready check");
    readyCheck.getUserTimers().put("user-1", 5);

    final EmbedBuilder embed =
        ReadyCheckEmbedBuilder.buildReadyCheckEmbed(readyCheck, mockJDA, "Test ready check");

    final MessageEmbed built = embed.build();
    assertTrue(built.getDescription().contains("auto-unready in 5min"));
  }

  @Test
  void buildReadyCheckEmbed_RecoveredCheck_ShouldShowRecoveryIcon() {
    final ReadyCheckManager.ReadyCheck readyCheck =
        TestReadyCheckUtils.builder()
            .guild("guild-123")
            .initiator("user-initiator")
            .targetUsers("user-1")
            .build();
    readyCheck.setDescription("Test ready check");
    readyCheck.setRecovered(true);

    final EmbedBuilder embed =
        ReadyCheckEmbedBuilder.buildReadyCheckEmbed(readyCheck, mockJDA, "Test ready check");

    final MessageEmbed built = embed.build();
    assertEquals("♻️", built.getFooter().getText());
  }

  @Test
  void buildReadyCheckEmbed_MemberNotFound_ShouldShowUnknownUser() {
    final ReadyCheckManager.ReadyCheck readyCheck =
        TestReadyCheckUtils.builder()
            .guild("guild-123")
            .initiator("nonexistent-user")
            .targetUsers("user-1")
            .build();
    readyCheck.setDescription("Test ready check");

    when(mockGuild.getMemberById("nonexistent-user")).thenReturn(null);

    final EmbedBuilder embed =
        ReadyCheckEmbedBuilder.buildReadyCheckEmbed(readyCheck, mockJDA, "Test ready check");

    final MessageEmbed built = embed.build();
    assertTrue(built.getDescription().contains("Unknown User"));
  }

  @Test
  void buildReadyCheckEmbed_ScheduledUserExpiredTime_ShouldShowReadyNow() {
    final ReadyCheckManager.ReadyCheck readyCheck =
        TestReadyCheckUtils.builder()
            .guild("guild-123")
            .initiator("user-initiator")
            .targetUsers("user-1")
            .readyUsers("user-initiator")
            .build();
    readyCheck.setDescription("Test ready check");

    final long pastTime = System.currentTimeMillis() - 60000;
    TestReadyCheckUtils.addScheduledUser(readyCheck, "user-1", pastTime);

    final EmbedBuilder embed =
        ReadyCheckEmbedBuilder.buildReadyCheckEmbed(readyCheck, mockJDA, "Test ready check");

    final MessageEmbed built = embed.build();
    assertTrue(built.getDescription().contains("ready now!"));
  }

  @Test
  void buildReadyCheckEmbed_ScheduledUserLongTime_ShouldShowTimestamp() {
    final ReadyCheckManager.ReadyCheck readyCheck =
        TestReadyCheckUtils.builder()
            .guild("guild-123")
            .initiator("user-initiator")
            .targetUsers("user-1")
            .readyUsers("user-initiator")
            .build();
    readyCheck.setDescription("Test ready check");

    final long longFutureTime = System.currentTimeMillis() + 3900000;
    TestReadyCheckUtils.addScheduledUser(readyCheck, "user-1", longFutureTime);

    final EmbedBuilder embed =
        ReadyCheckEmbedBuilder.buildReadyCheckEmbed(readyCheck, mockJDA, "Test ready check");

    final MessageEmbed built = embed.build();
    assertTrue(built.getDescription().contains("<t:"));
  }

  @Test
  void createMentions_MentionEnabled_ShouldMentionUnreadyUsers() {
    final ReadyCheckManager.ReadyCheck readyCheck =
        TestReadyCheckUtils.builder()
            .guild("guild-123")
            .initiator("user-initiator")
            .targetUsers("user-1", "user-2")
            .readyUsers("user-initiator")
            .build();

    try (MockedStatic<ReadyCheckManager> mockedManager = mockStatic(ReadyCheckManager.class)) {
      mockedManager.when(() -> ReadyCheckManager.getMentionPreference("test-id")).thenReturn(true);

      final String mentions = ReadyCheckEmbedBuilder.createMentions(readyCheck, mockJDA, "test-id");

      assertTrue(mentions.contains(mockUser1.getAsMention()));
      assertTrue(mentions.contains(mockUser2.getAsMention()));
      assertFalse(mentions.contains(mockInitiator.getAsMention()));
    }
  }

  @Test
  void createMentions_MentionDisabled_ShouldReturnEmpty() {
    final ReadyCheckManager.ReadyCheck readyCheck =
        TestReadyCheckUtils.builder()
            .guild("guild-123")
            .initiator("user-initiator")
            .targetUsers("user-1", "user-2")
            .build();

    try (MockedStatic<ReadyCheckManager> mockedManager = mockStatic(ReadyCheckManager.class)) {
      mockedManager.when(() -> ReadyCheckManager.getMentionPreference("test-id")).thenReturn(false);

      final String mentions = ReadyCheckEmbedBuilder.createMentions(readyCheck, mockJDA, "test-id");

      assertTrue(mentions.isEmpty());
    }
  }

  @Test
  void createMentions_PassedUsers_ShouldNotMention() {
    final ReadyCheckManager.ReadyCheck readyCheck =
        TestReadyCheckUtils.builder()
            .guild("guild-123")
            .initiator("user-initiator")
            .targetUsers("user-1", "user-2")
            .passedUsers("user-1")
            .build();

    try (MockedStatic<ReadyCheckManager> mockedManager = mockStatic(ReadyCheckManager.class)) {
      mockedManager.when(() -> ReadyCheckManager.getMentionPreference("test-id")).thenReturn(true);

      final String mentions = ReadyCheckEmbedBuilder.createMentions(readyCheck, mockJDA, "test-id");

      assertFalse(mentions.contains(mockUser1.getAsMention()));
      assertTrue(mentions.contains(mockUser2.getAsMention()));
    }
  }

  @Test
  void createMentions_ScheduledUsers_ShouldNotMention() {
    final ReadyCheckManager.ReadyCheck readyCheck =
        TestReadyCheckUtils.builder()
            .guild("guild-123")
            .initiator("user-initiator")
            .targetUsers("user-1", "user-2")
            .build();

    TestReadyCheckUtils.addScheduledUser(readyCheck, "user-1", System.currentTimeMillis() + 60000);

    try (MockedStatic<ReadyCheckManager> mockedManager = mockStatic(ReadyCheckManager.class)) {
      mockedManager.when(() -> ReadyCheckManager.getMentionPreference("test-id")).thenReturn(true);

      final String mentions = ReadyCheckEmbedBuilder.createMentions(readyCheck, mockJDA, "test-id");

      assertFalse(mentions.contains(mockUser1.getAsMention()));
      assertTrue(mentions.contains(mockUser2.getAsMention()));
    }
  }

  @Test
  void createMentions_GuildNotFound_ShouldReturnEmpty() {
    final ReadyCheckManager.ReadyCheck readyCheck =
        TestReadyCheckUtils.builder().guild("nonexistent-guild").targetUsers("user-1").build();

    when(mockJDA.getGuildById("nonexistent-guild")).thenReturn(null);

    try (MockedStatic<ReadyCheckManager> mockedManager = mockStatic(ReadyCheckManager.class)) {
      mockedManager.when(() -> ReadyCheckManager.getMentionPreference("test-id")).thenReturn(true);

      final String mentions = ReadyCheckEmbedBuilder.createMentions(readyCheck, mockJDA, "test-id");

      assertTrue(mentions.isEmpty());
    }
  }

  @Test
  void buildReadyCheckEmbed_VariousUserStatuses_ShouldShowCorrectIcons() {
    final ReadyCheckManager.ReadyCheck readyCheck =
        TestReadyCheckUtils.builder()
            .guild("guild-123")
            .initiator("user-initiator")
            .targetUsers("user-1", "user-2")
            .readyUsers("user-initiator")
            .passedUsers("user-2")
            .build();
    readyCheck.setDescription("Test ready check");

    TestReadyCheckUtils.addScheduledUser(readyCheck, "user-1", System.currentTimeMillis() + 300000);

    final EmbedBuilder embed =
        ReadyCheckEmbedBuilder.buildReadyCheckEmbed(readyCheck, mockJDA, "Test ready check");

    final MessageEmbed built = embed.build();
    final String description = built.getDescription();

    assertTrue(description.contains("✅ Initiator"));
    assertTrue(description.contains("⏰ User1"));
    assertTrue(description.contains("🚫 ~~User2~~"));
  }
}
