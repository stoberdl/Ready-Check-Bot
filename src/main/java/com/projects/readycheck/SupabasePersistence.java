package com.projects.readycheck;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SupabasePersistence {
  private static final Logger logger = LoggerFactory.getLogger(SupabasePersistence.class);
  private static final OkHttpClient client = new OkHttpClient();
  private static final Gson gson = new Gson();
  private static final String SUPABASE_URL = System.getenv("SUPABASE_URL");
  private static final String SUPABASE_KEY = System.getenv("SUPABASE_KEY");
  private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

  private SupabasePersistence() {}

  static {
    if (SUPABASE_URL == null || SUPABASE_KEY == null) {
      throw new IllegalStateException(
          "SUPABASE_URL and SUPABASE_KEY environment variables must be set");
    }
  }

  public static void saveReadyCheck(
      ReadyCheckManager.ReadyCheck readyCheck, boolean mentionPeople) {
    try {
      Map<String, Object> config = new HashMap<>();
      config.put("guild_id", readyCheck.getGuildId());
      config.put("mention_people", mentionPeople);

      if (readyCheck.getRoleId() != null) {
        config.put("role_id", readyCheck.getRoleId());
        config.put("user_based", false);
      } else {
        config.put("user_ids", readyCheck.getTargetUsers().toArray(new String[0]));
        config.put("user_based", true);
      }

      RequestBody body = RequestBody.create(gson.toJson(config), JSON);
      Request request =
          new Request.Builder()
              .url(SUPABASE_URL + "/rest/v1/saved_configs")
              .header("apikey", SUPABASE_KEY)
              .header("Authorization", "Bearer " + SUPABASE_KEY)
              .header("Prefer", "resolution=merge-duplicates")
              .post(body)
              .build();

      client.newCall(request).execute().close();
      logger.info("Saved ready check configuration for guild: {}", readyCheck.getGuildId());

    } catch (Exception e) {
      logger.error("Failed to save ready check configuration: {}", e.getMessage(), e);
    }
  }

  public static List<ReadyCheckManager.SavedReadyCheck> getSavedReadyChecks(String guildId) {
    try {
      Request request =
          new Request.Builder()
              .url(
                  SUPABASE_URL
                      + "/rest/v1/saved_configs?guild_id=eq."
                      + guildId
                      + "&order=created_at.desc")
              .header("apikey", SUPABASE_KEY)
              .header("Authorization", "Bearer " + SUPABASE_KEY)
              .build();

      try (Response response = client.newCall(request).execute()) {
        String responseBody = response.body().string();
        Type listType = new TypeToken<List<Map<String, Object>>>() {}.getType();
        List<Map<String, Object>> configs = gson.fromJson(responseBody, listType);

        return configs.stream().map(SupabasePersistence::mapToSavedReadyCheck).toList();
      }
    } catch (Exception e) {
      logger.error("Failed to load saved configurations: {}", e.getMessage(), e);
      return List.of();
    }
  }

  public static void saveActiveReadyCheck(ReadyCheckManager.ReadyCheck readyCheck) {
    try {
      Map<String, Object> scheduledUsersData = new HashMap<>();
      for (Map.Entry<String, ReadyCheckManager.ScheduledUser> entry :
          readyCheck.getScheduledUsers().entrySet()) {
        Map<String, Object> userData = new HashMap<>();
        userData.put("readyTimestamp", entry.getValue().readyTimestamp());
        userData.put("reminderFuture", new HashMap<>());
        scheduledUsersData.put(entry.getKey(), userData);
      }

      Map<String, Object> data = new HashMap<>();
      data.put("id", readyCheck.getId());
      data.put("guild_id", readyCheck.getGuildId());
      data.put("channel_id", readyCheck.getChannelId());
      data.put("initiator_id", readyCheck.getInitiatorId());
      data.put("role_id", readyCheck.getRoleId());
      data.put("target_users", readyCheck.getTargetUsers().toArray(new String[0]));
      data.put("ready_users", readyCheck.getReadyUsers().toArray(new String[0]));
      data.put("passed_users", readyCheck.getPassedUsers().toArray(new String[0]));
      data.put("scheduled_users", gson.toJson(scheduledUsersData));
      data.put("user_timers", gson.toJson(readyCheck.getUserTimers()));
      data.put("description", readyCheck.getDescription());
      data.put("status", readyCheck.getStatus().name());
      data.put("message_id", readyCheck.getMessageId());

      RequestBody body = RequestBody.create(gson.toJson(data), JSON);
      Request request =
          new Request.Builder()
              .url(SUPABASE_URL + "/rest/v1/ready_checks")
              .header("apikey", SUPABASE_KEY)
              .header("Authorization", "Bearer " + SUPABASE_KEY)
              .header("Prefer", "resolution=merge-duplicates")
              .post(body)
              .build();

      client.newCall(request).execute().close();

    } catch (Exception e) {
      logger.error("Failed to save active ready check: {}", e.getMessage(), e);
    }
  }

  public static List<ReadyCheckManager.ReadyCheck> loadActiveReadyChecks() {
    try {
      long twelveHoursAgo = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(12);
      String cutoffTime = java.time.Instant.ofEpochMilli(twelveHoursAgo).toString();

      Request request =
          new Request.Builder()
              .url(
                  SUPABASE_URL
                      + "/rest/v1/ready_checks?status=eq.ACTIVE&created_at=gte."
                      + cutoffTime)
              .header("apikey", SUPABASE_KEY)
              .header("Authorization", "Bearer " + SUPABASE_KEY)
              .build();

      try (Response response = client.newCall(request).execute()) {
        String responseBody = response.body().string();
        Type listType = new TypeToken<List<Map<String, Object>>>() {}.getType();
        List<Map<String, Object>> checks = gson.fromJson(responseBody, listType);

        return checks.stream().map(SupabasePersistence::mapToReadyCheck).toList();
      }
    } catch (Exception e) {
      logger.error("Failed to load active ready checks: {}", e.getMessage(), e);
      return List.of();
    }
  }

  public static void deleteActiveReadyCheck(String readyCheckId) {
    try {
      Request request =
          new Request.Builder()
              .url(SUPABASE_URL + "/rest/v1/ready_checks?id=eq." + readyCheckId)
              .header("apikey", SUPABASE_KEY)
              .header("Authorization", "Bearer " + SUPABASE_KEY)
              .delete()
              .build();

      client.newCall(request).execute().close();
    } catch (Exception e) {
      logger.error("Failed to delete ready check: {}", e.getMessage(), e);
    }
  }

  private static ReadyCheckManager.SavedReadyCheck mapToSavedReadyCheck(
      Map<String, Object> config) {
    boolean userBased = (Boolean) config.get("user_based");
    boolean mentionPeople = (Boolean) config.getOrDefault("mention_people", true);

    if (userBased) {
      List<String> userIds = (List<String>) config.get("user_ids");
      return new ReadyCheckManager.SavedReadyCheck(userIds, true, mentionPeople);
    } else {
      String roleId = (String) config.get("role_id");
      return new ReadyCheckManager.SavedReadyCheck(roleId, false, mentionPeople);
    }
  }

  private static ReadyCheckManager.ReadyCheck mapToReadyCheck(Map<String, Object> data) {
    String id = (String) data.get("id");
    String guildId = (String) data.get("guild_id");
    String channelId = (String) data.get("channel_id");
    String initiatorId = (String) data.get("initiator_id");
    String roleId = (String) data.get("role_id");
    List<String> targetUsers = (List<String>) data.get("target_users");

    ReadyCheckManager.ReadyCheck readyCheck =
        new ReadyCheckManager.ReadyCheck(id, guildId, channelId, initiatorId, roleId, targetUsers);

    List<String> readyUsers = (List<String>) data.get("ready_users");
    if (readyUsers != null) {
      readyCheck.getReadyUsers().addAll(readyUsers);
    }

    List<String> passedUsers = (List<String>) data.get("passed_users");
    if (passedUsers != null) {
      readyCheck.getPassedUsers().addAll(passedUsers);
    }

    String scheduledUsersJson = (String) data.get("scheduled_users");
    if (scheduledUsersJson != null && !scheduledUsersJson.isEmpty()) {
      try {
        Type mapType = new TypeToken<Map<String, Map<String, Object>>>() {}.getType();
        Map<String, Map<String, Object>> scheduledData = gson.fromJson(scheduledUsersJson, mapType);
        for (Map.Entry<String, Map<String, Object>> entry : scheduledData.entrySet()) {
          String userId = entry.getKey();
          Map<String, Object> userData = entry.getValue();
          Object timestampObj = userData.get("readyTimestamp");
          if (timestampObj instanceof Number) {
            long timestamp = ((Number) timestampObj).longValue();
            readyCheck
                .getScheduledUsers()
                .put(userId, new ReadyCheckManager.ScheduledUser(timestamp, null));
          }
        }
      } catch (Exception e) {
        logger.debug("Failed to parse scheduled users: {}", e.getMessage());
      }
    }

    String userTimersJson = (String) data.get("user_timers");
    if (userTimersJson != null && !userTimersJson.isEmpty()) {
      try {
        Type mapType = new TypeToken<Map<String, Integer>>() {}.getType();
        Map<String, Integer> userTimers = gson.fromJson(userTimersJson, mapType);
        readyCheck.getUserTimers().putAll(userTimers);
      } catch (Exception e) {
        logger.debug("Failed to parse user timers: {}", e.getMessage());
      }
    }

    readyCheck.setDescription((String) data.get("description"));
    readyCheck.setMessageId((String) data.get("message_id"));

    String status = (String) data.get("status");
    if (status != null) {
      readyCheck.setStatus(ReadyCheckManager.ReadyCheckStatus.valueOf(status));
    }

    return readyCheck;
  }
}
