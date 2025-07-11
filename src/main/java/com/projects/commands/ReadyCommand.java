package com.projects.commands;

import com.projects.readycheck.ReadyCheckManager;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;

public final class ReadyCommand implements Command {

  @Override
  public String getName() {
    return "ready";
  }

  @Override
  public String getDescription() {
    return "Start a ready check: /ready targets:@GameRole @Alice @Bob";
  }

  @Override
  public void executeSlash(final SlashCommandInteractionEvent event) {
    final String guildId = event.getGuild().getId();
    final Member initiator = event.getMember();
    final TextChannel channel = event.getChannel().asTextChannel();

    final OptionMapping targetsOption = event.getOption("targets");
    final boolean mentionPeople = event.getOption("people", true, OptionMapping::getAsBoolean);

    if (targetsOption == null) {
      handleSavedReadyCheck(event, guildId, initiator, channel, mentionPeople);
      return;
    }

    final String targetsInput = targetsOption.getAsString();
    handleMixedTargetsReadyCheck(event, targetsInput, initiator, channel, guildId, mentionPeople);
  }

  private void handleMixedTargetsReadyCheck(
      final SlashCommandInteractionEvent event,
      final String targetsInput,
      final Member initiator,
      final TextChannel channel,
      final String guildId,
      final boolean mentionPeople) {

    final ParsedTargets parsed = parseTargetsInput(event, targetsInput);

    if (parsed.getAllMembers().isEmpty()) {
      sendNoValidTargetsReply(event);
      return;
    }

    final List<Member> targetMembers = getFilteredTargetMembers(parsed, initiator);
    if (targetMembers.isEmpty()) {
      sendSelfOnlyReply(event);
      return;
    }

    final String existingCheckId = findExistingReadyCheck(guildId, targetMembers, initiator);
    if (existingCheckId != null) {
      handleExistingCheck(event, existingCheckId);
      return;
    }

    createNewReadyCheck(event, targetMembers, initiator, guildId, channel, mentionPeople, parsed);
  }

  private void sendNoValidTargetsReply(final SlashCommandInteractionEvent event) {
    event
        .reply(
            """
            ❌ No valid targets found! Examples:
            • `@GameRole` - Ready check for a role
            • `@Alice @Bob @Charlie` - Ready check for specific users
            • `@GameRole @Alice @Bob` - Ready check for role + additional users\
            """)
        .setEphemeral(true)
        .queue();
  }

  private void sendSelfOnlyReply(final SlashCommandInteractionEvent event) {
    event.reply("❌ You can't start a ready check with only yourself!").setEphemeral(true).queue();
  }

  private List<Member> getFilteredTargetMembers(
      final ParsedTargets parsed, final Member initiator) {
    return parsed.getAllMembers().stream().filter(member -> !member.equals(initiator)).toList();
  }

  private String findExistingReadyCheck(
      final String guildId, final List<Member> targetMembers, final Member initiator) {
    final List<String> targetUserIds = targetMembers.stream().map(Member::getId).toList();
    return ReadyCheckManager.findExistingReadyCheck(guildId, targetUserIds, initiator.getId());
  }

  private void handleExistingCheck(
      final SlashCommandInteractionEvent event, final String existingCheckId) {
    ReadyCheckManager.resendExistingReadyCheck(existingCheckId, event.getJDA());
    event
        .reply(
            "♻️ Found an existing ready check with the same members! Refreshing that one"
                + " instead.")
        .setEphemeral(true)
        .queue();
  }

  private void createNewReadyCheck(
      final SlashCommandInteractionEvent event,
      final List<Member> targetMembers,
      final Member initiator,
      final String guildId,
      final TextChannel channel,
      final boolean mentionPeople,
      final ParsedTargets parsed) {

    final String readyCheckId =
        ReadyCheckManager.createUserReadyCheck(
            guildId, channel.getId(), initiator.getId(), targetMembers);

    ReadyCheckManager.setMentionPreference(readyCheckId, mentionPeople);

    final String description = buildDescription(initiator, parsed);

    ReadyCheckManager.createReadyCheckResponse(
        event, readyCheckId, targetMembers, initiator, description);
  }

  private ParsedTargets parseTargetsInput(
      final SlashCommandInteractionEvent event, final String input) {
    final ParsedTargets result = new ParsedTargets();

    parseRoleMentions(event, input, result);
    parseUserMentions(event, input, result);

    if (result.getAllMembers().isEmpty() && result.getRoles().isEmpty()) {
      parseByNames(event, input, result);
    }

    return result;
  }

  private void parseRoleMentions(
      final SlashCommandInteractionEvent event, final String input, final ParsedTargets result) {
    final Pattern roleMentionPattern = Pattern.compile("<@&(\\d+)>");
    final Matcher roleMatcher = roleMentionPattern.matcher(input);

    while (roleMatcher.find()) {
      final String roleId = roleMatcher.group(1);
      final Role role = event.getGuild().getRoleById(roleId);
      if (role != null) {
        result.addRole(role);
        final List<Member> roleMembers = event.getGuild().getMembersWithRoles(role);
        result.addMembers(roleMembers);
      }
    }
  }

