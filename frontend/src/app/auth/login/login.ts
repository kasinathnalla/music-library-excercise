import { Component, inject, output, signal } from '@angular/core';
import { AuthService } from '../auth.service';
import { CurrentUser } from '../auth.model';

@Component({
  selector: 'app-login',
  imports: [],
  templateUrl: './login.html',
  styleUrl: './login.css',
})
export class Login {
  private readonly auth = inject(AuthService);

  readonly signedIn = output<CurrentUser>();

  protected readonly username = signal('');
  protected readonly password = signal('');
  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);

  protected onUsername(event: Event): void {
    this.username.set((event.target as HTMLInputElement).value);
  }

  protected onPassword(event: Event): void {
    this.password.set((event.target as HTMLInputElement).value);
  }

  protected submit(event: Event): void {
    event.preventDefault();
    if (this.submitting()) {
      return;
    }
    this.error.set(null);
    this.submitting.set(true);
    this.auth.login(this.username().trim(), this.password()).subscribe({
      next: (user) => {
        this.submitting.set(false);
        this.password.set('');
        this.signedIn.emit(user);
      },
      error: (e: { status?: number }) => {
        this.submitting.set(false);
        this.error.set(
          e?.status === 401
            ? 'That username and password do not match an account.'
            : 'Could not sign in. Is the server running?',
        );
      },
    });
  }
}
