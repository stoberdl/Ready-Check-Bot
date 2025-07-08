package com.projects.listeners;

import com.projects.commands.*;
import java.util.HashMap;
import java.util.Map;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CommandListener extends ListenerAdapter {
  private static final Logger logger = LoggerFactory.getLogger(CommandListener.class);
  private final Map<String, Command> commands = new HashMap<>();

  public CommandListener() {
    commands.put("info", new InfoCommand());
    commands.put("ready", new ReadyCommand());
    commands.put("r", new RCommand());
    logger.info("Registered {} commands.", commands.size());
  }

  @Override
  public void onReady(@NotNull ReadyEvent event) {
    logger.info(
        "JDA is ready! Logged in as {}#{}",
        event.getJDA().getSelfUser().getName(),
        event.getJDA().getSelfUser().getDiscriminator());
  }

  @Override
  public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
    String commandName = event.getName();
    Command command = commands.get(commandName);

    if (command != null) {
      logger.debug(
          "Executing slash command: {} from user: {}", commandName, event.getUser().getName());
      command.executeSlash(event);
    } else {
      logger.warn(
          "Unknown slash command: {} from user: {}", commandName, event.getUser().getName());
      event.reply("Unknown command!").setEphemeral(true).queue();
    }
  }
}