  private void parseUserMentions(
      final SlashCommandInteractionEvent event, final String input, final ParsedTargets result) {
    final Pattern userMentionPattern = Pattern.compile("<@!?(\\d+)>");
    final Matcher userMatcher = userMentionPattern.matcher(input);

    while (userMatcher.find()) {
      final String userId = userMatcher.group(1);
      final Member member = event.getGuild().getMemberById(userId);
      if (member != null) {
        result.addMember(member);
      }
    }
  }

  private void parseByNames(
      final SlashCommandInteractionEvent event, final String input, final ParsedTargets result) {
    final String[] parts = input.split("\\s+");
    for (final String part : parts) {
      final String trimmedPart = part.trim();
      if (!trimmedPart.isEmpty()) {
        final List<Member> foundMembers =
            event.getGuild().getMembersByEffectiveName(trimmedPart, true);
        if (!foundMembers.isEmpty()) {
          result.addMember(foundMembers.getFirst());
        }
      }
    }
  }

  private String buildDescription(final Member initiator, final ParsedTargets parsed) {
    final StringBuilder desc =
        new StringBuilder("**" + initiator.getEffectiveName() + "** started a ready check");

    final List<String> parts = new ArrayList<>();

    if (!parsed.getRoles().isEmpty()) {
      final String roleNames =
          parsed.getRoles().stream()
              .map(Role::getAsMention)
              .collect(java.util.stream.Collectors.joining(", "));
      parts.add("for " + roleNames);
    }

    if (!parsed.getDirectUsers().isEmpty()) {
      if (parts.isEmpty()) {
        parts.add("for specific users");
      } else {
        parts.add("+ specific users");
      }
    }

    if (!parts.isEmpty()) {
      desc.append(" ").append(String.join(" ", parts));
    }

    return desc.toString();
  }

  private static final class ParsedTargets {
    private final Set<Role> roles = new HashSet<>();
    private final Set<Member> directUsers = new HashSet<>();
    private final Set<Member> allMembers = new HashSet<>();

    public void addRole(final Role role) {
      roles.add(role);
    }

    public void addMember(final Member member) {
      directUsers.add(member);
      allMembers.add(member);
    }

    public void addMembers(final List<Member> members) {
      allMembers.addAll(members);
    }

    public Set<Role> getRoles() {
      return roles;
    }

    public Set<Member> getDirectUsers() {
      return directUsers;
    }

    public List<Member> getAllMembers() {
      return new ArrayList<>(allMembers);
    }
  }

  private void handleSavedReadyCheck(
      final SlashCommandInteractionEvent event,
      final String guildId,
      final Member initiator,
      final TextChannel channel,
      final boolean mentionPeople) {

    final List<ReadyCheckManager.SavedReadyCheck> savedChecks =
        ReadyCheckManager.getSavedReadyChecks(guildId);

    if (savedChecks.isEmpty()) {
      sendNoSavedConfigsReply(event);
      return;
    }

    if (savedChecks.size() == 1) {
      startReadyCheckFromSaved(event, savedChecks.getFirst(), initiator, channel, mentionPeople);
    } else {
      showSavedConfigMenu(event, savedChecks, mentionPeople);
    }
  }

  private void sendNoSavedConfigsReply(final SlashCommandInteractionEvent event) {
    event
        .reply(
            """
            No saved ready check configurations found!
            **Usage:**
            • `/ready targets:@RoleName` - Ready check for a role
            • `/ready targets:@user1 @user2` - Ready check for specific users
            • Use 'Save for Later' button to save configurations\
            """)
        .setEphemeral(true)
        .queue();
  }

  private void showSavedConfigMenu(
      final SlashCommandInteractionEvent event,
      final List<ReadyCheckManager.SavedReadyCheck> savedChecks,
      final boolean mentionPeople) {

    final StringSelectMenu.Builder menuBuilder =
        StringSelectMenu.create("select_saved_ready_" + mentionPeople)
            .setPlaceholder("Choose a saved ready check configuration...");

    for (final ReadyCheckManager.SavedReadyCheck savedCheck : savedChecks) {
      addMenuOption(event, savedCheck, menuBuilder);
    }

    event
        .reply("Select a saved ready check configuration:")
        .addActionRow(menuBuilder.build())
        .setEphemeral(true)
        .queue();
  }

  private void addMenuOption(
      final SlashCommandInteractionEvent event,
      final ReadyCheckManager.SavedReadyCheck savedCheck,
      final StringSelectMenu.Builder menuBuilder) {

    if (savedCheck.isUserBased()) {
      menuBuilder.addOption(
          "User Group (" + savedCheck.getUserIds().size() + " users)",
          "users_" + savedCheck.getUserIds().hashCode(),
          "Ready check for specific users");
    } else {
      final Role role = event.getGuild().getRoleById(savedCheck.getRoleId());
      final String roleName = role != null ? role.getName() : "Unknown Role";
      menuBuilder.addOption(roleName, savedCheck.getRoleId(), "Ready check for " + roleName);
    }
  }

