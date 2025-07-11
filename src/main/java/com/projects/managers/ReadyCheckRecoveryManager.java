package com.projects.managers;

import com.projects.models.recovery.RecoveredReadyCheckData;
import com.projects.models.recovery.RecoveryConfig;
import com.projects.recovery.MessageParser;
import com.projects.recovery.UserResolver;
import java.util.concurrent.TimeUnit;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReadyCheckRecoveryManager {
  private static final Logger logger = LoggerFactory.getLogger(ReadyCheckRecoveryManager.class);
  private static final int RECOVERY_HOURS = 12;

  public static void recoverReadyChecksFromMessages(JDA jda) {
    logger.info("Starting ready check recovery from messages (last {} hours)...", RECOVERY_HOURS);

    for (Guild guild : jda.getGuilds()) {
      recoverGuildReadyChecks(guild);
    }
  }

  private static void recoverGuildReadyChecks(Guild guild) {
    long cutoffTime = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(RECOVERY_HOURS);
    int totalRecovered = 0;

    for (TextChannel channel : guild.getTextChannels()) {
      if (!channel.canTalk()) continue;

      try {
        int channelRecovered = scanChannelForReadyChecks(channel, cutoffTime);
        totalRecovered += channelRecovered;
      } catch (Exception e) {
        logger.debug("Couldn't scan channel {}: {}", channel.getName(), e.getMessage());
      }
    }

    if (totalRecovered > 0) {
      logger.info("Recovered {} ready checks from guild: {}", totalRecovered, guild.getName());
    }
  }

  private static int scanChannelForReadyChecks(TextChannel channel, long cutoffTime) {
    int recovered = 0;

    channel
        .getHistory()
        .retrievePast(RecoveryConfig.DEFAULT_MAX_MESSAGES)
        .queue(
            messages -> {
              for (Message message : messages) {
                if (shouldRecoverMessage(message, cutoffTime)) {
                  if (recoverReadyCheckFromMessage(message)) {
                    // Don't increment here since this is async
                  }
                }
              }
            });

    return recovered; // Note: This will be 0 due to async nature, but that's OK for logging
  }

  private static boolean shouldRecoverMessage(Message message, long cutoffTime) {
    // Must be from the bot
    if (!message.getAuthor().equals(message.getJDA().getSelfUser())) {
      return false;
    }

    if (message.getTimeCreated().toInstant().toEpochMilli() <= cutoffTime) {
      return false;
    }

    if (message.getEmbeds().isEmpty()) {
      return false;
    }

    var embed = message.getEmbeds().get(0);
    if (embed.getTitle() == null) {
      return false;
    }

    String title = embed.getTitle().toLowerCase();
    if (!title.contains("ready")) {
      return false;
    }

    return hasReadyCheckButtons(message);
  }

  private static boolean hasReadyCheckButtons(Message message) {
    return message.getActionRows().stream()
        .flatMap(row -> row.getComponents().stream())
        .anyMatch(
            component ->
                component instanceof Button button
                    && button.getId() != null
                    && button.getId().startsWith("toggle_ready_"));
  }

  private static boolean recoverReadyCheckFromMessage(Message message) {
    try {
      var embed = message.getEmbeds().get(0);

      String readyCheckId = extractReadyCheckId(message);
      if (readyCheckId == null) {
        logger.debug("No ready check ID found in message: {}", message.getId());
        return false;
      }

      if (ReadyCheckManager.hasActiveReadyCheck(readyCheckId)) {
        logger.debug("Ready check {} already exists, skipping recovery", readyCheckId);
        return false;
      }

      RecoveredReadyCheckData data =
          MessageParser.parseEmbedContent(embed.getDescription(), message.getGuild());

      if (data == null) {
        logger.debug("Could not parse embed content from message: {}", message.getId());
        return false;
      }

      ReadyCheckManager.ReadyCheck recoveredCheck =
          createRecoveredReadyCheck(
              readyCheckId,
              message.getGuild().getId(),
              message.getChannel().getId(),
              data,
              message.getId());

      if (recoveredCheck == null) {
        logger.debug("Could not create recovered ready check from message: {}", message.getId());
        return false;
      }

      ReadyCheckManager.addRecoveredReadyCheck(readyCheckId, recoveredCheck);

      updateRecoveredMessage(message, recoveredCheck, readyCheckId);

      logger.info(
          "Recovered ready check {} with {} users in {}",
          readyCheckId,
          recoveredCheck.getTargetUsers().size(),
          message.getGuild().getName());

      return true;

    } catch (Exception e) {
      logger.debug(
          "Failed to recover ready check from message {}: {}", message.getId(), e.getMessage());
      return false;
    }
  }

  private static String extractReadyCheckId(Message message) {
    return message.getActionRows().stream()
        .flatMap(row -> row.getComponents().stream())
        .filter(component -> component instanceof Button)
        .map(component -> ((Button) component).getId())
        .filter(id -> id != null && id.startsWith("toggle_ready_"))
        .map(id -> id.replace("toggle_ready_", ""))
        .findFirst()
        .orElse(null);
  }

  private static ReadyCheckManager.ReadyCheck createRecoveredReadyCheck(
      String readyCheckId,
      String guildId,
      String channelId,
      RecoveredReadyCheckData data,
      String messageId) {

    String initiatorId = UserResolver.resolveUserId(data.initiatorName(), guildId);
    if (initiatorId == null) {
      logger.warn("Could not resolve initiator: {} in guild: {}", data.initiatorName(), guildId);
      return null;
    }

    var resolvedUsers = UserResolver.resolveUserStates(data.userStates(), guildId);

    if (resolvedUsers.targetUsers().isEmpty()) {
      logger.warn("No users could be resolved for ready check: {}", readyCheckId);
      return null;
    }

    var recoveredCheck =
        ReadyCheckManager.createRecoveredReadyCheck(
            readyCheckId,
            guildId,
            channelId,
            initiatorId,
            resolvedUsers.targetUsers(),
            resolvedUsers.readyUsers(),
            resolvedUsers.passedUsers(),
            messageId);

    recoveredCheck.setDescription("🔄 **Recovered** " + data.initiatorName() + "'s ready check");

    return recoveredCheck;
  }

  private static void updateRecoveredMessage(
      Message message, ReadyCheckManager.ReadyCheck readyCheck, String readyCheckId) {
    try {
      var embed =
          ReadyCheckManager.buildReadyCheckEmbedForRecovery(
              readyCheck, message.getJDA(), readyCheck.getDescription());

      var mainButtons = ReadyCheckManager.createMainButtons(readyCheckId);
      var saveButton = ReadyCheckManager.createSaveButton(readyCheckId);

      message
          .editMessageEmbeds(embed.build())
          .setComponents(ActionRow.of(mainButtons), ActionRow.of(saveButton))
          .queue(
              success -> logger.debug("Updated recovered message: {}", readyCheckId),
              error -> logger.debug("Failed to update recovered message: {}", error.getMessage()));
    } catch (Exception e) {
      logger.debug("Could not update recovered message: {}", e.getMessage());
    }
  }
}
