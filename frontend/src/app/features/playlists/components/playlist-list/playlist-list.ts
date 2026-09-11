import { Component, inject, output, signal } from '@angular/core';
import { PlaylistService } from '../../services/playlist.service';
import { PlaylistSummary } from '../../models/playlist.model';

@Component({
  selector: 'app-playlist-list',
  templateUrl: './playlist-list.html',
  styleUrl: './playlist-list.css',
})
export class PlaylistList {
  private readonly playlistService = inject(PlaylistService);

  readonly open = output<string>();
  readonly back = output<void>();

  protected readonly playlists = signal<PlaylistSummary[]>([]);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly newName = signal('');
  protected readonly creating = signal(false);
  protected readonly pendingDeleteId = signal<string | null>(null);

  constructor() {
    this.load();
  }

  protected onNameInput(event: Event): void {
    this.newName.set((event.target as HTMLInputElement).value);
  }

  protected create(): void {
    const name = this.newName().trim();
    if (!name || this.creating()) {
      return;
    }
    this.creating.set(true);
    this.error.set(null);
    this.playlistService.create(name).subscribe({
      next: () => {
        this.newName.set('');
        this.creating.set(false);
        this.load();
      },
      error: (e) => {
        this.creating.set(false);
        this.error.set(this.messageFor(e, 'Could not create that playlist.'));
      },
    });
  }

  protected askDelete(id: string): void {
    this.pendingDeleteId.set(id);
  }

  protected cancelDelete(): void {
    this.pendingDeleteId.set(null);
  }

  protected confirmDelete(id: string): void {
    this.playlistService.delete(id).subscribe({
      next: () => {
        this.pendingDeleteId.set(null);
        this.load();
      },
      error: () => {
        this.pendingDeleteId.set(null);
        this.error.set('Could not delete that playlist.');
      },
    });
  }

  /** The API returns a typed body for handled failures; fall back only when it does not. */
  private messageFor(e: unknown, fallback: string): string {
    const err = e as { error?: { message?: string } };
    return err?.error?.message ?? fallback;
  }

  private load(): void {
    this.loading.set(true);
    this.playlistService.list().subscribe({
      next: (playlists) => {
        this.playlists.set(playlists);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Could not load your playlists.');
        this.loading.set(false);
      },
    });
  }
}
