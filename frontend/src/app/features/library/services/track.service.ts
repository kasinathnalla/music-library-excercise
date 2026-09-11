import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ListOptions, Track, TrackEdit, TrackPage } from '../models/track.model';
import { LibraryStats } from '../models/stats.model';

@Injectable({ providedIn: 'root' })
export class TrackService {
  private readonly http = inject(HttpClient);

  list(options: ListOptions = {}): Observable<TrackPage> {
    let params = new HttpParams().set('page', String(options.page ?? 0));
    if (options.size !== undefined) {
      params = params.set('size', String(options.size));
    }
    if (options.query) {
      params = params.set('query', options.query);
    }
    return this.http.get<TrackPage>('/api/tracks', { params });
  }

  upload(file: File): Observable<unknown> {
    const body = new FormData();
    body.append('file', file);
    return this.http.post('/api/tracks', body);
  }

  stats(): Observable<LibraryStats> {
    return this.http.get<LibraryStats>('/api/stats');
  }

  update(trackId: string, edit: TrackEdit): Observable<Track> {
    return this.http.patch<Track>(`/api/tracks/${trackId}`, edit);
  }

  delete(trackId: string): Observable<void> {
    return this.http.delete<void>(`/api/tracks/${trackId}`);
  }

  streamUrl(trackId: string): string {
    return `/api/tracks/${trackId}/stream`;
  }
}
