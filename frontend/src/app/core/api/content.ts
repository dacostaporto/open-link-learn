export type ContentType = 'YOUTUBE' | 'ARTICLE';
export type ContentStatus = 'PENDING' | 'COMPLETED';

export interface CreateContentRequest {
  url: string;
  title: string | null;
  type?: ContentType;
}

export interface UpdateContentRequest {
  title?: string;
  estimatedDurationMinutes?: number | null;
}

export interface ContentResponse {
  id: string;
  url: string;
  normalizedUrl: string;
  title: string;
  type: ContentType;
  thumbnailUrl: string | null;
  sourceName: string | null;
  durationSeconds: number | null;
  estimatedDurationMinutes: number | null;
  status: ContentStatus;
  assignedSessionId: string | null;
  createdAt: string;
  completedAt: string | null;
}

export interface ContentPageResponse {
  items: ContentResponse[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface ArchiveItemResponse {
  id: string;
  url: string;
  title: string;
  type: ContentType;
  status: 'COMPLETED';
  completedAt: string;
}

export interface ArchivePageResponse {
  items: ArchiveItemResponse[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface ArchiveQuery {
  search?: string;
  page?: number;
  size?: number;
}

export interface ContentListQuery {
  status?: ContentStatus;
  type?: ContentType;
  search?: string;
  page?: number;
  size?: number;
}