package com.projects.listeners;

import com.projects.readycheck.ReadyCheckManager;
import java.util.List;
import java.util.Objects;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public final class SelectionMenuInteractionListener extends ListenerAdapter {
  private static final String SELECT_SAVED_READY = "select_saved_ready";

  @Override
  public void onStringSelectInteraction(final StringSelectInteractionEvent event) {
    final String menuId = event.getComponentId();

    if (menuId.startsWith(SELECT_SAVED_READY)) {
      handleSavedReadySelection(event);
    }
  }

  private void handleSavedReadySelection(final StringSelectInteractionEvent event) {
    final String selectedValue = event.getValues().getFirst();
    final Member initiator = event.getMember();
    final TextChannel channel = event.getChannel().asTextChannel();
    final String guildId = Objects.requireNonNull(event.getGuild()).getId();

    final String componentId = event.getComponentId();
    final boolean mentionPeople = !componentId.endsWith("_false");

    if (selectedValue.startsWith("users_")) {
      handleUserBasedSelection(
          event, selectedValue, initiator, channel, guildId, mentionPeople, componentId);
    } else {
      handleRoleBasedSelection(
          event, selectedValue, initiator, channel, guildId, mentionPeople, componentId);
    }
  }

  private void handleUserBasedSelection(
      final StringSelectInteractionEvent event,
      final String selectedValue,
      final Member initiator,
      final TextChannel channel,
      final String guildId,
      final boolean mentionPeople,
      final String componentId) {
    final String[] parts = selectedValue.split("_");
    if (parts.length < 2) {
      event.reply("The selected saved configuration is invalid!").setEphemeral(true).queue();
      return;
    }

    final String hashCode = parts[1];

    final ReadyCheckManager.SavedReadyCheck savedCheck =
        findSavedCheckByHashCode(guildId, hashCode);
    if (savedCheck == null) {
      event.reply("The selected saved configuration no longer exists!").setEphemeral(true).queue();
      return;
    }

    final String existingCheckId =
        ReadyCheckManager.findExistingReadyCheck(guildId, savedCheck, initiator.getId());
    if (existingCheckId != null && ReadyCheckManager.isReadyCheckOngoing(existingCheckId)) {
      ReadyCheckManager.resendExistingReadyCheck(existingCheckId, event.getJDA());
      event
          .reply(
              "♻️ Found an existing ready check with the same members! Refreshing that one"
                  + " instead.")
          .setEphemeral(true)
          .queue();
      return;
    }

    final List<Member> targetMembers =
        getValidMembersFromUserIds(event, savedCheck.getUserIds(), initiator.getId());
    if (targetMembers.isEmpty()) {
      event
          .reply("No other saved users found or they are no longer in the server!")
          .setEphemeral(true)
          .queue();
      return;
    }

    final String readyCheckId =
        ReadyCheckManager.createUserReadyCheck(
            guildId, channel.getId(), initiator.getId(), targetMembers);

    final boolean useMentions =
        componentId.equals(SELECT_SAVED_READY) ? savedCheck.getMentionPeople() : mentionPeople;
    ReadyCheckManager.setMentionPreference(readyCheckId, useMentions);

    ReadyCheckManager.createReadyCheckResponse(
        event,
        readyCheckId,
        targetMembers,
        initiator,
        "**" + initiator.getEffectiveName() + "** started a ready check for specific users");
  }

  private void handleRoleBasedSelection(
      final StringSelectInteractionEvent event,
      final String selectedRoleId,
      final Member initiator,
      final TextChannel channel,
      final String guildId,
      final boolean mentionPeople,
      final String componentId) {
    final Role targetRole = event.getGuild().getRoleById(selectedRoleId);
    if (targetRole == null) {
      event.reply("The selected role no longer exists!").setEphemeral(true).queue();
      return;
    }

    final ReadyCheckManager.SavedReadyCheck savedCheck =
        ReadyCheckManager.getSavedReadyChecks(guildId).stream()
            .filter(check -> !check.isUserBased() && selectedRoleId.equals(check.getRoleId()))
            .findFirst()
            .orElse(null);

    final List<Member> targetMembers =
        event.getGuild().getMembersWithRoles(targetRole).stream()
            .filter(member -> !member.equals(initiator))
            .toList();

    if (targetMembers.isEmpty()) {
      event
          .reply("No other members found with the role: " + targetRole.getAsMention())
          .setEphemeral(true)
          .queue();
      return;
    }

    final String readyCheckId =
        ReadyCheckManager.createReadyCheck(
            guildId, channel.getId(), initiator.getId(), targetRole.getId(), targetMembers);

    final boolean useMentions =
        componentId.equals(SELECT_SAVED_READY) && savedCheck != null
            ? savedCheck.getMentionPeople()
            : mentionPeople;
    ReadyCheckManager.setMentionPreference(readyCheckId, useMentions);

    ReadyCheckManager.createReadyCheckResponse(
        event,
        readyCheckId,
        targetMembers,
        initiator,
        "**"
            + initiator.getEffectiveName()
            + "** started a ready check for "
            + targetRole.getAsMention());
  }

  private ReadyCheckManager.SavedReadyCheck findSavedCheckByHashCode(
      final String guildId, final String hashCode) {
    return ReadyCheckManager.getSavedReadyChecks(guildId).stream()
        .filter(
            check ->
                check.isUserBased()
                    && String.valueOf(check.getUserIds().hashCode()).equals(hashCode))
        .findFirst()
        .orElse(null);
  }

  private List<Member> getValidMembersFromUserIds(
      final StringSelectInteractionEvent event,
      final List<String> userIds,
      final String initiatorId) {
    return userIds.stream()
        .filter(userId -> !userId.equals(initiatorId))
        .map(userId -> event.getGuild().getMemberById(userId))
        .filter(Objects::nonNull)
        .toList();
  }
}
