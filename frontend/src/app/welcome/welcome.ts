import { Component, input, output } from '@angular/core';
import { LibraryStats } from '../catalog/stats.model';

@Component({
  selector: 'app-welcome',
  imports: [],
  templateUrl: './welcome.html',
  styleUrl: './welcome.css',
})
export class Welcome {
  readonly stats = input<LibraryStats | null>(null);
  readonly enter = output<void>();

  protected formatTotalDuration(ms: number | undefined): string {
    if (!ms) {
      return '0m';
    }
    const totalMinutes = Math.round(ms / 60000);
    if (totalMinutes < 60) {
      return `${totalMinutes}m`;
    }
    const hours = Math.floor(totalMinutes / 60);
    const minutes = totalMinutes % 60;
    return minutes === 0 ? `${hours}h` : `${hours}h ${minutes}m`;
  }
}
