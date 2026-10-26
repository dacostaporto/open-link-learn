import { ContentResponse } from './content';

export type SessionStatus = 'PLANNED' | 'IN_PROGRESS' | 'COMPLETED' | 'SKIPPED' | 'CANCELLED';

export interface CreateSessionRequest {
  startsAt: string;
  durationMinutes: number;
  timezone: string;
  contentItemId: string | null;
  reminderMinutesBefore: number;
  assignmentCutoffMinutesBefore: number;
}

export interface UpdateSessionRequest {
  startsAt?: string;
  durationMinutes?: number;
  contentItemId?: string | null;
}

export interface SessionResponse {
  id: string;
  seriesId: string | null;
  startsAt: string;
  endsAt: string;
  timezone: string;
  content: Pick<ContentResponse, 'id' | 'title' | 'url' | 'type'> | null;
  status: SessionStatus;
  reminderMinutesBefore: number;
  assignmentCutoffMinutesBefore: number;
  completedAt: string | null;
}

export interface SessionListResponse {
  items: SessionResponse[];
}