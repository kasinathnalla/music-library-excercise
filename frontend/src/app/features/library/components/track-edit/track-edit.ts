import { Component, computed, inject, input, linkedSignal, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TrackService } from '../../services/track.service';
import { Track, TrackEdit } from '../../models/track.model';

@Component({
  selector: 'app-track-edit',
  imports: [FormsModule],
  templateUrl: './track-edit.html',
  styleUrl: './track-edit.css',
})
export class TrackEditDialog {
  private readonly trackService = inject(TrackService);

  readonly track = input.required<Track>();
  readonly saved = output<Track>();
  readonly cancelled = output<void>();

  /*
   * linkedSignal, not signal seeded in a constructor or during render: these are writable
   * form fields whose starting value comes from an input. Seeding them from the template
   * threw NG0600, because writing signals while Angular renders is not allowed.
   */
  protected readonly title = linkedSignal(() => this.track().title ?? '');
  protected readonly artistName = linkedSignal(() => this.track().artistName ?? '');
  protected readonly albumTitle = linkedSignal(() => this.track().albumTitle ?? '');
  protected readonly trackNumber = linkedSignal<number | null>(() => this.track().trackNumber ?? null);
  protected readonly discNumber = linkedSignal<number | null>(() => this.track().discNumber ?? null);
  protected readonly releaseYear = linkedSignal<number | null>(() => this.track().releaseYear ?? null);

  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly titleInvalid = computed(() => this.title().trim().length === 0);

  protected wasEdited(field: string): boolean {
    return (this.track().userEditedFields ?? []).includes(field);
  }

  protected save(): void {
    if (this.titleInvalid()) {
      return;
    }
    const original = this.track();
    const edit: TrackEdit = {};

    // Only send what actually changed, so provenance records real edits rather than every
    // field the form happened to display.
    if (this.title().trim() !== (original.title ?? '')) {
      edit.title = this.title().trim();
    }
    if (this.artistName().trim() !== (original.artistName ?? '')) {
      edit.artistName = this.artistName().trim();
    }
    if (this.albumTitle().trim() !== (original.albumTitle ?? '')) {
      edit.albumTitle = this.albumTitle().trim();
    }
    if (this.asNumber(this.trackNumber()) !== (original.trackNumber ?? null)) {
      edit.trackNumber = this.asNumber(this.trackNumber()) ?? undefined;
    }
    if (this.asNumber(this.discNumber()) !== (original.discNumber ?? null)) {
      edit.discNumber = this.asNumber(this.discNumber()) ?? undefined;
    }
    if (this.asNumber(this.releaseYear()) !== (original.releaseYear ?? null)) {
      edit.releaseYear = this.asNumber(this.releaseYear()) ?? undefined;
    }

    if (Object.keys(edit).length === 0) {
      this.cancelled.emit();
      return;
    }

    this.saving.set(true);
    this.error.set(null);
    this.trackService.update(original.id, edit).subscribe({
      next: (updated) => {
        this.saving.set(false);
        this.saved.emit(updated);
      },
      error: (e) => {
        this.saving.set(false);
        this.error.set(e?.error?.message ?? 'Could not save those changes.');
      },
    });
  }

  /** A number input yields '' when cleared, which must mean null rather than NaN. */
  private asNumber(value: number | string | null): number | null {
    if (value === null || value === '' || value === undefined) {
      return null;
    }
    const n = Number(value);
    return Number.isFinite(n) ? n : null;
  }

  protected onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Escape') {
      this.cancelled.emit();
    }
  }
}
