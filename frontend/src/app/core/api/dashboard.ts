export interface DashboardNextSession {
  id: string;
  startsAt: string;
  title: string;
}

export interface DashboardSummary {
  pendingContentCount: number;
  completedContentCount: number;
  todaySessionCount: number;
  unassignedUpcomingSessionCount: number;
  nextSession: DashboardNextSession | null;
}