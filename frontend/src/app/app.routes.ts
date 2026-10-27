import { Routes } from '@angular/router';

export const routes: Routes = [
	{ path: '', pathMatch: 'full', redirectTo: 'home' },
	{
		path: 'home',
		loadComponent: () =>
			import('./features/home/home-page').then((module) => module.HomePage),
	},
	{
		path: 'backlog',
		loadComponent: () =>
			import('./features/backlog/backlog-page').then((module) => module.BacklogPage),
	},
	{
		path: 'preview-content/:sessionId',
		loadComponent: () =>
			import('./features/backlog/preview-content-page/preview-content-page').then((module) => module.PreviewContentPage),
	},
	{
		path: 'calendar/series/new',
		loadComponent: () =>
			import('./features/calendar/session-series-create-page/session-series-create-page').then((module) => module.SessionSeriesCreatePage),
	},
	{
		path: 'calendar',
		loadComponent: () =>
			import('./features/calendar/calendar-page').then((module) => module.CalendarPage),
	},
	{
		path: 'history',
		loadComponent: () =>
			import('./features/archive/archive-page').then((module) => module.ArchivePage),
	},
	{
		path: 'settings',
		loadComponent: () =>
			import('./features/settings/settings-page').then((module) => module.SettingsPage),
	},
	{ path: '**', redirectTo: 'home' },
];
