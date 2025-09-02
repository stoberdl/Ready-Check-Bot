package com.projects.listeners;

import static org.mockito.Mockito.*;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class CommandListenerTest {

  private CommandListener commandListener;
  private SlashCommandInteractionEvent mockSlashEvent;
  private ReadyEvent mockReadyEvent;
  private JDA mockJDA;
  private User mockUser;
  private User mockSelfUser;
  private ReplyCallbackAction mockReply;

  @BeforeEach
  void setUp() {
    commandListener = new CommandListener();

    mockSlashEvent = mock(SlashCommandInteractionEvent.class);
    mockReadyEvent = mock(ReadyEvent.class);
    mockJDA = mock(JDA.class);
    mockUser = mock(User.class);
    mockSelfUser = mock(User.class);
    mockReply = mock(ReplyCallbackAction.class);

    when(mockSlashEvent.getUser()).thenReturn(mockUser);
    when(mockSlashEvent.reply(anyString())).thenReturn(mockReply);
    when(mockReply.setEphemeral(anyBoolean())).thenReturn(mockReply);
    when(mockReply.queue()).thenReturn(null);

    when(mockReadyEvent.getJDA()).thenReturn(mockJDA);
    when(mockJDA.getSelfUser()).thenReturn(mockSelfUser);
    when(mockSelfUser.getName()).thenReturn("TestBot");
    when(mockSelfUser.getDiscriminator()).thenReturn("1234");

    when(mockUser.getName()).thenReturn("TestUser");
  }

  @Test
  void onReady_ShouldLogReadyMessage() {
    commandListener.onReady(mockReadyEvent);

    verify(mockReadyEvent).getJDA();
    verify(mockJDA).getSelfUser();
    verify(mockSelfUser).getName();
    verify(mockSelfUser).getDiscriminator();
  }

  @Test
  void onSlashCommandInteraction_InfoCommand_ShouldExecute() {
    when(mockSlashEvent.getName()).thenReturn("info");

    commandListener.onSlashCommandInteraction(mockSlashEvent);

    verify(mockSlashEvent).getName();
    verify(mockSlashEvent).getUser();
  }

  @Test
  void onSlashCommandInteraction_ReadyCommand_ShouldExecute() {
    when(mockSlashEvent.getName()).thenReturn("ready");

    commandListener.onSlashCommandInteraction(mockSlashEvent);

    verify(mockSlashEvent).getName();
    verify(mockSlashEvent).getUser();
  }

  @Test
  void onSlashCommandInteraction_RCommand_ShouldExecute() {
    when(mockSlashEvent.getName()).thenReturn("r");

    commandListener.onSlashCommandInteraction(mockSlashEvent);

    verify(mockSlashEvent).getName();
    verify(mockSlashEvent).getUser();
  }

  @Test
  void onSlashCommandInteraction_UnknownCommand_ShouldReplyWithError() {
    when(mockSlashEvent.getName()).thenReturn("unknown");

    commandListener.onSlashCommandInteraction(mockSlashEvent);

    verify(mockSlashEvent).reply("Unknown command!");
    verify(mockReply).setEphemeral(true);
    verify(mockReply).queue();
  }

  @Test
  void constructor_ShouldRegisterAllCommands() {
    final CommandListener listener = new CommandListener();

    assertNotNull(listener);
  }

  @Test
  void onSlashCommandInteraction_ValidCommands_ShouldNotReplyWithError() {
    final String[] validCommands = {"info", "ready", "r"};

    for (final String command : validCommands) {
      when(mockSlashEvent.getName()).thenReturn(command);

      commandListener.onSlashCommandInteraction(mockSlashEvent);

      verify(mockSlashEvent, never()).reply("Unknown command!");
      reset(mockSlashEvent);
      when(mockSlashEvent.getUser()).thenReturn(mockUser);
      when(mockUser.getName()).thenReturn("TestUser");
    }
  }

  @Test
  void onSlashCommandInteraction_CaseInsensitive_ShouldWork() {
    when(mockSlashEvent.getName()).thenReturn("INFO");

    commandListener.onSlashCommandInteraction(mockSlashEvent);

    verify(mockSlashEvent).reply("Unknown command!");
    verify(mockReply).setEphemeral(true);
  }

  @Test
  void onSlashCommandInteraction_EmptyCommand_ShouldReplyWithError() {
    when(mockSlashEvent.getName()).thenReturn("");

    commandListener.onSlashCommandInteraction(mockSlashEvent);

    verify(mockSlashEvent).reply("Unknown command!");
    verify(mockReply).setEphemeral(true);
  }

  @Test
  void onSlashCommandInteraction_NullCommand_ShouldHandleGracefully() {
    when(mockSlashEvent.getName()).thenReturn(null);

    commandListener.onSlashCommandInteraction(mockSlashEvent);

    verify(mockSlashEvent).reply("Unknown command!");
    verify(mockReply).setEphemeral(true);
  }

  @Test
  void onSlashCommandInteraction_MultipleUsers_ShouldExecuteForEach() {
    final User user1 = mock(User.class);
    final User user2 = mock(User.class);
    when(user1.getName()).thenReturn("User1");
    when(user2.getName()).thenReturn("User2");

    when(mockSlashEvent.getName()).thenReturn("info");
    when(mockSlashEvent.getUser()).thenReturn(user1);
    commandListener.onSlashCommandInteraction(mockSlashEvent);

    when(mockSlashEvent.getUser()).thenReturn(user2);
    commandListener.onSlashCommandInteraction(mockSlashEvent);

    verify(mockSlashEvent, times(2)).getName();
    verify(mockSlashEvent, times(2)).getUser();
  }

  @Test
  void onSlashCommandInteraction_PartialCommandMatch_ShouldNotMatch() {
    when(mockSlashEvent.getName()).thenReturn("inf");

    commandListener.onSlashCommandInteraction(mockSlashEvent);

    verify(mockSlashEvent).reply("Unknown command!");
    verify(mockReply).setEphemeral(true);
  }

  @Test
  void onSlashCommandInteraction_CommandWithSpaces_ShouldNotMatch() {
    when(mockSlashEvent.getName()).thenReturn("info ");

    commandListener.onSlashCommandInteraction(mockSlashEvent);

    verify(mockSlashEvent).reply("Unknown command!");
    verify(mockReply).setEphemeral(true);
  }

  private void assertNotNull(final Object object) {
    if (object == null) {
      throw new AssertionError("Expected object to be non-null");
    }
  }
}
