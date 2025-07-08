package com.projects;

import com.projects.botConfig.BotConfig;
import com.projects.listeners.*;
import com.projects.managers.ReadyCheckManager;
import java.util.EnumSet;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReadyCheckBot {
  private static final Logger logger = LoggerFactory.getLogger(ReadyCheckBot.class);
  private static JDA jda;

  public static void main(String[] args) {
    String botToken = BotConfig.getBotToken();

    if (botToken == null || botToken.isEmpty()) {
      logger.error("Bot token not found in config.properties. Please provide a valid token.");
      return;
    }

    try {
      jda =
          JDABuilder.createDefault(botToken)
              .enableIntents(EnumSet.allOf(GatewayIntent.class))
              .enableCache(CacheFlag.MEMBER_OVERRIDES)
              .setMemberCachePolicy(MemberCachePolicy.ALL)
              .addEventListeners(new CommandListener())
              .addEventListeners(new ButtonInteractionListener())
              .addEventListeners(new ModalInteractionListener())
              .addEventListeners(new SelectionMenuInteractionListener())
              .addEventListeners(new MessageListener())
              .build();

      jda.awaitReady();
      logger.info("Bot is online and ready!");

      ReadyCheckManager.setJDA(jda);

      registerSlashCommands();
    } catch (Exception e) {
      logger.error("Error starting the bot: ", e);
    }
  }

  private static void registerSlashCommands() {
    if (jda == null) {
      logger.error("JDA instance is not initialized. Cannot register slash commands.");
      return;
    }

    logger.info("Registering Slash Commands...");
    jda.updateCommands()
        .addCommands(
            Commands.slash("info", "Displays information about the bot."),
            Commands.slash("r", "Use saved ready checks"),
            Commands.slash("ready", "Start a ready check")
                .addOption(
                    OptionType.STRING, "targets", "Roles and/or users: @GameRole @Alice @Bob", true)
                .addOption(
                    OptionType.BOOLEAN,
                    "people",
                    "Whether to @mention people in messages (default: true)",
                    false))
        .queue(
            success -> logger.info("Slash commands registered successfully!"),
            failure -> logger.error("Failed to register slash commands: ", failure));
  }
}
