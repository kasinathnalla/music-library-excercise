import { Component, inject, output, signal } from '@angular/core';
import { TrackService } from '../../services/track.service';
import { Track } from '../../models/track.model';
import { TrackEditDialog } from '../track-edit/track-edit';
import { AuthService } from '../../../../core/services/auth.service';
import { PlaybackService } from '../../../../core/services/playback.service';
import { AddToPlaylist } from '../../../playlists/components/add-to-playlist/add-to-playlist';

@Component({
  selector: 'app-track-list',
  imports: [TrackEditDialog, AddToPlaylist],
  templateUrl: './track-list.html',
  styleUrl: './track-list.css',
})
export class TrackList {
  private readonly trackService = inject(TrackService);

  /**
   * Used only to decide what to show. The server is the actual boundary: a customer who
   * reached these endpoints another way is refused there, not here.
   */
  protected readonly auth = inject(AuthService);

  /**
   * Playback is shared rather than owned here, so that opening a playlist does not stop the
   * music and so that only one <audio> element exists (D24).
   */
  protected readonly playback = inject(PlaybackService);

  readonly back = output<void>();
  readonly libraryChanged = output<void>();
  readonly playlistsChanged = output<void>();

  protected readonly tracks = signal<Track[]>([]);
  protected readonly total = signal(0);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly query = signal('');
  protected readonly pendingDeleteId = signal<string | null>(null);
  protected readonly deletingId = signal<string | null>(null);
  protected readonly editing = signal<Track | null>(null);

  /**
   * The track whose "add to playlist" panel is open, if any. Which playlists exist and how a track
   * gets into one is AddToPlaylist's business, not this component's.
   */
  protected readonly addingTo = signal<string | null>(null);
  protected readonly addMessage = signal<string | null>(null);

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
    this.playback.playOne(track);
  }

  // --- adding to a playlist ---------------------------------------------------------

  protected toggleAddMenu(track: Track): void {
    this.addMessage.set(null);
    this.addingTo.update((open) => (open === track.id ? null : track.id));
  }

  protected closeAddMenu(): void {
    this.addingTo.set(null);
  }

  protected onAddedToPlaylist(playlistName: string): void {
    this.addingTo.set(null);
    this.addMessage.set(`Added to ${playlistName}.`);
    this.playlistsChanged.emit();
  }

  // --- uploading, editing, deleting --------------------------------------------------

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
    this.playback.updateTrack(updated);
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
        // The track is gone from the library and from every playlist that held it, so a queue
        // still pointing at it would advance into a 404.
        this.playback.forget(track.id);
        this.pendingDeleteId.set(null);
        this.deletingId.set(null);
        this.load(this.query());
        this.libraryChanged.emit();
        this.playlistsChanged.emit();
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
