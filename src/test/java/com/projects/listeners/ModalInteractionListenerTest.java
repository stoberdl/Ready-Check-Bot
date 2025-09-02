package com.projects.listeners;

import static org.mockito.Mockito.*;

import com.projects.readycheck.ReadyCheckManager;
import com.projects.testutils.TestReadyCheckUtils;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

final class ModalInteractionListenerTest {

  private ModalInteractionListener modalListener;
  private ModalInteractionEvent mockEvent;
  private JDA mockJDA;
  private User mockUser;
  private ModalMapping mockTimeInput;
  private ReplyCallbackAction mockReply;

  @BeforeEach
  void setUp() {
    modalListener = new ModalInteractionListener();

    mockJDA = mock(JDA.class);
    mockUser = mock(User.class);
    mockEvent = mock(ModalInteractionEvent.class);
    mockTimeInput = mock(ModalMapping.class);
    mockReply = mock(ReplyCallbackAction.class);

    when(mockUser.getId()).thenReturn("user-123");
    when(mockEvent.getJDA()).thenReturn(mockJDA);
    when(mockEvent.getUser()).thenReturn(mockUser);
    when(mockEvent.getValue("time")).thenReturn(mockTimeInput);
    when(mockEvent.reply(anyString())).thenReturn(mockReply);
    when(mockReply.setEphemeral(anyBoolean())).thenReturn(mockReply);
    when(mockReply.queue()).thenReturn(null);
    when(mockReply.queue(any())).thenReturn(null);

    ReadyCheckManager.setJDA(mockJDA);
    ReadyCheckManager.getActiveReadyChecks().clear();
  }

  @AfterEach
  void tearDown() {
    ReadyCheckManager.getActiveReadyChecks().clear();
  }

  @Test
  void onModalInteraction_ReadyAtModal_ValidTime_ShouldScheduleUser() {
    when(mockEvent.getModalId()).thenReturn("ready_at_test-check-id_user-123");
    when(mockTimeInput.getAsString()).thenReturn("5:30pm");

    final ReadyCheckManager.ReadyCheck readyCheck =
        TestReadyCheckUtils.builder().id("test-check-id").build();
    ReadyCheckManager.getActiveReadyChecks().put("test-check-id", readyCheck);

    try (MockedStatic<ReadyCheckManager> mockedManager = mockStatic(ReadyCheckManager.class)) {
      mockedManager.when(() -> ReadyCheckManager.getJDA()).thenReturn(mockJDA);
      mockedManager
          .when(
              () ->
                  ReadyCheckManager.scheduleReadyAtSmart(
                      eq("test-check-id"), eq("5:30pm"), eq("user-123"), eq(mockJDA)))
          .thenReturn("5:30 PM");

      modalListener.onModalInteraction(mockEvent);

      mockedManager.verify(
          () ->
              ReadyCheckManager.scheduleReadyAtSmart(
                  "test-check-id", "5:30pm", "user-123", mockJDA));
      mockedManager.verify(() -> ReadyCheckManager.updateReadyCheckEmbed("test-check-id", mockJDA));
      verify(mockEvent).reply(contains("You'll be ready at **5:30 PM**"));
      verify(mockReply).setEphemeral(true);
    }
  }

  @Test
  void onModalInteraction_ReadyAtModal_InvalidTime_ShouldShowError() {
    when(mockEvent.getModalId()).thenReturn("ready_at_test-check-id_user-123");
    when(mockTimeInput.getAsString()).thenReturn("invalid-time");

    try (MockedStatic<ReadyCheckManager> mockedManager = mockStatic(ReadyCheckManager.class)) {
      mockedManager
          .when(
              () ->
                  ReadyCheckManager.scheduleReadyAtSmart(
                      anyString(), anyString(), anyString(), any(JDA.class)))
          .thenThrow(new IllegalArgumentException("Invalid time format"));

      modalListener.onModalInteraction(mockEvent);

      verify(mockEvent).reply(contains("Invalid time format"));
      verify(mockReply).setEphemeral(true);
    }
  }

  @Test
  void onModalInteraction_InvalidModalId_ShouldShowError() {
    when(mockEvent.getModalId()).thenReturn("invalid_modal_id");

    modalListener.onModalInteraction(mockEvent);

    verify(mockEvent).reply("❌ Invalid modal format.");
    verify(mockReply).setEphemeral(true);
  }

  @Test
  void onModalInteraction_MalformedModalId_ShouldShowError() {
    when(mockEvent.getModalId()).thenReturn("ready_at_incomplete");

    modalListener.onModalInteraction(mockEvent);

    verify(mockEvent).reply("❌ Invalid modal format.");
    verify(mockReply).setEphemeral(true);
  }

  @Test
  void onModalInteraction_NonReadyAtModal_ShouldIgnore() {
    when(mockEvent.getModalId()).thenReturn("other_modal_test-id_user-123");

    modalListener.onModalInteraction(mockEvent);

    verifyNoMoreInteractions(mockEvent);
  }

  @Test
  void onModalInteraction_NullTimeInput_ShouldHandleGracefully() {
    when(mockEvent.getModalId()).thenReturn("ready_at_test-check-id_user-123");
    when(mockEvent.getValue("time")).thenReturn(null);

    assertDoesNotThrow(() -> modalListener.onModalInteraction(mockEvent));
  }

