package com.projects.commands;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import com.projects.managers.ReadyCheckManager;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class RCommand implements Command {

    @Override
    public String getName() {
        return "r";
    }

    @Override
    public String getDescription() {
        return "Start a ready check using saved configurations";
    }

    @Override
    public void executeSlash(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();
        Member initiator = event.getMember();

        List<ReadyCheckManager.SavedReadyCheck> savedChecks = ReadyCheckManager.getSavedReadyChecks(guildId);

        if (savedChecks.isEmpty()) {
            event.reply("No saved ready check configurations found! Use `/ready` and click '💾' to create one.").setEphemeral(true).queue();
            return;
        }

        if (savedChecks.size() == 1) {
            handleSingleSavedCheck(event, savedChecks.get(0), initiator, guildId);
        } else {
            showSavedConfigurationMenu(event, savedChecks);
        }
    }

    private void handleSingleSavedCheck(SlashCommandInteractionEvent event, ReadyCheckManager.SavedReadyCheck savedCheck, Member initiator, String guildId) {
        String existingCheckId = ReadyCheckManager.findExistingReadyCheck(guildId, savedCheck, initiator.getId());
        if (existingCheckId != null && ReadyCheckManager.isReadyCheckOngoing(existingCheckId)) {
            ReadyCheckManager.resendExistingReadyCheck(existingCheckId, event.getJDA());
            event.reply("♻️ Found an existing ready check with the same members! Refreshing that one instead.").setEphemeral(true).queue();
            return;
        }

        startReadyCheckFromSaved(event, savedCheck, initiator);
    }

    private void showSavedConfigurationMenu(SlashCommandInteractionEvent event, List<ReadyCheckManager.SavedReadyCheck> savedChecks) {
        StringSelectMenu.Builder menuBuilder = StringSelectMenu.create("select_saved_ready")
                .setPlaceholder("Choose a saved ready check configuration...");

        Set<String> usedValues = new HashSet<>();
        int userGroupCounter = 1;

        for (ReadyCheckManager.SavedReadyCheck savedCheck : savedChecks) {
            if (savedCheck.isUserBased()) {
                String userNames = getUserNamesFromIds(event, savedCheck.getUserIds());
                String uniqueValue = "users_" + savedCheck.getUserIds().hashCode() + "_" + userGroupCounter;

                // Ensure unique values
                while (usedValues.contains(uniqueValue)) {
                    userGroupCounter++;
                    uniqueValue = "users_" + savedCheck.getUserIds().hashCode() + "_" + userGroupCounter;
                }

                usedValues.add(uniqueValue);
                String mentionText = savedCheck.getMentionPeople() ? "mentions users" : "no mentions";
                menuBuilder.addOption("Users: " + userNames, uniqueValue, mentionText);
                userGroupCounter++;
            } else {
                Role role = event.getGuild().getRoleById(savedCheck.getRoleId());
                String roleName = role != null ? role.getName() : "Unknown Role";
                String roleValue = savedCheck.getRoleId();

                // Only add if not already used
                if (!usedValues.contains(roleValue)) {
                    usedValues.add(roleValue);
                    String mentionText = savedCheck.getMentionPeople() ? "mentions users" : "no mentions";
                    menuBuilder.addOption("Role: " + roleName, roleValue, mentionText);
                }
            }
        }

        event.reply("Select a saved configuration:")
                .addActionRow(menuBuilder.build())
                .setEphemeral(true)
                .queue();
    }

    private String getUserNamesFromIds(SlashCommandInteractionEvent event, List<String> userIds) {
        String names = userIds.stream()
                .map(userId -> {
                    Member member = event.getGuild().getMemberById(userId);
                    return member != null ? member.getEffectiveName() : "Unknown";
                })
                .limit(3)
                .collect(Collectors.joining(", "));

        return userIds.size() > 3 ? names + " + " + (userIds.size() - 3) + " more" : names;
    }

    private void startReadyCheckFromSaved(SlashCommandInteractionEvent event, ReadyCheckManager.SavedReadyCheck savedCheck, Member initiator) {
        if (savedCheck.isUserBased()) {
            handleUserBasedSavedCheck(event, savedCheck, initiator);
        } else {
            handleRoleBasedSavedCheck(event, savedCheck, initiator);
        }
    }

    private void handleUserBasedSavedCheck(SlashCommandInteractionEvent event, ReadyCheckManager.SavedReadyCheck savedCheck, Member initiator) {
        List<Member> targetMembers = getValidMembersFromIds(event, savedCheck.getUserIds(), initiator.getId());

        if (targetMembers.isEmpty()) {
            event.reply("No other saved users found or they are no longer in the server!").setEphemeral(true).queue();
            return;
        }

        String readyCheckId = ReadyCheckManager.createUserReadyCheck(
                event.getGuild().getId(),
                event.getChannel().getId(),
                initiator.getId(),
                targetMembers
        );

        // Use the saved mention preference
        ReadyCheckManager.setMentionPreference(readyCheckId, savedCheck.getMentionPeople());

        ReadyCheckManager.createReadyCheckResponse(event, readyCheckId, targetMembers, initiator,
                "**" + initiator.getEffectiveName() + "** started a ready check for specific users");
    }

    private void handleRoleBasedSavedCheck(SlashCommandInteractionEvent event, ReadyCheckManager.SavedReadyCheck savedCheck, Member initiator) {
        Role targetRole = event.getGuild().getRoleById(savedCheck.getRoleId());
        if (targetRole == null) {
            event.reply("The saved role no longer exists!").setEphemeral(true).queue();
            return;
        }

        List<Member> targetMembers = event.getGuild().getMembersWithRoles(targetRole)
                .stream()
                .filter(member -> !member.equals(initiator))
                .collect(Collectors.toList());

        if (targetMembers.isEmpty()) {
            event.reply("No other members found with the role: " + targetRole.getAsMention()).setEphemeral(true).queue();
            return;
        }

        String readyCheckId = ReadyCheckManager.createReadyCheck(
                event.getGuild().getId(),
                event.getChannel().getId(),
                initiator.getId(),
                targetRole.getId(),
                targetMembers
        );

        // Use the saved mention preference
        ReadyCheckManager.setMentionPreference(readyCheckId, savedCheck.getMentionPeople());

        ReadyCheckManager.createReadyCheckResponse(event, readyCheckId, targetMembers, initiator,
                "**" + initiator.getEffectiveName() + "** started a ready check for " + targetRole.getAsMention());
    }

    private List<Member> getValidMembersFromIds(SlashCommandInteractionEvent event, List<String> userIds, String initiatorId) {
        return userIds.stream()
                .filter(userId -> !userId.equals(initiatorId))
                .map(userId -> event.getGuild().getMemberById(userId))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}