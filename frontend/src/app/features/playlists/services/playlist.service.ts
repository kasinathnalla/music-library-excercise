import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Playlist, PlaylistSummary } from '../models/playlist.model';

/**
 * The only file that knows playlist API URLs, mirroring the note in AGENTS.md about
 * {@code track.service.ts}.
 *
 * <p>Nothing here passes a user id: the server takes the caller from the session and scopes every
 * query by it. A client that could name an owner would be a client that could ask for someone
 * else's playlists.
 */
@Injectable({ providedIn: 'root' })
export class PlaylistService {
  private readonly http = inject(HttpClient);

  list(): Observable<PlaylistSummary[]> {
    return this.http.get<PlaylistSummary[]>('/api/playlists');
  }

  get(playlistId: string): Observable<Playlist> {
    return this.http.get<Playlist>(`/api/playlists/${playlistId}`);
  }

  create(name: string): Observable<Playlist> {
    return this.http.post<Playlist>('/api/playlists', { name });
  }

  rename(playlistId: string, name: string): Observable<Playlist> {
    return this.http.patch<Playlist>(`/api/playlists/${playlistId}`, { name });
  }

  delete(playlistId: string): Observable<void> {
    return this.http.delete<void>(`/api/playlists/${playlistId}`);
  }

  addTrack(playlistId: string, trackId: string): Observable<Playlist> {
    return this.http.post<Playlist>(`/api/playlists/${playlistId}/items`, { trackId });
  }

  removeItem(playlistId: string, itemId: string): Observable<Playlist> {
    return this.http.delete<Playlist>(`/api/playlists/${playlistId}/items/${itemId}`);
  }

  /**
   * Sends the complete new order, not a move (D23). The server refuses a list that is not a
   * permutation of the playlist's current items, so this cannot quietly drop an entry.
   */
  reorder(playlistId: string, itemIds: string[]): Observable<Playlist> {
    return this.http.put<Playlist>(`/api/playlists/${playlistId}/items`, { itemIds });
  }
}
