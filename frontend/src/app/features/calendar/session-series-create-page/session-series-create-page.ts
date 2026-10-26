import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { SessionSeriesApiService } from '../../../core/api/session-series-api.service';
import { SessionSeriesResponse, Weekday } from '../../../core/api/session-series';
import { SettingsApiService } from '../../../core/api/settings-api.service';
import { nextWholeHour } from '../session-time/session-time';

const weekdays: Weekday[] = [
  'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY',
];
const weekdayByDateIndex: Weekday[] = [
  'SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY',
];

const timezoneValidator: ValidatorFn = (control: AbstractControl<string>): ValidationErrors | null => {
  const timezone = control.value.trim();
  if (!timezone) return null;

  try {
    new Intl.DateTimeFormat('en', { timeZone: timezone });
    return null;
  } catch {
    return { invalidTimezone: true };
  }
};

const integerRangeValidator = (minimum: number, maximum: number): ValidatorFn =>
  (control: AbstractControl<number | null>): ValidationErrors | null => {
    const value = control.value;
    if (value === null) return null;
    if (!Number.isInteger(value)) return { wholeNumber: true };
    return value < minimum || value > maximum ? { range: { minimum, maximum } } : null;
  };

const selectedWeekdayValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const group = control as FormGroup;
  return Object.values(group.controls).some((weekday) => weekday.value === true)
    ? null
    : { requiredWeekday: true };
};

const dateRangeValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const startDate = control.get('startDate')?.value as string | undefined;
  const endDate = control.get('endDate')?.value as string | undefined;
  return startDate && endDate && endDate < startDate ? { endDateBeforeStart: true } : null;
};

@Component({
  selector: 'app-session-series-create-page',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './session-series-create-page.html',
  styleUrl: './session-series-create-page.scss',
})
export class SessionSeriesCreatePage implements OnInit {
  private readonly settingsApi = inject(SettingsApiService);
  private readonly seriesApi = inject(SessionSeriesApiService);

  protected readonly weekdays = weekdays;
  protected readonly isLoading = signal(true);
  protected readonly isSaving = signal(false);
  protected readonly loadError = signal<string | null>(null);
  protected readonly formError = signal<string | null>(null);
  protected readonly createdSeries = signal<SessionSeriesResponse | null>(null);
  protected readonly submitted = signal(false);

  protected readonly seriesForm = new FormGroup({
    startDate: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    endDate: new FormControl('', { nonNullable: true }),
    startTime: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    durationMinutes: new FormControl<number | null>(30, {
      validators: [Validators.required, integerRangeValidator(1, 720)],
    }),
    timezone: new FormControl('', { nonNullable: true, validators: [Validators.required, timezoneValidator] }),
    daysOfWeek: new FormGroup({
      MONDAY: new FormControl(false, { nonNullable: true }),
      TUESDAY: new FormControl(false, { nonNullable: true }),
      WEDNESDAY: new FormControl(false, { nonNullable: true }),
      THURSDAY: new FormControl(false, { nonNullable: true }),
      FRIDAY: new FormControl(false, { nonNullable: true }),
      SATURDAY: new FormControl(false, { nonNullable: true }),
      SUNDAY: new FormControl(false, { nonNullable: true }),
    }, { validators: [selectedWeekdayValidator] }),
    reminderMinutesBefore: new FormControl<number | null>(10, {
      validators: [Validators.required, integerRangeValidator(0, 1440)],
    }),
    assignmentCutoffMinutesBefore: new FormControl<number | null>(30, {
      validators: [Validators.required, integerRangeValidator(0, 10080)],
    }),
  }, { validators: [dateRangeValidator] });

  ngOnInit(): void {
    this.loadSettings();
  }

  protected retryLoad(): void {
    this.loadSettings();
  }

  protected createSeries(): void {
    if (this.isSaving() || this.createdSeries()) return;
    this.submitted.set(true);
    this.formError.set(null);
    if (this.seriesForm.invalid) {
      this.seriesForm.markAllAsTouched();
      return;
    }

    const values = this.seriesForm.getRawValue();
    if (values.durationMinutes === null
      || values.reminderMinutesBefore === null
      || values.assignmentCutoffMinutesBefore === null) {
      this.seriesForm.markAllAsTouched();
      return;
    }

    const request = {
      startDate: values.startDate,
      endDate: values.endDate || null,
      startTime: values.startTime,
      durationMinutes: values.durationMinutes,
      timezone: values.timezone.trim(),
      daysOfWeek: weekdays.filter((weekday) => values.daysOfWeek[weekday]),
      reminderMinutesBefore: values.reminderMinutesBefore,
      assignmentCutoffMinutesBefore: values.assignmentCutoffMinutesBefore,
    };

    this.isSaving.set(true);
    this.seriesApi.createSeries(request)
      .pipe(finalize(() => this.isSaving.set(false)))
      .subscribe({
        next: (series) => this.createdSeries.set(series),
        error: (error: unknown) => this.formError.set(this.getErrorMessage(error)),
      });
  }

  protected showEndDateError(): boolean {
    return this.seriesForm.hasError('endDateBeforeStart')
      && (this.seriesForm.controls.endDate.touched || this.submitted());
  }

  private loadSettings(): void {
    this.isLoading.set(true);
    this.loadError.set(null);
    this.settingsApi.getSettings()
      .pipe(finalize(() => this.isLoading.set(false)))
      .subscribe({
        next: (settings) => {
          const start = nextWholeHour(settings.timezone);
          const weekday = weekdayByDateIndex[new Date(`${start.date}T00:00:00Z`).getUTCDay()];
          this.seriesForm.patchValue({
            startDate: start.date,
            startTime: start.time,
            timezone: settings.timezone,
            reminderMinutesBefore: settings.defaultReminderMinutesBefore,
            assignmentCutoffMinutesBefore: settings.defaultAssignmentCutoffMinutesBefore,
          });
          this.seriesForm.controls.daysOfWeek.controls[weekday].setValue(true);
        },
        error: () => this.loadError.set('Your planner settings could not be loaded.'),
      });
  }

  private getErrorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 0) return 'The API could not be reached. Check your connection and try again.';
      const detail = (error.error as { detail?: unknown } | null)?.detail;
      if (typeof detail === 'string') return detail;
    }
    return 'The series could not be created. Check the details and try again.';
  }
}
