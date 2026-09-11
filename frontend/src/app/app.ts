import { Component, inject, signal } from '@angular/core';
import { TrackList } from './features/library/components/track-list/track-list';
import { Welcome } from './features/welcome/components/welcome/welcome';
import { Login } from './features/auth/components/login/login';
import { Register } from './features/auth/components/register/register';
import { PlaylistList } from './features/playlists/components/playlist-list/playlist-list';
import { PlaylistDetail } from './features/playlists/components/playlist-detail/playlist-detail';
import { Player } from './shared/components/player/player';
import { TrackService } from './features/library/services/track.service';
import { AuthService } from './core/services/auth.service';
import { LibraryStats } from './features/library/models/stats.model';

@Component({
  selector: 'app-root',
  imports: [TrackList, Welcome, Login, Register, PlaylistList, PlaylistDetail, Player],
  styleUrl: './app.css',
  template: `
    @if (view() === 'login') {
      <app-login (signedIn)="onSignedIn()" (createAccount)="view.set('register')" />
    } @else if (view() === 'register') {
      <app-register (registered)="onSignedIn()" (backToSignIn)="view.set('login')" />
    } @else {
      @if (auth.user(); as user) {
        <div class="account-bar">
          <nav class="sections">
            <button
              type="button"
              class="section"
              [class.current]="view() === 'library'"
              (click)="open()"
            >Library</button>
            <button
              type="button"
              class="section"
              [class.current]="view() === 'playlists' || view() === 'playlist'"
              (click)="showPlaylists()"
            >Playlists</button>
          </nav>
          <span class="who">
            {{ displayName(user) }}
            <span class="role" [class.admin]="user.role === 'ADMIN'">
              {{ user.role === 'ADMIN' ? 'Admin' : 'Listener' }}
            </span>
          </span>
          <button type="button" class="sign-out" (click)="signOut()">Sign out</button>
        </div>
      }

      @if (view() === 'welcome') {
        <app-welcome [stats]="stats()" (enter)="open()" />
      } @else if (view() === 'playlists') {
        <app-playlist-list (open)="openPlaylist($event)" (back)="showWelcome()" />
      } @else if (view() === 'playlist') {
        @if (openPlaylistId(); as playlistId) {
          <app-playlist-detail [playlistId]="playlistId" (back)="showPlaylists()" />
        }
      } @else {
        <app-track-list (back)="showWelcome()" (libraryChanged)="loadStats()" />
      }

      <!--
        Rendered here, once, rather than inside a view: a player that stopped when you opened a
        different screen would be worse than no player at all (D24).
      -->
      <app-player />
    }
  `,
})
export class App {
  private readonly trackService = inject(TrackService);
  protected readonly auth = inject(AuthService);

  protected readonly view =
    signal<'login' | 'register' | 'welcome' | 'library' | 'playlists' | 'playlist'>('login');
  protected readonly stats = signal<LibraryStats | null>(null);
  protected readonly openPlaylistId = signal<string | null>(null);

  constructor() {
    // Ask whether a session is already valid before doing anything else. Loading stats first
    // would 401 on every cold load and fill the console with errors before the user has done
    // anything wrong.
    this.auth.refresh().subscribe({
      next: () => this.onSignedIn(),
      error: () => this.view.set('login'),
    });
  }

  protected onSignedIn(): void {
    this.view.set('welcome');
    this.loadStats();
  }

  protected signOut(): void {
    this.auth.logout().subscribe({
      next: () => this.toLogin(),
      // The session is gone locally either way; there is nothing useful to do with a failure.
      error: () => this.toLogin(),
    });
  }

  private toLogin(): void {
    this.stats.set(null);
    this.openPlaylistId.set(null);
    this.view.set('login');
  }

  /** First and last name when the account has them; the two seeded accounts do not. */
  protected displayName(user: { username: string; firstName?: string | null; lastName?: string | null }): string {
    return user.firstName && user.lastName ? `${user.firstName} ${user.lastName}` : user.username;
  }

  protected open(): void {
    this.view.set('library');
  }

  protected showPlaylists(): void {
    this.openPlaylistId.set(null);
    this.view.set('playlists');
  }

  protected openPlaylist(playlistId: string): void {
    this.openPlaylistId.set(playlistId);
    this.view.set('playlist');
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
