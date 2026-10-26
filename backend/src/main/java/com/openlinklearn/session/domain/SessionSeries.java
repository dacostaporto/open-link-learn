package com.openlinklearn.session.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "session_series")
public class SessionSeries {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(nullable = false, length = 64)
    private String timezone;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "days_of_week", nullable = false, columnDefinition = "smallint[]")
    private Short[] daysOfWeek;

    @Column(name = "reminder_minutes_before")
    private Integer reminderMinutesBefore;

    @Column(name = "assignment_cutoff_minutes_before")
    private Integer assignmentCutoffMinutesBefore;

    @Column(nullable = false)
    private boolean active;

    protected SessionSeries() {
    }

    public SessionSeries(
            UUID id,
            UUID ownerId,
            LocalDate startDate,
            LocalDate endDate,
            LocalTime startTime,
            int durationMinutes,
            String timezone,
            Short[] daysOfWeek,
            Integer reminderMinutesBefore,
            Integer assignmentCutoffMinutesBefore
    ) {
        this.id = id;
        this.ownerId = ownerId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.startTime = startTime;
        this.durationMinutes = durationMinutes;
        this.timezone = timezone;
        this.daysOfWeek = daysOfWeek.clone();
        this.reminderMinutesBefore = reminderMinutesBefore;
        this.assignmentCutoffMinutesBefore = assignmentCutoffMinutesBefore;
        this.active = true;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public String getTimezone() {
        return timezone;
    }

    public Short[] getDaysOfWeek() {
        return daysOfWeek.clone();
    }

    public Integer getReminderMinutesBefore() {
        return reminderMinutesBefore;
    }

    public Integer getAssignmentCutoffMinutesBefore() {
        return assignmentCutoffMinutesBefore;
    }

    public boolean isActive() {
        return active;
    }

    public void updateSchedule(
            LocalDate startDate,
            LocalDate endDate,
            LocalTime startTime,
            int durationMinutes,
            String timezone,
            Short[] daysOfWeek,
            Integer reminderMinutesBefore,
            Integer assignmentCutoffMinutesBefore
    ) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.startTime = startTime;
        this.durationMinutes = durationMinutes;
        this.timezone = timezone;
        this.daysOfWeek = daysOfWeek.clone();
        this.reminderMinutesBefore = reminderMinutesBefore;
        this.assignmentCutoffMinutesBefore = assignmentCutoffMinutesBefore;
    }

    public void deactivate() {
        active = false;
    }
}