import { Component, effect, inject, input, output, signal } from '@angular/core';
import { PlaylistService } from '../../services/playlist.service';
import { Playlist, PlaylistItem } from '../../models/playlist.model';
import { PlaybackService } from '../../../../core/services/playback.service';

@Component({
  selector: 'app-playlist-detail',
  templateUrl: './playlist-detail.html',
  styleUrl: './playlist-detail.css',
})
export class PlaylistDetail {
  private readonly playlistService = inject(PlaylistService);
  protected readonly playback = inject(PlaybackService);

  readonly playlistId = input.required<string>();
  readonly back = output<void>();
  readonly changed = output<void>();

  protected readonly playlist = signal<Playlist | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);

  constructor() {
    // An effect rather than a constructor call because an input is not yet set when the
    // constructor runs. It reloads if the id ever changes, which costs nothing and removes a
    // whole class of stale-view bug.
    effect(() => {
      const id = this.playlistId();
      this.load(id);
    });
  }

  protected playAll(): void {
    const items = this.playlist()?.items ?? [];
    this.playback.playQueue(items.map((item) => item.track), 0);
  }

  protected playFrom(index: number): void {
    const items = this.playlist()?.items ?? [];
    this.playback.playQueue(items.map((item) => item.track), index);
  }

  protected moveUp(index: number): void {
    if (index > 0) {
      this.swap(index, index - 1);
    }
  }

  protected moveDown(index: number): void {
    const items = this.playlist()?.items ?? [];
    if (index < items.length - 1) {
      this.swap(index, index + 1);
    }
  }

  /**
   * Sends the whole new order, not a move (D23). The server refuses anything that is not a
   * permutation of the current items, so a stale view cannot silently drop a track.
   */
  private swap(from: number, to: number): void {
    const current = this.playlist();
    if (!current || this.busy()) {
      return;
    }
    const ids = current.items.map((item) => item.itemId);
    [ids[from], ids[to]] = [ids[to], ids[from]];

    this.busy.set(true);
    this.playlistService.reorder(current.id, ids).subscribe({
      next: (updated) => {
        this.playlist.set(updated);
        this.busy.set(false);
        this.changed.emit();
      },
      error: () => {
        this.busy.set(false);
        this.error.set('Could not reorder. Reloading.');
        this.load(current.id);
      },
    });
  }

  protected remove(item: PlaylistItem): void {
    const current = this.playlist();
    if (!current || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.playlistService.removeItem(current.id, item.itemId).subscribe({
      next: (updated) => {
        this.playlist.set(updated);
        this.busy.set(false);
        this.changed.emit();
      },
      error: () => {
        this.busy.set(false);
        this.error.set(`Could not remove “${item.track.title}”.`);
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

  private load(playlistId: string): void {
    this.loading.set(true);
    this.error.set(null);
    this.playlistService.get(playlistId).subscribe({
      next: (playlist) => {
        this.playlist.set(playlist);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Could not load that playlist.');
        this.loading.set(false);
      },
    });
  }
}
