package com.projects.readycheck;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.projects.readycheck.exceptions.ReadyCheckNotFoundException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReadyCheckServiceTest {

  @Mock
  private JDA mockJDA;

  @Mock
  private Member mockMember1;

  @Mock
  private Member mockMember2;

  @Mock
  private Member mockInitiator;

  private ReadyCheckService readyCheckService;

  @BeforeEach
  void setUp() {
    readyCheckService = new ReadyCheckService();

    // Setup mock member IDs with lenient stubbing
    lenient().when(mockMember1.getId()).thenReturn("user1");
    lenient().when(mockMember2.getId()).thenReturn("user2");
    lenient().when(mockInitiator.getId()).thenReturn("initiator");
  }

  @Test
  @DisplayName("Should create user-based ready check")
  void testCreateUserReadyCheck() {
    List<Member> targetMembers = Arrays.asList(mockMember1, mockMember2);

    String readyCheckId = readyCheckService.createUserReadyCheck(
        "guild123",
        "channel456",
        "initiator",
        targetMembers
    );

    ReadyCheckManager.ReadyCheck readyCheck = readyCheckService.getActiveReadyCheck(readyCheckId);
    assertNull(readyCheck.getRoleId());
  }

  @Test
  @DisplayName("Should create role-based ready check")
  void testCreateRoleReadyCheck() {
    List<Member> targetMembers = Arrays.asList(mockMember1, mockMember2);

    String readyCheckId = readyCheckService.createReadyCheck(
        "guild123",
        "channel456",
        "initiator",
        "role789",
        targetMembers
    );

    ReadyCheckManager.ReadyCheck readyCheck = readyCheckService.getActiveReadyCheck(readyCheckId);
    assertEquals("role789", readyCheck.getRoleId());
  }

  @Test
  @DisplayName("Should include initiator as ready and in target users")
  void testInitiatorIncludedInReadyCheck() {
    List<Member> targetMembers = Arrays.asList(mockMember1, mockMember2);

    String readyCheckId = readyCheckService.createUserReadyCheck(
        "guild123",
        "channel456",
        "initiator",
        targetMembers
    );

    ReadyCheckManager.ReadyCheck readyCheck = readyCheckService.getActiveReadyCheck(readyCheckId);

    assertTrue(readyCheck.getReadyUsers().contains("initiator"));
    assertTrue(readyCheck.getTargetUsers().contains("initiator"));
  }

  @Test
  @DisplayName("Should manage mention preferences")
  void testMentionPreferences() {
    List<Member> targetMembers = Arrays.asList(mockMember1);
    String readyCheckId = readyCheckService.createUserReadyCheck(
        "guild123",
        "channel456",
        "initiator",
        targetMembers
    );

    // Default should be true
    assertTrue(readyCheckService.getMentionPreference(readyCheckId));

    // Set to false
    readyCheckService.setMentionPreference(readyCheckId, false);
    assertFalse(readyCheckService.getMentionPreference(readyCheckId));

    // Set back to true
    readyCheckService.setMentionPreference(readyCheckId, true);
    assertTrue(readyCheckService.getMentionPreference(readyCheckId));
  }


  @Test
  @DisplayName("Should mark user as ready")
  void testMarkUserReady() {
    List<Member> targetMembers = Arrays.asList(mockMember1);
    String readyCheckId = readyCheckService.createUserReadyCheck(
        "guild123",
        "channel456",
        "initiator",
        targetMembers
    );

    readyCheckService.markUserReady(readyCheckId, "user1");

    ReadyCheckManager.ReadyCheck readyCheck = readyCheckService.getActiveReadyCheck(readyCheckId);
    assertTrue(readyCheck.getReadyUsers().contains("user1"));
  }

  @Test
  @DisplayName("Should handle operations on non-existent ready check gracefully")
  void testNonExistentReadyCheckOperations() {
    // Should not throw exceptions, just log warnings
    assertDoesNotThrow(() -> readyCheckService.markUserReady("non-existent", "user1"));
    assertDoesNotThrow(() -> readyCheckService.ensureUserInReadyCheck("non-existent", "user1"));

    // Should return false for toggle operations
    assertFalse(readyCheckService.toggleUserReady("non-existent", "user1"));
  }

  @Test
  @DisplayName("Should toggle user ready status")
  void testToggleUserReady() {
    List<Member> targetMembers = Arrays.asList(mockMember1);
    String readyCheckId = readyCheckService.createUserReadyCheck(
        "guild123",
        "channel456",
        "initiator",
        targetMembers
    );

    ReadyCheckManager.ReadyCheck readyCheck = readyCheckService.getActiveReadyCheck(readyCheckId);

    // Toggle to ready
    boolean result = readyCheckService.toggleUserReady(readyCheckId, "user1");
    assertTrue(result);
    assertTrue(readyCheck.getReadyUsers().contains("user1"));

    // Toggle back to not ready
    result = readyCheckService.toggleUserReady(readyCheckId, "user1");
    assertFalse(result);
    assertFalse(readyCheck.getReadyUsers().contains("user1"));
  }


  @Test
  @DisplayName("Should mark user as passed")
  void testMarkUserPassed() {
    List<Member> targetMembers = Arrays.asList(mockMember1);
    String readyCheckId = readyCheckService.createUserReadyCheck(
        "guild123",
        "channel456",
        "initiator",
        targetMembers
    );

    // First mark user as ready
    readyCheckService.markUserReady(readyCheckId, "user1");

    // Then mark as passed
    readyCheckService.markUserPassed(readyCheckId, "user1");

    ReadyCheckManager.ReadyCheck readyCheck = readyCheckService.getActiveReadyCheck(readyCheckId);
    assertTrue(readyCheck.getPassedUsers().contains("user1"));
    assertFalse(readyCheck.getReadyUsers().contains("user1"));
  }

  @Test
  @DisplayName("Should ensure user is in ready check")
  void testEnsureUserInReadyCheck() {
    List<Member> targetMembers = Arrays.asList(mockMember1);
    String readyCheckId = readyCheckService.createUserReadyCheck(
        "guild123",
        "channel456",
        "initiator",
        targetMembers
    );

    readyCheckService.ensureUserInReadyCheck(readyCheckId, "new-user");

    ReadyCheckManager.ReadyCheck readyCheck = readyCheckService.getActiveReadyCheck(readyCheckId);
    assertTrue(readyCheck.getTargetUsers().contains("new-user"));
  }


  @Test
  @DisplayName("Should find active ready check in channel")
  void testFindActiveReadyCheckInChannel() {
    List<Member> targetMembers = Arrays.asList(mockMember1);
    String readyCheckId = readyCheckService.createUserReadyCheck(
        "guild123",
        "channel456",
        "initiator",
        targetMembers
    );

    String foundId = readyCheckService.findActiveReadyCheckInChannel("guild123", "channel456");
    assertEquals(readyCheckId, foundId);
  }

  @Test
  @DisplayName("Should return null when no active ready check in channel")
  void testFindNoActiveReadyCheckInChannel() {
    String foundId = readyCheckService.findActiveReadyCheckInChannel("guild123", "channel456");
    assertNull(foundId);
  }

  @Test
  @DisplayName("Should check if all users are ready")
  void testCheckIfAllReady() {
    List<Member> targetMembers = Arrays.asList(mockMember1, mockMember2);
    String readyCheckId = readyCheckService.createUserReadyCheck(
        "guild123",
        "channel456",
        "initiator",
        targetMembers
    );

    // Initially not all ready (only initiator is ready)
    assertFalse(readyCheckService.checkIfAllReady(readyCheckId));

    // Mark all users as ready
    readyCheckService.markUserReady(readyCheckId, "user1");
    readyCheckService.markUserReady(readyCheckId, "user2");

    // Now all should be ready
    assertTrue(readyCheckService.checkIfAllReady(readyCheckId));
  }

  @Test
  @DisplayName("Should handle schedule ready at with exception")
  void testScheduleReadyAtWithException() {
    List<Member> targetMembers = Arrays.asList(mockMember1);
    String readyCheckId = readyCheckService.createUserReadyCheck(
        "guild123",
        "channel456",
        "initiator",
        targetMembers
    );

    // This should not throw an exception from the service level
    assertDoesNotThrow(() -> {
      try {
        readyCheckService.scheduleReadyAt(readyCheckId, "invalid-time", "user1", mockJDA);
      } catch (ReadyCheckNotFoundException e) {
        // This is expected if the ready check doesn't exist
      }
    });
  }

  @Test
  @DisplayName("Should throw ReadyCheckNotFoundException for non-existent ready check")
  void testScheduleReadyAtNonExistentReadyCheck() {
    assertThrows(ReadyCheckNotFoundException.class, () ->
        readyCheckService.scheduleReadyAt("non-existent", "15", "user1", mockJDA)
    );
  }

  @Test
  @DisplayName("Should get active ready checks map")
  void testGetActiveReadyChecks() {
    List<Member> targetMembers = Arrays.asList(mockMember1);
    String readyCheckId = readyCheckService.createUserReadyCheck(
        "guild123",
        "channel456",
        "initiator",
        targetMembers
    );

    Map<String, ReadyCheckManager.ReadyCheck> activeChecks = readyCheckService.getActiveReadyChecks();
    assertTrue(activeChecks.containsKey(readyCheckId));
    assertEquals(1, activeChecks.size());
  }

  @Test
  @DisplayName("Should return null for non-existent ready check")
  void testGetNonExistentReadyCheck() {
    ReadyCheckManager.ReadyCheck readyCheck = readyCheckService.getActiveReadyCheck("non-existent");
    assertNull(readyCheck);
  }
}