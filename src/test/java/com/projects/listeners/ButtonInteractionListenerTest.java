package com.projects.listeners;

import static org.mockito.Mockito.*;

import com.projects.readycheck.ReadyCheckManager;
import com.projects.testutils.MockDiscord;
import com.projects.testutils.TestReadyCheckUtils;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.requests.restaction.interactions.ModalCallbackAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import net.dv8tion.jda.api.requests.restaction.interactions.UpdateInteractionAction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

final class ButtonInteractionListenerTest {

  private ButtonInteractionListener buttonListener;
  private ButtonInteractionEvent mockEvent;
  private JDA mockJDA;
  private User mockUser;
  private ReplyCallbackAction mockReply;
  private UpdateInteractionAction mockUpdate;
  private ModalCallbackAction mockModal;

  @BeforeEach
  void setUp() {
    buttonListener = new ButtonInteractionListener();

    mockJDA = MockDiscord.createMockJDA();
    mockUser = mock(User.class);
    mockEvent = mock(ButtonInteractionEvent.class);
    mockReply = mock(ReplyCallbackAction.class);
    mockUpdate = mock(UpdateInteractionAction.class);
    mockModal = mock(ModalCallbackAction.class);

    when(mockUser.getId()).thenReturn("user-123");
    when(mockEvent.getJDA()).thenReturn(mockJDA);
    when(mockEvent.getUser()).thenReturn(mockUser);
    when(mockEvent.reply(anyString())).thenReturn(mockReply);
    when(mockEvent.deferEdit()).thenReturn(mockUpdate);
    when(mockEvent.replyModal(any(Modal.class))).thenReturn(mockModal);
    when(mockReply.setEphemeral(anyBoolean())).thenReturn(mockReply);
    when(mockReply.queue()).thenReturn(null);
    when(mockReply.queue(any())).thenReturn(null);
    when(mockUpdate.queue()).thenReturn(null);
    when(mockModal.queue()).thenReturn(null);

    ReadyCheckManager.setJDA(mockJDA);
    ReadyCheckManager.getActiveReadyChecks().clear();
  }

  @AfterEach
  void tearDown() {
    ReadyCheckManager.getActiveReadyChecks().clear();
  }

  @Test
  void onButtonInteraction_ToggleReadyButton_ShouldToggleUserReady() {
    when(mockEvent.getComponentId()).thenReturn("toggle_ready_test-check-id");

    final ReadyCheckManager.ReadyCheck readyCheck =
        TestReadyCheckUtils.builder().id("test-check-id").targetUsers("user-123").build();
    ReadyCheckManager.getActiveReadyChecks().put("test-check-id", readyCheck);

    try (MockedStatic<ReadyCheckManager> mockedManager = mockStatic(ReadyCheckManager.class)) {
      mockedManager.when(() -> ReadyCheckManager.getJDA()).thenReturn(mockJDA);
      mockedManager
          .when(() -> ReadyCheckManager.getActiveReadyChecks())
          .thenReturn(ReadyCheckManager.getActiveReadyChecks());

      buttonListener.onButtonInteraction(mockEvent);

      mockedManager.verify(
          () -> ReadyCheckManager.ensureUserInReadyCheck("test-check-id", "user-123"));
      mockedManager.verify(() -> ReadyCheckManager.toggleUserReady("test-check-id", "user-123"));
      mockedManager.verify(() -> ReadyCheckManager.updateReadyCheckEmbed("test-check-id", mockJDA));
      verify(mockEvent).deferEdit();
    }
  }

  @Test
  void onButtonInteraction_ToggleReadyButton_WhenAllReady_ShouldNotifyCompletion() {
    when(mockEvent.getComponentId()).thenReturn("toggle_ready_test-check-id");

    final ReadyCheckManager.ReadyCheck readyCheck =
        TestReadyCheckUtils.builder().id("test-check-id").targetUsers("user-123").build();
    ReadyCheckManager.getActiveReadyChecks().put("test-check-id", readyCheck);

    try (MockedStatic<ReadyCheckManager> mockedManager = mockStatic(ReadyCheckManager.class)) {
      mockedManager.when(() -> ReadyCheckManager.getJDA()).thenReturn(mockJDA);
      mockedManager
          .when(() -> ReadyCheckManager.getActiveReadyChecks())
          .thenReturn(ReadyCheckManager.getActiveReadyChecks());
      mockedManager
          .when(() -> ReadyCheckManager.toggleUserReady("test-check-id", "user-123"))
          .thenReturn(true);
      mockedManager.when(() -> ReadyCheckManager.checkIfAllReady("test-check-id")).thenReturn(true);

      buttonListener.onButtonInteraction(mockEvent);

      mockedManager.verify(() -> ReadyCheckManager.notifyAllReady("test-check-id", mockJDA));
    }
  }

