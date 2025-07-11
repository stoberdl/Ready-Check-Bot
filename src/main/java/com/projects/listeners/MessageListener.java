package com.projects.listeners;

import com.projects.readycheck.ReadyCheckManager;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageListener extends ListenerAdapter {
  private static final Logger logger = LoggerFactory.getLogger(MessageListener.class);

  private static final Pattern R_PATTERN =
      Pattern.compile("^[rR](?:\\s+(in|at)\\s+([\\w:.,\\s]{1,50}))?$");
  private static final int MAX_MESSAGE_LENGTH = 100;

  @Override
  public void onMessageReceived(MessageReceivedEvent event) {
    if (event.getAuthor().isBot() || !event.isFromGuild()) {
      return;
    }

    String messageContent = event.getMessage().getContentRaw().trim();

    if (messageContent.length() > MAX_MESSAGE_LENGTH) {
      return;
    }

    Matcher matcher = R_PATTERN.matcher(messageContent);
    if (matcher.matches()) {
      String timeType = matcher.group(1);
      String timeValue = matcher.group(2);
      handleRMessage(event, timeType, timeValue);
    }
  }

  private void handleRMessage(MessageReceivedEvent event, String timeType, String timeValue) {
    String guildId = event.getGuild().getId();
    Member initiator = event.getMember();
    if (initiator == null) {
      logger.warn("Initiator member is null for guild: {}", guildId);
      return;
    }

    String userId = initiator.getId();
    String existingCheckId = ReadyCheckManager.findActiveReadyCheckForUser(guildId, userId);

    if (existingCheckId != null) {
      handleExistingReadyCheck(existingCheckId, userId, timeType, timeValue, event);
      return;
    }

    List<ReadyCheckManager.SavedReadyCheck> savedChecks =
        ReadyCheckManager.getSavedReadyChecks(guildId);

    if (savedChecks.isEmpty()) {
      return;
    }

    ReadyCheckManager.SavedReadyCheck lastSavedCheck = savedChecks.getLast();
    startReadyCheckFromSaved(event, lastSavedCheck, initiator, timeType, timeValue);
  }

  private void handleExistingReadyCheck(
      String readyCheckId,
      String userId,
      String timeType,
      String timeValue,
      MessageReceivedEvent event) {
    try {
      ReadyCheckManager.unmarkUserPassed(readyCheckId, userId);

      if (timeType == null) {
        ReadyCheckManager.markUserReady(readyCheckId, userId);
      } else if ("in".equals(timeType) && timeValue != null) {
        ReadyCheckManager.scheduleReadyAt(readyCheckId, timeValue.trim(), userId, event.getJDA());
      } else if ("at".equals(timeType) && timeValue != null) {
        ReadyCheckManager.scheduleReadyAtSmart(
            readyCheckId, timeValue.trim(), userId, event.getJDA());
      }

      ReadyCheckManager.updateReadyCheckEmbed(readyCheckId, event.getJDA());

      if (ReadyCheckManager.checkIfAllReady(readyCheckId)) {
        ReadyCheckManager.notifyAllReady(readyCheckId, event.getJDA());
      }
    } catch (Exception e) {
      logger.debug(
          "Failed to parse time input '{}' for user {}: {}", timeValue, userId, e.getMessage());
    }
  }

  private void startReadyCheckFromSaved(
      MessageReceivedEvent event,
      ReadyCheckManager.SavedReadyCheck savedCheck,
      Member initiator,
      String timeType,
      String timeValue) {
    if (savedCheck.isUserBased()) {
      handleUserBasedSavedCheck(event, savedCheck, initiator, timeType, timeValue);
    } else {
      handleRoleBasedSavedCheck(event, savedCheck, initiator, timeType, timeValue);
    }
  }

  private void handleUserBasedSavedCheck(
      MessageReceivedEvent event,
      ReadyCheckManager.SavedReadyCheck savedCheck,
      Member initiator,
      String timeType,
      String timeValue) {
    List<Member> targetMembers =
        getValidMembersFromIds(event, savedCheck.getUserIds(), initiator.getId());

    if (targetMembers.isEmpty()) {
      return;
    }

    String readyCheckId =
        ReadyCheckManager.createUserReadyCheck(
            event.getGuild().getId(), event.getChannel().getId(), initiator.getId(), targetMembers);

    ReadyCheckManager.setMentionPreference(readyCheckId, false);
    handleInitiatorReadyStatus(readyCheckId, initiator.getId(), timeType, timeValue, event);

    String description =
        "**" + initiator.getEffectiveName() + "** started a ready check for specific users";
    createReadyCheckResponseForMessage(event, readyCheckId, targetMembers, initiator, description);
  }

  private void handleRoleBasedSavedCheck(
      MessageReceivedEvent event,
      ReadyCheckManager.SavedReadyCheck savedCheck,
      Member initiator,
      String timeType,
      String timeValue) {
    Role targetRole = event.getGuild().getRoleById(savedCheck.getRoleId());
    if (targetRole == null) {
      return;
    }

    List<Member> targetMembers =
        event.getGuild().getMembersWithRoles(targetRole).stream()
            .filter(member -> !member.equals(initiator))
            .collect(Collectors.toList());

    if (targetMembers.isEmpty()) {
      return;
    }

    String readyCheckId =
        ReadyCheckManager.createReadyCheck(
            event.getGuild().getId(),
            event.getChannel().getId(),
            initiator.getId(),
            targetRole.getId(),
            targetMembers);

    ReadyCheckManager.setMentionPreference(readyCheckId, false);
    handleInitiatorReadyStatus(readyCheckId, initiator.getId(), timeType, timeValue, event);

    String description =
        "**"
            + initiator.getEffectiveName()
            + "** started a ready check for "
            + targetRole.getAsMention();
    createReadyCheckResponseForMessage(event, readyCheckId, targetMembers, initiator, description);
  }

  private void handleInitiatorReadyStatus(
      String readyCheckId,
      String userId,
      String timeType,
      String timeValue,
      MessageReceivedEvent event) {
    try {
      if (timeType == null) {
        ReadyCheckManager.markUserReady(readyCheckId, userId);
      } else if ("in".equals(timeType) && timeValue != null) {
        ReadyCheckManager.scheduleReadyAt(readyCheckId, timeValue.trim(), userId, event.getJDA());
      } else if ("at".equals(timeType) && timeValue != null) {
        ReadyCheckManager.scheduleReadyAtSmart(
            readyCheckId, timeValue.trim(), userId, event.getJDA());
      }
    } catch (Exception e) {
      logger.debug(
          "Failed to parse time input, marking user as ready immediately: {}", e.getMessage());
      ReadyCheckManager.markUserReady(readyCheckId, userId);
    }
  }

  private void createReadyCheckResponseForMessage(
      MessageReceivedEvent event,
      String readyCheckId,
      List<Member> targetMembers,
      Member initiator,
      String description) {
    ReadyCheckManager.sendReadyCheckToChannel(
        event.getChannel().asTextChannel(),
        readyCheckId,
        targetMembers,
        initiator,
        description,
        event.getJDA());
  }

  private List<Member> getValidMembersFromIds(
      MessageReceivedEvent event, List<String> userIds, String initiatorId) {
    return userIds.stream()
        .filter(userId -> !userId.equals(initiatorId))
        .map(userId -> event.getGuild().getMemberById(userId))
        .filter(Objects::nonNull)
        .distinct()
        .collect(Collectors.toList());
  }
}
