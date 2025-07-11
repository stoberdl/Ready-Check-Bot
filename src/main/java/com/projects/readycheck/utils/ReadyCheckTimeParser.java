package com.projects.readycheck.utils;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class ReadyCheckTimeParser {
  private static final ZoneId SYSTEM_TIMEZONE = ZoneId.systemDefault();

  private ReadyCheckTimeParser() {}

  public static long parseTimeInputAsMinutes(final String timeInput) {
    final String input = timeInput.trim();

    if (input.matches("\\d+")) {
      final long minutes = Long.parseLong(input);
      if (minutes >= 1 && minutes <= 1440) {
        return minutes;
      } else {
        throw new IllegalArgumentException("Minutes must be between 1 and 1440");
      }
    }

    throw new IllegalArgumentException(
        "For 'r in X', please use just the number of minutes (e.g., '5', '30')");
  }

  public static LocalTime parseTargetTime(final String timeInput) {
    final String input = timeInput.trim().toLowerCase();
    final LocalTime now = LocalTime.now(SYSTEM_TIMEZONE);

    if (containsAmPm(input)) {
      return parseExplicitAmPm(input);
    }

    if (input.contains(":") && is24HourFormat(input)) {
      return parse24HourFormat(input);
    }

    if (input.contains(":")) {
      return parseTimeWithColon(input, now);
    }

    if (input.matches("\\d{3,4}")) {
      return parseCompactTime(input, now);
    }

    return parseWithSmartDetection(input, now);
  }

  private static boolean containsAmPm(final String input) {
    return input.contains("pm") || input.contains("am");
  }

  private static LocalTime parseTimeWithColon(final String input, final LocalTime now) {
    return parseTimeComponents(input, now);
  }

  private static LocalTime parseTimeComponents(final String input, final LocalTime now) {
    final String[] parts = input.split(":");
    if (parts.length != 2) {
      throw new IllegalArgumentException("Invalid time format");
    }

    try {
      final int hour = Integer.parseInt(parts[0]);
      final int minute = Integer.parseInt(parts[1]);

      validateMinutes(minute);
      validateHour(hour);

      return smartAmPmDetection(hour, minute, now);
    } catch (final NumberFormatException e) {
      throw new IllegalArgumentException("Invalid time format");
    }
  }

  private static void validateMinutes(final int minute) {
    if (minute < 0 || minute > 59) {
      throw new IllegalArgumentException("Invalid minutes");
    }
  }

  private static void validateHour(final int hour) {
    if (hour < 1 || hour > 12) {
      throw new IllegalArgumentException("Hour must be between 1 and 12 for ambiguous format");
    }
  }

  private static LocalTime parseCompactTime(final String input, final LocalTime now) {
    try {
      return switch (input.length()) {
        case 3 -> parseThreeDigitTime(input, now);
        case 4 -> parseFourDigitTime(input, now);
        default -> throw new IllegalArgumentException("Invalid time format");
      };
    } catch (final NumberFormatException e) {
      throw new IllegalArgumentException("Invalid time format");
    }
  }

  private static LocalTime parseThreeDigitTime(final String input, final LocalTime now) {
    final int hour = Integer.parseInt(input.substring(0, 1));
    final int minute = Integer.parseInt(input.substring(1, 3));

    validateMinutes(minute);

    if (hour >= 1 && hour <= 9) {
      return smartAmPmDetection(hour, minute, now);
    }
    throw new IllegalArgumentException("Invalid hour in time format");
  }

  private static LocalTime parseFourDigitTime(final String input, final LocalTime now) {
    final int hour = Integer.parseInt(input.substring(0, 2));
    final int minute = Integer.parseInt(input.substring(2, 4));

    validateMinutes(minute);

    if (hour >= 1 && hour <= 12) {
      return smartAmPmDetection(hour, minute, now);
    }
    throw new IllegalArgumentException("Invalid hour in time format");
  }

  private static LocalTime parseExplicitAmPm(final String input) {
    final String normalizedInput = input.toUpperCase().replaceAll("\\s+", "");

    if (normalizedInput.matches("\\d+(PM|AM)")) {
      final int hour = Integer.parseInt(normalizedInput.replaceAll("\\D", ""));
      final boolean isPM = normalizedInput.contains("PM");

      if (hour >= 1 && hour <= 12) {
        if (isPM) {
          return LocalTime.of(hour == 12 ? 12 : hour + 12, 0);
        } else {
          return LocalTime.of(hour == 12 ? 0 : hour, 0);
        }
      } else {
        throw new IllegalArgumentException("Hour must be between 1 and 12");
      }
    } else {
      return LocalTime.parse(normalizedInput, DateTimeFormatter.ofPattern("h:mma"));
    }
  }

  private static boolean is24HourFormat(final String input) {
    final String[] parts = input.split(":");
    if (parts.length >= 1) {
      try {
        final int hour = Integer.parseInt(parts[0]);
        return hour >= 13 || hour == 0;
      } catch (final NumberFormatException e) {
        return false;
      }
    }
    return false;
  }

  private static LocalTime parse24HourFormat(final String input) {
    final String[] parts = input.split(":");
    if (parts.length == 2) {
      final int hour = Integer.parseInt(parts[0]);
      final int minute = Integer.parseInt(parts[1]);

      if (hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59) {
        return LocalTime.of(hour, minute);
      }
    }
    throw new IllegalArgumentException("Invalid 24-hour format");
  }

  private static LocalTime parseWithSmartDetection(final String input, final LocalTime now) {
    final int hour = Integer.parseInt(input);

    if (hour < 1 || hour > 12) {
      throw new IllegalArgumentException("Hour must be between 1 and 12 for ambiguous format");
    }

    return smartAmPmDetection(hour, 0, now);
  }

  private static LocalTime smartAmPmDetection(
      final int hour, final int minute, final LocalTime now) {
    final LocalTime amTime = LocalTime.of(hour == 12 ? 0 : hour, minute);
    final LocalTime pmTime = LocalTime.of(hour == 12 ? 12 : hour + 12, minute);

    final int currentHour = now.getHour();

    if (isLateNightOrEarlyMorning(currentHour)) {
      return amTime.isAfter(now) ? amTime : pmTime;
    }

    if (isMorning(currentHour)) {
      return amTime.isAfter(now) ? amTime : pmTime;
    }

    return pmTime.isAfter(now) ? pmTime : amTime;
  }

  private static boolean isLateNightOrEarlyMorning(final int currentHour) {
    return currentHour >= 22 || currentHour <= 5;
  }

  private static boolean isMorning(final int currentHour) {
    return currentHour <= 11;
  }
}
