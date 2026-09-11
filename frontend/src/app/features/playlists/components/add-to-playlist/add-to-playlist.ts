import { Component, computed, inject, input, output, signal } from '@angular/core';
import { Track } from '../../../library/models/track.model';
import { PlaylistService } from '../../services/playlist.service';
import { PlaylistSummary } from '../../models/playlist.model';

/** The value the select uses for "make a new one" — not a playlist id. */
const NEW_PLAYLIST = '__new__';

/**
 * Puts one track into a playlist, or into a new one.
 *
 * <p>Lives in the playlists feature so that knowing how playlists work stays here. The library
 * renders it and knows only that a track can be added somewhere.
 */
@Component({
  selector: 'app-add-to-playlist',
  templateUrl: './add-to-playlist.html',
  styleUrl: './add-to-playlist.css',
})
export class AddToPlaylist {
  private readonly playlistService = inject(PlaylistService);

  readonly track = input.required<Track>();

  /** Emits the playlist name, so the caller can confirm it where the user is looking. */
  readonly added = output<string>();
  readonly cancelled = output<void>();

  protected readonly playlists = signal<PlaylistSummary[]>([]);
  protected readonly loading = signal(true);
  protected readonly choice = signal('');
  protected readonly newName = signal('');
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);

  protected readonly newPlaylistValue = NEW_PLAYLIST;
  protected readonly creatingNew = computed(() => this.choice() === NEW_PLAYLIST);

  /** Ids are unique per row, so the label points at the right control when several rows exist. */
  protected readonly selectId = computed(() => `add-to-playlist-${this.track().id}`);
  protected readonly nameId = computed(() => `new-playlist-${this.track().id}`);

  constructor() {
    this.load();
  }

  /** Choosing an existing playlist adds straight away; choosing "New" reveals the name field. */
  protected onChoose(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.choice.set(value);
    this.error.set(null);
    if (!value || value === NEW_PLAYLIST) {
      return;
    }
    const playlist = this.playlists().find((p) => p.id === value);
    if (playlist) {
      this.addTo(playlist);
    }
  }

  protected onNewNameInput(event: Event): void {
    this.newName.set((event.target as HTMLInputElement).value);
  }

  protected createAndAdd(): void {
    const name = this.newName().trim();
    if (!name || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.playlistService.create(name).subscribe({
      next: (created) => {
        this.playlistService.addTrack(created.id, this.track().id).subscribe({
          next: () => {
            this.busy.set(false);
            this.added.emit(created.name);
          },
          error: () => {
            this.busy.set(false);
            this.error.set(`Made “${created.name}”, but could not add the track to it.`);
          },
        });
      },
      error: (e) => {
        this.busy.set(false);
        this.error.set(this.messageFor(e, 'Could not create that playlist.'));
      },
    });
  }

  private addTo(playlist: PlaylistSummary): void {
    this.busy.set(true);
    this.playlistService.addTrack(playlist.id, this.track().id).subscribe({
      next: () => {
        this.busy.set(false);
        this.added.emit(playlist.name);
      },
      error: (e) => {
        this.busy.set(false);
        this.choice.set('');
        this.error.set(this.messageFor(e, `Could not add “${this.track().title}”.`));
      },
    });
  }

  /** The API returns a typed body for handled failures; fall back only when it does not. */
  private messageFor(e: unknown, fallback: string): string {
    const err = e as { error?: { message?: string } };
    return err?.error?.message ?? fallback;
  }

  private load(): void {
    this.playlistService.list().subscribe({
      next: (playlists) => {
        this.playlists.set(playlists);
        this.loading.set(false);
        // Nothing to choose between yet, so go straight to naming the first one.
        if (playlists.length === 0) {
          this.choice.set(NEW_PLAYLIST);
        }
      },
      error: () => {
        this.playlists.set([]);
        this.loading.set(false);
        this.error.set('Could not load your playlists.');
      },
    });
  }
}
