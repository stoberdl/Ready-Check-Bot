package com.projects.listeners;

import com.projects.managers.ReadyCheckManager;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class MessageListener extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(MessageListener.class);

    // Pattern to match "r", "R", "r in X", "R in X", "r at X", "R at X"
    private static final Pattern R_PATTERN = Pattern.compile("^[rR](?:\\s+(in|at)\\s+(.+))?$");

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) {
            return;
        }
        if (!event.isFromGuild()) {
            return;
        }

        String messageContent = event.getMessage().getContentRaw().trim();
        Matcher matcher = R_PATTERN.matcher(messageContent);

        if (matcher.matches()) {
            String timeType = matcher.group(1); // "in" or "at"
            String timeValue = matcher.group(2); // the time value

            handleRMessage(event, timeType, timeValue);
        }
    }

    private void handleRMessage(MessageReceivedEvent event, String timeType, String timeValue) {
        String guildId = event.getGuild().getId();
        Member initiator = event.getMember();
        String userId = initiator.getId();

        // First, check if there's an active ready check within the past 2 hours that includes this user
        String existingCheckId = ReadyCheckManager.findActiveReadyCheckForUser(guildId, userId);

        if (existingCheckId != null) {
            handleExistingReadyCheck(existingCheckId, userId, timeType, timeValue, event);
            return;
        }

        // No active ready check found, create a new one using the last saved config
        List<ReadyCheckManager.SavedReadyCheck> savedChecks = ReadyCheckManager.getSavedReadyChecks(guildId);

        if (savedChecks.isEmpty()) {
            return; // Silent fail - no saved configs
        }

        ReadyCheckManager.SavedReadyCheck lastSavedCheck = savedChecks.getLast();

        startReadyCheckFromSaved(event, lastSavedCheck, initiator, timeType, timeValue);
    }

    private void handleExistingReadyCheck(String readyCheckId, String userId, String timeType, String timeValue, MessageReceivedEvent event) {
        try {
            if (timeType == null) {
                // Just "r" or "R" - mark as ready now and remove from passed users
                ReadyCheckManager.unmarkUserPassed(readyCheckId, userId);
                ReadyCheckManager.markUserReady(readyCheckId, userId);
            } else if ("in".equals(timeType) && timeValue != null) {
                // "r in X" - schedule to be ready in X minutes (NOT auto-unready timer)
                ReadyCheckManager.unmarkUserPassed(readyCheckId, userId);
                ReadyCheckManager.scheduleReadyAt(readyCheckId, timeValue.trim(), userId, event.getJDA());
            } else if ("at".equals(timeType) && timeValue != null) {
                // "r at X" - same as "Ready at" button functionality with smart AM/PM detection
                ReadyCheckManager.unmarkUserPassed(readyCheckId, userId);
                ReadyCheckManager.scheduleReadyAtSmart(readyCheckId, timeValue.trim(), userId, event.getJDA());
            }

            // Update the embed to reflect the change
            ReadyCheckManager.updateReadyCheckEmbed(readyCheckId, event.getJDA());

            // Check if all ready and notify if so
            if (ReadyCheckManager.checkIfAllReady(readyCheckId)) {
                ReadyCheckManager.notifyAllReady(readyCheckId, event.getJDA());
            }
        } catch (Exception e) {
            // Silent fail if can't parse time values
        }
    }

    private void startReadyCheckFromSaved(MessageReceivedEvent event, ReadyCheckManager.SavedReadyCheck savedCheck, Member initiator, String timeType, String timeValue) {
        if (savedCheck.isUserBased()) {
            handleUserBasedSavedCheck(event, savedCheck, initiator, timeType, timeValue);
        } else {
            handleRoleBasedSavedCheck(event, savedCheck, initiator, timeType, timeValue);
        }
    }

    private void handleUserBasedSavedCheck(MessageReceivedEvent event, ReadyCheckManager.SavedReadyCheck savedCheck, Member initiator, String timeType, String timeValue) {
        List<Member> targetMembers = getValidMembersFromIds(event, savedCheck.getUserIds(), initiator.getId());

        if (targetMembers.isEmpty()) {
            return; // Silent fail
        }

        String readyCheckId = ReadyCheckManager.createUserReadyCheck(
                event.getGuild().getId(),
                event.getChannel().getId(),
                initiator.getId(),
                targetMembers
        );

        // Always set mentions to false for message-based ready checks
        ReadyCheckManager.setMentionPreference(readyCheckId, false);

        // Handle initiator's ready status based on the command
        handleInitiatorReadyStatus(readyCheckId, initiator.getId(), timeType, timeValue, event);

        // Create the ready check response
        createReadyCheckResponseForMessage(event, readyCheckId, targetMembers, initiator,
                "**" + initiator.getEffectiveName() + "** started a ready check for specific users");
    }

    private void handleRoleBasedSavedCheck(MessageReceivedEvent event, ReadyCheckManager.SavedReadyCheck savedCheck, Member initiator, String timeType, String timeValue) {
        Role targetRole = event.getGuild().getRoleById(savedCheck.getRoleId());
        if (targetRole == null) {
            return; // Silent fail
        }

        List<Member> targetMembers = event.getGuild().getMembersWithRoles(targetRole)
                .stream()
                .filter(member -> !member.equals(initiator))
                .collect(Collectors.toList());

        if (targetMembers.isEmpty()) {
            return; // Silent fail
        }

        String readyCheckId = ReadyCheckManager.createReadyCheck(
                event.getGuild().getId(),
                event.getChannel().getId(),
                initiator.getId(),
                targetRole.getId(),
                targetMembers
        );

        // Always set mentions to false for message-based ready checks
        ReadyCheckManager.setMentionPreference(readyCheckId, false);

        // Handle initiator's ready status based on the command
        handleInitiatorReadyStatus(readyCheckId, initiator.getId(), timeType, timeValue, event);

        // Create the ready check response
        createReadyCheckResponseForMessage(event, readyCheckId, targetMembers, initiator,
                "**" + initiator.getEffectiveName() + "** started a ready check for " + targetRole.getAsMention());
    }

    private void handleInitiatorReadyStatus(String readyCheckId, String userId, String timeType, String timeValue, MessageReceivedEvent event) {
        try {
            if (timeType == null) {
                // Just "r" or "R" - mark as ready now
                ReadyCheckManager.markUserReady(readyCheckId, userId);
            } else if ("in".equals(timeType) && timeValue != null) {
                // "r in X" - schedule to be ready in X minutes (NOT auto-unready timer)
                ReadyCheckManager.scheduleReadyAt(readyCheckId, timeValue.trim(), userId, event.getJDA());
            } else if ("at".equals(timeType) && timeValue != null) {
                // "r at X" - same as "Ready at" button functionality with smart AM/PM detection
                ReadyCheckManager.scheduleReadyAtSmart(readyCheckId, timeValue.trim(), userId, event.getJDA());
            }
        } catch (Exception e) {
            // If can't parse, just mark as ready
            ReadyCheckManager.markUserReady(readyCheckId, userId);
        }
    }

    private void createReadyCheckResponseForMessage(MessageReceivedEvent event, String readyCheckId, List<Member> targetMembers, Member initiator, String description) {
        // Send the ready check directly to the channel since we're dealing with a message event
        ReadyCheckManager.sendReadyCheckToChannel(event.getChannel().asTextChannel(), readyCheckId, targetMembers, initiator, description, event.getJDA());
    }

    private List<Member> getValidMembersFromIds(MessageReceivedEvent event, List<String> userIds, String initiatorId) {
        return userIds.stream()
                .filter(userId -> !userId.equals(initiatorId))
                .map(userId -> event.getGuild().getMemberById(userId))
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }
}