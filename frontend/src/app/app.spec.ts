import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';
import { routes } from './app.routes';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter(routes)],
    }).compileComponents();
  });

  it('renders navigation for the planner routes', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();

    const links = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('.nav-link'),
    ).map((link) => link.textContent?.trim().replace(/^\d+/, ''));

    expect(links).toEqual([
      'Home',
      'Backlog',
      'Calendar',
      'History',
      'Settings',
    ]);
  });
});
