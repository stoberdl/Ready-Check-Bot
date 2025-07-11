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

public class ReadyCheckUtils {

  private ReadyCheckUtils() {}

  public static Set<String> getAllUsers(ReadyCheckManager.ReadyCheck readyCheck) {
    Set<String> allUsers = new HashSet<>(readyCheck.getTargetUsers());
    allUsers.add(readyCheck.getInitiatorId());
    return allUsers;
  }

  public static TextChannel getChannelFromGuild(Guild guild, String channelId) {
    return guild != null ? guild.getTextChannelById(channelId) : null;
  }

  public static List<Button> createMainButtons(String readyCheckId) {
    return Arrays.asList(
        Button.success("toggle_ready_" + readyCheckId, "Toggle Ready"),
        Button.primary("ready_at_" + readyCheckId, "Ready At..."),
        Button.primary("ready_until_" + readyCheckId, "Ready Until..."),
        Button.danger("pass_" + readyCheckId, "Pass"));
  }

  public static List<Button> createSaveButton(String readyCheckId) {
    return Collections.singletonList(Button.secondary("save_ready_" + readyCheckId, "💾"));
  }

  public static String buildCheckDescription(
      Member initiator, List<Member> targetMembers, String originalDescription) {
    boolean initiatorInTargets = targetMembers.contains(initiator);
    int totalUsers = targetMembers.size() + (initiatorInTargets ? 0 : 1);

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
      ReadyCheckManager.ReadyCheck readyCheck, String userId) {
    return readyCheck.getTargetUsers().contains(userId)
        || readyCheck.getPassedUsers().contains(userId)
        || readyCheck.getInitiatorId().equals(userId);
  }

  public static Set<String> createUserSet(List<String> targetUserIds, String initiatorId) {
    Set<String> allUserIds = new HashSet<>(targetUserIds);
    allUserIds.add(initiatorId);
    return allUserIds;
  }

  public static boolean matchesSavedCheck(
      ReadyCheckManager.ReadyCheck check,
      ReadyCheckManager.SavedReadyCheck savedCheck,
      String initiatorId) {
    if (savedCheck.isUserBased()) {
      Set<String> checkUserIds = new HashSet<>(check.getTargetUsers());
      checkUserIds.add(initiatorId);
      return checkUserIds.equals(new HashSet<>(savedCheck.getUserIds()));
    } else {
      return savedCheck.getRoleId().equals(check.getRoleId());
    }
  }
}
