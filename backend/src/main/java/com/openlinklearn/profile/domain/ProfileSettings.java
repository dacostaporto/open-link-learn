package com.openlinklearn.profile.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "app_profile")
public class ProfileSettings {

    @Id
    private UUID id;

    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone;

    @Column(name = "default_reminder_minutes_before", nullable = false)
    private int defaultReminderMinutesBefore;

    @Column(name = "default_assignment_cutoff_minutes_before", nullable = false)
    private int defaultAssignmentCutoffMinutesBefore;

    @Column(name = "recurrence_horizon_days", nullable = false)
    private int recurrenceHorizonDays;

    protected ProfileSettings() {
    }

    public UUID getId() {
        return id;
    }

    public String getTimezone() {
        return timezone;
    }

    public int getDefaultReminderMinutesBefore() {
        return defaultReminderMinutesBefore;
    }

    public int getDefaultAssignmentCutoffMinutesBefore() {
        return defaultAssignmentCutoffMinutesBefore;
    }

    public int getRecurrenceHorizonDays() {
        return recurrenceHorizonDays;
    }

    public void updateSettings(String timezone, int reminderMinutes, int assignmentCutoffMinutes, int horizonDays) {
        this.timezone = timezone;
        this.defaultReminderMinutesBefore = reminderMinutes;
        this.defaultAssignmentCutoffMinutesBefore = assignmentCutoffMinutes;
        this.recurrenceHorizonDays = horizonDays;
    }
}