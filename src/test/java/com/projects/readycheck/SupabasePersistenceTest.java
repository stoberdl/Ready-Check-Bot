package com.projects.readycheck;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.projects.testutils.TestReadyCheckUtils;
import java.io.IOException;
import java.util.List;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

final class SupabasePersistenceTest {

  private OkHttpClient mockClient;
  private Call mockCall;
  private Response mockResponse;
  private ResponseBody mockResponseBody;

  @BeforeEach
  void setUp() {
    mockClient = mock(OkHttpClient.class);
    mockCall = mock(Call.class);
    mockResponse = mock(Response.class);
    mockResponseBody = mock(ResponseBody.class);

    try {
      when(mockClient.newCall(any(Request.class))).thenReturn(mockCall);
      when(mockCall.execute()).thenReturn(mockResponse);
      when(mockResponse.body()).thenReturn(mockResponseBody);
      when(mockResponse.close()).thenReturn(null);
    } catch (final IOException e) {
      fail("Setup failed: " + e.getMessage());
    }
  }

  @Test
  void saveReadyCheck_RoleBasedCheck_ShouldSerializeCorrectly() throws IOException {
    final ReadyCheckManager.ReadyCheck readyCheck =
        TestReadyCheckUtils.builder()
            .guild("guild-123")
            .role("role-456")
            .targetUsers("user-1", "user-2")
            .build();

    when(mockResponseBody.string()).thenReturn("[]");

    try (MockedStatic<OkHttpClient> mockedClient = mockStatic(OkHttpClient.class)) {
      mockedClient.when(OkHttpClient::new).thenReturn(mockClient);

      assertDoesNotThrow(() -> SupabasePersistence.saveReadyCheck(readyCheck, true));

      verify(mockClient).newCall(any(Request.class));
      verify(mockCall).execute();
    }
  }

  @Test
  void saveReadyCheck_UserBasedCheck_ShouldSerializeCorrectly() throws IOException {
    final ReadyCheckManager.ReadyCheck readyCheck =
        TestReadyCheckUtils.builder().guild("guild-123").targetUsers("user-1", "user-2").build();

    when(mockResponseBody.string()).thenReturn("[]");

    try (MockedStatic<OkHttpClient> mockedClient = mockStatic(OkHttpClient.class)) {
      mockedClient.when(OkHttpClient::new).thenReturn(mockClient);

      assertDoesNotThrow(() -> SupabasePersistence.saveReadyCheck(readyCheck, false));

      verify(mockClient).newCall(any(Request.class));
      verify(mockCall).execute();
    }
  }

  @Test
  void getSavedReadyChecks_ValidResponse_ShouldDeserializeCorrectly() throws IOException {
    final String jsonResponse =
        """
        [
          {
            "guild_id": "guild-123",
            "role_id": "role-456",
            "user_based": false,
            "mention_people": true
          },
          {
            "guild_id": "guild-123",
            "user_ids": ["user-1", "user-2"],
            "user_based": true,
            "mention_people": false
          }
        ]
        """;

    when(mockResponseBody.string()).thenReturn(jsonResponse);

    try (MockedStatic<OkHttpClient> mockedClient = mockStatic(OkHttpClient.class)) {
      mockedClient.when(OkHttpClient::new).thenReturn(mockClient);

      final List<ReadyCheckManager.SavedReadyCheck> savedChecks =
          SupabasePersistence.getSavedReadyChecks("guild-123");

      assertEquals(2, savedChecks.size());

      final ReadyCheckManager.SavedReadyCheck roleCheck = savedChecks.get(0);
      assertFalse(roleCheck.isUserBased());
      assertEquals("role-456", roleCheck.getRoleId());
      assertTrue(roleCheck.getMentionPeople());

      final ReadyCheckManager.SavedReadyCheck userCheck = savedChecks.get(1);
      assertTrue(userCheck.isUserBased());
      assertEquals(List.of("user-1", "user-2"), userCheck.getUserIds());
      assertFalse(userCheck.getMentionPeople());
    }
  }

  @Test
  void getSavedReadyChecks_EmptyResponse_ShouldReturnEmptyList() throws IOException {
    when(mockResponseBody.string()).thenReturn("[]");

    try (MockedStatic<OkHttpClient> mockedClient = mockStatic(OkHttpClient.class)) {
      mockedClient.when(OkHttpClient::new).thenReturn(mockClient);

      final List<ReadyCheckManager.SavedReadyCheck> savedChecks =
          SupabasePersistence.getSavedReadyChecks("guild-123");

      assertTrue(savedChecks.isEmpty());
    }
  }

  @Test
  void getSavedReadyChecks_NetworkError_ShouldReturnEmptyList() throws IOException {
    when(mockCall.execute()).thenThrow(new IOException("Network error"));

    try (MockedStatic<OkHttpClient> mockedClient = mockStatic(OkHttpClient.class)) {
      mockedClient.when(OkHttpClient::new).thenReturn(mockClient);

      final List<ReadyCheckManager.SavedReadyCheck> savedChecks =
          SupabasePersistence.getSavedReadyChecks("guild-123");

      assertTrue(savedChecks.isEmpty());
    }
  }

