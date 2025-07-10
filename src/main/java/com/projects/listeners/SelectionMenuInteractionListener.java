package com.projects.listeners;

import com.projects.managers.ReadyCheckManager;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class SelectionMenuInteractionListener extends ListenerAdapter {

  @Override
  public void onStringSelectInteraction(StringSelectInteractionEvent event) {
    String menuId = event.getComponentId();

    if (menuId.startsWith("select_saved_ready")) {
      handleSavedReadySelection(event);
    }
  }

  private void handleSavedReadySelection(StringSelectInteractionEvent event) {
    String selectedValue = event.getValues().getFirst();
    Member initiator = event.getMember();
    TextChannel channel = event.getChannel().asTextChannel();
    String guildId = Objects.requireNonNull(event.getGuild()).getId();

    // Extract mentionPeople parameter from component ID
    String componentId = event.getComponentId();
    boolean mentionPeople = !componentId.endsWith("_false");

    if (selectedValue.startsWith("users_")) {
      handleUserBasedSelection(
          event, selectedValue, initiator, channel, guildId, mentionPeople, componentId);
    } else {
      handleRoleBasedSelection(
          event, selectedValue, initiator, channel, guildId, mentionPeople, componentId);
    }
  }

  private void handleUserBasedSelection(
      StringSelectInteractionEvent event,
      String selectedValue,
      Member initiator,
      TextChannel channel,
      String guildId,
      boolean mentionPeople,
      String componentId) {
    // Extract hash code from value like "users_12345_1"
    String[] parts = selectedValue.split("_");
    if (parts.length < 2) {
      event.reply("The selected saved configuration is invalid!").setEphemeral(true).queue();
      return;
    }

    String hashCode = parts[1];

    ReadyCheckManager.SavedReadyCheck savedCheck = findSavedCheckByHashCode(guildId, hashCode);
    if (savedCheck == null) {
      event.reply("The selected saved configuration no longer exists!").setEphemeral(true).queue();
      return;
    }

    // Check for existing ready check
    String existingCheckId =
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

    List<Member> targetMembers =
        getValidMembersFromUserIds(event, savedCheck.getUserIds(), initiator.getId());
    if (targetMembers.isEmpty()) {
      event
          .reply("No other saved users found or they are no longer in the server!")
          .setEphemeral(true)
          .queue();
      return;
    }

    String readyCheckId =
        ReadyCheckManager.createUserReadyCheck(
            guildId, channel.getId(), initiator.getId(), targetMembers);

    // For /ready command menu selections, use the command's mention preference
    // For /r command menu selections, use the saved mention preference
    boolean useMentions =
        componentId.equals("select_saved_ready") ? savedCheck.getMentionPeople() : mentionPeople;
    ReadyCheckManager.setMentionPreference(readyCheckId, useMentions);

    ReadyCheckManager.createReadyCheckResponse(
        event,
        readyCheckId,
        targetMembers,
        initiator,
        "**" + initiator.getEffectiveName() + "** started a ready check for specific users");
  }

  private void handleRoleBasedSelection(
      StringSelectInteractionEvent event,
      String selectedRoleId,
      Member initiator,
      TextChannel channel,
      String guildId,
      boolean mentionPeople,
      String componentId) {
    Role targetRole = event.getGuild().getRoleById(selectedRoleId);
    if (targetRole == null) {
      event.reply("The selected role no longer exists!").setEphemeral(true).queue();
      return;
    }

    // Find the saved check to get the mention preference
    ReadyCheckManager.SavedReadyCheck savedCheck =
        ReadyCheckManager.getSavedReadyChecks(guildId).stream()
            .filter(check -> !check.isUserBased() && selectedRoleId.equals(check.getRoleId()))
            .findFirst()
            .orElse(null);

    List<Member> targetMembers =
        event.getGuild().getMembersWithRoles(targetRole).stream()
            .filter(member -> !member.equals(initiator))
            .collect(Collectors.toList());

    if (targetMembers.isEmpty()) {
      event
          .reply("No other members found with the role: " + targetRole.getAsMention())
          .setEphemeral(true)
          .queue();
      return;
    }

    String readyCheckId =
        ReadyCheckManager.createReadyCheck(
            guildId, channel.getId(), initiator.getId(), targetRole.getId(), targetMembers);

    // For /ready command menu selections, use the command's mention preference
    // For /r command menu selections, use the saved mention preference
    boolean useMentions =
        componentId.equals("select_saved_ready") && savedCheck != null
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
      String guildId, String hashCode) {
    return ReadyCheckManager.getSavedReadyChecks(guildId).stream()
        .filter(
            check ->
                check.isUserBased()
                    && String.valueOf(check.getUserIds().hashCode()).equals(hashCode))
        .findFirst()
        .orElse(null);
  }

  private List<Member> getValidMembersFromUserIds(
      StringSelectInteractionEvent event, List<String> userIds, String initiatorId) {
    return userIds.stream()
        .filter(userId -> !userId.equals(initiatorId))
        .map(userId -> event.getGuild().getMemberById(userId))
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }
}
