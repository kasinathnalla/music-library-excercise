import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { CurrentUser, RegisterDetails } from './auth.model';

/**
 * Signing in, and remembering who is signed in.
 *
 * The password is used once, to make the call that signs in, and is never stored. The server
 * puts the authenticated context in a session, so the cookie carries every later request --
 * including the audio the browser fetches for itself, which cannot be given an Authorization
 * header. That is why this service holds a user and not a credential.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  readonly user = signal<CurrentUser | null>(null);
  readonly isAdmin = computed(() => this.user()?.role === 'ADMIN');
  readonly isSignedIn = computed(() => this.user() !== null);

  /** Sign in. A successful response is also what establishes the session. */
  login(username: string, password: string): Observable<CurrentUser> {
    const headers = new HttpHeaders({
      Authorization: `Basic ${encodeCredentials(username, password)}`,
    });
    return this.http
      .get<CurrentUser>('/api/auth/me', { headers })
      .pipe(tap((user) => this.user.set(user)));
  }

  /**
   * Create a customer account. There is no way to ask for anything else here -- the request
   * shape has no role field -- so this can never mint an admin.
   *
   * Registering does not itself establish a session; the caller signs in right after with the
   * same credentials, reusing the one code path (login) that already does that correctly.
   */
  register(details: RegisterDetails): Observable<CurrentUser> {
    return this.http.post<CurrentUser>('/api/auth/register', details);
  }

  /** Ask whether an existing session is still valid. Run once, at startup. */
  refresh(): Observable<CurrentUser> {
    return this.http.get<CurrentUser>('/api/auth/me').pipe(tap((user) => this.user.set(user)));
  }

  logout(): Observable<void> {
    return this.http.post<void>('/api/auth/logout', {}).pipe(tap(() => this.user.set(null)));
  }

  /** Forget the session locally, without calling the server. Used when a 401 comes back. */
  clear(): void {
    this.user.set(null);
  }
}

/** btoa only handles Latin-1, so a non-ASCII password would throw rather than fail to log in. */
function encodeCredentials(username: string, password: string): string {
  const raw = `${username}:${password}`;
  const bytes = new TextEncoder().encode(raw);
  let binary = '';
  for (const byte of bytes) {
    binary += String.fromCharCode(byte);
  }
  return btoa(binary);
}
