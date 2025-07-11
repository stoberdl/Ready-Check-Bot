package com.projects.readycheck.utils;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;

public final class VoiceChannelMentionFilter {

  private VoiceChannelMentionFilter() {}

  public static String createCompletionMentions(final Set<String> readyUserIds, final Guild guild) {
    return readyUserIds.stream()
        .map(userId -> createMentionIfNeeded(userId, guild))
        .filter(Objects::nonNull)
        .collect(Collectors.joining(" "));
  }

  private static String createMentionIfNeeded(final String userId, final Guild guild) {
    final Member member = guild.getMemberById(userId);
    if (member == null) {
      return null;
    }

    return shouldMentionForCompletion(member) ? member.getAsMention() : null;
  }

  private static boolean shouldMentionForCompletion(final Member member) {
    return !isActiveInVoiceChannel(member);
  }

  private static boolean isActiveInVoiceChannel(final Member member) {
    final var voiceState = member.getVoiceState();
    return voiceState != null && voiceState.inAudioChannel() && !voiceState.isDeafened();
  }
}
