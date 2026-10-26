import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, input, output, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';
import { finalize } from 'rxjs';
import { ContentApiService } from '../../../core/api/content-api.service';
import { ContentResponse, ContentType, CreateContentRequest } from '../../../core/api/content';

const httpUrlValidator: ValidatorFn = (control): ValidationErrors | null => {
  const value = String(control.value ?? '').trim();
  if (!value) {
    return null;
  }

  try {
    const url = new URL(value);
    return (url.protocol === 'http:' || url.protocol === 'https:') && url.hostname
      ? null
      : { httpUrl: true };
  } catch {
    return { httpUrl: true };
  }
};

@Component({
  selector: 'app-add-content-page',
  imports: [ReactiveFormsModule],
  templateUrl: './add-content-page.html',
  styleUrls: ['./add-content-page.scss'],
})
export class AddContentPage {
  private readonly contentApi = inject(ContentApiService);
  readonly presentation = input<'page' | 'banner'>('page');
  protected readonly contentAdded = output<ContentResponse>();

  protected readonly form = new FormGroup({
    url: new FormControl('', { nonNullable: true, validators: [Validators.required, httpUrlValidator] }),
    title: new FormControl('', { nonNullable: true }),
    type: new FormControl<'' | ContentType>('', { nonNullable: true }),
  });
  protected readonly isSubmitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly createdContent = signal<ContentResponse | null>(null);

  protected submit(): void {
    this.errorMessage.set(null);
    this.createdContent.set(null);

    if (this.isSubmitting()) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const url = this.form.controls.url.value.trim();
    const enteredTitle = this.form.controls.title.value.trim();
    const selectedType = this.form.controls.type.value;
    const request: CreateContentRequest = {
      url,
      title: enteredTitle || null,
      ...(selectedType ? { type: selectedType } : {}),
    };

    this.isSubmitting.set(true);
    this.contentApi.createContent(request)
      .pipe(finalize(() => this.isSubmitting.set(false)))
      .subscribe({
        next: (content) => {
          this.createdContent.set(content);
          this.form.reset();
          this.contentAdded.emit(content);
        },
        error: (error: unknown) => this.errorMessage.set(this.getErrorMessage(error)),
      });
  }

  protected clearMessages(): void {
    this.errorMessage.set(null);
    this.createdContent.set(null);
  }

  private getErrorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 0) {
        return 'The API could not be reached. Check that the backend is running and try again.';
      }

      const body = error.error as { detail?: unknown; message?: unknown } | null;
      if (error.status === 409) {
        return typeof body?.detail === 'string' ? body.detail : 'This URL is already in your backlog.';
      }
      if (typeof body?.detail === 'string') {
        return body.detail;
      }
      if (typeof body?.message === 'string') {
        return body.message;
      }
      if (error.status === 400) {
        return 'Check the URL and try again.';
      }
    }

    return 'Content could not be saved. Try again.';
  }
}