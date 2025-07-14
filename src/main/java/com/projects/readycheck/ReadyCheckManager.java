package com.projects.readycheck;

import com.projects.readycheck.utils.ReadyCheckUtils;
import com.projects.readycheck.utils.VoiceChannelMentionFilter;
import java.awt.Color;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ReadyCheckManager {
  private static final Logger logger = LoggerFactory.getLogger(ReadyCheckManager.class);
  private static final Map<String, ReadyCheck> activeReadyChecks = new ConcurrentHashMap<>();
  private static final Map<String, Boolean> mentionPreferences = new ConcurrentHashMap<>();
  private static final long EIGHT_HOURS_MS = TimeUnit.HOURS.toMillis(8);
  private static JDA globalJDA;

  static {
    ReadyCheckScheduler.startPeriodicUpdater();
    scheduleRecovery();
  }

  private ReadyCheckManager() {}

  public enum ReadyCheckStatus {
    ACTIVE,
    COMPLETED
  }

  public static void setJDA(final JDA jda) {
    globalJDA = jda;
  }

  public static JDA getJDA() {
    return globalJDA;
  }

  public static Map<String, ReadyCheck> getActiveReadyChecks() {
    return activeReadyChecks;
  }

  public static boolean hasActiveReadyCheck(final String readyCheckId) {
    return activeReadyChecks.containsKey(readyCheckId);
  }

  public static ReadyCheck getActiveReadyCheck(final String readyCheckId) {
    return activeReadyChecks.get(readyCheckId);
  }

  public static void addRecoveredReadyCheck(
      final String readyCheckId, final ReadyCheck readyCheck) {
    activeReadyChecks.put(readyCheckId, readyCheck);
    logger.info("Added recovered ready check: {}", readyCheckId);
  }

  public static ReadyCheck createRecoveredReadyCheck(
      final String readyCheckId,
      final String guildId,
      final String channelId,
      final String initiatorId,
      final String roleId,
      final Set<String> targetUsers,
      final Set<String> readyUsers,
      final Set<String> passedUsers,
      final String messageId) {

    final ReadyCheck recoveredCheck =
        new ReadyCheck(
            readyCheckId, guildId, channelId, initiatorId, roleId, new ArrayList<>(targetUsers));

    recoveredCheck.getTargetUsers().clear();
    recoveredCheck.getTargetUsers().addAll(targetUsers);
    recoveredCheck.getReadyUsers().addAll(readyUsers);
    recoveredCheck.getPassedUsers().addAll(passedUsers);
    recoveredCheck.setMessageId(messageId);

    return recoveredCheck;
  }

  public static String createUserReadyCheck(
      final String guildId,
      final String channelId,
      final String initiatorId,
      final List<Member> targetMembers) {
    return createReadyCheck(
        guildId, channelId, initiatorId, null, targetMembers, "**Ready Check** for specific users");
  }

  public static String createReadyCheck(
      final String guildId,
      final String channelId,
      final String initiatorId,
      final String roleId,
      final List<Member> targetMembers) {
    return createReadyCheck(
        guildId, channelId, initiatorId, roleId, targetMembers, "**Ready Check** for role members");
  }

  public static void setMentionPreference(final String readyCheckId, final boolean mentionPeople) {
    mentionPreferences.put(readyCheckId, mentionPeople);
  }

  public static boolean getMentionPreference(final String readyCheckId) {
    return mentionPreferences.getOrDefault(readyCheckId, true);
  }

  public static boolean markUserReady(final String readyCheckId, final String userId) {
    final ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    if (readyCheck == null) {
      logger.warn(
          "Attempted to mark user {} ready for non-existent ready check: {}", userId, readyCheckId);
      return false;
    }

    ReadyCheckScheduler.cancelExistingScheduledUser(readyCheck, userId);
    readyCheck.getUserTimers().remove(userId);
    readyCheck.getReadyUsers().add(userId);

    return checkAndUpdateCompletionStatus(readyCheck, readyCheckId);
  }

  public static boolean toggleUserReady(final String readyCheckId, final String userId) {
    final ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    if (readyCheck == null) return false;

    readyCheck.getTargetUsers().add(userId);

    if (readyCheck.getReadyUsers().contains(userId)) {
      readyCheck.getReadyUsers().remove(userId);
      return false;
    }

    markUserAsReady(readyCheck, userId);
    return true;
  }

  public static void markUserPassed(final String readyCheckId, final String userId) {
    final ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    if (readyCheck == null) return;

    readyCheck.getTargetUsers().add(userId);
    readyCheck.getPassedUsers().add(userId);
    readyCheck.getReadyUsers().remove(userId);
    readyCheck.getScheduledUsers().remove(userId);
    readyCheck.getUserUntilTimes().remove(userId);
  }

  public static void unmarkUserPassed(final String readyCheckId, final String userId) {
    final ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    if (readyCheck != null) {
      readyCheck.getPassedUsers().remove(userId);
    }
  }

  public static void ensureUserInReadyCheck(final String readyCheckId, final String userId) {
    final ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    if (readyCheck != null) {
      readyCheck.getTargetUsers().add(userId);
    }
  }

  public static void scheduleReadyAt(
      final String readyCheckId, final String timeInput, final String userId, final JDA jda) {
    final ReadyCheck readyCheck = getReadyCheckOrThrow(readyCheckId);
    ReadyCheckScheduler.scheduleReadyAt(readyCheck, timeInput, userId, jda);
  }

  public static String scheduleReadyAtSmart(
      final String readyCheckId, final String timeInput, final String userId, final JDA jda) {
    final ReadyCheck readyCheck = getReadyCheckOrThrow(readyCheckId);
    return ReadyCheckScheduler.scheduleReadyAtSmart(readyCheck, timeInput, userId, jda);
  }

  public static String scheduleReadyUntil(
      final String readyCheckId, final String timeInput, final String userId, final JDA jda) {
    final ReadyCheck readyCheck = getReadyCheckOrThrow(readyCheckId);
    return ReadyCheckScheduler.scheduleReadyUntil(readyCheck, timeInput, userId, jda);
  }

  public static boolean checkIfAllReady(final String readyCheckId) {
    final ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    return readyCheck != null && allNonPassedReady(readyCheck);
  }

  public static boolean isReadyCheckOngoing(final String readyCheckId) {
    final ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    return readyCheck != null && !allNonPassedReady(readyCheck);
  }

  public static String findActiveReadyCheckInChannel(final String guildId, final String channelId) {
    final long eightHoursAgo = System.currentTimeMillis() - EIGHT_HOURS_MS;

    return activeReadyChecks.values().stream()
        .filter(readyCheck -> readyCheck.getGuildId().equals(guildId))
        .filter(readyCheck -> readyCheck.getChannelId().equals(channelId))
        .filter(readyCheck -> readyCheck.getCreatedTime() >= eightHoursAgo)
        .filter(readyCheck -> readyCheck.getStatus() == ReadyCheckStatus.ACTIVE)
        .filter(readyCheck -> isReadyCheckOngoing(readyCheck.getId()))
        .map(ReadyCheck::getId)
        .findFirst()
        .orElse(null);
  }

  public static String findExistingReadyCheck(
      final String guildId, final SavedReadyCheck savedCheck, final String initiatorId) {
    return activeReadyChecks.values().stream()
        .filter(check -> check.getGuildId().equals(guildId))
        .filter(check -> isReadyCheckOngoing(check.getId()))
        .filter(check -> ReadyCheckUtils.matchesSavedCheck(check, savedCheck, initiatorId))
        .map(ReadyCheck::getId)
        .findFirst()
        .orElse(null);
  }

  public static String findExistingReadyCheck(
      final String guildId, final List<String> targetUserIds, final String initiatorId) {
    return findExistingReadyCheckInternal(
        guildId, ReadyCheckUtils.createUserSet(targetUserIds, initiatorId));
  }

  public static String findActiveReadyCheckForUser(final String guildId, final String userId) {
    final long eightHoursAgo = System.currentTimeMillis() - EIGHT_HOURS_MS;

    return activeReadyChecks.values().stream()
        .filter(readyCheck -> readyCheck.getGuildId().equals(guildId))
        .filter(readyCheck -> readyCheck.getCreatedTime() >= eightHoursAgo)
        .filter(readyCheck -> readyCheck.getStatus() == ReadyCheckStatus.ACTIVE)
        .filter(readyCheck -> ReadyCheckUtils.userCanEngageWithReadyCheck(readyCheck, userId))
        .map(ReadyCheck::getId)
        .findFirst()
        .orElse(null);
  }

  public static void refreshReadyCheckMessage(final String readyCheckId, final JDA jda) {
    final ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    if (readyCheck == null || readyCheck.getMessageId() == null) return;

    final Guild guild = jda.getGuildById(readyCheck.getGuildId());
    final TextChannel channel =
        ReadyCheckUtils.getChannelFromGuild(guild, readyCheck.getChannelId());
    if (channel == null) return;

    channel
        .retrieveMessageById(readyCheck.getMessageId())
        .queue(
            oldMessage -> {
              oldMessage.delete().queue(null, error -> {});
              sendRefreshedReadyCheck(readyCheck, channel, readyCheckId);
            },
            error -> sendRefreshedReadyCheck(readyCheck, channel, readyCheckId));
  }

  public static void createReadyCheckResponse(
      final Object event,
      final String readyCheckId,
      final List<Member> targetMembers,
      final Member initiator,
      final String description) {

    final ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    if (readyCheck == null) return;

    final JDA jda = getJDAFromEvent(event);
    ensureJDAInitialized(jda);

    final String betterDescription = buildDescription(initiator, targetMembers, description);
    readyCheck.setDescription(betterDescription);

    final EmbedBuilder embed =
        ReadyCheckEmbedBuilder.buildReadyCheckEmbed(readyCheck, jda, betterDescription);
    final List<Button> mainButtons = ReadyCheckUtils.createMainButtons(readyCheckId);
    final List<Button> saveButton = ReadyCheckUtils.createSaveButton(readyCheckId);
    final String mentions = ReadyCheckEmbedBuilder.createMentions(readyCheck, jda, readyCheckId);

    handleEventResponse(event, embed, mainButtons, saveButton, mentions, readyCheckId);
  }

  public static void sendReadyCheckToChannel(
      final TextChannel channel,
      final String readyCheckId,
      final List<Member> targetMembers,
      final Member initiator,
      final String description,
      final JDA jda) {

    final ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    if (readyCheck == null) return;

    ensureJDAInitialized(jda);

    final String betterDescription = buildDescription(initiator, targetMembers, description);
    readyCheck.setDescription(betterDescription);

    final EmbedBuilder embed =
        ReadyCheckEmbedBuilder.buildReadyCheckEmbed(readyCheck, jda, betterDescription);
    final List<Button> mainButtons = ReadyCheckUtils.createMainButtons(readyCheckId);
    final List<Button> saveButton = ReadyCheckUtils.createSaveButton(readyCheckId);
    final String mentions = ReadyCheckEmbedBuilder.createMentions(readyCheck, jda, readyCheckId);

    channel
        .sendMessage(mentions)
        .setEmbeds(embed.build())
        .setComponents(ActionRow.of(mainButtons), ActionRow.of(saveButton))
        .queue(message -> readyCheck.setMessageId(message.getId()));
  }

  public static void updateReadyCheckEmbed(final String readyCheckId, final JDA jda) {
    final ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    if (readyCheck == null || readyCheck.getMessageId() == null) return;

    final Guild guild = jda.getGuildById(readyCheck.getGuildId());
    if (guild == null) return;

    final TextChannel channel = guild.getTextChannelById(readyCheck.getChannelId());
    if (channel == null) return;

    handleStatusTransition(readyCheck, channel);
    ReadyCheckScheduler.cleanupExpiredScheduledUsers(readyCheck);
    updateMessage(readyCheck, channel, readyCheckId);
  }

  public static void notifyAllReady(final String readyCheckId, final JDA jda) {
    final ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    if (readyCheck == null) return;

    final Guild guild = jda.getGuildById(readyCheck.getGuildId());
    final TextChannel channel =
        ReadyCheckUtils.getChannelFromGuild(guild, readyCheck.getChannelId());
    if (channel == null) return;

    readyCheck.setStatus(ReadyCheckStatus.COMPLETED);
    final Set<String> allUsers = ReadyCheckUtils.getAllUsers(readyCheck);
    final List<String> readyUserNames = getReadyUserNames(readyCheck, allUsers, guild);
    final String readyUserMentions = createReadyUserMentions(readyCheck, allUsers, guild);

    replaceReadyCheckWithSummary(readyCheckId, jda, readyUserNames, readyUserMentions);
  }

  public static void resendExistingReadyCheck(final String readyCheckId, final JDA jda) {
    final ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    if (readyCheck == null) return;

    final Guild guild = jda.getGuildById(readyCheck.getGuildId());
    final TextChannel channel =
        ReadyCheckUtils.getChannelFromGuild(guild, readyCheck.getChannelId());
    if (channel == null) return;

    final var embed =
        ReadyCheckEmbedBuilder.buildReadyCheckEmbed(readyCheck, jda, readyCheck.getDescription());
    final var mainButtons = ReadyCheckUtils.createMainButtons(readyCheckId);
    final var saveButton = ReadyCheckUtils.createSaveButton(readyCheckId);
    final String mentions = ReadyCheckEmbedBuilder.createMentions(readyCheck, jda, readyCheckId);

    channel
        .sendMessage(mentions)
        .setEmbeds(embed.build())
        .setComponents(ActionRow.of(mainButtons), ActionRow.of(saveButton))
        .queue(message -> readyCheck.setMessageId(message.getId()));
  }

  public static void saveReadyCheck(final String readyCheckId) {
    final ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    if (readyCheck == null) {
      logger.warn("Attempted to save non-existent ready check: {}", readyCheckId);
      return;
    }

    final boolean mentionPeople = getMentionPreference(readyCheckId);
    ReadyCheckPersistence.saveReadyCheck(readyCheck, mentionPeople);
  }

  public static List<SavedReadyCheck> getSavedReadyChecks(final String guildId) {
    return ReadyCheckPersistence.getSavedReadyChecks(guildId);
  }

  public static EmbedBuilder buildReadyCheckEmbedForRecovery(
      final ReadyCheck readyCheck, final JDA jda, final String description) {
    return ReadyCheckEmbedBuilder.buildReadyCheckEmbed(readyCheck, jda, description);
  }

  public static void setReadyCheckMessageId(final String readyCheckId, final String messageId) {
    final ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    if (readyCheck != null) {
      readyCheck.setMessageId(messageId);
    }
  }

  private static void scheduleRecovery() {
    ReadyCheckScheduler.getScheduler()
        .schedule(
            () -> {
              if (globalJDA != null) {
                ReadyCheckRecoveryManager.recoverReadyChecksFromMessages(globalJDA);
              } else {
                logger.warn("JDA not ready for recovery, skipping message recovery");
              }
            },
            10,
            TimeUnit.SECONDS);
  }

  private static void scheduleCompletionMessageDeletion(
      final TextChannel channel, final String messageId) {
    ReadyCheckScheduler.getScheduler()
        .schedule(
            () -> {
              channel
                  .retrieveMessageById(messageId)
                  .queue(
                      message ->
                          message
                              .delete()
                              .queue(
                                  success ->
                                      logger.debug(
                                          "Auto-deleted completion message: {}", messageId),
                                  error ->
                                      logger.debug(
                                          "Failed to auto-delete completion message: {}",
                                          error.getMessage())),
                      error ->
                          logger.debug(
                              "Completion message {} already deleted or not found", messageId));
            },
            30,
            TimeUnit.MINUTES);
  }

  private static void sendRefreshedReadyCheck(
      final ReadyCheck readyCheck, final TextChannel channel, final String readyCheckId) {

    final EmbedBuilder embed =
        ReadyCheckEmbedBuilder.buildReadyCheckEmbed(
            readyCheck, globalJDA, readyCheck.getDescription());
    final List<Button> mainButtons = ReadyCheckUtils.createMainButtons(readyCheckId);
    final List<Button> saveButton = ReadyCheckUtils.createSaveButton(readyCheckId);

    channel
        .sendMessage("")
        .setEmbeds(embed.build())
        .setComponents(ActionRow.of(mainButtons), ActionRow.of(saveButton))
        .queue(newMessage -> readyCheck.setMessageId(newMessage.getId()));
  }

  private static ReadyCheck getReadyCheckOrThrow(final String readyCheckId) {
    final ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    if (readyCheck == null) {
      throw new IllegalArgumentException("Ready check not found");
    }
    return readyCheck;
  }

  private static boolean checkAndUpdateCompletionStatus(
      final ReadyCheck readyCheck, final String readyCheckId) {
    final boolean allReady = readyCheck.getReadyUsers().containsAll(readyCheck.getTargetUsers());
    if (allReady && readyCheck.getStatus() == ReadyCheckStatus.ACTIVE) {
      readyCheck.setStatus(ReadyCheckStatus.COMPLETED);
      logger.info("Ready check completed: {}", readyCheckId);
    }
    return allReady;
  }

  private static void markUserAsReady(final ReadyCheck readyCheck, final String userId) {
    ReadyCheckScheduler.cancelExistingScheduledUser(readyCheck, userId);
    readyCheck.getReadyUsers().add(userId);
    readyCheck.getPassedUsers().remove(userId);
    readyCheck.getUserUntilTimes().remove(userId);
  }

  private static String findExistingReadyCheckInternal(
      final String guildId, final Set<String> targetUsers) {
    return activeReadyChecks.values().stream()
        .filter(check -> check.getGuildId().equals(guildId))
        .filter(check -> isReadyCheckOngoing(check.getId()))
        .filter(check -> checkUserSetMatch(check, targetUsers))
        .map(ReadyCheck::getId)
        .findFirst()
        .orElse(null);
  }

  private static boolean checkUserSetMatch(final ReadyCheck check, final Set<String> targetUsers) {
    final Set<String> checkUsers = new HashSet<>(check.getTargetUsers());
    checkUsers.add(check.getInitiatorId());
    return checkUsers.equals(targetUsers);
  }

  private static JDA getJDAFromEvent(final Object event) {
    return switch (event) {
      case SlashCommandInteractionEvent slashEvent -> slashEvent.getJDA();
      case StringSelectInteractionEvent selectEvent -> selectEvent.getJDA();
      default -> null;
    };
  }

  private static void ensureJDAInitialized(final JDA jda) {
    if (globalJDA == null && jda != null) {
      setJDA(jda);
    }
  }

  private static String buildDescription(
      final Member initiator, final List<Member> targetMembers, final String description) {
    return ReadyCheckUtils.buildCheckDescription(initiator, targetMembers, description);
  }

  private static void handleEventResponse(
      final Object event,
      final EmbedBuilder embed,
      final List<Button> mainButtons,
      final List<Button> saveButton,
      final String mentions,
      final String readyCheckId) {

    switch (event) {
      case SlashCommandInteractionEvent slashEvent ->
          handleSlashCommandResponse(
              slashEvent, embed, mainButtons, saveButton, mentions, readyCheckId);
      case StringSelectInteractionEvent selectEvent ->
          handleSelectMenuResponse(
              selectEvent, embed, mainButtons, saveButton, mentions, readyCheckId);
      default -> logger.warn("Unknown event type for ready check response");
    }
  }

  private static void handleSlashCommandResponse(
      final SlashCommandInteractionEvent event,
      final EmbedBuilder embed,
      final List<Button> mainButtons,
      final List<Button> saveButton,
      final String mentions,
      final String readyCheckId) {

    event
        .reply(mentions)
        .setEmbeds(embed.build())
        .setComponents(ActionRow.of(mainButtons), ActionRow.of(saveButton))
        .queue(
            response ->
                response
                    .retrieveOriginal()
                    .queue(message -> setReadyCheckMessageId(readyCheckId, message.getId())));
  }

  private static void handleSelectMenuResponse(
      final StringSelectInteractionEvent event,
      final EmbedBuilder embed,
      final List<Button> mainButtons,
      final List<Button> saveButton,
      final String mentions,
      final String readyCheckId) {

    event
        .reply(mentions)
        .setEmbeds(embed.build())
        .setComponents(ActionRow.of(mainButtons), ActionRow.of(saveButton))
        .queue(
            response ->
                response
                    .retrieveOriginal()
                    .queue(message -> setReadyCheckMessageId(readyCheckId, message.getId())));
  }

  private static void handleStatusTransition(
      final ReadyCheck readyCheck, final TextChannel channel) {
    final boolean wasCompleted = readyCheck.getStatus() == ReadyCheckStatus.COMPLETED;
    final boolean nowCompleted = allNonPassedReady(readyCheck);

    if (wasCompleted && !nowCompleted && readyCheck.getCompletionMessageId() != null) {
      deleteCompletionMessage(channel, readyCheck);
      readyCheck.setCompletionMessageId(null);
      readyCheck.setStatus(ReadyCheckStatus.ACTIVE);
    }
  }

  private static void deleteCompletionMessage(
      final TextChannel channel, final ReadyCheck readyCheck) {
    channel
        .retrieveMessageById(readyCheck.getCompletionMessageId())
        .queue(message -> message.delete().queue(null, error -> {}), error -> {});
  }

  private static void updateMessage(
      final ReadyCheck readyCheck, final TextChannel channel, final String readyCheckId) {
    channel
        .retrieveMessageById(readyCheck.getMessageId())
        .queue(
            message -> {
              final EmbedBuilder embed =
                  ReadyCheckEmbedBuilder.buildReadyCheckEmbed(
                      readyCheck, globalJDA, readyCheck.getDescription());
              final List<Button> mainButtons = ReadyCheckUtils.createMainButtons(readyCheckId);
              final List<Button> saveButton = ReadyCheckUtils.createSaveButton(readyCheckId);

              message
                  .editMessageEmbeds(embed.build())
                  .setComponents(ActionRow.of(mainButtons), ActionRow.of(saveButton))
                  .queue();
            },
            error -> {});
  }

  private static List<String> getReadyUserNames(
      final ReadyCheck readyCheck, final Set<String> allUsers, final Guild guild) {
    return allUsers.stream()
        .filter(userId -> readyCheck.getReadyUsers().contains(userId))
        .filter(userId -> !readyCheck.getPassedUsers().contains(userId))
        .map(
            userId -> {
              final Member member = guild.getMemberById(userId);
              return member != null ? member.getEffectiveName() : null;
            })
        .filter(Objects::nonNull)
        .toList();
  }

  private static String createReadyUserMentions(
      final ReadyCheck readyCheck, final Set<String> allUsers, final Guild guild) {

    final Set<String> readyUsers =
        allUsers.stream()
            .filter(userId -> readyCheck.getReadyUsers().contains(userId))
            .filter(userId -> !readyCheck.getPassedUsers().contains(userId))
            .collect(Collectors.toSet());

    return VoiceChannelMentionFilter.createCompletionMentions(readyUsers, guild);
  }

  private static void replaceReadyCheckWithSummary(
      final String readyCheckId,
      final JDA jda,
      final List<String> readyUserNames,
      final String mentions) {

    final ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    if (readyCheck == null || readyCheck.getMessageId() == null) return;

    final Guild guild = jda.getGuildById(readyCheck.getGuildId());
    final TextChannel channel =
        ReadyCheckUtils.getChannelFromGuild(guild, readyCheck.getChannelId());
    if (channel == null) return;

    final EmbedBuilder summaryEmbed = createSummaryEmbed(readyCheck, readyUserNames);
    replaceMessageWithSummary(channel, readyCheck, summaryEmbed, mentions);
  }

  private static EmbedBuilder createSummaryEmbed(
      final ReadyCheck readyCheck, final List<String> readyUserNames) {
    final StringBuilder memberList = new StringBuilder();
    readyUserNames.forEach(userName -> memberList.append("✅ ").append(userName).append("\n"));

    return new EmbedBuilder()
        .setTitle("✅ Ready Check Complete")
        .setDescription(readyCheck.getDescription() + "\n\n" + memberList)
        .setColor(Color.GREEN)
        .setTimestamp(Instant.now());
  }

  private static void replaceMessageWithSummary(
      final TextChannel channel,
      final ReadyCheck readyCheck,
      final EmbedBuilder summaryEmbed,
      final String mentions) {

    channel
        .retrieveMessageById(readyCheck.getMessageId())
        .queue(
            oldMessage -> {
              oldMessage.delete().queue(null, error -> {});
              sendSummaryMessage(channel, readyCheck, summaryEmbed, mentions);
            },
            error -> sendSummaryMessage(channel, readyCheck, summaryEmbed, mentions));
  }

  private static void sendSummaryMessage(
      final TextChannel channel,
      final ReadyCheck readyCheck,
      final EmbedBuilder summaryEmbed,
      final String mentions) {

    channel
        .sendMessage(mentions)
        .setEmbeds(summaryEmbed.build())
        .queue(
            newMessage -> {
              readyCheck.setCompletionMessageId(newMessage.getId());
              scheduleCompletionMessageDeletion(channel, newMessage.getId());
            });
  }

  private static String createReadyCheck(
      final String guildId,
      final String channelId,
      final String initiatorId,
      final String roleId,
      final List<Member> targetMembers,
      final String description) {

    final String readyCheckId = UUID.randomUUID().toString();
    final List<String> targetUserIds = targetMembers.stream().map(Member::getId).toList();

    final ReadyCheck readyCheck =
        new ReadyCheck(readyCheckId, guildId, channelId, initiatorId, roleId, targetUserIds);
    readyCheck.setDescription(description);
    readyCheck.getReadyUsers().add(initiatorId);
    readyCheck.getTargetUsers().add(initiatorId);

    activeReadyChecks.put(readyCheckId, readyCheck);
    return readyCheckId;
  }

  private static boolean allNonPassedReady(final ReadyCheck readyCheck) {
    final Set<String> allUsers = ReadyCheckUtils.getAllUsers(readyCheck);
    return allUsers.stream()
        .filter(userId -> !readyCheck.getPassedUsers().contains(userId))
        .allMatch(userId -> readyCheck.getReadyUsers().contains(userId));
  }

  public record ScheduledUser(long readyTimestamp, ScheduledFuture<?> reminderFuture) {
    public void cancel() {
      if (reminderFuture != null && !reminderFuture.isDone()) {
        reminderFuture.cancel(false);
      }
    }
  }

  public static final class ReadyCheck {
    private final String id;
    private final String guildId;
    private final String channelId;
    private final String initiatorId;
    private final String roleId;
    private final Set<String> targetUsers;
    private final Set<String> readyUsers;
    private final Map<String, ScheduledUser> scheduledUsers;
    private final Map<String, ScheduledFuture<?>> scheduledUntilFutures;
    private final Map<String, Integer> userTimers;
    private final Map<String, String> userUntilTimes;
    private final Set<String> passedUsers;
    private final long createdTime;
    private String messageId;
    private String completionMessageId;
    private ReadyCheckStatus status;
    private String description;
    private boolean recovered = false;

    public ReadyCheck(
        final String id,
        final String guildId,
        final String channelId,
        final String initiatorId,
        final String roleId,
        final List<String> targetUserIds) {
      this.id = id;
      this.guildId = guildId;
      this.channelId = channelId;
      this.initiatorId = initiatorId;
      this.roleId = roleId;
      this.targetUsers = new HashSet<>(targetUserIds);
      this.readyUsers = new HashSet<>();
      this.userTimers = new HashMap<>();
      this.userUntilTimes = new HashMap<>();
      this.passedUsers = new HashSet<>();
      this.status = ReadyCheckStatus.ACTIVE;
      this.createdTime = System.currentTimeMillis();
      this.scheduledUsers = new HashMap<>();
      this.scheduledUntilFutures = new HashMap<>();
    }

    public String getId() {
      return id;
    }

    public String getGuildId() {
      return guildId;
    }

    public String getChannelId() {
      return channelId;
    }

    public String getInitiatorId() {
      return initiatorId;
    }

    public String getRoleId() {
      return roleId;
    }

    public Set<String> getTargetUsers() {
      return targetUsers;
    }

    public Set<String> getReadyUsers() {
      return readyUsers;
    }

    public Map<String, ScheduledUser> getScheduledUsers() {
      return scheduledUsers;
    }

    public Map<String, ScheduledFuture<?>> getScheduledUntilFutures() {
      return scheduledUntilFutures;
    }

    public Map<String, Integer> getUserTimers() {
      return userTimers;
    }

    public Map<String, String> getUserUntilTimes() {
      return userUntilTimes;
    }

    public Set<String> getPassedUsers() {
      return passedUsers;
    }

    public String getMessageId() {
      return messageId;
    }

    public String getCompletionMessageId() {
      return completionMessageId;
    }

    public ReadyCheckStatus getStatus() {
      return status;
    }

    public long getCreatedTime() {
      return createdTime;
    }

    public String getDescription() {
      return description;
    }

    public boolean isRecovered() {
      return recovered;
    }

    public void setMessageId(final String messageId) {
      this.messageId = messageId;
    }

    public void setCompletionMessageId(final String completionMessageId) {
      this.completionMessageId = completionMessageId;
    }

    public void setStatus(final ReadyCheckStatus status) {
      this.status = status;
    }

    public void setDescription(final String description) {
      this.description = description;
    }

    public void setRecovered(final boolean recovered) {
      this.recovered = recovered;
    }
  }

  public static final class SavedReadyCheck {
    private final String roleId;
    private final List<String> userIds;
    private final boolean userBased;
    private final boolean mentionPeople;

    public SavedReadyCheck(
        final String roleId, final boolean userBased, final boolean mentionPeople) {
      this.roleId = roleId;
      this.userIds = null;
      this.userBased = userBased;
      this.mentionPeople = mentionPeople;
    }

    public SavedReadyCheck(
        final List<String> userIds, final boolean userBased, final boolean mentionPeople) {
      this.roleId = null;
      this.userIds = userIds;
      this.userBased = userBased;
      this.mentionPeople = mentionPeople;
    }

    public String getRoleId() {
      return roleId;
    }

    public List<String> getUserIds() {
      return userIds;
    }

    public boolean isUserBased() {
      return userBased;
    }

    public boolean getMentionPeople() {
      return mentionPeople;
    }
  }
}
