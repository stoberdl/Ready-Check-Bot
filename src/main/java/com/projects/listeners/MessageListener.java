package com.projects.listeners;

import com.projects.readycheck.ReadyCheckManager;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MessageListener extends ListenerAdapter {
  private static final Logger logger = LoggerFactory.getLogger(MessageListener.class);
  private static final Random random = new Random();

  private static final Pattern R_PATTERN =
      Pattern.compile("^[rR](?:\\s+(in|at)\\s+([\\w:.,\\s]{1,50}))?$");
  private static final int MAX_MESSAGE_LENGTH = 100;

  @Override
  public void onMessageReceived(final MessageReceivedEvent event) {
    if (event.getAuthor().isBot() || !event.isFromGuild()) {
      return;
    }

    final String messageContent = event.getMessage().getContentRaw().trim();

    if (isMentioningBot(event)) {
      handleBotMention(event);
      return;
    }

    if (messageContent.length() > MAX_MESSAGE_LENGTH) {
      return;
    }

    final Matcher matcher = R_PATTERN.matcher(messageContent);
    if (matcher.matches()) {
      final String timeType = matcher.group(1);
      final String timeValue = matcher.group(2);
      handleRMessage(event, timeType, timeValue);
    }
  }

  private boolean isMentioningBot(final MessageReceivedEvent event) {
    return event.getMessage().getMentions().isMentioned(event.getJDA().getSelfUser());
  }

  private void handleBotMention(final MessageReceivedEvent event) {
    final String response = getRandomResponse();
    event.getChannel().sendMessage(response).queue();
  }

  private String getRandomResponse() {
    final int roll = random.nextInt(100);

    if (roll < 70) {
      return "wat";
    } else if (roll < 90) {
      return "idk";
    } else if (roll < 95) {
      return "cs?";
    } else {
      return "ARAM";
    }
  }

  private void handleRMessage(
      final MessageReceivedEvent event, final String timeType, final String timeValue) {
    final String guildId = event.getGuild().getId();
    final String channelId = event.getChannel().getId();
    final Member initiator = event.getMember();
    if (initiator == null) {
      logger.warn("Initiator member is null for guild: {}", guildId);
      return;
    }

    final String userId = initiator.getId();

    String existingCheckId = ReadyCheckManager.findActiveReadyCheckForUser(guildId, userId);

    if (existingCheckId != null) {
      handleExistingReadyCheck(existingCheckId, userId, timeType, timeValue, event, false);
      return;
    }

    existingCheckId = ReadyCheckManager.findActiveReadyCheckInChannel(guildId, channelId);

    if (existingCheckId != null) {
      ReadyCheckManager.ensureUserInReadyCheck(existingCheckId, userId);
      ReadyCheckManager.refreshReadyCheckMessage(existingCheckId, event.getJDA());
      handleExistingReadyCheck(existingCheckId, userId, timeType, timeValue, event, true);
      return;
    }

    final List<ReadyCheckManager.SavedReadyCheck> savedChecks =
        ReadyCheckManager.getSavedReadyChecks(guildId);

    if (savedChecks.isEmpty()) {
      return;
    }

    final ReadyCheckManager.SavedReadyCheck lastSavedCheck = savedChecks.getLast();
    startReadyCheckFromSaved(event, lastSavedCheck, initiator, timeType, timeValue);
  }

  private void handleExistingReadyCheck(
      final String readyCheckId,
      final String userId,
      final String timeType,
      final String timeValue,
      final MessageReceivedEvent event,
      final boolean isRefreshed) {
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

      if (!isRefreshed) {
        ReadyCheckManager.updateReadyCheckEmbed(readyCheckId, event.getJDA());
      }

      if (ReadyCheckManager.checkIfAllReady(readyCheckId)) {
        ReadyCheckManager.notifyAllReady(readyCheckId, event.getJDA());
      }
    } catch (final Exception e) {
      logger.debug(
          "Failed to parse time input '{}' for user {}: {}", timeValue, userId, e.getMessage());
    }
  }

  private void startReadyCheckFromSaved(
      final MessageReceivedEvent event,
      final ReadyCheckManager.SavedReadyCheck savedCheck,
      final Member initiator,
      final String timeType,
      final String timeValue) {
    if (savedCheck.isUserBased()) {
      handleUserBasedSavedCheck(event, savedCheck, initiator, timeType, timeValue);
    } else {
      handleRoleBasedSavedCheck(event, savedCheck, initiator, timeType, timeValue);
    }
  }

  private void handleUserBasedSavedCheck(
      final MessageReceivedEvent event,
      final ReadyCheckManager.SavedReadyCheck savedCheck,
      final Member initiator,
      final String timeType,
      final String timeValue) {
    final List<Member> targetMembers =
        getValidMembersFromIds(event, savedCheck.getUserIds(), initiator.getId());

    if (targetMembers.isEmpty()) {
      return;
    }

    final String readyCheckId =
        ReadyCheckManager.createUserReadyCheck(
            event.getGuild().getId(), event.getChannel().getId(), initiator.getId(), targetMembers);

    ReadyCheckManager.setMentionPreference(readyCheckId, false);
    handleInitiatorReadyStatus(readyCheckId, initiator.getId(), timeType, timeValue, event);

    final String description =
        "**" + initiator.getEffectiveName() + "** started a ready check for specific users";
    createReadyCheckResponseForMessage(event, readyCheckId, targetMembers, initiator, description);
  }

  private void handleRoleBasedSavedCheck(
      final MessageReceivedEvent event,
      final ReadyCheckManager.SavedReadyCheck savedCheck,
      final Member initiator,
      final String timeType,
      final String timeValue) {
    final Role targetRole = event.getGuild().getRoleById(savedCheck.getRoleId());
    if (targetRole == null) {
      return;
    }

    final List<Member> targetMembers =
        event.getGuild().getMembersWithRoles(targetRole).stream()
            .filter(member -> !member.equals(initiator))
            .toList();

    if (targetMembers.isEmpty()) {
      return;
    }

    final String readyCheckId =
        ReadyCheckManager.createReadyCheck(
            event.getGuild().getId(),
            event.getChannel().getId(),
            initiator.getId(),
            targetRole.getId(),
            targetMembers);

    ReadyCheckManager.setMentionPreference(readyCheckId, false);
    handleInitiatorReadyStatus(readyCheckId, initiator.getId(), timeType, timeValue, event);

    final String description =
        "**"
            + initiator.getEffectiveName()
            + "** started a ready check for "
            + targetRole.getAsMention();
    createReadyCheckResponseForMessage(event, readyCheckId, targetMembers, initiator, description);
  }

  private void handleInitiatorReadyStatus(
      final String readyCheckId,
      final String userId,
      final String timeType,
      final String timeValue,
      final MessageReceivedEvent event) {
    try {
      if (timeType == null) {
        ReadyCheckManager.markUserReady(readyCheckId, userId);
      } else if ("in".equals(timeType) && timeValue != null) {
        ReadyCheckManager.scheduleReadyAt(readyCheckId, timeValue.trim(), userId, event.getJDA());
      } else if ("at".equals(timeType) && timeValue != null) {
        ReadyCheckManager.scheduleReadyAtSmart(
            readyCheckId, timeValue.trim(), userId, event.getJDA());
      }
    } catch (final Exception e) {
      logger.debug(
          "Failed to parse time input, marking user as ready immediately: {}", e.getMessage());
      ReadyCheckManager.markUserReady(readyCheckId, userId);
    }
  }

  private void createReadyCheckResponseForMessage(
      final MessageReceivedEvent event,
      final String readyCheckId,
      final List<Member> targetMembers,
      final Member initiator,
      final String description) {
    ReadyCheckManager.sendReadyCheckToChannel(
        event.getChannel().asTextChannel(),
        readyCheckId,
        targetMembers,
        initiator,
        description,
        event.getJDA());
  }

  private List<Member> getValidMembersFromIds(
      final MessageReceivedEvent event, final List<String> userIds, final String initiatorId) {
    return userIds.stream()
        .filter(userId -> !userId.equals(initiatorId))
        .map(userId -> event.getGuild().getMemberById(userId))
        .filter(Objects::nonNull)
        .distinct()
        .toList();
  }
}
