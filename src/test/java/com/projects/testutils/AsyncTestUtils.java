package com.projects.testutils;

import static org.mockito.Mockito.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class AsyncTestUtils {

  private AsyncTestUtils() {}

  public static ScheduledExecutorService createMockScheduler() {
    final ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);

    when(scheduler.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
        .thenReturn(mock(ScheduledFuture.class));

    when(scheduler.scheduleWithFixedDelay(
            any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class)))
        .thenReturn(mock(ScheduledFuture.class));

    return scheduler;
  }

  public static ScheduledFuture<?> createMockScheduledFuture() {
    final ScheduledFuture<?> future = mock(ScheduledFuture.class);
    when(future.isDone()).thenReturn(false);
    when(future.cancel(anyBoolean())).thenReturn(true);
    return future;
  }

  public static void executeScheduledTask(final ScheduledExecutorService scheduler) {
    verify(scheduler).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));

    doAnswer(
            invocation -> {
              final Runnable task = invocation.getArgument(0);
              task.run();
              return mock(ScheduledFuture.class);
            })
        .when(scheduler)
        .schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
  }

  public static void executePeriodicTask(final ScheduledExecutorService scheduler) {
    verify(scheduler)
        .scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));

    doAnswer(
            invocation -> {
              final Runnable task = invocation.getArgument(0);
              task.run();
              return mock(ScheduledFuture.class);
            })
        .when(scheduler)
        .scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));
  }

  public static void waitForAsync(final long milliseconds) {
    try {
      Thread.sleep(milliseconds);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  public static CompletableFuture<Void> createCompletedFuture() {
    return CompletableFuture.completedFuture(null);
  }

  public static CompletableFuture<Void> createFailedFuture(final Throwable exception) {
    final CompletableFuture<Void> future = new CompletableFuture<>();
    future.completeExceptionally(exception);
    return future;
  }
}
