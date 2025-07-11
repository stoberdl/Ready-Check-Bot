package com.projects.commands;

import com.projects.readycheck.ReadyCheckManager;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;

public class ReadyCommand implements Command {

  @Override
  public String getName() {
    return "ready";
  }

  @Override
  public String getDescription() {
    return "Start a ready check: /ready targets:@GameRole @Alice @Bob";
  }

  @Override
  public void executeSlash(SlashCommandInteractionEvent event) {
    String guildId = event.getGuild().getId();
    Member initiator = event.getMember();
    TextChannel channel = event.getChannel().asTextChannel();

    // Get the targets option
    OptionMapping targetsOption = event.getOption("targets");
    boolean mentionPeople = event.getOption("people", true, OptionMapping::getAsBoolean);

    if (targetsOption == null) {
      // No targets provided, show saved ready checks
      handleSavedReadyCheck(event, guildId, initiator, channel, mentionPeople);
      return;
    }

    String targetsInput = targetsOption.getAsString();

    // Handle mixed targets (roles and users)
    handleMixedTargetsReadyCheck(event, targetsInput, initiator, channel, guildId, mentionPeople);
  }

  private void handleMixedTargetsReadyCheck(
      SlashCommandInteractionEvent event,
      String targetsInput,
      Member initiator,
      TextChannel channel,
      String guildId,
      boolean mentionPeople) {
    ParsedTargets parsed = parseTargetsInput(event, targetsInput);

    if (parsed.getAllMembers().isEmpty()) {
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
      return;
    }

    // Remove initiator from targets
    List<Member> targetMembers =
        parsed.getAllMembers().stream()
            .filter(member -> !member.equals(initiator))
            .collect(Collectors.toList());

    if (targetMembers.isEmpty()) {
      event.reply("❌ You can't start a ready check with only yourself!").setEphemeral(true).queue();
      return;
    }

    // Check for existing ready check with same members
    List<String> targetUserIds =
        targetMembers.stream().map(Member::getId).collect(Collectors.toList());

    String existingCheckId =
        ReadyCheckManager.findExistingReadyCheck(guildId, targetUserIds, initiator.getId());
    if (existingCheckId != null) {
      ReadyCheckManager.resendExistingReadyCheck(existingCheckId, event.getJDA());
      event
          .reply(
              "♻️ Found an existing ready check with the same members! Refreshing that one"
                  + " instead.")
          .setEphemeral(true)
          .queue();
      return;
    }

    // Create ready check
    String readyCheckId =
        ReadyCheckManager.createUserReadyCheck(
            guildId, channel.getId(), initiator.getId(), targetMembers);

    // Set the mention preference for this ready check
    ReadyCheckManager.setMentionPreference(readyCheckId, mentionPeople);

    // Build description based on what was parsed
    String description = buildDescription(initiator, parsed);

    ReadyCheckManager.createReadyCheckResponse(
        event, readyCheckId, targetMembers, initiator, description);
  }

  // Enhanced parser that handles roles AND users in one string
  private ParsedTargets parseTargetsInput(SlashCommandInteractionEvent event, String input) {
    ParsedTargets result = new ParsedTargets();

    // Pattern to match role mentions <@&123456789>
    Pattern roleMentionPattern = Pattern.compile("<@&(\\d+)>");
    Matcher roleMatcher = roleMentionPattern.matcher(input);

    while (roleMatcher.find()) {
      String roleId = roleMatcher.group(1);
      Role role = event.getGuild().getRoleById(roleId);
      if (role != null) {
        result.addRole(role);
        // Add all members with this role
        List<Member> roleMembers = event.getGuild().getMembersWithRoles(role);
        result.addMembers(roleMembers);
      }
    }

    // Pattern to match user mentions <@!123456789> or <@123456789>
    Pattern userMentionPattern = Pattern.compile("<@!?(\\d+)>");
    Matcher userMatcher = userMentionPattern.matcher(input);

    while (userMatcher.find()) {
      String userId = userMatcher.group(1);
      Member member = event.getGuild().getMemberById(userId);
      if (member != null) {
        result.addMember(member);
      }
    }

    // If no mentions found, try parsing by names
    if (result.getAllMembers().isEmpty() && result.getRoles().isEmpty()) {
      String[] parts = input.split("\\s+");
      for (String part : parts) {
        part = part.trim();
        if (!part.isEmpty()) {
          // Try to find member by name
          List<Member> foundMembers = event.getGuild().getMembersByEffectiveName(part, true);
          if (!foundMembers.isEmpty()) {
            result.addMember(foundMembers.getFirst());
          }
        }
      }
    }

    return result;
  }

