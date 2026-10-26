export type Weekday =
  | 'MONDAY'
  | 'TUESDAY'
  | 'WEDNESDAY'
  | 'THURSDAY'
  | 'FRIDAY'
  | 'SATURDAY'
  | 'SUNDAY';

export interface CreateSessionSeriesRequest {
  startDate: string;
  endDate: string | null;
  startTime: string;
  durationMinutes: number;
  timezone: string;
  daysOfWeek: Weekday[];
  reminderMinutesBefore: number;
  assignmentCutoffMinutesBefore: number;
}

export interface UpdateSessionSeriesRequest extends CreateSessionSeriesRequest {
  applyTo: 'ENTIRE_SERIES';
}

export interface SessionSeriesResponse extends CreateSessionSeriesRequest {
  id: string;
  active: boolean;
  generatedThrough: string;
}
