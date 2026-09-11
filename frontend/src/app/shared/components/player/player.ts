import { Component, ElementRef, effect, inject, viewChild } from '@angular/core';
import { PlaybackService } from '../../../core/services/playback.service';

/**
 * The one {@code <audio>} element in the application.
 *
 * <p>Rendered from {@code app.ts} rather than from a view, so that switching between the library
 * and a playlist does not stop the music.
 */
@Component({
  selector: 'app-player',
  templateUrl: './player.html',
  styleUrl: './player.css',
})
export class Player {
  protected readonly playback = inject(PlaybackService);

  private readonly audio = viewChild<ElementRef<HTMLAudioElement>>('audio');

  constructor() {
    // Changing [src] on an existing <audio> does not restart playback on its own: autoplay only
    // applies to the first load, so without this the queue advances silently and nothing is
    // heard. This is an effect rather than template work precisely because it writes to the DOM.
    effect(() => {
      const url = this.playback.streamUrl();
      const element = this.audio()?.nativeElement;
      if (!element || !url) {
        return;
      }
      element.load();
      // Rejected when the browser blocks autoplay before any user gesture. The controls are
      // right there, so there is nothing useful to say about it.
      element.play().catch(() => undefined);
    });
  }
}
