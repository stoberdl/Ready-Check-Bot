package com.projects.recovery;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MentionRecoveryManager {
  private static final Logger logger = LoggerFactory.getLogger(MentionRecoveryManager.class);
  private static final Random random = new Random();
  private static final int RECOVERY_HOURS = 10;
  private static final int DEFAULT_MAX_MESSAGES = 30;
  private static final Set<String> repliedUsers = new HashSet<>();

  private MentionRecoveryManager() {}

  public static void recoverMissedMentions(final JDA jda) {
    repliedUsers.clear();
    logger.info("Starting mention recovery from messages (last {} hours)...", RECOVERY_HOURS);

    for (final Guild guild : jda.getGuilds()) {
      recoverGuildMentions(guild, jda);
    }
  }

  private static void recoverGuildMentions(final Guild guild, final JDA jda) {
    final long currentTime = System.currentTimeMillis();
    final long cutoffTime = currentTime - TimeUnit.HOURS.toMillis(RECOVERY_HOURS);

    int totalRecovered = 0;

    for (final TextChannel channel : guild.getTextChannels()) {
      if (!channel.canTalk()) continue;

      try {
        final int channelRecovered = scanChannelForMentions(channel, jda, cutoffTime);
        totalRecovered += channelRecovered;
      } catch (final Exception e) {
        logger.debug("Couldn't scan channel {}: {}", channel.getName(), e.getMessage());
      }
    }

    if (totalRecovered > 0) {
      logger.info("Recovered {} missed mentions from guild: {}", totalRecovered, guild.getName());
    }
  }

  private static int scanChannelForMentions(
      final TextChannel channel, final JDA jda, final long cutoffTime) {

    try {
      final var messages = channel.getHistory().retrievePast(DEFAULT_MAX_MESSAGES).complete();

      for (final Message message : messages) {
        if (isMissedMention(message, jda, cutoffTime) && !hasAlreadyRepliedToUser(message)) {
          replyToMissedMention(message);
        }
      }
    } catch (final Exception e) {
      logger.debug("Error scanning channel {}: {}", channel.getName(), e.getMessage());
    }

    return 0;
  }

  private static boolean isMissedMention(
      final Message message, final JDA jda, final long cutoffTime) {
    if (message.getAuthor().isBot()) {
      return false;
    }

    final long messageTime = message.getTimeCreated().toInstant().toEpochMilli();

    if (messageTime < cutoffTime) {
      return false;
    }

    if (!message.getMentions().isMentioned(jda.getSelfUser())) {
      return false;
    }

    return !botAlreadyRespondedToMention(message, jda);
  }

  private static boolean botAlreadyRespondedToMention(final Message message, final JDA jda) {
    try {
      final var recentMessages = message.getChannel().getHistory().retrievePast(10).complete();

      final long mentionTime = message.getTimeCreated().toInstant().toEpochMilli();

      for (final Message msg : recentMessages) {
        if (!msg.getAuthor().equals(jda.getSelfUser())) {
          continue;
        }

        final long botReplyTime = msg.getTimeCreated().toInstant().toEpochMilli();
        final boolean isReplyAfterMention =
            botReplyTime > mentionTime && (botReplyTime - mentionTime) < 300000;

        if (isReplyAfterMention) {
          logger.debug(
              "Found bot reply to mention from {}: original={}, reply={}",
              message.getAuthor().getName(),
              mentionTime,
              botReplyTime);
          return true;
        }
      }

      logger.debug("No bot reply found for mention from {}", message.getAuthor().getName());
      return false;

    } catch (final Exception e) {
      logger.error("Error checking for existing reply: {}", e.getMessage());
      return false;
    }
  }

  private static boolean hasAlreadyRepliedToUser(final Message message) {
    return repliedUsers.contains(message.getAuthor().getId());
  }

  private static void replyToMissedMention(final Message message) {
    try {
      final String userId = message.getAuthor().getId();
      repliedUsers.add(userId);

      final String response = getRandomResponse();
      message
          .reply(response)
          .mentionRepliedUser(false)
          .queue(
              success ->
                  logger.debug("Replied to missed mention from: {}", message.getAuthor().getName()),
              error -> logger.debug("Failed to reply to missed mention: {}", error.getMessage()));
    } catch (final Exception e) {
      logger.debug("Error replying to missed mention: {}", e.getMessage());
    }
  }

  private static String getRandomResponse() {
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
}
