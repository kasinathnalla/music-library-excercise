import { Track } from '../../library/models/track.model';

/** A playlist without its contents, as the list view receives it. */
export interface PlaylistSummary {
  id: string;
  name: string;
  trackCount: number;
  updatedAt: string;
}

/**
 * One entry. The track is nested rather than flattened because the entry's own identity matters:
 * removing and reordering are done by itemId, not by track id, and the same track may appear twice.
 */
export interface PlaylistItem {
  itemId: string;
  position: number;
  track: Track;
}

export interface Playlist {
  id: string;
  name: string;
  updatedAt: string;
  items: PlaylistItem[];
}
