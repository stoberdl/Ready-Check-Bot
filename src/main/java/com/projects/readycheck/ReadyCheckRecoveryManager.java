package com.projects.readycheck;

import com.projects.readycheck.utils.ReadyCheckUtils;
import com.projects.recovery.MessageParser;
import com.projects.recovery.RecoveredReadyCheckData;
import com.projects.recovery.UserResolver;
import java.util.concurrent.TimeUnit;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ReadyCheckRecoveryManager {
  private static final Logger logger = LoggerFactory.getLogger(ReadyCheckRecoveryManager.class);
  private static final int RECOVERY_HOURS = 12;
  private static final String TOGGLE_READY_PREFIX = "toggle_ready_";
  public static final int DEFAULT_MAX_MESSAGES = 10;

  private ReadyCheckRecoveryManager() {}

  public static void recoverReadyChecksFromMessages(final JDA jda) {
    logger.info("Starting ready check recovery from messages (last {} hours)...", RECOVERY_HOURS);

    for (final Guild guild : jda.getGuilds()) {
      recoverGuildReadyChecks(guild);
    }
  }

  private static void recoverGuildReadyChecks(final Guild guild) {
    final long cutoffTime = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(RECOVERY_HOURS);
    int totalRecovered = 0;

    for (final TextChannel channel : guild.getTextChannels()) {
      if (!channel.canTalk()) continue;

      try {
        final int channelRecovered = scanChannelForReadyChecks(channel, cutoffTime);
        totalRecovered += channelRecovered;
      } catch (final Exception e) {
        logger.debug("Couldn't scan channel {}: {}", channel.getName(), e.getMessage());
      }
    }

    if (totalRecovered > 0) {
      logger.info("Recovered {} ready checks from guild: {}", totalRecovered, guild.getName());
    }
  }

  private static int scanChannelForReadyChecks(final TextChannel channel, final long cutoffTime) {
    channel
        .getHistory()
        .retrievePast(DEFAULT_MAX_MESSAGES)
        .queue(
            messages -> {
              for (final Message message : messages) {
                if (isRecoverableMessage(message, cutoffTime) && hasReadyCheckButtons(message)) {
                  recoverReadyCheckFromMessage(message);
                }
              }
            });

    return 0; // Async operation, can't return actual count
  }

  private static boolean isRecoverableMessage(final Message message, final long cutoffTime) {
    if (!isValidMessageForRecovery(message, cutoffTime)) {
      return false;
    }

    final var embed = message.getEmbeds().getFirst();
    if (embed.getTitle() == null) {
      return false;
    }

    final String title = embed.getTitle().toLowerCase();
    return title.contains("ready");
  }

  private static boolean isValidMessageForRecovery(final Message message, final long cutoffTime) {
    return message.getAuthor().equals(message.getJDA().getSelfUser())
        && message.getTimeCreated().toInstant().toEpochMilli() > cutoffTime
        && !message.getEmbeds().isEmpty();
  }

  private static boolean hasReadyCheckButtons(final Message message) {
    return message.getActionRows().stream()
        .flatMap(row -> row.getComponents().stream())
        .anyMatch(
            component ->
                component instanceof Button button
                    && button.getId() != null
                    && button.getId().startsWith(TOGGLE_READY_PREFIX));
  }

  private static void recoverReadyCheckFromMessage(final Message message) {
    try {
      final MessageEmbed embed = message.getEmbeds().getFirst();
      final String readyCheckId = extractReadyCheckId(message);

      if (readyCheckId == null) {
        logger.debug("No ready check ID found in message: {}", message.getId());
        return;
      }

      if (ReadyCheckManager.hasActiveReadyCheck(readyCheckId)) {
        logger.debug("Ready check {} already exists, skipping recovery", readyCheckId);
        return;
      }

      attemptRecovery(message, embed, readyCheckId);

    } catch (final Exception e) {
      logger.debug(
          "Failed to recover ready check from message {}: {}", message.getId(), e.getMessage());
    }
  }

  private static void attemptRecovery(
      final Message message, final MessageEmbed embed, final String readyCheckId) {
    final RecoveredReadyCheckData data = MessageParser.parseEmbedContent(embed.getDescription());
    if (data == null) {
      logger.debug("Could not parse embed content from message: {}", message.getId());
      return;
    }

    final ReadyCheckManager.ReadyCheck recoveredCheck =
        createRecoveredReadyCheck(
            readyCheckId,
            message.getGuild().getId(),
            message.getChannel().getId(),
            data,
            message.getId());

    if (recoveredCheck == null) {
      logger.debug("Could not create recovered ready check from message: {}", message.getId());
      return;
    }

    ReadyCheckManager.addRecoveredReadyCheck(readyCheckId, recoveredCheck);
    updateRecoveredMessage(message, recoveredCheck, readyCheckId);

    logger.info(
        "Recovered ready check {} with {} users in {}",
        readyCheckId,
        recoveredCheck.getTargetUsers().size(),
        message.getGuild().getName());
  }

  private static String extractReadyCheckId(final Message message) {
    return message.getActionRows().stream()
        .flatMap(row -> row.getComponents().stream())
        .filter(component -> component instanceof Button)
        .map(component -> ((Button) component).getId())
        .filter(id -> id != null && id.startsWith(TOGGLE_READY_PREFIX))
        .map(id -> id.replace(TOGGLE_READY_PREFIX, ""))
        .findFirst()
        .orElse(null);
  }

  private static ReadyCheckManager.ReadyCheck createRecoveredReadyCheck(
      final String readyCheckId,
      final String guildId,
      final String channelId,
      final RecoveredReadyCheckData data,
      final String messageId) {

    final String initiatorId = UserResolver.resolveUserId(data.initiatorName(), guildId);
    if (initiatorId == null) {
      logger.warn("Could not resolve initiator: {} in guild: {}", data.initiatorName(), guildId);
      return null;
    }

    final var resolvedUsers = UserResolver.resolveUserStates(data.userStates(), guildId);
    if (resolvedUsers.targetUsers().isEmpty()) {
      logger.warn("No users could be resolved for ready check: {}", readyCheckId);
      return null;
    }

    final ReadyCheckManager.ReadyCheck recoveredCheck =
        ReadyCheckManager.createRecoveredReadyCheck(
            readyCheckId,
            guildId,
            channelId,
            initiatorId,
            null, // roleId
            resolvedUsers.targetUsers(),
            resolvedUsers.readyUsers(),
            resolvedUsers.passedUsers(),
            messageId);

    recoveredCheck.setDescription("**" + data.initiatorName() + "** started a ready check");
    recoveredCheck.setRecovered(true);

    return recoveredCheck;
  }

  private static void updateRecoveredMessage(
      final Message message,
      final ReadyCheckManager.ReadyCheck readyCheck,
      final String readyCheckId) {
    try {
      final var embed =
          ReadyCheckManager.buildReadyCheckEmbedForRecovery(
              readyCheck, message.getJDA(), readyCheck.getDescription());
      final var mainButtons = ReadyCheckUtils.createMainButtons(readyCheckId);
      final var saveButton = ReadyCheckUtils.createSaveButton(readyCheckId);

      message
          .editMessageEmbeds(embed.build())
          .setComponents(ActionRow.of(mainButtons), ActionRow.of(saveButton))
          .queue(
              success -> logger.debug("Updated recovered message: {}", readyCheckId),
              error -> logger.debug("Failed to update recovered message: {}", error.getMessage()));
    } catch (final Exception e) {
      logger.debug("Could not update recovered message: {}", e.getMessage());
    }
  }
}
