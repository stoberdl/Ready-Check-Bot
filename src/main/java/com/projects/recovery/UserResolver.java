package com.projects.recovery;

import com.projects.readycheck.ReadyCheckManager;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserResolver {
  private static final Logger logger = LoggerFactory.getLogger(UserResolver.class);

  private UserResolver() {}

  public static String resolveUserId(String displayName, String guildId) {
    if (displayName == null || displayName.trim().isEmpty()) {
      return null;
    }

    Guild guild = ReadyCheckManager.getJDA().getGuildById(guildId);
    if (guild == null) {
      logger.debug("Guild not found: {}", guildId);
      return null;
    }

    String cleanName = displayName.trim();

    List<Member> members = guild.getMembersByEffectiveName(cleanName, true);
    if (!members.isEmpty()) {
      logger.debug("Exact match found for '{}': {}", cleanName, members.getFirst().getId());
      return members.getFirst().getId();
    }

    members = guild.getMembersByEffectiveName(cleanName, false);
    if (!members.isEmpty()) {
      logger.debug("Case-insensitive match found for '{}': {}", cleanName, members.getFirst().getId());
      return members.getFirst().getId();
    }

    String userId = searchByUsername(guild, cleanName);
    if (userId != null) {
      logger.debug("Username match found for '{}': {}", cleanName, userId);
      return userId;
    }

    userId = searchByPartialName(guild, cleanName);
    if (userId != null) {
      logger.debug("Partial match found for '{}': {}", cleanName, userId);
      return userId;
    }

    logger.debug("No match found for display name: '{}'", cleanName);
    return null;
  }

  private static String searchByUsername(Guild guild, String displayName) {
    return guild.getMembers().stream()
        .filter(member -> member.getUser().getName().equalsIgnoreCase(displayName))
        .map(Member::getId)
        .findFirst()
        .orElse(null);
  }

  private static String searchByPartialName(Guild guild, String displayName) {
    String lowerName = displayName.toLowerCase();

    return guild.getMembers().stream()
        .filter(
            member -> {
              String effectiveName = member.getEffectiveName().toLowerCase();
              String userName = member.getUser().getName().toLowerCase();

              return effectiveName.contains(lowerName)
                  || lowerName.contains(effectiveName)
                  || userName.contains(lowerName)
                  || lowerName.contains(userName);
            })
        .map(Member::getId)
        .findFirst()
        .orElse(null);
  }

  public static EnhancedResolvedUserStates resolveEnhancedUserStates(
      List<MessageParser.EnhancedUserState> userStates, String guildId) {
    Set<String> targetUsers = new HashSet<>();
    Set<String> readyUsers = new HashSet<>();
    Set<String> passedUsers = new HashSet<>();
    Set<String> scheduledUsers = new HashSet<>();
    Map<String, MessageParser.TimingData> userTimingData = new HashMap<>();
    int unresolvedCount = 0;

    for (MessageParser.EnhancedUserState userState : userStates) {
      String userId = resolveUserId(userState.displayName(), guildId);

      if (userId != null) {
        targetUsers.add(userId);

        switch (userState.status()) {
          case "✅" -> {
            readyUsers.add(userId);
            if (userState.timing().type() != MessageParser.TimingType.NONE) {
              userTimingData.put(userId, userState.timing());
            }
          }
          case "🚫" -> passedUsers.add(userId);
          case "⏰" -> {
            scheduledUsers.add(userId);
            if (userState.timing().type() != MessageParser.TimingType.NONE) {
              userTimingData.put(userId, userState.timing());
            }
          }
          case "❌" -> {}
          default ->
              logger.debug(
                  "Unknown status '{}' for user '{}'", userState.status(), userState.displayName());
        }
      } else {
        unresolvedCount++;
        logger.debug(
            "Could not resolve user: '{}' with status '{}'",
            userState.displayName(),
            userState.status());
      }
    }

    logger.debug(
        "Resolved {}/{} users in guild {}", targetUsers.size(), userStates.size(), guildId);

    return new EnhancedResolvedUserStates(
        targetUsers, readyUsers, passedUsers, scheduledUsers, userTimingData, unresolvedCount);
  }

  public record EnhancedResolvedUserStates(
      Set<String> targetUsers,
      Set<String> readyUsers,
      Set<String> passedUsers,
      Set<String> scheduledUsers,
      Map<String, MessageParser.TimingData> userTimingData,
      int unresolvedCount) {}
}
