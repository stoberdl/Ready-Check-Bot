package com.projects.listeners;

import com.projects.readycheck.ReadyCheckManager;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;

public class ModalInteractionListener extends ListenerAdapter {
  private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

  @Override
  public void onModalInteraction(ModalInteractionEvent event) {
    String modalId = event.getModalId();

    if (modalId.startsWith("ready_at_")) {
      handleReadyAtModal(event, modalId);
    } else if (modalId.startsWith("ready_until_")) {
      handleReadyUntilModal(event, modalId);
    }
  }

  private void handleReadyAtModal(ModalInteractionEvent event, String modalId) {
    String[] parts = extractModalParts(modalId, "ready_at_");
    if (parts == null) {
      event
          .reply("❌ Invalid modal format.")
          .setEphemeral(true)
          .queue(
              hook -> {
                scheduleEphemeralDeletion(hook, 20);
              });
      return;
    }

    String readyCheckId = parts[0];
    String userId = parts[1];
    String timeInput = Objects.requireNonNull(event.getValue("time")).getAsString();

    try {
      String formattedTime =
          ReadyCheckManager.scheduleReadyAtSmart(readyCheckId, timeInput, userId, event.getJDA());
      ReadyCheckManager.updateReadyCheckEmbed(readyCheckId, event.getJDA());
      event
          .reply(
              "⏰ You'll be ready at **"
                  + formattedTime
                  + "**! I'll send you a reminder in the channel when it's time.")
          .setEphemeral(true)
          .queue(
              hook -> {
                scheduleEphemeralDeletion(hook, 20);
              });
    } catch (Exception e) {
      replyWithTimeFormatError(event);
    }
  }

  private void handleReadyUntilModal(ModalInteractionEvent event, String modalId) {
    String[] parts = extractModalParts(modalId, "ready_until_");
    if (parts == null) {
      event
          .reply("❌ Invalid modal format.")
          .setEphemeral(true)
          .queue(
              hook -> {
                scheduleEphemeralDeletion(hook, 20);
              });
      return;
    }

    String readyCheckId = parts[0];
    String userId = parts[1];
    String timeInput = Objects.requireNonNull(event.getValue("time")).getAsString();

    try {
      String formattedTime =
          ReadyCheckManager.scheduleReadyUntil(readyCheckId, timeInput, userId, event.getJDA());
      boolean allReady = ReadyCheckManager.markUserReady(readyCheckId, userId);
      ReadyCheckManager.updateReadyCheckEmbed(readyCheckId, event.getJDA());

      String response =
          "✅ You're ready until **"
              + formattedTime
              + "**! You'll automatically be marked as passed at that time.";
      if (allReady) {
        ReadyCheckManager.notifyAllReady(readyCheckId, event.getJDA());
        response = "✅ You're ready until **" + formattedTime + "**!";
      }

      event
          .reply(response)
          .setEphemeral(true)
          .queue(
              hook -> {
                scheduleEphemeralDeletion(hook, 15);
              });
    } catch (Exception e) {
      replyWithTimeFormatError(event);
    }
  }

  private String[] extractModalParts(String modalId, String prefix) {
    String[] parts = modalId.replace(prefix, "").split("_");
    return parts.length < 2 ? null : parts;
  }

  private void replyWithTimeFormatError(ModalInteractionEvent event) {
    event
        .reply(
            "❌ Invalid time format. Please use formats like: **5**, **530**, **5:30 PM**,"
                + " **3:45pm**, **17:30**, **8:00**")
        .setEphemeral(true)
        .queue(
            hook -> {
              scheduleEphemeralDeletion(hook, 20);
            });
  }

  // todo: extract this method
  private void scheduleEphemeralDeletion(InteractionHook hook, int seconds) {
    scheduler.schedule(
        () -> {
          hook.deleteOriginal().queue(null, error -> {});
        },
        seconds,
        TimeUnit.SECONDS);
  }
}
