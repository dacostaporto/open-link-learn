package com.openlinklearn.session.application;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.zone.ZoneOffsetTransition;
import java.time.zone.ZoneRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.time.DayOfWeek;


@Component
public final class WeeklyRecurrenceService {

    public record WeeklyRule(
            LocalDate startDate,
            LocalDate endDate,
            Set<DayOfWeek> weekdays,
            LocalTime startTime,
            String timezone
    ) { }

    public record Occurrence(
            LocalDate localDate,
            LocalDateTime localStart,
            ZoneOffset offset,
            Instant startsAt
    ) { }


    public List<Occurrence> calculate(WeeklyRule rule, LocalDate fromInclusive, LocalDate throughInclusive) {
        ZoneId zone = validate(rule, fromInclusive, throughInclusive);
        Set<DayOfWeek> weekdays = Set.copyOf(rule.weekdays());
        ZoneRules zoneRules = zone.getRules();

        LocalDate firstDate = fromInclusive.isAfter(rule.startDate()) ? fromInclusive : rule.startDate();
        LocalDate lastDate = throughInclusive;
        if (rule.endDate() != null && rule.endDate().isBefore(lastDate)) {
            lastDate = rule.endDate();
        }
        if (firstDate.isAfter(lastDate)) {
            return List.of();
        }

        List<Occurrence> occurrences = new ArrayList<>();
        LocalDate date = firstDate;
        while (true) {
            if (weekdays.contains(date.getDayOfWeek())) {
                LocalDateTime requestedLocalStart = LocalDateTime.of(date, rule.startTime());
                occurrences.add(resolve(requestedLocalStart, zoneRules));
            }
            if (date.equals(lastDate)) {
                break;
            }
            date = date.plusDays(1);
        }
        return List.copyOf(occurrences);
    }

    private ZoneId validate(WeeklyRule rule, LocalDate fromInclusive, LocalDate throughInclusive) {
        if (rule == null || rule.startDate() == null || rule.startTime() == null) {
            throw new IllegalArgumentException("A recurrence rule, start date, and local start time are required.");
        }
        if (rule.endDate() != null && rule.endDate().isBefore(rule.startDate())) {
            throw new IllegalArgumentException("The recurrence end date cannot be before its start date.");
        }
        if (rule.weekdays() == null || rule.weekdays().isEmpty()
                || rule.weekdays().stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("At least one valid weekday is required.");
        }
        if (fromInclusive == null || throughInclusive == null || fromInclusive.isAfter(throughInclusive)) {
            throw new IllegalArgumentException("The calculation window must have ordered inclusive date bounds.");
        }

        String timezone = rule.timezone() == null ? "" : rule.timezone().trim();
        if (timezone.isBlank() || timezone.length() > 64 || !ZoneId.getAvailableZoneIds().contains(timezone)) {
            throw new IllegalArgumentException("Timezone must be a valid IANA timezone.");
        }
        return ZoneId.of(timezone);
    }

    private Occurrence resolve(LocalDateTime requestedLocalStart, ZoneRules rules) {
        List<ZoneOffset> offsets = rules.getValidOffsets(requestedLocalStart);
        if (offsets.size() == 1) {
            return occurrence(requestedLocalStart.toLocalDate(), requestedLocalStart, offsets.get(0));
        }
        if (offsets.size() > 1) {
            return occurrence(requestedLocalStart.toLocalDate(), requestedLocalStart, offsets.get(0));
        }

        ZoneOffsetTransition transition = rules.getTransition(requestedLocalStart);
        if (transition == null || !transition.isGap()) {
            throw new IllegalArgumentException("The local recurrence time cannot be resolved in its timezone.");
        }
        LocalDateTime shiftedLocalStart = requestedLocalStart.plus(transition.getDuration());
        return occurrence(requestedLocalStart.toLocalDate(), shiftedLocalStart, transition.getOffsetAfter());
    }

    private Occurrence occurrence(LocalDate localDate, LocalDateTime localStart, ZoneOffset offset) {
        return new Occurrence(localDate, localStart, offset, localStart.toInstant(offset));
    }
}