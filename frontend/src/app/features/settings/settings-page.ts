import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';
import { finalize } from 'rxjs';
import { Settings } from '../../core/api/settings';
import { SettingsApiService } from '../../core/api/settings-api.service';
import { PushSettingsSection } from './push-settings-section/push-settings-section';

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

@Component({
  selector: 'app-settings-page',
  imports: [ReactiveFormsModule, PushSettingsSection],
  templateUrl: './settings-page.html',
  styleUrl: './settings-page.scss',
})
export class SettingsPage implements OnInit {
  private readonly settingsApi = inject(SettingsApiService);

  protected readonly settingsForm = new FormGroup({
    timezone: new FormControl('', { nonNullable: true, validators: [Validators.required, timezoneValidator] }),
    defaultReminderMinutesBefore: new FormControl<number | null>(null, {
      validators: [Validators.required, integerRangeValidator(0, 1440)],
    }),
    defaultAssignmentCutoffMinutesBefore: new FormControl<number | null>(null, {
      validators: [Validators.required, integerRangeValidator(0, 10080)],
    }),
    recurrenceHorizonDays: new FormControl<number | null>(null, {
      validators: [Validators.required, integerRangeValidator(7, 365)],
    }),
  });

  protected readonly isLoading = signal(true);
  protected readonly isSaving = signal(false);
  protected readonly loadError = signal<string | null>(null);
  protected readonly saveError = signal<string | null>(null);
  protected readonly saveSuccess = signal(false);

  ngOnInit(): void {
    this.loadSettings();
  }

  protected retryLoad(): void {
    this.loadSettings();
  }

  protected saveSettings(): void {
    this.saveError.set(null);
    this.saveSuccess.set(false);
    if (this.settingsForm.invalid) {
      this.settingsForm.markAllAsTouched();
      return;
    }

    const values = this.settingsForm.getRawValue();
    if (values.defaultReminderMinutesBefore === null
      || values.defaultAssignmentCutoffMinutesBefore === null
      || values.recurrenceHorizonDays === null) {
      this.settingsForm.markAllAsTouched();
      return;
    }

    const request: Settings = {
      ...values,
      timezone: values.timezone.trim(),
      defaultReminderMinutesBefore: values.defaultReminderMinutesBefore,
      defaultAssignmentCutoffMinutesBefore: values.defaultAssignmentCutoffMinutesBefore,
      recurrenceHorizonDays: values.recurrenceHorizonDays,
    };

    this.isSaving.set(true);
    this.settingsApi.updateSettings(request)
      .pipe(finalize(() => this.isSaving.set(false)))
      .subscribe({
        next: (settings) => {
          this.populateForm(settings);
          this.saveSuccess.set(true);
        },
        error: (error: unknown) => this.saveError.set(this.getErrorMessage(error, 'save')),
      });
  }

  private loadSettings(): void {
    this.isLoading.set(true);
    this.loadError.set(null);
    this.settingsApi.getSettings()
      .pipe(finalize(() => this.isLoading.set(false)))
      .subscribe({
        next: (settings) => this.populateForm(settings),
        error: (error: unknown) => this.loadError.set(this.getErrorMessage(error, 'load')),
      });
  }

  private populateForm(settings: Settings): void {
    this.settingsForm.setValue(settings);
    this.settingsForm.markAsPristine();
    this.settingsForm.markAsUntouched();
  }

  private getErrorMessage(error: unknown, operation: 'load' | 'save'): string {
    const fallback = operation === 'load'
      ? 'Settings could not be loaded. Check your connection and try again.'
      : 'Settings could not be saved. Your changes are still here; try again.';

    if (error instanceof HttpErrorResponse) {
      if (error.status === 0) return 'The API could not be reached. Check your connection and try again.';
      const detail = (error.error as { detail?: unknown } | null)?.detail;
      if (typeof detail === 'string') return detail;
    }
    return fallback;
  }
}