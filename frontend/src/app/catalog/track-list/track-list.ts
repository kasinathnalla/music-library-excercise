import { Component, inject, output, signal } from '@angular/core';
import { TrackService } from '../track.service';
import { Track } from '../track.model';
import { TrackEditDialog } from '../track-edit/track-edit';

@Component({
  selector: 'app-track-list',
  imports: [TrackEditDialog],
  templateUrl: './track-list.html',
  styleUrl: './track-list.css',
})
export class TrackList {
  private readonly trackService = inject(TrackService);

  readonly back = output<void>();
  readonly libraryChanged = output<void>();

  protected readonly tracks = signal<Track[]>([]);
  protected readonly total = signal(0);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly query = signal('');
  protected readonly nowPlayingId = signal<string | null>(null);
  protected readonly nowPlayingUrl = signal<string | null>(null);
  protected readonly nowPlayingTitle = signal('');
  protected readonly pendingDeleteId = signal<string | null>(null);
  protected readonly deletingId = signal<string | null>(null);
  protected readonly editing = signal<Track | null>(null);

  private searchTimer: ReturnType<typeof setTimeout> | undefined;

  constructor() {
    this.load('');
  }

  protected onSearch(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.query.set(value);
    // Debounced so typing does not issue a request per keystroke.
    clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => this.load(value), 250);
  }

  protected play(track: Track): void {
    this.nowPlayingId.set(track.id);
    this.nowPlayingTitle.set(track.title);
    this.nowPlayingUrl.set(this.trackService.streamUrl(track.id));
  }

  protected onUpload(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) {
      return;
    }
    this.error.set(null);
    this.trackService.upload(file).subscribe({
      next: () => {
        input.value = '';
        this.load(this.query());
        this.libraryChanged.emit();
      },
      error: (e) => {
        input.value = '';
        this.error.set(this.uploadErrorMessage(e));
      },
    });
  }

  /** The API returns a typed body for handled failures; fall back only when it does not. */
  private uploadErrorMessage(e: unknown): string {
    const err = e as { status?: number; error?: { message?: string } };
    if (err?.error?.message) {
      return err.error.message;
    }
    if (err?.status === 413) {
      return 'That file is larger than the upload limit.';
    }
    return 'Upload failed.';
  }

  protected edit(track: Track): void {
    this.editing.set(track);
  }

  protected onEditSaved(updated: Track): void {
    this.editing.set(null);
    // The edit may have changed the title the player is showing.
    if (this.nowPlayingId() === updated.id) {
      this.nowPlayingTitle.set(updated.title);
    }
    this.load(this.query());
    this.libraryChanged.emit();
  }

  protected onEditCancelled(): void {
    this.editing.set(null);
  }

  protected askDelete(track: Track): void {
    this.pendingDeleteId.set(track.id);
  }

  protected cancelDelete(): void {
    this.pendingDeleteId.set(null);
  }

  protected confirmDelete(track: Track): void {
    this.deletingId.set(track.id);
    this.error.set(null);
    this.trackService.delete(track.id).subscribe({
      next: () => {
        // Stop playback if the track that was removed is the one playing.
        if (this.nowPlayingId() === track.id) {
          this.nowPlayingId.set(null);
          this.nowPlayingUrl.set(null);
          this.nowPlayingTitle.set('');
        }
        this.pendingDeleteId.set(null);
        this.deletingId.set(null);
        this.load(this.query());
        this.libraryChanged.emit();
      },
      error: () => {
        this.deletingId.set(null);
        this.pendingDeleteId.set(null);
        this.error.set(`Could not delete “${track.title}”.`);
      },
    });
  }

  protected formatDuration(ms: number | undefined): string {
    if (!ms || ms < 0) {
      return '';
    }
    const totalSeconds = Math.round(ms / 1000);
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;
    return `${minutes}:${String(seconds).padStart(2, '0')}`;
  }

  private load(query: string): void {
    this.loading.set(true);
    this.error.set(null);
    this.trackService.list({ query }).subscribe({
      next: (page) => {
        this.tracks.set(page.items);
        this.total.set(page.totalElements);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Could not load the library.');
        this.loading.set(false);
      },
    });
  }
}
