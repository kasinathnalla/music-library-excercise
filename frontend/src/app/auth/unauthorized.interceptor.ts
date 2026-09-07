import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

/**
 * Keeps the app's idea of the session honest.
 *
 * A 401 means the session is gone -- expired, or ended in another tab -- so the user is
 * cleared and the app falls back to the login view. A 403 is different and deliberately left
 * alone: the user is legitimately signed in and was refused a particular action, so clearing
 * the session would bounce them to a login screen they are already past.
 *
 * `/api/auth/me` is exempt because a 401 there is the answer to a question, not the loss of a
 * session: it is how both signing in and the startup check report "no".
 */
export const unauthorizedInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401 && !req.url.includes('/api/auth/me')) {
        auth.clear();
      }
      return throwError(() => error);
    }),
  );
};