  private void startReadyCheckFromSaved(
      final SlashCommandInteractionEvent event,
      final ReadyCheckManager.SavedReadyCheck savedCheck,
      final Member initiator,
      final TextChannel channel,
      final boolean mentionPeople) {

    if (savedCheck.isUserBased()) {
      handleUserBasedSavedCheck(event, savedCheck, initiator, channel, mentionPeople);
    } else {
      handleRoleBasedSavedCheck(event, savedCheck, initiator, channel, mentionPeople);
    }
  }

  private void handleUserBasedSavedCheck(
      final SlashCommandInteractionEvent event,
      final ReadyCheckManager.SavedReadyCheck savedCheck,
      final Member initiator,
      final TextChannel channel,
      final boolean mentionPeople) {

    final List<Member> targetMembers = getValidMembersFromSaved(event, savedCheck, initiator);
    if (targetMembers.isEmpty()) {
      sendNoSavedUsersReply(event);
      return;
    }

    createUserBasedReadyCheck(event, targetMembers, initiator, channel, mentionPeople);
  }

  private void handleRoleBasedSavedCheck(
      final SlashCommandInteractionEvent event,
      final ReadyCheckManager.SavedReadyCheck savedCheck,
      final Member initiator,
      final TextChannel channel,
      final boolean mentionPeople) {

    final Role targetRole = event.getGuild().getRoleById(savedCheck.getRoleId());
    if (targetRole == null) {
      sendRoleNotExistsReply(event);
      return;
    }

    final List<Member> targetMembers = getRoleMembers(event, targetRole, initiator);
    if (targetMembers.isEmpty()) {
      sendNoRoleMembersReply(event, targetRole);
      return;
    }

    createRoleBasedReadyCheck(event, targetRole, targetMembers, initiator, channel, mentionPeople);
  }

  private List<Member> getValidMembersFromSaved(
      final SlashCommandInteractionEvent event,
      final ReadyCheckManager.SavedReadyCheck savedCheck,
      final Member initiator) {

    final List<Member> targetMembers = new ArrayList<>();
    for (final String userId : savedCheck.getUserIds()) {
      if (!userId.equals(initiator.getId())) {
        final Member member = event.getGuild().getMemberById(userId);
        if (member != null) {
          targetMembers.add(member);
        }
      }
    }
    return targetMembers;
  }

  private List<Member> getRoleMembers(
      final SlashCommandInteractionEvent event, final Role targetRole, final Member initiator) {
    return event.getGuild().getMembersWithRoles(targetRole).stream()
        .filter(member -> !member.equals(initiator))
        .toList();
  }

  private void sendNoSavedUsersReply(final SlashCommandInteractionEvent event) {
    event
        .reply("No other saved users found or they are no longer in the server!")
        .setEphemeral(true)
        .queue();
  }

  private void sendRoleNotExistsReply(final SlashCommandInteractionEvent event) {
    event.reply("The saved role no longer exists!").setEphemeral(true).queue();
  }

  private void sendNoRoleMembersReply(
      final SlashCommandInteractionEvent event, final Role targetRole) {
    event
        .reply("No other members found with the role: " + targetRole.getAsMention())
        .setEphemeral(true)
        .queue();
  }

  private void createUserBasedReadyCheck(
      final SlashCommandInteractionEvent event,
      final List<Member> targetMembers,
      final Member initiator,
      final TextChannel channel,
      final boolean mentionPeople) {

    final String readyCheckId =
        ReadyCheckManager.createUserReadyCheck(
            event.getGuild().getId(), channel.getId(), initiator.getId(), targetMembers);

    ReadyCheckManager.setMentionPreference(readyCheckId, mentionPeople);
    startUserBasedReadyCheck(event, readyCheckId, targetMembers, initiator);
  }

  private void createRoleBasedReadyCheck(
      final SlashCommandInteractionEvent event,
      final Role targetRole,
      final List<Member> targetMembers,
      final Member initiator,
      final TextChannel channel,
      final boolean mentionPeople) {

    final String readyCheckId =
        ReadyCheckManager.createReadyCheck(
            event.getGuild().getId(),
            channel.getId(),
            initiator.getId(),
            targetRole.getId(),
            targetMembers);

    ReadyCheckManager.setMentionPreference(readyCheckId, mentionPeople);
    startRoleBasedReadyCheck(event, readyCheckId, targetRole, targetMembers, initiator);
  }

  private void startUserBasedReadyCheck(
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

  private void startRoleBasedReadyCheck(
      final SlashCommandInteractionEvent event,
      final String readyCheckId,
      final Role targetRole,
      final List<Member> targetMembers,
      final Member initiator) {
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
}
