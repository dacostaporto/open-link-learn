import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

@Component({
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  selector: 'app-root',
  styleUrl: './app.scss',
  templateUrl: './app.html',
})
export class App {
  protected readonly navigation = [
    { label: 'Home', path: '/home', icon: 'M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z' },
    { label: 'Backlog', path: '/backlog', icon: 'M6 3h12v18l-6-4-6 4z' },
    { label: 'Calendar', path: '/calendar', icon: 'M4 6h16v14H4zM4 11h16M8 3v5M16 3v5' },
    { label: 'History', path: '/history', icon: 'M3 5h18v4H3zM5 9v11h14V9M10 13h4' },
    { label: 'Settings', path: '/settings', icon: 'M4 7h9M17 7h3M4 17h3M11 17h9M15 4v6M7 14v6' },
  ];

  protected skipToContent(event: Event): void {
    event.preventDefault();
    document.getElementById('main-content')?.focus();
  }
}
