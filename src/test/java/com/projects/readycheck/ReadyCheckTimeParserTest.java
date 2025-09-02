package com.projects.readycheck;

import static org.junit.jupiter.api.Assertions.*;

import com.projects.readycheck.utils.ReadyCheckTimeParser;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

final class ReadyCheckTimeParserTest {

  @Test
  void parseTimeInputAsMinutes_ValidMinutes_ShouldReturnMinutes() {
    assertEquals(15, ReadyCheckTimeParser.parseTimeInputAsMinutes("15"));
    assertEquals(60, ReadyCheckTimeParser.parseTimeInputAsMinutes("60"));
    assertEquals(1440, ReadyCheckTimeParser.parseTimeInputAsMinutes("1440"));
  }

  @Test
  void parseTimeInputAsMinutes_ZeroMinutes_ShouldThrowException() {
    assertThrows(
        IllegalArgumentException.class, () -> ReadyCheckTimeParser.parseTimeInputAsMinutes("0"));
  }

  @Test
  void parseTimeInputAsMinutes_TooLargeMinutes_ShouldThrowException() {
    assertThrows(
        IllegalArgumentException.class, () -> ReadyCheckTimeParser.parseTimeInputAsMinutes("1441"));
  }

  @Test
  void parseTimeInputAsMinutes_NonNumeric_ShouldThrowException() {
    assertThrows(
        IllegalArgumentException.class, () -> ReadyCheckTimeParser.parseTimeInputAsMinutes("abc"));
  }

  @Test
  void parseTargetTime_ExplicitAM_ShouldParseCorrectly() {
    final LocalTime result = ReadyCheckTimeParser.parseTargetTime("8am");
    assertEquals(LocalTime.of(8, 0), result);
  }

  @Test
  void parseTargetTime_ExplicitPM_ShouldParseCorrectly() {
    final LocalTime result = ReadyCheckTimeParser.parseTargetTime("8pm");
    assertEquals(LocalTime.of(20, 0), result);
  }

  @Test
  void parseTargetTime_ExplicitPMWithMinutes_ShouldParseCorrectly() {
    final LocalTime result = ReadyCheckTimeParser.parseTargetTime("5:30pm");
    assertEquals(LocalTime.of(17, 30), result);
  }

  @Test
  void parseTargetTime_Noon_ShouldParseAs12PM() {
    final LocalTime result = ReadyCheckTimeParser.parseTargetTime("12pm");
    assertEquals(LocalTime.of(12, 0), result);
  }

  @Test
  void parseTargetTime_Midnight_ShouldParseAs12AM() {
    final LocalTime result = ReadyCheckTimeParser.parseTargetTime("12am");
    assertEquals(LocalTime.of(0, 0), result);
  }

  @Test
  void parseTargetTime_24HourFormat_ShouldParseCorrectly() {
    final LocalTime result = ReadyCheckTimeParser.parseTargetTime("17:30");
    assertEquals(LocalTime.of(17, 30), result);
  }

  @Test
  void parseTargetTime_24HourMidnight_ShouldParseCorrectly() {
    final LocalTime result = ReadyCheckTimeParser.parseTargetTime("0:00");
    assertEquals(LocalTime.of(0, 0), result);
  }

  @Test
  void parseTargetTime_CompactTime3Digits_ShouldParseCorrectly() {
    final LocalTime result = ReadyCheckTimeParser.parseTargetTime("530");

    assertTrue(result.equals(LocalTime.of(5, 30)) || result.equals(LocalTime.of(17, 30)));
  }

  @Test
  void parseTargetTime_CompactTime4Digits_ShouldParseCorrectly() {
    final LocalTime result = ReadyCheckTimeParser.parseTargetTime("1230");

    assertTrue(result.equals(LocalTime.of(0, 30)) || result.equals(LocalTime.of(12, 30)));
  }

  @Test
  void parseTargetTime_HourOnly_ShouldUseSmartDetection() {
    final LocalTime result = ReadyCheckTimeParser.parseTargetTime("8");

    assertTrue(result.equals(LocalTime.of(8, 0)) || result.equals(LocalTime.of(20, 0)));
  }

  @Test
  void parseTargetTime_TimeWithColon_ShouldUseSmartDetection() {
    final LocalTime result = ReadyCheckTimeParser.parseTargetTime("8:30");

    assertTrue(result.equals(LocalTime.of(8, 30)) || result.equals(LocalTime.of(20, 30)));
  }

  @Test
  void parseTargetTime_InvalidHour_ShouldThrowException() {
    assertThrows(
        IllegalArgumentException.class, () -> ReadyCheckTimeParser.parseTargetTime("25:30"));
  }

  @Test
  void parseTargetTime_InvalidMinutes_ShouldThrowException() {
    assertThrows(
        IllegalArgumentException.class, () -> ReadyCheckTimeParser.parseTargetTime("8:65"));
  }

  @Test
  void parseTargetTime_InvalidFormat_ShouldThrowException() {
    assertThrows(IllegalArgumentException.class, () -> ReadyCheckTimeParser.parseTargetTime("abc"));
  }

  @Test
  void parseTargetTime_EmptyString_ShouldThrowException() {
    assertThrows(IllegalArgumentException.class, () -> ReadyCheckTimeParser.parseTargetTime(""));
  }

  @Test
  void parseTargetTime_WhitespaceOnly_ShouldThrowException() {
    assertThrows(IllegalArgumentException.class, () -> ReadyCheckTimeParser.parseTargetTime("   "));
  }

  @Test
  void parseTargetTime_CaseInsensitive_ShouldWork() {
    final LocalTime result1 = ReadyCheckTimeParser.parseTargetTime("8PM");
    final LocalTime result2 = ReadyCheckTimeParser.parseTargetTime("8pm");
    final LocalTime result3 = ReadyCheckTimeParser.parseTargetTime("8Pm");

    assertEquals(result1, result2);
    assertEquals(result2, result3);
    assertEquals(LocalTime.of(20, 0), result1);
  }

  @Test
  void parseTargetTime_WithSpaces_ShouldTrimAndParse() {
    final LocalTime result = ReadyCheckTimeParser.parseTargetTime("  8:30pm  ");
    assertEquals(LocalTime.of(20, 30), result);
  }

  @Test
  void parseTargetTime_CompactWithSpaces_ShouldWork() {
    final LocalTime result = ReadyCheckTimeParser.parseTargetTime(" 8 pm ");
    assertEquals(LocalTime.of(20, 0), result);
  }

  @Test
  void parseTargetTime_HourOutOfRange_ShouldThrowException() {
    assertThrows(IllegalArgumentException.class, () -> ReadyCheckTimeParser.parseTargetTime("13"));

    assertThrows(IllegalArgumentException.class, () -> ReadyCheckTimeParser.parseTargetTime("0"));
  }

  @Test
  void parseTargetTime_ValidEdgeCases() {
    assertEquals(LocalTime.of(1, 0), ReadyCheckTimeParser.parseTargetTime("1am"));
    assertEquals(LocalTime.of(23, 59), ReadyCheckTimeParser.parseTargetTime("23:59"));
    assertEquals(LocalTime.of(0, 0), ReadyCheckTimeParser.parseTargetTime("0:00"));
    assertEquals(LocalTime.of(12, 0), ReadyCheckTimeParser.parseTargetTime("12:00pm"));
    assertEquals(LocalTime.of(0, 0), ReadyCheckTimeParser.parseTargetTime("12:00am"));
  }
}
