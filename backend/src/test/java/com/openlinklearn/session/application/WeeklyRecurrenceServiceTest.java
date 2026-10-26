package com.openlinklearn.session.application;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WeeklyRecurrenceServiceTest {

    private final WeeklyRecurrenceService service = new WeeklyRecurrenceService();

    @Test
    void calculatesOccurrencesForOneWeekdayWithinTheInclusiveWindow() {
        List<WeeklyRecurrenceService.Occurrence> occurrences = service.calculate(
                rule(LocalDate.parse("2026-10-19"), null, Set.of(DayOfWeek.MONDAY), LocalTime.of(8, 15), "Europe/Madrid"),
                LocalDate.parse("2026-10-18"),
                LocalDate.parse("2026-10-26"));

        assertEquals(List.of(LocalDate.parse("2026-10-19"), LocalDate.parse("2026-10-26")), dates(occurrences));
        assertEquals(Instant.parse("2026-10-26T07:15:00Z"), occurrences.get(1).startsAt());
    }

    @Test
    void calculatesOccurrencesForMultipleWeekdaysInDateOrder() {
        List<WeeklyRecurrenceService.Occurrence> occurrences = service.calculate(
                rule(LocalDate.parse("2026-10-19"), null,
                        Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
                        LocalTime.of(9, 0), "Etc/UTC"),
                LocalDate.parse("2026-10-19"),
                LocalDate.parse("2026-10-25"));

        assertEquals(List.of(
                LocalDate.parse("2026-10-19"),
                LocalDate.parse("2026-10-21"),
                LocalDate.parse("2026-10-23")
        ), dates(occurrences));
    }

    @Test
    void includesTheSeriesEndDateAndClampsTheCalculationWindowToSeriesDates() {
        List<WeeklyRecurrenceService.Occurrence> occurrences = service.calculate(
                rule(LocalDate.parse("2026-10-18"), LocalDate.parse("2026-10-25"),
                        Set.of(DayOfWeek.SUNDAY), LocalTime.NOON, "Etc/UTC"),
                LocalDate.parse("2026-10-17"),
                LocalDate.parse("2026-11-01"));

        assertEquals(List.of(LocalDate.parse("2026-10-18"), LocalDate.parse("2026-10-25")), dates(occurrences));
    }

    @Test
    void shiftsNonexistentMadridLocalTimeForwardByTheDstGap() {
        WeeklyRecurrenceService.Occurrence occurrence = service.calculate(
                rule(LocalDate.parse("2026-03-29"), LocalDate.parse("2026-03-29"),
                        Set.of(DayOfWeek.SUNDAY), LocalTime.of(2, 30), "Europe/Madrid"),
                LocalDate.parse("2026-03-29"),
                LocalDate.parse("2026-03-29")).get(0);

        assertEquals(LocalDate.parse("2026-03-29"), occurrence.localDate());
        assertEquals(LocalDate.parse("2026-03-29").atTime(3, 30), occurrence.localStart());
        assertEquals(ZoneOffset.ofHours(2), occurrence.offset());
        assertEquals(Instant.parse("2026-03-29T01:30:00Z"), occurrence.startsAt());
    }

    @Test
    void selectsTheEarlierOffsetForRepeatedMadridLocalTime() {
        WeeklyRecurrenceService.Occurrence occurrence = service.calculate(
                rule(LocalDate.parse("2026-10-25"), LocalDate.parse("2026-10-25"),
                        Set.of(DayOfWeek.SUNDAY), LocalTime.of(2, 30), "Europe/Madrid"),
                LocalDate.parse("2026-10-25"),
                LocalDate.parse("2026-10-25")).get(0);

        assertEquals(LocalDate.parse("2026-10-25").atTime(2, 30), occurrence.localStart());
        assertEquals(ZoneOffset.ofHours(2), occurrence.offset());
        assertEquals(Instant.parse("2026-10-25T00:30:00Z"), occurrence.startsAt());
    }

    @Test
    void rejectsInvalidWeekdaysDateBoundsAndNonIanaTimezones() {
        assertThrows(IllegalArgumentException.class, () -> service.calculate(
                rule(LocalDate.parse("2026-10-19"), null, Set.of(), LocalTime.NOON, "Etc/UTC"),
                LocalDate.parse("2026-10-19"), LocalDate.parse("2026-10-20")));
        assertThrows(IllegalArgumentException.class, () -> service.calculate(
                rule(LocalDate.parse("2026-10-20"), LocalDate.parse("2026-10-19"),
                        Set.of(DayOfWeek.MONDAY), LocalTime.NOON, "Etc/UTC"),
                LocalDate.parse("2026-10-19"), LocalDate.parse("2026-10-20")));
        assertThrows(IllegalArgumentException.class, () -> service.calculate(
                rule(LocalDate.parse("2026-10-19"), null, Set.of(DayOfWeek.MONDAY), LocalTime.NOON, "Not/AZone"),
                LocalDate.parse("2026-10-19"), LocalDate.parse("2026-10-20")));
        assertThrows(IllegalArgumentException.class, () -> service.calculate(
                rule(LocalDate.parse("2026-10-19"), null, Set.of(DayOfWeek.MONDAY), LocalTime.NOON, "+02:00"),
                LocalDate.parse("2026-10-19"), LocalDate.parse("2026-10-20")));
        assertThrows(IllegalArgumentException.class, () -> service.calculate(
                rule(LocalDate.parse("2026-10-19"), null, Set.of(DayOfWeek.MONDAY), LocalTime.NOON, "Etc/UTC"),
                LocalDate.parse("2026-10-20"), LocalDate.parse("2026-10-19")));
    }

    @Test
    void handlesTheMaximumSupportedDateBoundary() {
        LocalDate lastDate = LocalDate.MAX;
        List<WeeklyRecurrenceService.Occurrence> occurrences = service.calculate(
                rule(lastDate, lastDate, Set.of(lastDate.getDayOfWeek()), LocalTime.MIDNIGHT, "Etc/UTC"),
                lastDate,
                lastDate);

        assertEquals(List.of(lastDate), dates(occurrences));
    }

    private WeeklyRecurrenceService.WeeklyRule rule(
            LocalDate startDate,
            LocalDate endDate,
            Set<DayOfWeek> weekdays,
            LocalTime startTime,
            String timezone
    ) {
        return new WeeklyRecurrenceService.WeeklyRule(startDate, endDate, weekdays, startTime, timezone);
    }

    private List<LocalDate> dates(List<WeeklyRecurrenceService.Occurrence> occurrences) {
        return occurrences.stream().map(WeeklyRecurrenceService.Occurrence::localDate).toList();
    }
}