package com.projects.readycheck.utils;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class ReadyCheckTimeParser {
  private static final ZoneId SYSTEM_TIMEZONE = ZoneId.systemDefault();

  private ReadyCheckTimeParser() {}

  public static long parseTimeInputAsMinutes(String timeInput) {
    String input = timeInput.trim();

    if (input.matches("\\d+")) {
      long minutes = Long.parseLong(input);
      if (minutes >= 1 && minutes <= 1440) {
        return minutes;
      } else {
        throw new IllegalArgumentException("Minutes must be between 1 and 1440");
      }
    }

    throw new IllegalArgumentException(
        "For 'r in X', please use just the number of minutes (e.g., '5', '30')");
  }

  public static LocalTime parseTargetTime(String timeInput) {
    String input = timeInput.trim().toLowerCase();
    LocalTime now = LocalTime.now(SYSTEM_TIMEZONE);

    if (input.contains("pm") || input.contains("am")) {
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

  private static LocalTime parseTimeWithColon(String input, LocalTime now) {
    return parseTimeComponents(input, now);
  }

  private static LocalTime parseTimeComponents(String input, LocalTime now) {
    String[] parts = input.split(":");
    if (parts.length != 2) {
      throw new IllegalArgumentException("Invalid time format");
    }

    try {
      int hour = Integer.parseInt(parts[0]);
      int minute = Integer.parseInt(parts[1]);

      validateMinutes(minute);
      validateHour(hour);

      return smartAmPmDetection(hour, minute, now);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Invalid time format");
    }
  }

  private static void validateMinutes(int minute) {
    if (minute < 0 || minute > 59) {
      throw new IllegalArgumentException("Invalid minutes");
    }
  }

  private static void validateHour(int hour) {
    if (hour < 1 || hour > 12) {
      throw new IllegalArgumentException("Hour must be between 1 and 12 for ambiguous format");
    }
  }

  private static LocalTime parseCompactTime(String input, LocalTime now) {
    try {
      if (input.length() == 3) {
        return parseThreeDigitTime(input, now);
      } else if (input.length() == 4) {
        return parseFourDigitTime(input, now);
      }

      throw new IllegalArgumentException("Invalid time format");
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Invalid time format");
    }
  }

  private static LocalTime parseThreeDigitTime(String input, LocalTime now) {
    int hour = Integer.parseInt(input.substring(0, 1));
    int minute = Integer.parseInt(input.substring(1, 3));

    validateMinutes(minute);

    if (hour >= 1 && hour <= 9) {
      return smartAmPmDetection(hour, minute, now);
    }
    throw new IllegalArgumentException("Invalid hour in time format");
  }

  private static LocalTime parseFourDigitTime(String input, LocalTime now) {
    int hour = Integer.parseInt(input.substring(0, 2));
    int minute = Integer.parseInt(input.substring(2, 4));

    validateMinutes(minute);

    if (hour >= 1 && hour <= 12) {
      return smartAmPmDetection(hour, minute, now);
    }
    throw new IllegalArgumentException("Invalid hour in time format");
  }

  private static LocalTime parseExplicitAmPm(String input) {
    String normalizedInput = input.toUpperCase().replaceAll("\\s+", "");

    if (normalizedInput.matches("\\d+(PM|AM)")) {
      int hour = Integer.parseInt(normalizedInput.replaceAll("[^0-9]", ""));
      boolean isPM = normalizedInput.contains("PM");

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

  private static boolean is24HourFormat(String input) {
    String[] parts = input.split(":");
    if (parts.length >= 1) {
      try {
        int hour = Integer.parseInt(parts[0]);
        return hour >= 13 || hour == 0;
      } catch (NumberFormatException e) {
        return false;
      }
    }
    return false;
  }

  private static LocalTime parse24HourFormat(String input) {
    String[] parts = input.split(":");
    if (parts.length == 2) {
      int hour = Integer.parseInt(parts[0]);
      int minute = Integer.parseInt(parts[1]);

      if (hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59) {
        return LocalTime.of(hour, minute);
      }
    }
    throw new IllegalArgumentException("Invalid 24-hour format");
  }

  private static LocalTime parseWithSmartDetection(String input, LocalTime now) {
    int hour = Integer.parseInt(input);

    if (hour < 1 || hour > 12) {
      throw new IllegalArgumentException("Hour must be between 1 and 12 for ambiguous format");
    }

    return smartAmPmDetection(hour, 0, now);
  }

  private static LocalTime smartAmPmDetection(int hour, int minute, LocalTime now) {
    LocalTime amTime = LocalTime.of(hour == 12 ? 0 : hour, minute);
    LocalTime pmTime = LocalTime.of(hour == 12 ? 12 : hour + 12, minute);

    int currentHour = now.getHour();

    if (currentHour >= 22 || currentHour <= 5) {
      return amTime.isAfter(now) ? amTime : pmTime;
    }

    if (currentHour <= 11) {
      return amTime.isAfter(now) ? amTime : pmTime;
    }

    return pmTime.isAfter(now) ? pmTime : amTime;
  }
}
