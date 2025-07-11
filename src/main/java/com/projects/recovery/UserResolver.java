package com.projects.recovery;

import com.projects.readycheck.ReadyCheckManager;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserResolver {
  private static final Logger logger = LoggerFactory.getLogger(UserResolver.class);

  private UserResolver() {
    // Private constructor to hide implicit public one
  }

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
      logger.debug("Exact match found for '{}': {}", cleanName, members.get(0).getId());
      return members.get(0).getId();
    }

    members = guild.getMembersByEffectiveName(cleanName, false);
    if (!members.isEmpty()) {
      logger.debug("Case-insensitive match found for '{}': {}", cleanName, members.get(0).getId());
      return members.get(0).getId();
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

  public static ResolvedUserStates resolveUserStates(List<UserState> userStates, String guildId) {
    Set<String> targetUsers = new HashSet<>();
    Set<String> readyUsers = new HashSet<>();
    Set<String> passedUsers = new HashSet<>();
    Set<String> scheduledUsers = new HashSet<>();
    int unresolvedCount = 0;

    for (UserState userState : userStates) {
      String userId = resolveUserId(userState.displayName(), guildId);

      if (userId != null) {
        targetUsers.add(userId);

        switch (userState.status()) {
          case "✅" -> readyUsers.add(userId);
          case "🚫" -> passedUsers.add(userId);
          case "⏰" -> scheduledUsers.add(userId);
          case "❌" -> {
            // Not ready, just in targets
          }
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

    return new ResolvedUserStates(
        targetUsers, readyUsers, passedUsers, scheduledUsers, unresolvedCount);
  }

  public static boolean validateUserId(String userId, String guildId) {
    if (userId == null || guildId == null) {
      return false;
    }

    Guild guild = ReadyCheckManager.getJDA().getGuildById(guildId);
    if (guild == null) {
      return false;
    }

    Member member = guild.getMemberById(userId);
    return member != null;
  }

  public static String getCurrentDisplayName(String userId, String guildId) {
    Guild guild = ReadyCheckManager.getJDA().getGuildById(guildId);
    if (guild == null) {
      return "Unknown User";
    }

    Member member = guild.getMemberById(userId);
    return member != null ? member.getEffectiveName() : "Unknown User";
  }

  public static List<String> findPotentialMatches(String displayName, String guildId) {
    Guild guild = ReadyCheckManager.getJDA().getGuildById(guildId);
    if (guild == null) {
      return List.of();
    }

    String lowerName = displayName.toLowerCase();

    return guild.getMembers().stream()
        .filter(
            member -> {
              String effectiveName = member.getEffectiveName().toLowerCase();
              String userName = member.getUser().getName().toLowerCase();

              return effectiveName.contains(lowerName)
                  || userName.contains(lowerName)
                  || lowerName.contains(effectiveName)
                  || lowerName.contains(userName);
            })
        .map(member -> String.format("%s (%s)", member.getEffectiveName(), member.getId()))
        .toList();
  }
}