  @Test
  void onButtonInteraction_PassButton_ShouldMarkUserPassed() {
    when(mockEvent.getComponentId()).thenReturn("pass_test-check-id");

    final ReadyCheckManager.ReadyCheck readyCheck =
        TestReadyCheckUtils.builder().id("test-check-id").targetUsers("user-123").build();
    ReadyCheckManager.getActiveReadyChecks().put("test-check-id", readyCheck);

    try (MockedStatic<ReadyCheckManager> mockedManager = mockStatic(ReadyCheckManager.class)) {
      mockedManager.when(() -> ReadyCheckManager.getJDA()).thenReturn(mockJDA);

      buttonListener.onButtonInteraction(mockEvent);

      mockedManager.verify(() -> ReadyCheckManager.markUserPassed("test-check-id", "user-123"));
      mockedManager.verify(() -> ReadyCheckManager.updateReadyCheckEmbed("test-check-id", mockJDA));
      verify(mockEvent).reply(contains("You've been marked as passed"));
      verify(mockReply).setEphemeral(true);
    }
  }

  @Test
  void onButtonInteraction_ReadyAtButton_ShouldShowModal() {
    when(mockEvent.getComponentId()).thenReturn("ready_at_test-check-id");

    final ReadyCheckManager.ReadyCheck readyCheck =
        TestReadyCheckUtils.builder().id("test-check-id").targetUsers("user-123").build();
    ReadyCheckManager.getActiveReadyChecks().put("test-check-id", readyCheck);

    try (MockedStatic<ReadyCheckManager> mockedManager = mockStatic(ReadyCheckManager.class)) {
      mockedManager.when(() -> ReadyCheckManager.getJDA()).thenReturn(mockJDA);

      buttonListener.onButtonInteraction(mockEvent);

      mockedManager.verify(() -> ReadyCheckManager.unmarkUserPassed("test-check-id", "user-123"));
      verify(mockEvent).replyModal(any(Modal.class));
    }
  }

  @Test
  void onButtonInteraction_SaveReadyButton_ShouldSaveConfiguration() {
    when(mockEvent.getComponentId()).thenReturn("save_ready_test-check-id");

    final ReadyCheckManager.ReadyCheck readyCheck =
        TestReadyCheckUtils.builder().id("test-check-id").build();
    ReadyCheckManager.getActiveReadyChecks().put("test-check-id", readyCheck);

    try (MockedStatic<ReadyCheckManager> mockedManager = mockStatic(ReadyCheckManager.class)) {
      mockedManager.when(() -> ReadyCheckManager.getJDA()).thenReturn(mockJDA);

      buttonListener.onButtonInteraction(mockEvent);

      mockedManager.verify(() -> ReadyCheckManager.saveReadyCheck("test-check-id"));
      verify(mockEvent).reply(contains("Ready check configuration saved"));
      verify(mockReply).setEphemeral(true);
    }
  }

  @Test
  void onButtonInteraction_UnknownButton_ShouldIgnore() {
    when(mockEvent.getComponentId()).thenReturn("unknown_button_id");

    buttonListener.onButtonInteraction(mockEvent);

    verifyNoMoreInteractions(mockEvent);
  }

