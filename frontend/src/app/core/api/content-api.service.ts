import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ArchivePageResponse, ArchiveQuery, ContentPageResponse, ContentListQuery, ContentResponse, CreateContentRequest, UpdateContentRequest } from './content';

@Injectable({ providedIn: 'root' })
export class ContentApiService {
  private readonly http = inject(HttpClient);

  createContent(request: CreateContentRequest): Observable<ContentResponse> {
    return this.http.post<ContentResponse>('/api/v1/content', request);
  }

  getContent(query: ContentListQuery): Observable<ContentPageResponse> {
    let params = new HttpParams();
    if (query.status) params = params.set('status', query.status);
    if (query.type) params = params.set('type', query.type);
    if (query.search) params = params.set('search', query.search);
    if (query.page !== undefined) params = params.set('page', query.page);
    if (query.size !== undefined) params = params.set('size', query.size);
    return this.http.get<ContentPageResponse>('/api/v1/content', { params });
  }

  getContentById(id: string): Observable<ContentResponse> {
    return this.http.get<ContentResponse>(`/api/v1/content/${id}`);
  }

  getArchive(query: ArchiveQuery): Observable<ArchivePageResponse> {
    let params = new HttpParams();
    if (query.search) params = params.set('search', query.search);
    if (query.page !== undefined) params = params.set('page', query.page);
    if (query.size !== undefined) params = params.set('size', query.size);
    return this.http.get<ArchivePageResponse>('/api/v1/history', { params });
  }

  updateContent(id: string, request: UpdateContentRequest): Observable<ContentResponse> {
    return this.http.patch<ContentResponse>(`/api/v1/content/${id}`, request);
  }

  completeContent(id: string): Observable<ContentResponse> {
    return this.http.post<ContentResponse>(`/api/v1/content/${id}/complete`, {});
  }
  
}