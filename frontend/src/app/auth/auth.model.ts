export type Role = 'ADMIN' | 'CUSTOMER';

/** What `GET /api/auth/me` returns. Mirrors the CurrentUser record on the server. */
export interface CurrentUser {
  username: string;
  role: Role;
  /** Absent for the two seeded accounts, which predate profile fields. */
  firstName?: string | null;
  lastName?: string | null;
}

export interface RegisterDetails {
  username: string;
  password: string;
  firstName: string;
  lastName: string;
  /** ISO 8601, e.g. "1994-03-02" -- what an <input type="date"> produces. */
  dateOfBirth: string;
  address: string;
}