  @Test
  void onButtonInteraction_ToggleReadyButton_UserNotReady_ShouldNotNotifyCompletion() {
    when(mockEvent.getComponentId()).thenReturn("toggle_ready_test-check-id");

    final ReadyCheckManager.ReadyCheck readyCheck =
        TestReadyCheckUtils.builder().id("test-check-id").targetUsers("user-123").build();
    ReadyCheckManager.getActiveReadyChecks().put("test-check-id", readyCheck);

    try (MockedStatic<ReadyCheckManager> mockedManager = mockStatic(ReadyCheckManager.class)) {
      mockedManager.when(() -> ReadyCheckManager.getJDA()).thenReturn(mockJDA);
      mockedManager
          .when(() -> ReadyCheckManager.getActiveReadyChecks())
          .thenReturn(ReadyCheckManager.getActiveReadyChecks());
      mockedManager
          .when(() -> ReadyCheckManager.toggleUserReady("test-check-id", "user-123"))
          .thenReturn(false);
      mockedManager
          .when(() -> ReadyCheckManager.checkIfAllReady("test-check-id"))
          .thenReturn(false);

      buttonListener.onButtonInteraction(mockEvent);

      mockedManager.verify(() -> ReadyCheckManager.notifyAllReady(anyString(), any()), never());
    }
  }

  @Test
  void onButtonInteraction_ToggleReadyButton_AllReadyButUserNotReady_ShouldNotNotifyCompletion() {
    when(mockEvent.getComponentId()).thenReturn("toggle_ready_test-check-id");

    final ReadyCheckManager.ReadyCheck readyCheck =
        TestReadyCheckUtils.builder().id("test-check-id").targetUsers("user-123").build();
    ReadyCheckManager.getActiveReadyChecks().put("test-check-id", readyCheck);

    try (MockedStatic<ReadyCheckManager> mockedManager = mockStatic(ReadyCheckManager.class)) {
      mockedManager.when(() -> ReadyCheckManager.getJDA()).thenReturn(mockJDA);
      mockedManager
          .when(() -> ReadyCheckManager.getActiveReadyChecks())
          .thenReturn(ReadyCheckManager.getActiveReadyChecks());
      mockedManager
          .when(() -> ReadyCheckManager.toggleUserReady("test-check-id", "user-123"))
          .thenReturn(false);
      mockedManager.when(() -> ReadyCheckManager.checkIfAllReady("test-check-id")).thenReturn(true);

      buttonListener.onButtonInteraction(mockEvent);

      mockedManager.verify(() -> ReadyCheckManager.notifyAllReady(anyString(), any()), never());
    }
  }

  @Test
  void onButtonInteraction_ButtonIdExtraction_ShouldHandleCorrectly() {
    final String[] buttonIds = {
      "toggle_ready_my-check-123",
      "pass_another-check-456",
      "ready_at_complex-id-with-dashes",
      "save_ready_simple"
    };

    final String[] expectedIds = {
      "my-check-123", "another-check-456", "complex-id-with-dashes", "simple"
    };

    for (int i = 0; i < buttonIds.length; i++) {
      final ReadyCheckManager.ReadyCheck readyCheck =
          TestReadyCheckUtils.builder().id(expectedIds[i]).build();
      ReadyCheckManager.getActiveReadyChecks().put(expectedIds[i], readyCheck);
    }

    try (MockedStatic<ReadyCheckManager> mockedManager = mockStatic(ReadyCheckManager.class)) {
      mockedManager.when(() -> ReadyCheckManager.getJDA()).thenReturn(mockJDA);

      for (int i = 0; i < buttonIds.length; i++) {
        when(mockEvent.getComponentId()).thenReturn(buttonIds[i]);
        buttonListener.onButtonInteraction(mockEvent);

        final String expectedId = expectedIds[i];
        if (buttonIds[i].startsWith("toggle_ready_")) {
          mockedManager.verify(
              () -> ReadyCheckManager.toggleUserReady(eq(expectedId), anyString()));
        } else if (buttonIds[i].startsWith("pass_")) {
          mockedManager.verify(() -> ReadyCheckManager.markUserPassed(eq(expectedId), anyString()));
        } else if (buttonIds[i].startsWith("ready_at_")) {
          mockedManager.verify(
              () -> ReadyCheckManager.unmarkUserPassed(eq(expectedId), anyString()));
        } else if (buttonIds[i].startsWith("save_ready_")) {
          mockedManager.verify(() -> ReadyCheckManager.saveReadyCheck(eq(expectedId)));
        }
      }
    }
  }
}
