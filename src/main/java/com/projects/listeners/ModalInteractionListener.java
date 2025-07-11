package com.projects.listeners;

import com.projects.readycheck.ReadyCheckManager;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;

public final class ModalInteractionListener extends ListenerAdapter {
  private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
  private static final String READY_AT_PREFIX = "ready_at_";
  private static final String READY_UNTIL_PREFIX = "ready_until_";

  @Override
  public void onModalInteraction(final ModalInteractionEvent event) {
    final String modalId = event.getModalId();

    if (modalId.startsWith(READY_AT_PREFIX)) {
      handleReadyAtModal(event, modalId);
    } else if (modalId.startsWith(READY_UNTIL_PREFIX)) {
      handleReadyUntilModal(event, modalId);
    }
  }

  private void handleReadyAtModal(final ModalInteractionEvent event, final String modalId) {
    final String[] parts = extractModalParts(modalId, READY_AT_PREFIX);
    if (parts == null) {
      event
          .reply("❌ Invalid modal format.")
          .setEphemeral(true)
          .queue(hook -> scheduleEphemeralDeletion(hook, 20));
      return;
    }

    final String readyCheckId = parts[0];
    final String userId = parts[1];
    final String timeInput = Objects.requireNonNull(event.getValue("time")).getAsString();

    try {
      final String formattedTime =
          ReadyCheckManager.scheduleReadyAtSmart(readyCheckId, timeInput, userId, event.getJDA());
      ReadyCheckManager.updateReadyCheckEmbed(readyCheckId, event.getJDA());
      event
          .reply(
              "⏰ You'll be ready at **"
                  + formattedTime
                  + "**! I'll send you a reminder in the channel when it's time.")
          .setEphemeral(true)
          .queue(hook -> scheduleEphemeralDeletion(hook, 20));
    } catch (final Exception e) {
      replyWithTimeFormatError(event);
    }
  }

  private void handleReadyUntilModal(final ModalInteractionEvent event, final String modalId) {
    final String[] parts = extractModalParts(modalId, READY_UNTIL_PREFIX);
    if (parts == null) {
      event
          .reply("❌ Invalid modal format.")
          .setEphemeral(true)
          .queue(hook -> scheduleEphemeralDeletion(hook, 20));
      return;
    }

    final String readyCheckId = parts[0];
    final String userId = parts[1];
    final String timeInput = Objects.requireNonNull(event.getValue("time")).getAsString();

    try {
      final String formattedTime =
          ReadyCheckManager.scheduleReadyUntil(readyCheckId, timeInput, userId, event.getJDA());
      final boolean allReady = ReadyCheckManager.markUserReady(readyCheckId, userId);
      ReadyCheckManager.updateReadyCheckEmbed(readyCheckId, event.getJDA());

      String response =
          "✅ You're ready until **"
              + formattedTime
              + "**! You'll automatically be marked as passed at that time.";
      if (allReady) {
        ReadyCheckManager.notifyAllReady(readyCheckId, event.getJDA());
        response = "✅ You're ready until **" + formattedTime + "**!";
      }

      event.reply(response).setEphemeral(true).queue(hook -> scheduleEphemeralDeletion(hook, 15));
    } catch (final Exception e) {
      replyWithTimeFormatError(event);
    }
  }

  private String[] extractModalParts(final String modalId, final String prefix) {
    final String[] parts = modalId.replace(prefix, "").split("_");
    return parts.length < 2 ? null : parts;
  }

  private void replyWithTimeFormatError(final ModalInteractionEvent event) {
    event
        .reply(
            "❌ Invalid time format. Please use formats like: **5**, **530**, **5:30 PM**,"
                + " **3:45pm**, **17:30**, **8:00**")
        .setEphemeral(true)
        .queue(hook -> scheduleEphemeralDeletion(hook, 20));
  }

  private void scheduleEphemeralDeletion(final InteractionHook hook, final int seconds) {
    scheduler.schedule(
        () -> hook.deleteOriginal().queue(null, error -> {}), seconds, TimeUnit.SECONDS);
  }
}
