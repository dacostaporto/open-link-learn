import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, input, output, signal } from '@angular/core';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';
import { finalize } from 'rxjs';
import { SessionSeriesApiService } from '../../../core/api/session-series-api.service';
import { SessionSeriesResponse, Weekday } from '../../../core/api/session-series';

const weekdays: Weekday[] = [
  'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY',
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
  selector: 'app-session-series-edit-panel',
  imports: [ReactiveFormsModule],
  standalone: true,
  templateUrl: './session-series-edit-panel.html',
  styleUrl: './session-series-edit-panel.scss',
})
export class SessionSeriesEditPanel implements OnInit {
  private readonly seriesApi = inject(SessionSeriesApiService);

  readonly seriesId = input.required<string>();
  readonly saved = output<void>();
  readonly dismissed = output<void>();

  protected readonly weekdays = weekdays;
  protected readonly series = signal<SessionSeriesResponse | null>(null);
  protected readonly isLoading = signal(true);
  protected readonly isSaving = signal(false);
  protected readonly submitted = signal(false);
  protected readonly loadError = signal<string | null>(null);
  protected readonly saveError = signal<string | null>(null);

  protected readonly seriesForm = new FormGroup({
    startDate: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    endDate: new FormControl('', { nonNullable: true }),
    startTime: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    durationMinutes: new FormControl<number | null>(null, {
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
    reminderMinutesBefore: new FormControl<number | null>(null, {
      validators: [Validators.required, integerRangeValidator(0, 1440)],
    }),
    assignmentCutoffMinutesBefore: new FormControl<number | null>(null, {
      validators: [Validators.required, integerRangeValidator(0, 10080)],
    }),
  }, { validators: [dateRangeValidator] });

  ngOnInit(): void {
    this.loadSeries();
  }

  protected retryLoad(): void {
    this.loadSeries();
  }

  protected saveSeries(): void {
    if (this.isSaving()) return;
    this.submitted.set(true);
    this.saveError.set(null);
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

    this.isSaving.set(true);
    this.seriesApi.updateSeries(this.seriesId(), {
      applyTo: 'ENTIRE_SERIES',
      startDate: values.startDate,
      endDate: values.endDate || null,
      startTime: values.startTime,
      durationMinutes: values.durationMinutes,
      timezone: values.timezone.trim(),
      daysOfWeek: weekdays.filter((weekday) => values.daysOfWeek[weekday]),
      reminderMinutesBefore: values.reminderMinutesBefore,
      assignmentCutoffMinutesBefore: values.assignmentCutoffMinutesBefore,
    }).pipe(finalize(() => this.isSaving.set(false))).subscribe({
      next: (series) => {
        this.series.set(series);
        this.saved.emit();
      },
      error: (error: unknown) => this.saveError.set(this.getErrorMessage(error)),
    });
  }

  protected showEndDateError(): boolean {
    return this.seriesForm.hasError('endDateBeforeStart')
      && (this.seriesForm.controls.endDate.touched || this.submitted());
  }

  private loadSeries(): void {
    this.isLoading.set(true);
    this.loadError.set(null);
    this.seriesApi.getSeries(this.seriesId())
      .pipe(finalize(() => this.isLoading.set(false)))
      .subscribe({
        next: (series) => {
          this.series.set(series);
          this.seriesForm.patchValue({
            startDate: series.startDate,
            endDate: series.endDate ?? '',
            startTime: series.startTime,
            durationMinutes: series.durationMinutes,
            timezone: series.timezone,
            reminderMinutesBefore: series.reminderMinutesBefore,
            assignmentCutoffMinutesBefore: series.assignmentCutoffMinutesBefore,
          });
          for (const weekday of weekdays) {
            this.seriesForm.controls.daysOfWeek.controls[weekday].setValue(series.daysOfWeek.includes(weekday));
          }
        },
        error: (error: unknown) => this.loadError.set(this.getErrorMessage(error, 'load')),
      });
  }

  private getErrorMessage(error: unknown, operation: 'load' | 'save' = 'save'): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 0) return 'The API could not be reached. Check your connection and try again.';
      const detail = (error.error as { detail?: unknown } | null)?.detail;
      if (typeof detail === 'string') return detail;
    }
    return operation === 'load'
      ? 'The series schedule could not be loaded. Check your connection and try again.'
      : 'The series could not be updated. Your changes are still here; try again.';
  }
}
