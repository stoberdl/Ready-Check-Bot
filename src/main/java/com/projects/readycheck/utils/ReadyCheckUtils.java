package com.projects.readycheck.utils;

import com.projects.readycheck.ReadyCheckManager;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

public final class ReadyCheckUtils {

  private ReadyCheckUtils() {}

  public static Set<String> getAllUsers(final ReadyCheckManager.ReadyCheck readyCheck) {
    final Set<String> allUsers = new HashSet<>(readyCheck.getTargetUsers());
    allUsers.add(readyCheck.getInitiatorId());
    return allUsers;
  }

  public static TextChannel getChannelFromGuild(final Guild guild, final String channelId) {
    return guild != null ? guild.getTextChannelById(channelId) : null;
  }

  public static List<Button> createMainButtons(final String readyCheckId) {
    return Arrays.asList(
        Button.success("toggle_ready_" + readyCheckId, "Toggle Ready"),
        Button.primary("ready_at_" + readyCheckId, "Ready At..."),
        Button.danger("pass_" + readyCheckId, "Pass"));
  }

  public static List<Button> createSaveButton(final String readyCheckId) {
    return Collections.singletonList(Button.secondary("save_ready_" + readyCheckId, "💾"));
  }

  public static String buildCheckDescription(
      final Member initiator, final List<Member> targetMembers, final String originalDescription) {
    final boolean initiatorInTargets = targetMembers.contains(initiator);
    final int totalUsers = targetMembers.size() + (initiatorInTargets ? 0 : 1);

    if (originalDescription.contains("specific users")) {
      return "**"
          + initiator.getEffectiveName()
          + "** started a ready check for "
          + totalUsers
          + " users";
    }

    return originalDescription;
  }

  public static boolean userCanEngageWithReadyCheck(
      final ReadyCheckManager.ReadyCheck readyCheck, final String userId) {
    return readyCheck.getTargetUsers().contains(userId)
        || readyCheck.getPassedUsers().contains(userId)
        || readyCheck.getInitiatorId().equals(userId);
  }

  public static Set<String> createUserSet(
      final List<String> targetUserIds, final String initiatorId) {
    final Set<String> allUserIds = new HashSet<>(targetUserIds);
    allUserIds.add(initiatorId);
    return allUserIds;
  }

  public static boolean matchesSavedCheck(
      final ReadyCheckManager.ReadyCheck check,
      final ReadyCheckManager.SavedReadyCheck savedCheck,
      final String initiatorId) {
    if (savedCheck.isUserBased()) {
      final Set<String> checkUserIds = new HashSet<>(check.getTargetUsers());
      checkUserIds.add(initiatorId);
      return checkUserIds.equals(new HashSet<>(savedCheck.getUserIds()));
    } else {
      return savedCheck.getRoleId().equals(check.getRoleId());
    }
  }
}
