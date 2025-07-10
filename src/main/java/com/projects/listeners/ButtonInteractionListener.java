package com.projects.listeners;

import com.projects.managers.ReadyCheckManager;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;

public class ButtonInteractionListener extends ListenerAdapter {
  private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

  @Override
  public void onButtonInteraction(ButtonInteractionEvent event) {
    String buttonId = event.getComponentId();

    if (buttonId.startsWith("toggle_ready_")) {
      handleToggleReadyButton(event, extractReadyCheckId(buttonId, "toggle_ready_"));
    } else if (buttonId.startsWith("pass_")) {
      handlePassButton(event, extractReadyCheckId(buttonId, "pass_"));
    } else if (buttonId.startsWith("ready_at_")) {
      handleReadyAtButton(event, extractReadyCheckId(buttonId, "ready_at_"));
    } else if (buttonId.startsWith("ready_until_")) {
      handleReadyUntilButton(event, extractReadyCheckId(buttonId, "ready_until_"));
    } else if (buttonId.startsWith("save_ready_")) {
      handleSaveReadyButton(event, extractReadyCheckId(buttonId, "save_ready_"));
    }
  }

  private String extractReadyCheckId(String buttonId, String prefix) {
    return buttonId.replace(prefix, "");
  }

  private void scheduleEphemeralDeletion(InteractionHook hook, int seconds) {
    scheduler.schedule(
        () -> hook.deleteOriginal().queue(null, error -> {}), seconds, TimeUnit.SECONDS);
  }

  private void handleToggleReadyButton(ButtonInteractionEvent event, String readyCheckId) {
    String userId = event.getUser().getId();

    ReadyCheckManager.ensureUserInReadyCheck(readyCheckId, userId);
    boolean isNowReady = ReadyCheckManager.toggleUserReady(readyCheckId, userId);
    ReadyCheckManager.updateReadyCheckEmbed(readyCheckId, event.getJDA());

    boolean allReady = ReadyCheckManager.checkIfAllReady(readyCheckId);

    if (allReady && isNowReady) {
      ReadyCheckManager.notifyAllReady(readyCheckId, event.getJDA());
    }

    event.deferEdit().queue();
  }

  private void handlePassButton(ButtonInteractionEvent event, String readyCheckId) {
    String userId = event.getUser().getId();
    ReadyCheckManager.markUserPassed(readyCheckId, userId);
    ReadyCheckManager.updateReadyCheckEmbed(readyCheckId, event.getJDA());

    event
        .reply("🚫 You've been marked as passed and won't be included in the ready count.")
        .setEphemeral(true)
        .queue(hook -> scheduleEphemeralDeletion(hook, 15));
  }

  private void handleReadyAtButton(ButtonInteractionEvent event, String readyCheckId) {
    TextInput timeInput = createTimeInput("When will you be ready?");
    Modal modal =
        createModal(
            "ready_at_" + readyCheckId + "_" + event.getUser().getId(),
            "Ready At Specific Time",
            timeInput);
    event.replyModal(modal).queue();
  }

  private void handleReadyUntilButton(ButtonInteractionEvent event, String readyCheckId) {
    TextInput timeInput = createTimeInput("Ready until what time?");
    Modal modal =
        createModal(
            "ready_until_" + readyCheckId + "_" + event.getUser().getId(),
            "Ready Until Specific Time",
            timeInput);
    event.replyModal(modal).queue();
  }

  private void handleSaveReadyButton(ButtonInteractionEvent event, String readyCheckId) {
    ReadyCheckManager.saveReadyCheck(readyCheckId);
    event
        .reply(
            "💾 Ready check configuration saved! Use `/r` to quickly start this type of ready check"
                + " again.(or type 'r' in chat for most recent)")
        .setEphemeral(true)
        .queue(hook -> scheduleEphemeralDeletion(hook, 20));
  }

  private TextInput createTimeInput(String label) {
    return TextInput.create("time", label, TextInputStyle.SHORT)
        .setPlaceholder("Examples: 5, 5:30, 3:45pm, 17:30")
        .setMinLength(1)
        .setMaxLength(20)
        .build();
  }

  private Modal createModal(String modalId, String title, TextInput input) {
    return Modal.create(modalId, title).addActionRow(input).build();
  }
}
