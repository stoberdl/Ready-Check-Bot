import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

final class ReadyCheckBotTest {

  @Test
  void testApplicationStarts() {
    assertDoesNotThrow(
        () -> {
          final Class<?> clazz = Class.forName("com.projects.ReadyCheckBot");
          assertNotNull(clazz);
        });
  }
}