  @Test
  void saveActiveReadyCheck_CompleteReadyCheck_ShouldSerializeAllFields() throws IOException {
    final ReadyCheckManager.ReadyCheck readyCheck =
        TestReadyCheckUtils.builder()
            .id("check-123")
            .guild("guild-456")
            .channel("channel-789")
            .initiator("user-initiator")
            .role("role-123")
            .targetUsers("user-1", "user-2")
            .readyUsers("user-initiator", "user-1")
            .passedUsers("user-2")
            .build();

    readyCheck.setDescription("Test ready check");
    readyCheck.setMessageId("message-123");
    readyCheck.setStatus(ReadyCheckManager.ReadyCheckStatus.ACTIVE);
    readyCheck.getUserTimers().put("user-1", 5);

    when(mockResponseBody.string()).thenReturn("[]");

    try (MockedStatic<OkHttpClient> mockedClient = mockStatic(OkHttpClient.class)) {
      mockedClient.when(OkHttpClient::new).thenReturn(mockClient);

      assertDoesNotThrow(() -> SupabasePersistence.saveActiveReadyCheck(readyCheck));

      verify(mockClient).newCall(any(Request.class));
      verify(mockCall).execute();
    }
  }

  @Test
  void loadActiveReadyChecks_ValidResponse_ShouldDeserializeCorrectly() throws IOException {
    final String jsonResponse =
        """
        [
          {
            "id": "check-123",
            "guild_id": "guild-456",
            "channel_id": "channel-789",
            "initiator_id": "user-initiator",
            "role_id": "role-123",
            "target_users": ["user-1", "user-2"],
            "ready_users": ["user-initiator"],
            "passed_users": ["user-2"],
            "scheduled_users": "{}",
            "user_timers": "{}",
            "description": "Test ready check",
            "status": "ACTIVE",
            "message_id": "message-123"
          }
        ]
        """;

    when(mockResponseBody.string()).thenReturn(jsonResponse);

    try (MockedStatic<OkHttpClient> mockedClient = mockStatic(OkHttpClient.class)) {
      mockedClient.when(OkHttpClient::new).thenReturn(mockClient);

      final List<ReadyCheckManager.ReadyCheck> readyChecks =
          SupabasePersistence.loadActiveReadyChecks();

      assertEquals(1, readyChecks.size());

      final ReadyCheckManager.ReadyCheck readyCheck = readyChecks.get(0);
      assertEquals("check-123", readyCheck.getId());
      assertEquals("guild-456", readyCheck.getGuildId());
      assertEquals("channel-789", readyCheck.getChannelId());
      assertEquals("user-initiator", readyCheck.getInitiatorId());
      assertEquals("role-123", readyCheck.getRoleId());
      assertTrue(readyCheck.getTargetUsers().contains("user-1"));
      assertTrue(readyCheck.getTargetUsers().contains("user-2"));
      assertTrue(readyCheck.getReadyUsers().contains("user-initiator"));
      assertTrue(readyCheck.getPassedUsers().contains("user-2"));
      assertEquals("Test ready check", readyCheck.getDescription());
      assertEquals(ReadyCheckManager.ReadyCheckStatus.ACTIVE, readyCheck.getStatus());
      assertEquals("message-123", readyCheck.getMessageId());
    }
  }

  @Test
  void loadActiveReadyChecks_NetworkError_ShouldReturnEmptyList() throws IOException {
    when(mockCall.execute()).thenThrow(new IOException("Network error"));

    try (MockedStatic<OkHttpClient> mockedClient = mockStatic(OkHttpClient.class)) {
      mockedClient.when(OkHttpClient::new).thenReturn(mockClient);

      final List<ReadyCheckManager.ReadyCheck> readyChecks =
          SupabasePersistence.loadActiveReadyChecks();

      assertTrue(readyChecks.isEmpty());
    }
  }

  @Test
  void deleteActiveReadyCheck_ShouldMakeDeleteRequest() throws IOException {
    when(mockResponseBody.string()).thenReturn("");

    try (MockedStatic<OkHttpClient> mockedClient = mockStatic(OkHttpClient.class)) {
      mockedClient.when(OkHttpClient::new).thenReturn(mockClient);

      assertDoesNotThrow(() -> SupabasePersistence.deleteActiveReadyCheck("check-123"));

      verify(mockClient).newCall(any(Request.class));
      verify(mockCall).execute();
    }
  }

  @Test
  void loadActiveReadyChecks_WithScheduledUsers_ShouldDeserializeScheduledUsers()
      throws IOException {
    final String jsonResponse =
        """
        [
          {
            "id": "check-123",
            "guild_id": "guild-456",
            "channel_id": "channel-789",
            "initiator_id": "user-initiator",
            "role_id": null,
            "target_users": ["user-1"],
            "ready_users": [],
            "passed_users": [],
            "scheduled_users": "{\\"user-1\\": {\\"readyTimestamp\\": 1640995200000}}",
            "user_timers": "{\\"user-1\\": 10}",
            "description": "Test ready check",
            "status": "ACTIVE",
            "message_id": null
          }
        ]
        """;

    when(mockResponseBody.string()).thenReturn(jsonResponse);

    try (MockedStatic<OkHttpClient> mockedClient = mockStatic(OkHttpClient.class)) {
      mockedClient.when(OkHttpClient::new).thenReturn(mockClient);

      final List<ReadyCheckManager.ReadyCheck> readyChecks =
          SupabasePersistence.loadActiveReadyChecks();

      assertEquals(1, readyChecks.size());

      final ReadyCheckManager.ReadyCheck readyCheck = readyChecks.get(0);
      assertTrue(readyCheck.getScheduledUsers().containsKey("user-1"));
      assertEquals(1640995200000L, readyCheck.getScheduledUsers().get("user-1").readyTimestamp());
      assertEquals(Integer.valueOf(10), readyCheck.getUserTimers().get("user-1"));
    }
  }
}
