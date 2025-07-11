package com.projects.readycheck;

import com.projects.readycheck.utils.ReadyCheckUtils;
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

public class ReadyCheckManager {
  private static final Logger logger = LoggerFactory.getLogger(ReadyCheckManager.class);
  private static final Map<String, ReadyCheck> activeReadyChecks = new ConcurrentHashMap<>();
  private static final Map<String, Boolean> mentionPreferences = new ConcurrentHashMap<>();
  private static final long TWO_HOURS_MS = TimeUnit.HOURS.toMillis(2);
  private static JDA globalJDA;

  static {
    ReadyCheckScheduler.startPeriodicUpdater();

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

  public enum ReadyCheckStatus {
    ACTIVE,
    COMPLETED
  }

  public static void setJDA(JDA jda) {
    globalJDA = jda;
  }

  public static JDA getJDA() {
    return globalJDA;
  }

  public static Map<String, ReadyCheck> getActiveReadyChecks() {
    return activeReadyChecks;
  }

  public static boolean hasActiveReadyCheck(String readyCheckId) {
    return activeReadyChecks.containsKey(readyCheckId);
  }

  public static ReadyCheck getActiveReadyCheck(String readyCheckId) {
    return activeReadyChecks.get(readyCheckId);
  }

  public static void addRecoveredReadyCheck(String readyCheckId, ReadyCheck readyCheck) {
    activeReadyChecks.put(readyCheckId, readyCheck);
    logger.info("Added recovered ready check: {}", readyCheckId);
  }

  public static ReadyCheck createRecoveredReadyCheck(
      String readyCheckId,
      String guildId,
      String channelId,
      String initiatorId,
      String roleId,
      Set<String> targetUsers,
      Set<String> readyUsers,
      Set<String> passedUsers,
      String messageId) {

    ReadyCheck recoveredCheck =
        new ReadyCheck(
            readyCheckId, guildId, channelId, initiatorId, roleId, new ArrayList<>(targetUsers));

    recoveredCheck.getTargetUsers().clear();
    recoveredCheck.getTargetUsers().addAll(targetUsers);
    recoveredCheck.getReadyUsers().addAll(readyUsers);
    recoveredCheck.getPassedUsers().addAll(passedUsers);
    recoveredCheck.setMessageId(messageId);

    return recoveredCheck;
  }

  public static EmbedBuilder buildReadyCheckEmbedForRecovery(
      ReadyCheck readyCheck, JDA jda, String description) {
    return ReadyCheckEmbedBuilder.buildReadyCheckEmbed(readyCheck, jda, description);
  }

  public static void setMentionPreference(String readyCheckId, boolean mentionPeople) {
    mentionPreferences.put(readyCheckId, mentionPeople);
  }

  public static boolean getMentionPreference(String readyCheckId) {
    return mentionPreferences.getOrDefault(readyCheckId, true);
  }

  public static void createReadyCheckResponse(
      Object event,
      String readyCheckId,
      List<Member> targetMembers,
      Member initiator,
      String description) {
    ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    if (readyCheck == null) return;

    JDA jda = getJDAFromEvent(event);
    if (globalJDA == null && jda != null) {
      setJDA(jda);
    }

    String betterDescription =
        ReadyCheckUtils.buildCheckDescription(initiator, targetMembers, description);
    readyCheck.setDescription(betterDescription);

    EmbedBuilder embed =
        ReadyCheckEmbedBuilder.buildReadyCheckEmbed(readyCheck, jda, betterDescription);
    List<Button> mainButtons = ReadyCheckUtils.createMainButtons(readyCheckId);
    List<Button> saveButton = ReadyCheckUtils.createSaveButton(readyCheckId);
    String mentions = ReadyCheckEmbedBuilder.createMentions(readyCheck, jda, readyCheckId);

    if (event instanceof SlashCommandInteractionEvent slashEvent) {
      handleSlashCommandResponse(
          slashEvent, embed, mainButtons, saveButton, mentions, readyCheckId);
    } else if (event instanceof StringSelectInteractionEvent selectEvent) {
      handleSelectMenuResponse(selectEvent, embed, mainButtons, saveButton, mentions, readyCheckId);
    }
  }

  public static boolean isReadyCheckOngoing(String readyCheckId) {
    ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    return readyCheck != null && !allNonPassedReady(readyCheck);
  }

  public static String findExistingReadyCheck(
      String guildId, SavedReadyCheck savedCheck, String initiatorId) {
    return activeReadyChecks.values().stream()
        .filter(check -> check.getGuildId().equals(guildId))
        .filter(check -> isReadyCheckOngoing(check.getId()))
        .filter(check -> ReadyCheckUtils.matchesSavedCheck(check, savedCheck, initiatorId))
        .map(ReadyCheck::getId)
        .findFirst()
        .orElse(null);
  }

  public static String findExistingReadyCheck(
      String guildId, List<String> targetUserIds, String initiatorId) {
    return findExistingReadyCheckInternal(
        guildId, ReadyCheckUtils.createUserSet(targetUserIds, initiatorId));
  }

  public static String createUserReadyCheck(
      String guildId, String channelId, String initiatorId, List<Member> targetMembers) {
    return createReadyCheck(
        guildId, channelId, initiatorId, null, targetMembers, "**Ready Check** for specific users");
  }

  public static String createReadyCheck(
      String guildId,
      String channelId,
      String initiatorId,
      String roleId,
      List<Member> targetMembers) {
    return createReadyCheck(
        guildId, channelId, initiatorId, roleId, targetMembers, "**Ready Check** for role members");
  }

  public static void scheduleReadyAt(
      String readyCheckId, String timeInput, String userId, JDA jda) {
    ReadyCheck readyCheck = getReadyCheckOrThrow(readyCheckId);
    ReadyCheckScheduler.scheduleReadyAt(readyCheck, timeInput, userId, jda);
  }

  public static String scheduleReadyAtSmart(
      String readyCheckId, String timeInput, String userId, JDA jda) {
    ReadyCheck readyCheck = getReadyCheckOrThrow(readyCheckId);
    return ReadyCheckScheduler.scheduleReadyAtSmart(readyCheck, timeInput, userId, jda);
  }

  public static String scheduleReadyUntil(
      String readyCheckId, String timeInput, String userId, JDA jda) {
    ReadyCheck readyCheck = getReadyCheckOrThrow(readyCheckId);
    return ReadyCheckScheduler.scheduleReadyUntil(readyCheck, timeInput, userId, jda);
  }

  public static void updateReadyCheckEmbed(String readyCheckId, JDA jda) {
    ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    if (readyCheck == null || readyCheck.getMessageId() == null) return;

    Guild guild = jda.getGuildById(readyCheck.getGuildId());
    if (guild == null) return;

    TextChannel channel = guild.getTextChannelById(readyCheck.getChannelId());
    if (channel == null) return;

    handleStatusTransition(readyCheck, channel);
    ReadyCheckScheduler.cleanupExpiredScheduledUsers(readyCheck);
    updateMessage(readyCheck, channel, readyCheckId);
  }

  public static boolean checkIfAllReady(String readyCheckId) {
    ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    return readyCheck != null && allNonPassedReady(readyCheck);
  }

  public static boolean markUserReady(String readyCheckId, String userId) {
    ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    if (readyCheck == null) {
      logger.warn(
          "Attempted to mark user {} ready for non-existent ready check: {}", userId, readyCheckId);
      return false;
    }

    ReadyCheckScheduler.cancelExistingScheduledUser(readyCheck, userId);
    readyCheck.getUserTimers().remove(userId);
    readyCheck.getReadyUsers().add(userId);

    boolean allReady = readyCheck.getReadyUsers().containsAll(readyCheck.getTargetUsers());
    if (allReady && readyCheck.getStatus() == ReadyCheckStatus.ACTIVE) {
      readyCheck.setStatus(ReadyCheckStatus.COMPLETED);
      logger.info("Ready check completed: {}", readyCheckId);
    }

    logger.debug("User {} marked as ready for ready check: {}", userId, readyCheckId);
    return allReady;
  }

  public static void notifyAllReady(String readyCheckId, JDA jda) {
    ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    if (readyCheck == null) return;

    Guild guild = jda.getGuildById(readyCheck.getGuildId());
    TextChannel channel = ReadyCheckUtils.getChannelFromGuild(guild, readyCheck.getChannelId());
    if (channel == null) return;

    readyCheck.setStatus(ReadyCheckStatus.COMPLETED);
    Set<String> allUsers = ReadyCheckUtils.getAllUsers(readyCheck);
    List<String> readyUserNames = getReadyUserNames(readyCheck, allUsers, guild);
    String readyUserMentions = createReadyUserMentions(readyCheck, allUsers, guild, readyCheckId);

    replaceReadyCheckWithSummary(readyCheckId, jda, readyUserNames, readyUserMentions);
  }

  public static void ensureUserInReadyCheck(String readyCheckId, String userId) {
    ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    if (readyCheck != null) {
      readyCheck.getTargetUsers().add(userId);
    }
  }

  public static boolean toggleUserReady(String readyCheckId, String userId) {
    ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    if (readyCheck == null) return false;

    readyCheck.getTargetUsers().add(userId);

    if (readyCheck.getReadyUsers().contains(userId)) {
      readyCheck.getReadyUsers().remove(userId);
      return false;
    } else {
      ReadyCheckScheduler.cancelExistingScheduledUser(readyCheck, userId);
      readyCheck.getReadyUsers().add(userId);
      readyCheck.getPassedUsers().remove(userId);
      readyCheck.getUserUntilTimes().remove(userId);
      return true;
    }
  }

  public static void markUserPassed(String readyCheckId, String userId) {
    ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    if (readyCheck == null) return;

    readyCheck.getTargetUsers().add(userId);
    readyCheck.getPassedUsers().add(userId);
    readyCheck.getReadyUsers().remove(userId);
    readyCheck.getScheduledUsers().remove(userId);
    readyCheck.getUserUntilTimes().remove(userId);
  }

  public static void unmarkUserPassed(String readyCheckId, String userId) {
    ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    if (readyCheck != null) {
      readyCheck.getPassedUsers().remove(userId);
    }
  }

  public static String findActiveReadyCheckForUser(String guildId, String userId) {
    long twoHoursAgo = System.currentTimeMillis() - TWO_HOURS_MS;

    return activeReadyChecks.values().stream()
        .filter(readyCheck -> readyCheck.getGuildId().equals(guildId))
        .filter(readyCheck -> readyCheck.getCreatedTime() >= twoHoursAgo)
        .filter(readyCheck -> readyCheck.getStatus() == ReadyCheckStatus.ACTIVE)
        .filter(readyCheck -> ReadyCheckUtils.userCanEngageWithReadyCheck(readyCheck, userId))
        .map(ReadyCheck::getId)
        .findFirst()
        .orElse(null);
  }

  public static void saveReadyCheck(String readyCheckId) {
    ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    if (readyCheck == null) {
      logger.warn("Attempted to save non-existent ready check: {}", readyCheckId);
      return;
    }

    boolean mentionPeople = getMentionPreference(readyCheckId);
    ReadyCheckPersistence.saveReadyCheck(readyCheck, mentionPeople);
  }

  public static void sendReadyCheckToChannel(
      TextChannel channel,
      String readyCheckId,
      List<Member> targetMembers,
      Member initiator,
      String description,
      JDA jda) {
    ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    if (readyCheck == null) return;

    if (globalJDA == null) {
      setJDA(jda);
    }

    String betterDescription =
        ReadyCheckUtils.buildCheckDescription(initiator, targetMembers, description);
    readyCheck.setDescription(betterDescription);

    EmbedBuilder embed =
        ReadyCheckEmbedBuilder.buildReadyCheckEmbed(readyCheck, jda, betterDescription);
    List<Button> mainButtons = ReadyCheckUtils.createMainButtons(readyCheckId);
    List<Button> saveButton = ReadyCheckUtils.createSaveButton(readyCheckId);
    String mentions = ReadyCheckEmbedBuilder.createMentions(readyCheck, jda, readyCheckId);

    channel
        .sendMessage(mentions)
        .setEmbeds(embed.build())
        .setComponents(ActionRow.of(mainButtons), ActionRow.of(saveButton))
        .queue(message -> readyCheck.setMessageId(message.getId()));
  }

  public static List<SavedReadyCheck> getSavedReadyChecks(String guildId) {
    return ReadyCheckPersistence.getSavedReadyChecks(guildId);
  }

  public static void resendExistingReadyCheck(String readyCheckId, JDA jda) {
    ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    if (readyCheck == null) return;

    Guild guild = jda.getGuildById(readyCheck.getGuildId());
    TextChannel channel = ReadyCheckUtils.getChannelFromGuild(guild, readyCheck.getChannelId());
    if (channel == null) return;

    EmbedBuilder embed =
        ReadyCheckEmbedBuilder.buildReadyCheckEmbed(readyCheck, jda, readyCheck.getDescription());
    List<Button> mainButtons = ReadyCheckUtils.createMainButtons(readyCheckId);
    List<Button> saveButton = ReadyCheckUtils.createSaveButton(readyCheckId);
    String mentions = ReadyCheckEmbedBuilder.createMentions(readyCheck, jda, readyCheckId);

    channel
        .sendMessage(mentions)
        .setEmbeds(embed.build())
        .setComponents(ActionRow.of(mainButtons), ActionRow.of(saveButton))
        .queue(message -> readyCheck.setMessageId(message.getId()));
  }

  public static void setReadyCheckMessageId(String readyCheckId, String messageId) {
    ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    if (readyCheck != null) {
      readyCheck.setMessageId(messageId);
    }
  }

  private static JDA getJDAFromEvent(Object event) {
    return switch (event) {
      case SlashCommandInteractionEvent slashEvent -> slashEvent.getJDA();
      case StringSelectInteractionEvent selectEvent -> selectEvent.getJDA();
      default -> null;
    };
  }

  private static ReadyCheck getReadyCheckOrThrow(String readyCheckId) {
    ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    if (readyCheck == null) {
      throw new IllegalArgumentException("Ready check not found");
    }
    return readyCheck;
  }

  private static void handleStatusTransition(ReadyCheck readyCheck, TextChannel channel) {
    boolean wasCompleted = readyCheck.getStatus() == ReadyCheckStatus.COMPLETED;
    boolean nowCompleted = allNonPassedReady(readyCheck);

    if (wasCompleted && !nowCompleted && readyCheck.getCompletionMessageId() != null) {
      channel
          .retrieveMessageById(readyCheck.getCompletionMessageId())
          .queue(message -> message.delete().queue(null, error -> {}), error -> {});
      readyCheck.setCompletionMessageId(null);
      readyCheck.setStatus(ReadyCheckStatus.ACTIVE);
    }
  }

  private static void updateMessage(
      ReadyCheck readyCheck, TextChannel channel, String readyCheckId) {
    channel
        .retrieveMessageById(readyCheck.getMessageId())
        .queue(
            message -> {
              EmbedBuilder embed =
                  ReadyCheckEmbedBuilder.buildReadyCheckEmbed(
                      readyCheck, globalJDA, readyCheck.getDescription());
              List<Button> mainButtons = ReadyCheckUtils.createMainButtons(readyCheckId);
              List<Button> saveButton = ReadyCheckUtils.createSaveButton(readyCheckId);

              message
                  .editMessageEmbeds(embed.build())
                  .setComponents(ActionRow.of(mainButtons), ActionRow.of(saveButton))
                  .queue();
            },
            error -> {});
  }

  private static List<String> getReadyUserNames(
      ReadyCheck readyCheck, Set<String> allUsers, Guild guild) {
    return allUsers.stream()
        .filter(userId -> readyCheck.getReadyUsers().contains(userId))
        .filter(userId -> !readyCheck.getPassedUsers().contains(userId))
        .map(
            userId -> {
              Member member = guild.getMemberById(userId);
              return member != null ? member.getEffectiveName() : null;
            })
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  private static String createReadyUserMentions(
      ReadyCheck readyCheck, Set<String> allUsers, Guild guild, String readyCheckId) {
    if (!getMentionPreference(readyCheckId)) {
      return "";
    }

    return allUsers.stream()
        .filter(userId -> readyCheck.getReadyUsers().contains(userId))
        .filter(userId -> !readyCheck.getPassedUsers().contains(userId))
        .map(
            userId -> {
              Member member = guild.getMemberById(userId);
              return member != null ? member.getAsMention() : null;
            })
        .filter(Objects::nonNull)
        .collect(Collectors.joining(" "));
  }

  private static void replaceReadyCheckWithSummary(
      String readyCheckId, JDA jda, List<String> readyUserNames, String mentions) {
    ReadyCheck readyCheck = activeReadyChecks.get(readyCheckId);
    if (readyCheck == null || readyCheck.getMessageId() == null) return;

    Guild guild = jda.getGuildById(readyCheck.getGuildId());
    TextChannel channel = ReadyCheckUtils.getChannelFromGuild(guild, readyCheck.getChannelId());
    if (channel == null) return;

    StringBuilder memberList = new StringBuilder();
    readyUserNames.forEach(userName -> memberList.append("✅ ").append(userName).append("\n"));

    EmbedBuilder summaryEmbed =
        new EmbedBuilder()
            .setTitle("✅ Ready Check Complete")
            .setDescription(readyCheck.getDescription() + "\n\n" + memberList)
            .setColor(Color.GREEN)
            .setTimestamp(Instant.now());

    channel
        .retrieveMessageById(readyCheck.getMessageId())
        .queue(
            oldMessage -> {
              oldMessage.delete().queue(null, error -> {});
              channel
                  .sendMessage(mentions)
                  .setEmbeds(summaryEmbed.build())
                  .queue(newMessage -> readyCheck.setMessageId(newMessage.getId()));
            },
            error ->
                channel
                    .sendMessage(mentions)
                    .setEmbeds(summaryEmbed.build())
                    .queue(newMessage -> readyCheck.setMessageId(newMessage.getId())));
  }

  private static String createReadyCheck(
      String guildId,
      String channelId,
      String initiatorId,
      String roleId,
      List<Member> targetMembers,
      String description) {
    String readyCheckId = UUID.randomUUID().toString();

    List<String> targetUserIds =
        targetMembers.stream().map(Member::getId).collect(Collectors.toList());

    ReadyCheck readyCheck =
        new ReadyCheck(readyCheckId, guildId, channelId, initiatorId, roleId, targetUserIds);
    readyCheck.setDescription(description);

    readyCheck.getReadyUsers().add(initiatorId);
    readyCheck.getTargetUsers().add(initiatorId);

    activeReadyChecks.put(readyCheckId, readyCheck);
    return readyCheckId;
  }

  private static String findExistingReadyCheckInternal(String guildId, Set<String> targetUsers) {
    return activeReadyChecks.values().stream()
        .filter(check -> check.getGuildId().equals(guildId))
        .filter(check -> isReadyCheckOngoing(check.getId()))
        .filter(
            check -> {
              Set<String> checkUsers = new HashSet<>(check.getTargetUsers());
              checkUsers.add(check.getInitiatorId());
              return checkUsers.equals(targetUsers);
            })
        .map(ReadyCheck::getId)
        .findFirst()
        .orElse(null);
  }

  private static boolean allNonPassedReady(ReadyCheck readyCheck) {
    Set<String> allUsers = ReadyCheckUtils.getAllUsers(readyCheck);
    return allUsers.stream()
        .filter(userId -> !readyCheck.getPassedUsers().contains(userId))
        .allMatch(userId -> readyCheck.getReadyUsers().contains(userId));
  }

  private static void handleSlashCommandResponse(
      SlashCommandInteractionEvent event,
      EmbedBuilder embed,
      List<Button> mainButtons,
      List<Button> saveButton,
      String mentions,
      String readyCheckId) {
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
      StringSelectInteractionEvent event,
      EmbedBuilder embed,
      List<Button> mainButtons,
      List<Button> saveButton,
      String mentions,
      String readyCheckId) {
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

  public record ScheduledUser(long readyTimestamp, ScheduledFuture<?> reminderFuture) {
    public void cancel() {
      if (reminderFuture != null && !reminderFuture.isDone()) {
        reminderFuture.cancel(false);
      }
    }
  }

  public static class ReadyCheck {
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
        String id,
        String guildId,
        String channelId,
        String initiatorId,
        String roleId,
        List<String> targetUserIds) {
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

    // Getters and setters
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

    public void setMessageId(String messageId) {
      this.messageId = messageId;
    }

    public void setCompletionMessageId(String completionMessageId) {
      this.completionMessageId = completionMessageId;
    }

    public void setStatus(ReadyCheckStatus status) {
      this.status = status;
    }

    public void setDescription(String description) {
      this.description = description;
    }

    public void setRecovered(boolean recovered) {
      this.recovered = recovered;
    }
  }

  public static class SavedReadyCheck {
    private final String roleId;
    private final List<String> userIds;
    private final boolean userBased;
    private final boolean mentionPeople;

    public SavedReadyCheck(String roleId, boolean userBased, boolean mentionPeople) {
      this.roleId = roleId;
      this.userIds = null;
      this.userBased = userBased;
      this.mentionPeople = mentionPeople;
    }

    public SavedReadyCheck(List<String> userIds, boolean userBased, boolean mentionPeople) {
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