  private String buildDescription(Member initiator, ParsedTargets parsed) {
    StringBuilder desc =
        new StringBuilder("**" + initiator.getEffectiveName() + "** started a ready check");

    List<String> parts = new ArrayList<>();

    if (!parsed.getRoles().isEmpty()) {
      String roleNames =
          parsed.getRoles().stream().map(Role::getAsMention).collect(Collectors.joining(", "));
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

  private static class ParsedTargets {
    private final Set<Role> roles = new HashSet<>();
    private final Set<Member> directUsers = new HashSet<>();
    private final Set<Member> allMembers = new HashSet<>();

    public void addRole(Role role) {
      roles.add(role);
    }

    public void addMember(Member member) {
      directUsers.add(member);
      allMembers.add(member);
    }

    public void addMembers(List<Member> members) {
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
      SlashCommandInteractionEvent event,
      String guildId,
      Member initiator,
      TextChannel channel,
      boolean mentionPeople) {
    List<ReadyCheckManager.SavedReadyCheck> savedChecks =
        ReadyCheckManager.getSavedReadyChecks(guildId);

    if (savedChecks.isEmpty()) {
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
      return;
    }

    if (savedChecks.size() == 1) {
      // Only one saved config, use it directly
      startReadyCheckFromSaved(event, savedChecks.getFirst(), initiator, channel, mentionPeople);
    } else {
      // Multiple saved configs, let user choose
      StringSelectMenu.Builder menuBuilder =
          StringSelectMenu.create("select_saved_ready_" + mentionPeople)
              .setPlaceholder("Choose a saved ready check configuration...");

      for (ReadyCheckManager.SavedReadyCheck savedCheck : savedChecks) {
        if (savedCheck.isUserBased()) {
          // For user-based, create a descriptive name
          menuBuilder.addOption(
              "User Group (" + savedCheck.getUserIds().size() + " users)",
              "users_" + savedCheck.getUserIds().hashCode(),
              "Ready check for specific users");
        } else {
          Role role = event.getGuild().getRoleById(savedCheck.getRoleId());
          String roleName = role != null ? role.getName() : "Unknown Role";
          menuBuilder.addOption(roleName, savedCheck.getRoleId(), "Ready check for " + roleName);
        }
      }

      event
          .reply("Select a saved ready check configuration:")
          .addActionRow(menuBuilder.build())
          .setEphemeral(true)
          .queue();
    }
  }

  private void startReadyCheckFromSaved(
      SlashCommandInteractionEvent event,
      ReadyCheckManager.SavedReadyCheck savedCheck,
      Member initiator,
      TextChannel channel,
      boolean mentionPeople) {
    if (savedCheck.isUserBased()) {
      // Handle user-based saved config
      List<Member> targetMembers = new ArrayList<>();
      for (String userId : savedCheck.getUserIds()) {
        if (!userId.equals(initiator.getId())) { // Exclude initiator
          Member member = event.getGuild().getMemberById(userId);
          if (member != null) {
            targetMembers.add(member);
          }
        }
      }

      if (targetMembers.isEmpty()) {
        event
            .reply("No other saved users found or they are no longer in the server!")
            .setEphemeral(true)
            .queue();
        return;
      }

      // Create user-based ready check
      String readyCheckId =
          ReadyCheckManager.createUserReadyCheck(
              event.getGuild().getId(), channel.getId(), initiator.getId(), targetMembers);

      // Set the mention preference for this ready check
      ReadyCheckManager.setMentionPreference(readyCheckId, mentionPeople);

      startUserBasedReadyCheck(event, readyCheckId, targetMembers, initiator);

    } else {
      // Handle role-based saved config
      Role targetRole = event.getGuild().getRoleById(savedCheck.getRoleId());
      if (targetRole == null) {
        event.reply("The saved role no longer exists!").setEphemeral(true).queue();
        return;
      }

      // Get current members with the role (excluding initiator for now, we'll add them back in the
      // manager)
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

      // Create ready check ID (this will include the initiator in the target users AND mark them as
      // ready)
      String readyCheckId =
          ReadyCheckManager.createReadyCheck(
              event.getGuild().getId(),
              channel.getId(),
              initiator.getId(),
              targetRole.getId(),
              targetMembers);

      // Set the mention preference for this ready check
      ReadyCheckManager.setMentionPreference(readyCheckId, mentionPeople);

      startRoleBasedReadyCheck(event, readyCheckId, targetRole, targetMembers, initiator);
    }
  }

  private void startUserBasedReadyCheck(
      SlashCommandInteractionEvent event,
      String readyCheckId,
      List<Member> targetMembers,
      Member initiator) {
    ReadyCheckManager.createReadyCheckResponse(
        event,
        readyCheckId,
        targetMembers,
        initiator,
        "**" + initiator.getEffectiveName() + "** started a ready check for specific users");
  }

  private void startRoleBasedReadyCheck(
      SlashCommandInteractionEvent event,
      String readyCheckId,
      Role targetRole,
      List<Member> targetMembers,
      Member initiator) {
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
