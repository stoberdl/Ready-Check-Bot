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

  @Override
  public void onModalInteraction(final ModalInteractionEvent event) {
    final String modalId = event.getModalId();

    if (modalId.startsWith(READY_AT_PREFIX)) {
      handleReadyAtModal(event, modalId);
    }
  }

  private void handleReadyAtModal(final ModalInteractionEvent event, final String modalId) {
    final String[] parts = extractModalParts(modalId);
    if (parts == null) {
      event
          .reply("❌ Invalid modal format.")
          .setEphemeral(true)
          .queue(this::scheduleEphemeralDeletion);
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
          .queue(this::scheduleEphemeralDeletion);
    } catch (final Exception e) {
      replyWithTimeFormatError(event);
    }
  }

  private String[] extractModalParts(final String modalId) {
    final String[] parts = modalId.replace(ModalInteractionListener.READY_AT_PREFIX, "").split("_");
    return parts.length < 2 ? null : parts;
  }

  private void replyWithTimeFormatError(final ModalInteractionEvent event) {
    event
        .reply(
            "❌ Invalid time format. Please use formats like: **5**, **530**, **5:30 PM**,"
                + " **3:45pm**, **17:30**, **8:00**")
        .setEphemeral(true)
        .queue(this::scheduleEphemeralDeletion);
  }

  private void scheduleEphemeralDeletion(final InteractionHook hook) {
    scheduler.schedule(() -> hook.deleteOriginal().queue(null, error -> {}), 20, TimeUnit.SECONDS);
  }
}
