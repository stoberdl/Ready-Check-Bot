package com.projects.readycheck;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.projects.readycheck.utils.VoiceChannelMentionFilter;
import com.projects.testutils.MockDiscord;
import java.util.Set;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class VoiceChannelMentionFilterTest {

  private Guild mockGuild;
  private Member mockMemberInVoice;
  private Member mockMemberNotInVoice;
  private Member mockMemberDeafened;
  private Member mockMemberNotFound;

  @BeforeEach
  void setUp() {
    mockGuild = MockDiscord.createMockGuild("guild-123");
    mockMemberInVoice = MockDiscord.createMockMember("user-in-voice", "InVoice");
    mockMemberNotInVoice = MockDiscord.createMockMember("user-not-in-voice", "NotInVoice");
    mockMemberDeafened = MockDiscord.createMockMember("user-deafened", "Deafened");

    setupMemberVoiceState(mockMemberInVoice, true, false);
    setupMemberVoiceState(mockMemberNotInVoice, false, false);
    setupMemberVoiceState(mockMemberDeafened, true, true);

    MockDiscord.setupGuildWithMembers(
        mockGuild, mockMemberInVoice, mockMemberNotInVoice, mockMemberDeafened);
    when(mockGuild.getMemberById("user-not-found")).thenReturn(null);
  }

  @Test
  void createCompletionMentions_UsersInVoiceAndActive_ShouldNotMention() {
    final Set<String> readyUserIds = Set.of("user-in-voice");

    final String mentions =
        VoiceChannelMentionFilter.createCompletionMentions(readyUserIds, mockGuild);

    assertTrue(mentions.isEmpty());
  }

  @Test
  void createCompletionMentions_UsersNotInVoice_ShouldMention() {
    final Set<String> readyUserIds = Set.of("user-not-in-voice");

    final String mentions =
        VoiceChannelMentionFilter.createCompletionMentions(readyUserIds, mockGuild);

    assertTrue(mentions.contains(mockMemberNotInVoice.getAsMention()));
  }

  @Test
  void createCompletionMentions_UsersDeafened_ShouldMention() {
    final Set<String> readyUserIds = Set.of("user-deafened");

    final String mentions =
        VoiceChannelMentionFilter.createCompletionMentions(readyUserIds, mockGuild);

    assertTrue(mentions.contains(mockMemberDeafened.getAsMention()));
  }

  @Test
  void createCompletionMentions_MixedUsers_ShouldOnlyMentionNonActiveVoice() {
    final Set<String> readyUserIds = Set.of("user-in-voice", "user-not-in-voice", "user-deafened");

    final String mentions =
        VoiceChannelMentionFilter.createCompletionMentions(readyUserIds, mockGuild);

    assertFalse(mentions.contains(mockMemberInVoice.getAsMention()));
    assertTrue(mentions.contains(mockMemberNotInVoice.getAsMention()));
    assertTrue(mentions.contains(mockMemberDeafened.getAsMention()));
  }

  @Test
  void createCompletionMentions_UserNotFound_ShouldSkipUser() {
    final Set<String> readyUserIds = Set.of("user-not-found", "user-not-in-voice");

    final String mentions =
        VoiceChannelMentionFilter.createCompletionMentions(readyUserIds, mockGuild);

    assertFalse(mentions.contains("user-not-found"));
    assertTrue(mentions.contains(mockMemberNotInVoice.getAsMention()));
  }

  @Test
  void createCompletionMentions_EmptySet_ShouldReturnEmpty() {
    final Set<String> readyUserIds = Set.of();

    final String mentions =
        VoiceChannelMentionFilter.createCompletionMentions(readyUserIds, mockGuild);

    assertTrue(mentions.isEmpty());
  }

  @Test
  void createCompletionMentions_AllUsersInActiveVoice_ShouldReturnEmpty() {
    final Set<String> readyUserIds = Set.of("user-in-voice");

    final String mentions =
        VoiceChannelMentionFilter.createCompletionMentions(readyUserIds, mockGuild);

    assertTrue(mentions.isEmpty());
  }

  @Test
  void createCompletionMentions_MultipleUsersNotInVoice_ShouldMentionAllWithSpaces() {
    final Set<String> readyUserIds = Set.of("user-not-in-voice", "user-deafened");

    final String mentions =
        VoiceChannelMentionFilter.createCompletionMentions(readyUserIds, mockGuild);

    assertTrue(mentions.contains(mockMemberNotInVoice.getAsMention()));
    assertTrue(mentions.contains(mockMemberDeafened.getAsMention()));
    assertTrue(mentions.contains(" "));
  }

  @Test
  void createCompletionMentions_UserWithNullVoiceState_ShouldMention() {
    final Member memberNullVoice = MockDiscord.createMockMember("user-null-voice", "NullVoice");
    when(memberNullVoice.getVoiceState()).thenReturn(null);
    when(mockGuild.getMemberById("user-null-voice")).thenReturn(memberNullVoice);

    final Set<String> readyUserIds = Set.of("user-null-voice");

    final String mentions =
        VoiceChannelMentionFilter.createCompletionMentions(readyUserIds, mockGuild);

    assertTrue(mentions.contains(memberNullVoice.getAsMention()));
  }

  @Test
  void createCompletionMentions_UserInVoiceButNotDeafened_ShouldNotMention() {
    setupMemberVoiceState(mockMemberInVoice, true, false);
    final Set<String> readyUserIds = Set.of("user-in-voice");

    final String mentions =
        VoiceChannelMentionFilter.createCompletionMentions(readyUserIds, mockGuild);

    assertTrue(mentions.isEmpty());
  }

  @Test
  void createCompletionMentions_UserInVoiceAndDeafened_ShouldMention() {
    setupMemberVoiceState(mockMemberDeafened, true, true);
    final Set<String> readyUserIds = Set.of("user-deafened");

    final String mentions =
        VoiceChannelMentionFilter.createCompletionMentions(readyUserIds, mockGuild);

    assertTrue(mentions.contains(mockMemberDeafened.getAsMention()));
  }

  private void setupMemberVoiceState(
      final Member member, final boolean inVoice, final boolean deafened) {
    final Guild.VoiceState voiceState = mock(Guild.VoiceState.class);
    when(member.getVoiceState()).thenReturn(voiceState);
    when(voiceState.inAudioChannel()).thenReturn(inVoice);
    when(voiceState.isDeafened()).thenReturn(deafened);
  }
}
