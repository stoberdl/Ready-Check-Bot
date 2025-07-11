package com.projects.commands;

import com.projects.readycheck.ReadyCheckManager;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;

public final class RCommand implements Command {

  @Override
  public String getName() {
    return "r";
  }

  @Override
  public String getDescription() {
    return "Start a ready check using saved configurations";
  }

  @Override
  public void executeSlash(final SlashCommandInteractionEvent event) {
    final String guildId = event.getGuild().getId();
    final Member initiator = event.getMember();

    final List<ReadyCheckManager.SavedReadyCheck> savedChecks =
        ReadyCheckManager.getSavedReadyChecks(guildId);

    if (savedChecks.isEmpty()) {
      sendNoConfigurationsReply(event);
      return;
    }

    if (savedChecks.size() == 1) {
      handleSingleSavedCheck(event, savedChecks.getFirst(), initiator, guildId);
    } else {
      showSavedConfigurationMenu(event, savedChecks);
    }
  }

  private void sendNoConfigurationsReply(final SlashCommandInteractionEvent event) {
    event
        .reply(
            "No saved ready check configurations found! Use `/ready` and click '💾' to create"
                + " one.")
        .setEphemeral(true)
        .queue();
  }

  private void handleSingleSavedCheck(
      final SlashCommandInteractionEvent event,
      final ReadyCheckManager.SavedReadyCheck savedCheck,
      final Member initiator,
      final String guildId) {

    final String existingCheckId =
        ReadyCheckManager.findExistingReadyCheck(guildId, savedCheck, initiator.getId());

    if (existingCheckId != null && ReadyCheckManager.isReadyCheckOngoing(existingCheckId)) {
      handleExistingReadyCheck(event, existingCheckId);
      return;
    }

    startReadyCheckFromSaved(event, savedCheck, initiator);
  }

  private void handleExistingReadyCheck(
      final SlashCommandInteractionEvent event, final String existingCheckId) {
    ReadyCheckManager.resendExistingReadyCheck(existingCheckId, event.getJDA());
    event
        .reply(
            "♻️ Found an existing ready check with the same members! Refreshing that one"
                + " instead.")
        .setEphemeral(true)
        .queue();
  }

  private void showSavedConfigurationMenu(
      final SlashCommandInteractionEvent event,
      final List<ReadyCheckManager.SavedReadyCheck> savedChecks) {
    final StringSelectMenu.Builder menuBuilder =
        StringSelectMenu.create("select_saved_ready")
            .setPlaceholder("Choose a saved ready check configuration...");

    final Set<String> usedValues = new HashSet<>();
    int userGroupCounter = 1;

    for (final ReadyCheckManager.SavedReadyCheck savedCheck : savedChecks) {
      if (savedCheck.isUserBased()) {
        userGroupCounter =
            addUserBasedOption(event, savedCheck, menuBuilder, usedValues, userGroupCounter);
      } else {
        addRoleBasedOption(event, savedCheck, menuBuilder, usedValues);
      }
    }

    event
        .reply("Select a saved configuration:")
        .addActionRow(menuBuilder.build())
        .setEphemeral(true)
        .queue();
  }

  private int addUserBasedOption(
      final SlashCommandInteractionEvent event,
      final ReadyCheckManager.SavedReadyCheck savedCheck,
      final StringSelectMenu.Builder menuBuilder,
      final Set<String> usedValues,
      int userGroupCounter) {

    final String userNames = getUserNamesFromIds(event, savedCheck.getUserIds());
    String uniqueValue = "users_" + savedCheck.getUserIds().hashCode() + "_" + userGroupCounter;

    while (usedValues.contains(uniqueValue)) {
      userGroupCounter++;
      uniqueValue = "users_" + savedCheck.getUserIds().hashCode() + "_" + userGroupCounter;
    }

    usedValues.add(uniqueValue);
    final String mentionText = savedCheck.getMentionPeople() ? "mentions users" : "no mentions";
    menuBuilder.addOption("Users: " + userNames, uniqueValue, mentionText);

    return userGroupCounter + 1;
  }

  private void addRoleBasedOption(
      final SlashCommandInteractionEvent event,
      final ReadyCheckManager.SavedReadyCheck savedCheck,
      final StringSelectMenu.Builder menuBuilder,
      final Set<String> usedValues) {

    final Role role = event.getGuild().getRoleById(savedCheck.getRoleId());
    final String roleName = role != null ? role.getName() : "Unknown Role";
    final String roleValue = savedCheck.getRoleId();

    if (!usedValues.contains(roleValue)) {
      usedValues.add(roleValue);
      final String mentionText = savedCheck.getMentionPeople() ? "mentions users" : "no mentions";
      menuBuilder.addOption("Role: " + roleName, roleValue, mentionText);
    }
  }

