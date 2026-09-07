export type Role = 'ADMIN' | 'CUSTOMER';

/** What `GET /api/auth/me` returns. Mirrors the CurrentUser record on the server. */
export interface CurrentUser {
  username: string;
  role: Role;
}
