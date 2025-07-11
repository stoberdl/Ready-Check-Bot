package com.projects;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ReadyCheckBotTest {

  @Test
  void testApplicationStarts() {
    assertDoesNotThrow(
        () -> {
          Class<?> clazz = Class.forName("com.projects.ReadyCheckBot");
          assertNotNull(clazz);
        });
  }
}
