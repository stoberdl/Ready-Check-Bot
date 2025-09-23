package com.projects.readycheck.utils;

import static org.junit.jupiter.api.Assertions.*;

import com.projects.readycheck.exceptions.InvalidTimeFormatException;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReadyCheckTimeParserTest {

  @Test
  @DisplayName("Should parse valid minutes input")
  void testParseValidMinutes() throws InvalidTimeFormatException {
    assertEquals(5, ReadyCheckTimeParser.parseTimeInputAsMinutes("5"));
    assertEquals(30, ReadyCheckTimeParser.parseTimeInputAsMinutes("30"));
    assertEquals(60, ReadyCheckTimeParser.parseTimeInputAsMinutes("60"));
    assertEquals(1440, ReadyCheckTimeParser.parseTimeInputAsMinutes("1440"));
    assertEquals(1, ReadyCheckTimeParser.parseTimeInputAsMinutes("1"));
  }


  @ParameterizedTest
  @ValueSource(strings = {"0", "-5", "1441", "2000", "-1"})
  @DisplayName("Should throw exception for invalid minute ranges")
  void testInvalidMinuteRanges(String input) {
    InvalidTimeFormatException exception = assertThrows(
        InvalidTimeFormatException.class,
        () -> ReadyCheckTimeParser.parseTimeInputAsMinutes(input)
    );
    // Check for either of the possible error messages
    assertTrue(exception.getMessage().contains("Minutes must be between 1 and 1440") ||
               exception.getMessage().contains("use just the number of minutes"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"abc", "5.5", "5:30", "fifteen", "", " ", "5 minutes"})
  @DisplayName("Should throw exception for non-numeric minute inputs")
  void testNonNumericMinuteInputs(String input) {
    InvalidTimeFormatException exception = assertThrows(
        InvalidTimeFormatException.class,
        () -> ReadyCheckTimeParser.parseTimeInputAsMinutes(input)
    );
    assertTrue(exception.getMessage().contains("use just the number of minutes"));
  }

  @Test
  @DisplayName("Should parse various time formats correctly")
  void testParseTimeFormats() {
    // AM/PM formats
    assertEquals(LocalTime.of(19, 30), ReadyCheckTimeParser.parseTargetTime("7:30pm"));
    assertEquals(LocalTime.of(8, 0), ReadyCheckTimeParser.parseTargetTime("8:00am"));
    assertEquals(LocalTime.of(12, 15), ReadyCheckTimeParser.parseTargetTime("12:15 PM"));

    // 24-hour formats
    assertEquals(LocalTime.of(17, 30), ReadyCheckTimeParser.parseTargetTime("17:30"));
    assertEquals(LocalTime.of(23, 59), ReadyCheckTimeParser.parseTargetTime("23:59"));
    assertEquals(LocalTime.of(0, 0), ReadyCheckTimeParser.parseTargetTime("00:00"));
  }

  @Test
  @DisplayName("Should parse compact time format (e.g., 530, 1730)")
  void testParseCompactTime() {
    LocalTime result1 = ReadyCheckTimeParser.parseTargetTime("530");
    // Should be 5:30, but AM/PM detection depends on current time
    assertTrue(result1.equals(LocalTime.of(5, 30)) || result1.equals(LocalTime.of(17, 30)));

    LocalTime result2 = ReadyCheckTimeParser.parseTargetTime("1230");
    assertTrue(result2.equals(LocalTime.of(12, 30)) || result2.equals(LocalTime.of(0, 30)));
  }

  @Test
  @DisplayName("Should parse midnight and noon correctly")
  void testMidnightAndNoon() {
    assertEquals(LocalTime.of(0, 0), ReadyCheckTimeParser.parseTargetTime("00:00"));
    assertEquals(LocalTime.of(0, 0), ReadyCheckTimeParser.parseTargetTime("12:00am"));
    assertEquals(LocalTime.of(12, 0), ReadyCheckTimeParser.parseTargetTime("12:00pm"));
  }


  @Test
  @DisplayName("Should parse single hour format")
  void testSingleHourFormat() {
    // These should trigger smart detection
    LocalTime result = ReadyCheckTimeParser.parseTargetTime("7");
    // Could be 7am or 7pm depending on current time
    assertTrue(result.equals(LocalTime.of(7, 0)) || result.equals(LocalTime.of(19, 0)));
  }
}