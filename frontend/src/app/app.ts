import { Component, inject, signal } from '@angular/core';
import { TrackList } from './catalog/track-list/track-list';
import { Welcome } from './welcome/welcome';
import { TrackService } from './catalog/track.service';
import { LibraryStats } from './catalog/stats.model';

@Component({
  selector: 'app-root',
  imports: [TrackList, Welcome],
  template: `
    @if (view() === 'welcome') {
      <app-welcome [stats]="stats()" (enter)="open()" />
    } @else {
      <app-track-list (back)="showWelcome()" (libraryChanged)="loadStats()" />
    }
  `,
})
export class App {
  private readonly trackService = inject(TrackService);

  protected readonly view = signal<'welcome' | 'library'>('welcome');
  protected readonly stats = signal<LibraryStats | null>(null);

  constructor() {
    this.loadStats();
  }

  protected open(): void {
    this.view.set('library');
  }

  protected showWelcome(): void {
    this.loadStats();
    this.view.set('welcome');
  }

  protected loadStats(): void {
    this.trackService.stats().subscribe({
      next: (s) => this.stats.set(s),
      error: () => this.stats.set(null),
    });
  }
}
