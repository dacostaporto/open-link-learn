import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';
import { finalize } from 'rxjs';
import { ContentApiService } from '../../core/api/content-api.service';
import { ContentPageResponse, ContentResponse, ContentType } from '../../core/api/content';
import { AddContentPage } from './add-content-page/add-content-page';

const positiveIntegerValidator: ValidatorFn = (control: AbstractControl<number | null>): ValidationErrors | null => {
  const value = control.value;
  return value === null || (Number.isInteger(value) && value > 0) ? null : { positiveInteger: true };
};

@Component({
  selector: 'app-backlog-page',
  imports: [AddContentPage, ReactiveFormsModule],
  templateUrl: './backlog-page.html',
  styleUrls: ['./backlog-page.scss'],
})
export class BacklogPage implements OnInit {
  private readonly contentApi = inject(ContentApiService);
  private readonly pageSize = 20;

  protected readonly filterForm = new FormGroup({
    search: new FormControl('', { nonNullable: true }),
    type: new FormControl<'' | ContentType>('', { nonNullable: true }),
  });
  protected readonly result = signal<ContentPageResponse | null>(null);
  protected readonly isLoading = signal(false);
  protected readonly loadError = signal<string | null>(null);
  protected readonly currentPage = signal(0);
  protected readonly editingContentId = signal<string | null>(null);
  protected readonly titleControl = new FormControl('', { nonNullable: true, validators: [Validators.required] });
  protected readonly durationControl = new FormControl<number | null>(null, { validators: [positiveIntegerValidator] });
  protected readonly savingEdit = signal(false);
  protected readonly editSaveError = signal<string | null>(null);
  protected readonly completingContentId = signal<string | null>(null);
  protected readonly completionError = signal<string | null>(null);

  ngOnInit(): void {
    this.loadContent();
  }

  protected applyFilters(): void {
    this.loadContent(0);
  }

  protected clearFilters(): void {
    this.filterForm.reset({ search: '', type: '' });
    this.loadContent(0);
  }

  protected handleContentAdded(_content: ContentResponse): void {
    this.clearFilters();
  }

  protected goToPage(page: number): void {
    this.loadContent(page);
  }

  protected startEditing(item: ContentResponse): void {
    this.titleControl.setValue(item.title);
    this.titleControl.markAsPristine();
    this.durationControl.setValue(item.estimatedDurationMinutes);
    this.durationControl.markAsPristine();
    this.editingContentId.set(item.id);
    this.editSaveError.set(null);
  }

  protected cancelEditing(): void {
    this.editingContentId.set(null);
    this.editSaveError.set(null);
  }

  protected saveEdits(item: ContentResponse): void {
    const title = this.titleControl.value.trim();
    if (this.titleControl.invalid || !title || this.durationControl.invalid) {
      this.titleControl.markAsTouched();
      this.durationControl.markAsTouched();
      return;
    }
    const estimatedDurationMinutes = this.durationControl.value;
    if (title === item.title && estimatedDurationMinutes === item.estimatedDurationMinutes) {
      this.cancelEditing();
      return;
    }

    this.savingEdit.set(true);
    this.editSaveError.set(null);
    this.contentApi.updateContent(item.id, { title, estimatedDurationMinutes })
      .pipe(finalize(() => this.savingEdit.set(false)))
      .subscribe({
        next: (updated) => {
          this.result.update((page) => page
            ? { ...page, items: page.items.map((entry) => entry.id === updated.id ? updated : entry) }
            : page);
          this.cancelEditing();
        },
        error: (error: unknown) => this.editSaveError.set(this.getEditErrorMessage(error)),
      });
  }

  protected completeItem(item: ContentResponse): void {
    this.completionError.set(null);
    this.completingContentId.set(item.id);
    this.contentApi.completeContent(item.id)
      .pipe(finalize(() => this.completingContentId.set(null)))
      .subscribe({
        next: () => {
          const page = this.result();
          if (page && page.items.length === 1 && page.page > 0) {
            this.loadContent(page.page - 1);
            return;
          }
          this.result.update((current) => {
            if (!current) return current;
            const totalItems = Math.max(0, current.totalItems - 1);
            return {
              ...current,
              items: current.items.filter((entry) => entry.id !== item.id),
              totalItems,
              totalPages: Math.ceil(totalItems / current.size),
            };
          });
        },
        error: () => this.completionError.set('This item could not be completed. Try again.'),
      });
  }

  protected retry(): void {
    this.loadContent(this.currentPage());
  }

  protected formatDate(value: string): string {
    return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' }).format(new Date(value));
  }

  protected formatDuration(value: number | null): string {
    if (value === null || value < 0) {
      return '';
    }
    const minutes = Math.floor(value / 60);
    const seconds = Math.floor(value % 60).toString().padStart(2, '0');
    return `${minutes}:${seconds}`;
  }

  protected get contentItems(): ContentResponse[] {
    return this.result()?.items ?? [];
  }

  private loadContent(page: number = 0): void {
    this.isLoading.set(true);
    this.loadError.set(null);
    const search = this.filterForm.controls.search.value.trim();
    const type = this.filterForm.controls.type.value;

    this.contentApi.getContent({
      status: 'PENDING',
      ...(type ? { type } : {}),
      ...(search ? { search } : {}),
      page,
      size: this.pageSize,
    }).pipe(finalize(() => this.isLoading.set(false)))
      .subscribe({
        next: (result) => {
          this.result.set(result);
          this.currentPage.set(result.page);
        },
        error: () => {
          this.loadError.set('Your backlog could not be loaded. Check your connection and try again.');
        },
      });
  }

  private getEditErrorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 0) {
        return 'The API could not be reached. Check your connection and try again.';
      }
      const body = error.error as { detail?: unknown } | null;
      if (typeof body?.detail === 'string') {
        return body.detail;
      }
    }
    return 'Content details could not be saved. Try again.';
  }
}