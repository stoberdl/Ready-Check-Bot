package com.projects.testutils;

import static org.mockito.Mockito.*;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.components.LayoutComponent;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.MessageEditAction;

public final class MockDiscord {

  private MockDiscord() {}

  public static JDA createMockJDA() {
    return mock(JDA.class);
  }

  public static Guild createMockGuild(final String guildId) {
    final Guild guild = mock(Guild.class);
    when(guild.getId()).thenReturn(guildId);
    when(guild.getName()).thenReturn("Test Guild");
    return guild;
  }

  public static Member createMockMember(final String userId, final String displayName) {
    final Member member = mock(Member.class);
    final User user = mock(User.class);

    when(user.getId()).thenReturn(userId);
    when(user.isBot()).thenReturn(false);
    when(member.getUser()).thenReturn(user);
    when(member.getId()).thenReturn(userId);
    when(member.getEffectiveName()).thenReturn(displayName);
    when(member.getAsMention()).thenReturn("<@" + userId + ">");
    when(member.getVoiceState()).thenReturn(null);

    return member;
  }

  public static Member createMockBotMember(final String userId, final String displayName) {
    final Member member = createMockMember(userId, displayName);
    when(member.getUser().isBot()).thenReturn(true);
    return member;
  }

  public static TextChannel createMockTextChannel(final String channelId) {
    final TextChannel channel = mock(TextChannel.class);
    when(channel.getId()).thenReturn(channelId);
    when(channel.getName()).thenReturn("test-channel");

    final MessageCreateAction createAction = mock(MessageCreateAction.class);
    when(channel.sendMessage(anyString())).thenReturn(createAction);
    when(createAction.setEmbeds((Collection<? extends MessageEmbed>) any()))
        .thenReturn(createAction);
    when(createAction.setComponents((Collection<? extends LayoutComponent>) any()))
        .thenReturn(createAction);
    when(createAction.queue(any())).thenReturn(CompletableFuture.completedFuture(null));

    final RestAction<Message> retrieveAction = mock(RestAction.class);
    when(channel.retrieveMessageById(anyString())).thenReturn(retrieveAction);

    return channel;
  }

  public static Role createMockRole(final String roleId, final String roleName) {
    final Role role = mock(Role.class);
    when(role.getId()).thenReturn(roleId);
    when(role.getName()).thenReturn(roleName);
    when(role.getAsMention()).thenReturn("<@&" + roleId + ">");
    return role;
  }

  public static Message createMockMessage(final String messageId) {
    final Message message = mock(Message.class);
    when(message.getId()).thenReturn(messageId);

    final MessageEditAction editAction = mock(MessageEditAction.class);
    when(message.editMessageEmbeds((Collection<? extends MessageEmbed>) any()))
        .thenReturn(editAction);
    when(editAction.setComponents((Collection<? extends LayoutComponent>) any()))
        .thenReturn(editAction);

    final RestAction<Void> deleteAction = mock(RestAction.class);
    when(message.delete()).thenReturn((AuditableRestAction<Void>) deleteAction);

    return message;
  }

  public static void setupGuildWithMembers(final Guild guild, final Member... members) {
    for (final Member member : members) {
      when(guild.getMemberById(member.getId())).thenReturn(member);
    }
  }

  public static void setupGuildWithRoles(final Guild guild, final Role... roles) {
    for (final Role role : roles) {
      when(guild.getRoleById(role.getId())).thenReturn(role);
      when(guild.getMembersWithRoles(role)).thenReturn(List.of());
    }
  }

  public static void setupGuildWithRoleMembers(
      final Guild guild, final Role role, final List<Member> members) {
    when(guild.getRoleById(role.getId())).thenReturn(role);
    when(guild.getMembersWithRoles(role)).thenReturn(members);
  }

  public static void setupGuildWithChannel(final Guild guild, final TextChannel channel) {
    when(guild.getTextChannelById(channel.getId())).thenReturn(channel);
  }

  public static void setupJDAWithGuild(final JDA jda, final Guild guild) {
    when(jda.getGuildById(guild.getId())).thenReturn(guild);
  }

  public static void setupMessageRetrieval(
      final TextChannel channel, final String messageId, final Message message) {
    final RestAction<Message> retrieveAction = mock(RestAction.class);
    when(channel.retrieveMessageById(messageId)).thenReturn(retrieveAction);
    when(retrieveAction.queue(any(), any()))
        .thenAnswer(
            invocation -> {
              final var successCallback =
                  invocation.getArgument(0, java.util.function.Consumer.class);
              successCallback.accept(message);
              return null;
            });
  }

  public static void setupMessageRetrievalFailure(
      final TextChannel channel, final String messageId) {
    final RestAction<Message> retrieveAction = mock(RestAction.class);
    when(channel.retrieveMessageById(messageId)).thenReturn(retrieveAction);
    when(retrieveAction.queue(any(), any()))
        .thenAnswer(
            invocation -> {
              final var failureCallback =
                  invocation.getArgument(1, java.util.function.Consumer.class);
              failureCallback.accept(new RuntimeException("Message not found"));
              return null;
            });
  }
}
