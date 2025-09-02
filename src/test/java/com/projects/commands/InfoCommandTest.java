package com.projects.commands;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.awt.Color;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class InfoCommandTest {

  private InfoCommand infoCommand;
  private SlashCommandInteractionEvent mockEvent;
  private ReplyCallbackAction mockReply;

  @BeforeEach
  void setUp() {
    infoCommand = new InfoCommand();
    mockEvent = mock(SlashCommandInteractionEvent.class);
    mockReply = mock(ReplyCallbackAction.class);

    when(mockEvent.replyEmbeds(any(MessageEmbed.class))).thenReturn(mockReply);
    when(mockReply.queue()).thenReturn(null);
  }

  @Test
  void getName_ShouldReturnInfo() {
    assertEquals("info", infoCommand.getName());
  }

  @Test
  void getDescription_ShouldReturnCorrectDescription() {
    assertEquals("Displays information about the bot.", infoCommand.getDescription());
  }

  @Test
  void executeSlash_ShouldCreateAndSendEmbed() {
    infoCommand.executeSlash(mockEvent);

    verify(mockEvent).replyEmbeds(any(MessageEmbed.class));
    verify(mockReply).queue();
  }

  @Test
  void executeSlash_ShouldCreateEmbedWithCorrectContent() {
    final EmbedBuilder expectedEmbed = new EmbedBuilder();
    expectedEmbed.setTitle("Bot Information");
    expectedEmbed.setDescription("Java discord bot for ready checking");
    expectedEmbed.setColor(new Color(148, 0, 211));
    expectedEmbed.addField("Language", "Java 21", true);
    expectedEmbed.addField("Library", "JDA (Java Discord API)", true);
    expectedEmbed.setFooter("Version 1.2");

    infoCommand.executeSlash(mockEvent);

    verify(mockEvent)
        .replyEmbeds(
            argThat(
                embed ->
                    "Bot Information".equals(embed.getTitle())
                        && "Java discord bot for ready checking".equals(embed.getDescription())
                        && embed.getColor().getRGB() == new Color(148, 0, 211).getRGB()
                        && embed.getFields().size() == 2
                        && "Language".equals(embed.getFields().get(0).getName())
                        && "Java 21".equals(embed.getFields().get(0).getValue())
                        && embed.getFields().get(0).isInline()
                        && "Library".equals(embed.getFields().get(1).getName())
                        && "JDA (Java Discord API)".equals(embed.getFields().get(1).getValue())
                        && embed.getFields().get(1).isInline()
                        && "Version 1.2".equals(embed.getFooter().getText())));
  }

  @Test
  void executeSlash_EmbedColor_ShouldBePurple() {
    infoCommand.executeSlash(mockEvent);

    verify(mockEvent)
        .replyEmbeds(
            argThat(embed -> embed.getColor().getRGB() == new Color(148, 0, 211).getRGB()));
  }

  @Test
  void executeSlash_EmbedFields_ShouldBeInline() {
    infoCommand.executeSlash(mockEvent);

    verify(mockEvent)
        .replyEmbeds(
            argThat(embed -> embed.getFields().stream().allMatch(MessageEmbed.Field::isInline)));
  }

  @Test
  void executeSlash_EmbedFieldCount_ShouldBeTwo() {
    infoCommand.executeSlash(mockEvent);

    verify(mockEvent).replyEmbeds(argThat(embed -> embed.getFields().size() == 2));
  }

  @Test
  void executeSlash_MultipleInvocations_ShouldWorkConsistently() {
    for (int i = 0; i < 3; i++) {
      infoCommand.executeSlash(mockEvent);
    }

    verify(mockEvent, times(3)).replyEmbeds(any(MessageEmbed.class));
    verify(mockReply, times(3)).queue();
  }
}
