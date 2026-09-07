import { Component, inject, signal } from '@angular/core';
import { TrackList } from './catalog/track-list/track-list';
import { Welcome } from './welcome/welcome';
import { Login } from './auth/login/login';
import { Register } from './auth/register/register';
import { TrackService } from './catalog/track.service';
import { AuthService } from './auth/auth.service';
import { LibraryStats } from './catalog/stats.model';

@Component({
  selector: 'app-root',
  imports: [TrackList, Welcome, Login, Register],
  styleUrl: './app.css',
  template: `
    @if (view() === 'login') {
      <app-login (signedIn)="onSignedIn()" (createAccount)="view.set('register')" />
    } @else if (view() === 'register') {
      <app-register (registered)="onSignedIn()" (backToSignIn)="view.set('login')" />
    } @else {
      @if (auth.user(); as user) {
        <div class="account-bar">
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
      } @else {
        <app-track-list (back)="showWelcome()" (libraryChanged)="loadStats()" />
      }
    }
  `,
})
export class App {
  private readonly trackService = inject(TrackService);
  protected readonly auth = inject(AuthService);

  protected readonly view = signal<'login' | 'register' | 'welcome' | 'library'>('login');
  protected readonly stats = signal<LibraryStats | null>(null);

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
    this.view.set('login');
  }

  /** First and last name when the account has them; the two seeded accounts do not. */
  protected displayName(user: { username: string; firstName?: string | null; lastName?: string | null }): string {
    return user.firstName && user.lastName ? `${user.firstName} ${user.lastName}` : user.username;
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
