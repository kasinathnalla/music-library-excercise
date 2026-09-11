import { Component, inject, output, signal } from '@angular/core';
import { switchMap } from 'rxjs';
import { AuthService } from '../../../../core/services/auth.service';
import { CurrentUser } from '../../../../core/models/auth.model';

/**
 * Self-registration. Always produces a customer -- there is nothing on this screen, and
 * nothing in the request it sends, that could ask for anything else. An admin account is
 * provisioned directly against the database, not through here.
 */
@Component({
  selector: 'app-register',
  imports: [],
  templateUrl: './register.html',
  styleUrl: './register.css',
})
export class Register {
  private readonly auth = inject(AuthService);

  readonly registered = output<CurrentUser>();
  readonly backToSignIn = output<void>();

  protected readonly firstName = signal('');
  protected readonly lastName = signal('');
  protected readonly dateOfBirth = signal('');
  protected readonly address = signal('');
  protected readonly username = signal('');
  protected readonly password = signal('');
  protected readonly confirm = signal('');
  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);

  protected onFirstName(event: Event): void {
    this.firstName.set((event.target as HTMLInputElement).value);
  }

  protected onLastName(event: Event): void {
    this.lastName.set((event.target as HTMLInputElement).value);
  }

  protected onDateOfBirth(event: Event): void {
    this.dateOfBirth.set((event.target as HTMLInputElement).value);
  }

  protected onAddress(event: Event): void {
    this.address.set((event.target as HTMLInputElement).value);
  }

  protected onUsername(event: Event): void {
    this.username.set((event.target as HTMLInputElement).value);
  }

  protected onPassword(event: Event): void {
    this.password.set((event.target as HTMLInputElement).value);
  }

  protected onConfirm(event: Event): void {
    this.confirm.set((event.target as HTMLInputElement).value);
  }

  protected submit(event: Event): void {
    event.preventDefault();
    if (this.submitting()) {
      return;
    }
    this.error.set(null);

    if (this.password() !== this.confirm()) {
      this.error.set('Those passwords do not match.');
      return;
    }

    const username = this.username().trim();
    const password = this.password();

    this.submitting.set(true);
    this.auth
      .register({
        username,
        password,
        firstName: this.firstName().trim(),
        lastName: this.lastName().trim(),
        dateOfBirth: this.dateOfBirth(),
        address: this.address().trim(),
      })
      // Registering does not sign you in on its own; immediately do what the login screen
      // does, with the password still in hand from the form.
      .pipe(switchMap(() => this.auth.login(username, password)))
      .subscribe({
        next: (user) => {
          this.submitting.set(false);
          this.password.set('');
          this.confirm.set('');
          this.registered.emit(user);
        },
        error: (e: { status?: number; error?: { message?: string; errors?: string[] } }) => {
          this.submitting.set(false);
          this.error.set(this.registrationErrorMessage(e));
        },
      });
  }

  private registrationErrorMessage(e: {
    status?: number;
    error?: { message?: string; errors?: string[] };
  }): string {
    if (e?.status === 409) {
      return 'That username is already taken.';
    }
    if (e?.status === 400) {
      return e.error?.message ?? 'Check that every field is filled in and the date of birth is in the past.';
    }
    return 'Could not create an account. Is the server running?';
  }
}
