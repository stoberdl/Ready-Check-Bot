package com.projects.readycheck;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReadyCheckPersistence {
  private static final Logger logger = LoggerFactory.getLogger(ReadyCheckPersistence.class);
  private static final Map<String, ReadyCheckManager.SavedReadyCheck> savedReadyChecks =
      new ConcurrentHashMap<>();
  private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
  private static final String SAVE_FILE = getConfigFilePath();

  static {
    loadSavedConfigurations();
  }

  private ReadyCheckPersistence() {}

  public static void saveReadyCheck(
      ReadyCheckManager.ReadyCheck readyCheck, boolean mentionPeople) {
    String guildId = readyCheck.getGuildId();
    ReadyCheckManager.SavedReadyCheck newSavedCheck = createSavedCheck(readyCheck, mentionPeople);

    String newKey = createSavedCheckKey(guildId, newSavedCheck);
    removeDuplicateConfigurations(guildId, newSavedCheck);

    savedReadyChecks.put(newKey, newSavedCheck);
    saveSavedConfigurations();

    logger.info(
        "Saved ready check configuration for guild: {}, type: {}",
        guildId,
        newSavedCheck.isUserBased() ? "user-based" : "role-based");
  }

  public static List<ReadyCheckManager.SavedReadyCheck> getSavedReadyChecks(String guildId) {
    List<ReadyCheckManager.SavedReadyCheck> checks =
        savedReadyChecks.entrySet().stream()
            .filter(entry -> entry.getKey().startsWith(guildId + "_"))
            .map(Map.Entry::getValue)
            .collect(Collectors.toList());

    Collections.reverse(checks);
    return checks;
  }

  private static ReadyCheckManager.SavedReadyCheck createSavedCheck(
      ReadyCheckManager.ReadyCheck readyCheck, boolean mentionPeople) {
    if (readyCheck.getRoleId() != null) {
      return new ReadyCheckManager.SavedReadyCheck(readyCheck.getRoleId(), false, mentionPeople);
    } else {
      List<String> allUserIds = new ArrayList<>(readyCheck.getTargetUsers());
      allUserIds.add(readyCheck.getInitiatorId());
      return new ReadyCheckManager.SavedReadyCheck(allUserIds, true, mentionPeople);
    }
  }

  private static String createSavedCheckKey(
      String guildId, ReadyCheckManager.SavedReadyCheck savedCheck) {
    return guildId
        + "_"
        + (savedCheck.isUserBased() ? savedCheck.getUserIds().hashCode() : savedCheck.getRoleId());
  }

  private static void removeDuplicateConfigurations(
      String guildId, ReadyCheckManager.SavedReadyCheck newSavedCheck) {
    savedReadyChecks
        .entrySet()
        .removeIf(
            entry -> {
              String key = entry.getKey();
              ReadyCheckManager.SavedReadyCheck savedCheck = entry.getValue();

              if (!key.startsWith(guildId + "_")) return false;

              if (savedCheck.isUserBased() == newSavedCheck.isUserBased()) {
                if (savedCheck.isUserBased()) {
                  Set<String> existingUsers = new HashSet<>(savedCheck.getUserIds());
                  Set<String> newUsers = new HashSet<>(newSavedCheck.getUserIds());
                  return existingUsers.equals(newUsers);
                } else {
                  return Objects.equals(savedCheck.getRoleId(), newSavedCheck.getRoleId());
                }
              }
              return false;
            });
  }

  private static void saveSavedConfigurations() {
    try (FileWriter writer = new FileWriter(SAVE_FILE)) {
      gson.toJson(savedReadyChecks, writer);
      logger.debug("Saved {} ready check configurations to file", savedReadyChecks.size());
    } catch (IOException e) {
      logger.error("Failed to save configurations to {}: {}", SAVE_FILE, e.getMessage(), e);
    }
  }

  private static void loadSavedConfigurations() {
    File file = new File(SAVE_FILE);

    File parentDir = file.getParentFile();
    if (parentDir != null && !parentDir.exists()) {
      boolean created = parentDir.mkdirs();
      if (created) {
        logger.info("Created config directory: {}", parentDir.getAbsolutePath());
      }
    }

    if (!file.exists()) {
      logger.info("No existing configuration file found at: {}", file.getAbsolutePath());
      return;
    }

    try (FileReader reader = new FileReader(file)) {
      Type type = new TypeToken<Map<String, ReadyCheckManager.SavedReadyCheck>>() {}.getType();
      Map<String, ReadyCheckManager.SavedReadyCheck> loaded = gson.fromJson(reader, type);
      if (loaded != null) {
        savedReadyChecks.putAll(loaded);
        logger.info(
            "Loaded {} saved ready check configurations from {}",
            loaded.size(),
            file.getAbsolutePath());
      }
    } catch (IOException e) {
      logger.error(
          "Failed to load configurations from {}: {}", file.getAbsolutePath(), e.getMessage(), e);
    }
  }

  private static String getConfigFilePath() {
    String configDir = System.getProperty("bot.config.dir", "/app/data");
    File dockerPath = new File(configDir);
    if (dockerPath.exists() && dockerPath.isDirectory()) {
      return configDir + "/saved_ready_checks.json";
    }
    return "saved_ready_checks.json";
  }
}