  @Test
  void onModalInteraction_EmptyTimeInput_ShouldShowError() {
    when(mockEvent.getModalId()).thenReturn("ready_at_test-check-id_user-123");
    when(mockTimeInput.getAsString()).thenReturn("");

    try (MockedStatic<ReadyCheckManager> mockedManager = mockStatic(ReadyCheckManager.class)) {
      mockedManager
          .when(
              () ->
                  ReadyCheckManager.scheduleReadyAtSmart(
                      anyString(), anyString(), anyString(), any(JDA.class)))
          .thenThrow(new IllegalArgumentException("Empty time"));

      modalListener.onModalInteraction(mockEvent);

      verify(mockEvent).reply(contains("Invalid time format"));
      verify(mockReply).setEphemeral(true);
    }
  }

  @Test
  void onModalInteraction_WhitespaceTimeInput_ShouldShowError() {
    when(mockEvent.getModalId()).thenReturn("ready_at_test-check-id_user-123");
    when(mockTimeInput.getAsString()).thenReturn("   ");

    try (MockedStatic<ReadyCheckManager> mockedManager = mockStatic(ReadyCheckManager.class)) {
      mockedManager
          .when(
              () ->
                  ReadyCheckManager.scheduleReadyAtSmart(
                      anyString(), anyString(), anyString(), any(JDA.class)))
          .thenThrow(new IllegalArgumentException("Whitespace only"));

      modalListener.onModalInteraction(mockEvent);

      verify(mockEvent).reply(contains("Invalid time format"));
      verify(mockReply).setEphemeral(true);
    }
  }

  @Test
  void onModalInteraction_VariousTimeFormats_ShouldHandleCorrectly() {
    final String[] validTimes = {"5", "530", "5:30", "5:30pm", "17:30", "8:00"};
    final String[] expectedResponses = {
      "5:00 AM", "5:30 AM", "5:30 AM", "5:30 PM", "5:30 PM", "8:00 AM"
    };

    for (int i = 0; i < validTimes.length; i++) {
      when(mockEvent.getModalId()).thenReturn("ready_at_test-check-id_user-123");
      when(mockTimeInput.getAsString()).thenReturn(validTimes[i]);

      try (MockedStatic<ReadyCheckManager> mockedManager = mockStatic(ReadyCheckManager.class)) {
        mockedManager.when(() -> ReadyCheckManager.getJDA()).thenReturn(mockJDA);
        mockedManager
            .when(
                () ->
                    ReadyCheckManager.scheduleReadyAtSmart(
                        eq("test-check-id"), eq(validTimes[i]), eq("user-123"), eq(mockJDA)))
            .thenReturn(expectedResponses[i]);

        modalListener.onModalInteraction(mockEvent);

        mockedManager.verify(
            () ->
                ReadyCheckManager.scheduleReadyAtSmart(
                    "test-check-id", validTimes[i], "user-123", mockJDA));
      }

      reset(mockEvent, mockReply);
      when(mockEvent.getJDA()).thenReturn(mockJDA);
      when(mockEvent.getUser()).thenReturn(mockUser);
      when(mockEvent.getValue("time")).thenReturn(mockTimeInput);
      when(mockEvent.reply(anyString())).thenReturn(mockReply);
      when(mockReply.setEphemeral(anyBoolean())).thenReturn(mockReply);
      when(mockReply.queue()).thenReturn(null);
      when(mockReply.queue(any())).thenReturn(null);
    }
  }

  @Test
  void onModalInteraction_ComplexModalId_ShouldParseCorrectly() {
    when(mockEvent.getModalId())
        .thenReturn("ready_at_complex-check-id-with-dashes_user-with-dashes-123");
    when(mockTimeInput.getAsString()).thenReturn("7pm");

    try (MockedStatic<ReadyCheckManager> mockedManager = mockStatic(ReadyCheckManager.class)) {
      mockedManager.when(() -> ReadyCheckManager.getJDA()).thenReturn(mockJDA);
      mockedManager
          .when(
              () ->
                  ReadyCheckManager.scheduleReadyAtSmart(
                      eq("complex-check-id-with-dashes"),
                      eq("7pm"),
                      eq("user-with-dashes-123"),
                      eq(mockJDA)))
          .thenReturn("7:00 PM");

      modalListener.onModalInteraction(mockEvent);

      mockedManager.verify(
          () ->
              ReadyCheckManager.scheduleReadyAtSmart(
                  "complex-check-id-with-dashes", "7pm", "user-with-dashes-123", mockJDA));
    }
  }

  @Test
  void onModalInteraction_SuccessfulScheduling_ShouldIncludeReminderMessage() {
    when(mockEvent.getModalId()).thenReturn("ready_at_test-check-id_user-123");
    when(mockTimeInput.getAsString()).thenReturn("8pm");

    try (MockedStatic<ReadyCheckManager> mockedManager = mockStatic(ReadyCheckManager.class)) {
      mockedManager.when(() -> ReadyCheckManager.getJDA()).thenReturn(mockJDA);
      mockedManager
          .when(
              () ->
                  ReadyCheckManager.scheduleReadyAtSmart(
                      anyString(), anyString(), anyString(), any(JDA.class)))
          .thenReturn("8:00 PM");

      modalListener.onModalInteraction(mockEvent);

      verify(mockEvent).reply(contains("I'll send you a reminder in the channel when it's time"));
    }
  }

  private void assertDoesNotThrow(Runnable runnable) {
    try {
      runnable.run();
    } catch (Exception e) {
      throw new AssertionError("Expected no exception but got: " + e.getMessage(), e);
    }
  }
}
