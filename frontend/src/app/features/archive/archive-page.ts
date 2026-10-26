import { Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import { ContentApiService } from '../../core/api/content-api.service';
import { ArchiveItemResponse, ArchivePageResponse } from '../../core/api/content';

@Component({
  selector: 'app-archive-page',
  imports: [FormsModule, ReactiveFormsModule],
  templateUrl: './archive-page.html',
  styleUrl: './archive-page.scss',
})
export class ArchivePage implements OnInit {
  private readonly contentApi = inject(ContentApiService);
  private readonly pageSize = 20;

  protected readonly result = signal<ArchivePageResponse | null>(null);
  protected readonly isLoading = signal(false);
  protected readonly loadError = signal<string | null>(null);
  protected readonly currentPage = signal(0);
  protected readonly searchControl = new FormControl('', { nonNullable: true });

  ngOnInit(): void {
    this.loadContent();
  }

  protected get contentItems(): ArchiveItemResponse[] {
    return this.result()?.items ?? [];
  }

  protected formatDate(value: string): string {
    return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' }).format(new Date(value));
  }

  protected goToPage(page: number): void {
    this.loadContent(page);
  }

  protected applySearch(): void {
    this.loadContent(0);
  }

  protected clearSearch(): void {
    this.searchControl.reset('');
    this.loadContent(0);
  }

  protected retry(): void {
    this.loadContent(this.currentPage());
  }

  private loadContent(page = 0): void {
    this.isLoading.set(true);
    this.loadError.set(null);
    const search = this.searchControl.value.trim();
    this.contentApi.getArchive({ ...(search ? { search } : {}), page, size: this.pageSize })
      .pipe(finalize(() => this.isLoading.set(false)))
      .subscribe({
        next: (result) => {
          this.result.set(result);
          this.currentPage.set(result.page);
        },
        error: () => this.loadError.set('Your history could not be loaded. Check your connection and try again.'),
      });
  }
}