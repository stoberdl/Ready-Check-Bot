package com.projects.readycheck;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.projects.readycheck.exceptions.DatabasePersistenceException;
import java.io.IOException;
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

  private static final String GUILD_ID = "guild_id";
  private static final String ROLE_ID = "role_id";
  private static final String USER_BASED = "user_based";
  private static final String API_KEY_HEADER = "apikey";
  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String BEARER_PREFIX = "Bearer ";

  private SupabasePersistence() {}

  static {
    if (SUPABASE_URL == null || SUPABASE_KEY == null) {
      throw new IllegalStateException(
          "SUPABASE_URL and SUPABASE_KEY environment variables must be set");
    }
  }

  public static void saveReadyCheck(
      ReadyCheckManager.ReadyCheck readyCheck, boolean mentionPeople) throws DatabasePersistenceException {
    try {
      Map<String, Object> config = new HashMap<>();
      config.put(GUILD_ID, readyCheck.getGuildId());
      config.put("mention_people", mentionPeople);

      if (readyCheck.getRoleId() != null) {
        config.put(ROLE_ID, readyCheck.getRoleId());
        config.put(USER_BASED, false);
      } else {
        config.put("user_ids", readyCheck.getTargetUsers().toArray(new String[0]));
        config.put(USER_BASED, true);
      }

      RequestBody body = RequestBody.create(gson.toJson(config), JSON);
      Request request =
          new Request.Builder()
              .url(SUPABASE_URL + "/rest/v1/saved_configs")
              .header(API_KEY_HEADER, SUPABASE_KEY)
              .header(AUTHORIZATION_HEADER, BEARER_PREFIX + SUPABASE_KEY)
              .header("Prefer", "resolution=merge-duplicates")
              .post(body)
              .build();

      client.newCall(request).execute().close();
      logger.info("Saved ready check configuration for guild: {}", readyCheck.getGuildId());

    } catch (IOException e) {
      throw new DatabasePersistenceException("save ready check configuration", e);
    } catch (Exception e) {
      throw new DatabasePersistenceException("save ready check configuration", e.getMessage());
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
              .header(API_KEY_HEADER, SUPABASE_KEY)
              .header(AUTHORIZATION_HEADER, BEARER_PREFIX + SUPABASE_KEY)
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
      Map<String, Object> scheduledUsersData = createScheduledUsersData(readyCheck);
      Map<String, Object> data = createActiveReadyCheckData(readyCheck, scheduledUsersData);

      RequestBody body = RequestBody.create(gson.toJson(data), JSON);
      Request request =
          new Request.Builder()
              .url(SUPABASE_URL + "/rest/v1/ready_checks")
              .header(API_KEY_HEADER, SUPABASE_KEY)
              .header(AUTHORIZATION_HEADER, BEARER_PREFIX + SUPABASE_KEY)
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
              .header(API_KEY_HEADER, SUPABASE_KEY)
              .header(AUTHORIZATION_HEADER, BEARER_PREFIX + SUPABASE_KEY)
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
              .header(API_KEY_HEADER, SUPABASE_KEY)
              .header(AUTHORIZATION_HEADER, BEARER_PREFIX + SUPABASE_KEY)
              .delete()
              .build();

      client.newCall(request).execute().close();
    } catch (Exception e) {
      logger.error("Failed to delete ready check: {}", e.getMessage(), e);
    }
  }

  private static Map<String, Object> createScheduledUsersData(
      ReadyCheckManager.ReadyCheck readyCheck) {
    Map<String, Object> scheduledUsersData = new HashMap<>();
    for (Map.Entry<String, ReadyCheckManager.ScheduledUser> entry :
        readyCheck.getScheduledUsers().entrySet()) {
      Map<String, Object> userData = new HashMap<>();
      userData.put("readyTimestamp", entry.getValue().readyTimestamp());
      userData.put("reminderFuture", new HashMap<>());
      scheduledUsersData.put(entry.getKey(), userData);
    }
    return scheduledUsersData;
  }

  private static Map<String, Object> createActiveReadyCheckData(
      ReadyCheckManager.ReadyCheck readyCheck, Map<String, Object> scheduledUsersData) {
    Map<String, Object> data = new HashMap<>();
    data.put("id", readyCheck.getId());
    data.put(GUILD_ID, readyCheck.getGuildId());
    data.put("channel_id", readyCheck.getChannelId());
    data.put("initiator_id", readyCheck.getInitiatorId());
    data.put(ROLE_ID, readyCheck.getRoleId());
    data.put("target_users", readyCheck.getTargetUsers().toArray(new String[0]));
    data.put("ready_users", readyCheck.getReadyUsers().toArray(new String[0]));
    data.put("passed_users", readyCheck.getPassedUsers().toArray(new String[0]));
    data.put("scheduled_users", gson.toJson(scheduledUsersData));
    data.put("user_timers", gson.toJson(readyCheck.getUserTimers()));
    data.put("description", readyCheck.getDescription());
    data.put("status", readyCheck.getStatus().name());
    data.put("message_id", readyCheck.getMessageId());
    return data;
  }

  private static ReadyCheckManager.SavedReadyCheck mapToSavedReadyCheck(
      Map<String, Object> config) {
    boolean userBased = (Boolean) config.get(USER_BASED);
    boolean mentionPeople = (Boolean) config.getOrDefault("mention_people", true);

    if (userBased) {
      List<String> userIds = (List<String>) config.get("user_ids");
      return new ReadyCheckManager.SavedReadyCheck(userIds, true, mentionPeople);
    } else {
      String roleId = (String) config.get(ROLE_ID);
      return new ReadyCheckManager.SavedReadyCheck(roleId, false, mentionPeople);
    }
  }

  private static ReadyCheckManager.ReadyCheck mapToReadyCheck(Map<String, Object> data) {
    ReadyCheckManager.ReadyCheck readyCheck = createBasicReadyCheck(data);
    populateReadyCheckUsers(readyCheck, data);
    populateScheduledUsers(readyCheck, data);
    populateUserTimers(readyCheck, data);
    setReadyCheckMetadata(readyCheck, data);
    return readyCheck;
  }

  private static ReadyCheckManager.ReadyCheck createBasicReadyCheck(Map<String, Object> data) {
    String id = (String) data.get("id");
    String guildId = (String) data.get(GUILD_ID);
    String channelId = (String) data.get("channel_id");
    String initiatorId = (String) data.get("initiator_id");
    String roleId = (String) data.get(ROLE_ID);
    List<String> targetUsers = (List<String>) data.get("target_users");

    return new ReadyCheckManager.ReadyCheck(
        id, guildId, channelId, initiatorId, roleId, targetUsers);
  }

  private static void populateReadyCheckUsers(
      ReadyCheckManager.ReadyCheck readyCheck, Map<String, Object> data) {
    List<String> readyUsers = (List<String>) data.get("ready_users");
    if (readyUsers != null) {
      readyCheck.getReadyUsers().addAll(readyUsers);
    }

    List<String> passedUsers = (List<String>) data.get("passed_users");
    if (passedUsers != null) {
      readyCheck.getPassedUsers().addAll(passedUsers);
    }
  }

  private static void populateScheduledUsers(
      ReadyCheckManager.ReadyCheck readyCheck, Map<String, Object> data) {
    String scheduledUsersJson = (String) data.get("scheduled_users");
    if (scheduledUsersJson != null && !scheduledUsersJson.isEmpty()) {
      try {
        Type mapType = new TypeToken<Map<String, Map<String, Object>>>() {}.getType();
        Map<String, Map<String, Object>> scheduledData = gson.fromJson(scheduledUsersJson, mapType);
        for (Map.Entry<String, Map<String, Object>> entry : scheduledData.entrySet()) {
          String userId = entry.getKey();
          Map<String, Object> userData = entry.getValue();
          Object timestampObj = userData.get("readyTimestamp");
          if (timestampObj instanceof Number number) {
            long timestamp = number.longValue();
            readyCheck
                .getScheduledUsers()
                .put(userId, new ReadyCheckManager.ScheduledUser(timestamp, null));
          }
        }
      } catch (Exception e) {
        logger.debug("Failed to parse scheduled users: {}", e.getMessage());
      }
    }
  }

  private static void populateUserTimers(
      ReadyCheckManager.ReadyCheck readyCheck, Map<String, Object> data) {
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
  }

  private static void setReadyCheckMetadata(
      ReadyCheckManager.ReadyCheck readyCheck, Map<String, Object> data) {
    readyCheck.setDescription((String) data.get("description"));
    readyCheck.setMessageId((String) data.get("message_id"));

    String status = (String) data.get("status");
    if (status != null) {
      readyCheck.setStatus(ReadyCheckManager.ReadyCheckStatus.valueOf(status));
    }
  }
}
