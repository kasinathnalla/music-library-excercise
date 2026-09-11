import { Injectable, computed, inject, signal } from '@angular/core';
import { Track } from '../../features/library/models/track.model';
import { TrackService } from '../../features/library/services/track.service';

/**
 * What is playing, and what plays next.
 *
 * <p>This exists because a playlist has to advance on its own, and the playback state it needs is
 * the same state the library view already had inside itself. Two components each owning an
 * {@code <audio>} element would not be a styling problem, it would be two tracks playing at once
 * (D24).
 *
 * Deliberately only a sliver of Phase 2: a queue, a position in it, and advance-on-ended. Shuffle,
 * repeat, previous, and a visible editable queue are still that phase's work.
 */
@Injectable({ providedIn: 'root' })
export class PlaybackService {
  private readonly trackService = inject(TrackService);

  private readonly queue = signal<Track[]>([]);
  private readonly index = signal(-1);

  readonly nowPlaying = computed<Track | null>(() => this.queue()[this.index()] ?? null);
  readonly upNext = computed(() => this.queue().length - this.index() - 1);

  /** Null when nothing is playing, which is what the player template keys off. */
  readonly streamUrl = computed<string | null>(() => {
    const track = this.nowPlaying();
    return track ? this.trackService.streamUrl(track.id) : null;
  });

  /** One track, from the library. Replaces whatever was queued. */
  playOne(track: Track): void {
    this.queue.set([track]);
    this.index.set(0);
  }

  /** A whole playlist, starting at a position in it. */
  playQueue(tracks: Track[], from = 0): void {
    if (tracks.length === 0) {
      return;
    }
    this.queue.set([...tracks]);
    this.index.set(Math.min(Math.max(from, 0), tracks.length - 1));
  }

  /** Called when the current track ends. Stops at the end rather than looping. */
  next(): void {
    if (this.index() < this.queue().length - 1) {
      this.index.update((i) => i + 1);
    } else {
      this.stop();
    }
  }

  stop(): void {
    this.queue.set([]);
    this.index.set(-1);
  }

  /**
   * Refreshes a queued track after it was edited, so the player does not keep showing the old
   * title. The queue holds snapshots, not live references.
   */
  updateTrack(updated: Track): void {
    this.queue.update((queue) =>
      queue.map((track) => (track.id === updated.id ? updated : track)),
    );
  }

  /**
   * Drops a track that no longer exists, for when an admin deletes it mid-listen.
   *
   * <p>Leaving it queued would mean advancing into a stream URL that 404s, which presents as the
   * player silently stopping for no visible reason.
   */
  forget(trackId: string): void {
    const current = this.nowPlaying();
    const remaining = this.queue().filter((track) => track.id !== trackId);
    if (remaining.length === this.queue().length) {
      return;
    }
    if (current && current.id === trackId) {
      this.stop();
      return;
    }
    const newIndex = current ? remaining.findIndex((track) => track.id === current.id) : -1;
    this.queue.set(remaining);
    this.index.set(newIndex);
  }
}