  private String getUserNamesFromIds(
      final SlashCommandInteractionEvent event, final List<String> userIds) {
    final String names =
        userIds.stream()
            .map(
                userId -> {
                  final Member member = event.getGuild().getMemberById(userId);
                  return member != null ? member.getEffectiveName() : "Unknown";
                })
            .limit(3)
            .collect(Collectors.joining(", "));

    return userIds.size() > 3 ? names + " + " + (userIds.size() - 3) + " more" : names;
  }

  private void startReadyCheckFromSaved(
      final SlashCommandInteractionEvent event,
      final ReadyCheckManager.SavedReadyCheck savedCheck,
      final Member initiator) {
    if (savedCheck.isUserBased()) {
      handleUserBasedSavedCheck(event, savedCheck, initiator);
    } else {
      handleRoleBasedSavedCheck(event, savedCheck, initiator);
    }
  }

  private void handleUserBasedSavedCheck(
      final SlashCommandInteractionEvent event,
      final ReadyCheckManager.SavedReadyCheck savedCheck,
      final Member initiator) {
    final List<Member> targetMembers =
        getValidMembersFromIds(event, savedCheck.getUserIds(), initiator.getId());

    if (targetMembers.isEmpty()) {
      sendNoMembersFoundReply(event);
      return;
    }

    final String readyCheckId = createUserReadyCheck(event, initiator, targetMembers);
    configureReadyCheck(readyCheckId, savedCheck);
    sendUserBasedResponse(event, readyCheckId, targetMembers, initiator);
  }

  private void handleRoleBasedSavedCheck(
      final SlashCommandInteractionEvent event,
      final ReadyCheckManager.SavedReadyCheck savedCheck,
      final Member initiator) {
    final Role targetRole = event.getGuild().getRoleById(savedCheck.getRoleId());
    if (targetRole == null) {
      sendRoleNotFoundReply(event);
      return;
    }

    final List<Member> targetMembers = getMembersWithRole(event, targetRole, initiator);
    if (targetMembers.isEmpty()) {
      sendNoRoleMembersReply(event, targetRole);
      return;
    }

    final String readyCheckId = createRoleReadyCheck(event, initiator, targetRole, targetMembers);
    configureReadyCheck(readyCheckId, savedCheck);
    sendRoleBasedResponse(event, readyCheckId, targetMembers, initiator, targetRole);
  }

  private void sendNoMembersFoundReply(final SlashCommandInteractionEvent event) {
    event
        .reply("No other saved users found or they are no longer in the server!")
        .setEphemeral(true)
        .queue();
  }

  private void sendRoleNotFoundReply(final SlashCommandInteractionEvent event) {
    event.reply("The saved role no longer exists!").setEphemeral(true).queue();
  }

  private void sendNoRoleMembersReply(
      final SlashCommandInteractionEvent event, final Role targetRole) {
    event
        .reply("No other members found with the role: " + targetRole.getAsMention())
        .setEphemeral(true)
        .queue();
  }

  private String createUserReadyCheck(
      final SlashCommandInteractionEvent event,
      final Member initiator,
      final List<Member> targetMembers) {
    return ReadyCheckManager.createUserReadyCheck(
        event.getGuild().getId(), event.getChannel().getId(), initiator.getId(), targetMembers);
  }

  private String createRoleReadyCheck(
      final SlashCommandInteractionEvent event,
      final Member initiator,
      final Role targetRole,
      final List<Member> targetMembers) {
    return ReadyCheckManager.createReadyCheck(
        event.getGuild().getId(),
        event.getChannel().getId(),
        initiator.getId(),
        targetRole.getId(),
        targetMembers);
  }

  private void configureReadyCheck(
      final String readyCheckId, final ReadyCheckManager.SavedReadyCheck savedCheck) {
    ReadyCheckManager.setMentionPreference(readyCheckId, savedCheck.getMentionPeople());
  }

  private void sendUserBasedResponse(
      final SlashCommandInteractionEvent event,
      final String readyCheckId,
      final List<Member> targetMembers,
      final Member initiator) {
    ReadyCheckManager.createReadyCheckResponse(
        event,
        readyCheckId,
        targetMembers,
        initiator,
        "**" + initiator.getEffectiveName() + "** started a ready check for specific users");
  }

  private void sendRoleBasedResponse(
      final SlashCommandInteractionEvent event,
      final String readyCheckId,
      final List<Member> targetMembers,
      final Member initiator,
      final Role targetRole) {
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

  private List<Member> getMembersWithRole(
      final SlashCommandInteractionEvent event, final Role targetRole, final Member initiator) {
    return event.getGuild().getMembersWithRoles(targetRole).stream()
        .filter(member -> !member.equals(initiator))
        .toList();
  }

  private List<Member> getValidMembersFromIds(
      final SlashCommandInteractionEvent event,
      final List<String> userIds,
      final String initiatorId) {
    return userIds.stream()
        .filter(userId -> !userId.equals(initiatorId))
        .map(userId -> event.getGuild().getMemberById(userId))
        .filter(Objects::nonNull)
        .toList();
  }
}
