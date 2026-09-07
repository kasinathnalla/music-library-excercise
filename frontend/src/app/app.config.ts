import { ApplicationConfig, provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { unauthorizedInterceptor } from './auth/unauthorized.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZonelessChangeDetection(),
    // XSRF protection is on by default: HttpClient reads the XSRF-TOKEN cookie the server
    // sets and echoes it as X-XSRF-TOKEN, which is what the API expects on every mutating
    // request. Do not add withNoXsrfProtection() -- uploads would start failing with 403.
    provideHttpClient(withFetch(), withInterceptors([unauthorizedInterceptor])),
  ],
};
