package com.projects.listeners;

import com.projects.readycheck.ReadyCheckManager;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;

public final class ButtonInteractionListener extends ListenerAdapter {
  private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
  private static final String READY_AT_PREFIX = "ready_at_";
  private static final String TOGGLE_READY_PREFIX = "toggle_ready_";
  private static final String PASS_PREFIX = "pass_";
  private static final String SAVE_READY_PREFIX = "save_ready_";

  @Override
  public void onButtonInteraction(final ButtonInteractionEvent event) {
    final String buttonId = event.getComponentId();

    if (buttonId.startsWith(TOGGLE_READY_PREFIX)) {
      handleToggleReadyButton(event, extractReadyCheckId(buttonId, TOGGLE_READY_PREFIX));
    } else if (buttonId.startsWith(PASS_PREFIX)) {
      handlePassButton(event, extractReadyCheckId(buttonId, PASS_PREFIX));
    } else if (buttonId.startsWith(READY_AT_PREFIX)) {
      handleReadyAtButton(event, extractReadyCheckId(buttonId, READY_AT_PREFIX));
    } else if (buttonId.startsWith(SAVE_READY_PREFIX)) {
      handleSaveReadyButton(event, extractReadyCheckId(buttonId, SAVE_READY_PREFIX));
    }
  }

  private String extractReadyCheckId(final String buttonId, final String prefix) {
    return buttonId.replace(prefix, "");
  }

  private void scheduleEphemeralDeletion(final InteractionHook hook, final int seconds) {
    scheduler.schedule(
        () -> hook.deleteOriginal().queue(null, error -> {}), seconds, TimeUnit.SECONDS);
  }

  private void handleToggleReadyButton(
      final ButtonInteractionEvent event, final String readyCheckId) {
    final String userId = event.getUser().getId();

    ReadyCheckManager.ensureUserInReadyCheck(readyCheckId, userId);
    final boolean isNowReady = ReadyCheckManager.toggleUserReady(readyCheckId, userId);
    ReadyCheckManager.updateReadyCheckEmbed(readyCheckId, event.getJDA());

    final boolean allReady = ReadyCheckManager.checkIfAllReady(readyCheckId);

    if (allReady && isNowReady) {
      ReadyCheckManager.notifyAllReady(readyCheckId, event.getJDA());
    }

    event.deferEdit().queue();
  }

  private void handlePassButton(final ButtonInteractionEvent event, final String readyCheckId) {
    final String userId = event.getUser().getId();
    ReadyCheckManager.markUserPassed(readyCheckId, userId);
    ReadyCheckManager.updateReadyCheckEmbed(readyCheckId, event.getJDA());

    event
        .reply("🚫 You've been marked as passed and won't be included in the ready count.")
        .setEphemeral(true)
        .queue(hook -> scheduleEphemeralDeletion(hook, 15));
  }

  private void handleReadyAtButton(final ButtonInteractionEvent event, final String readyCheckId) {
    final String userId = event.getUser().getId();
    ReadyCheckManager.unmarkUserPassed(readyCheckId, userId);

    final TextInput timeInput = createTimeInput("When will you be ready?");
    final Modal modal =
        createModal(
            READY_AT_PREFIX + readyCheckId + "_" + userId, "Ready At Specific Time", timeInput);
    event.replyModal(modal).queue();
  }

  private void handleSaveReadyButton(
      final ButtonInteractionEvent event, final String readyCheckId) {
    ReadyCheckManager.saveReadyCheck(readyCheckId);
    event
        .reply(
            "💾 Ready check configuration saved! Use `/r` to quickly start this type of ready check"
                + " again.(or type 'r' in chat for most recent)")
        .setEphemeral(true)
        .queue(hook -> scheduleEphemeralDeletion(hook, 20));
  }

  private TextInput createTimeInput(final String label) {
    return TextInput.create("time", label, TextInputStyle.SHORT)
        .setPlaceholder("Examples: 5, 530, 5:30, 5:30pm, 17:30")
        .setMinLength(1)
        .setMaxLength(20)
        .build();
  }

  private Modal createModal(final String modalId, final String title, final TextInput input) {
    return Modal.create(modalId, title).addActionRow(input).build();
  }
}
