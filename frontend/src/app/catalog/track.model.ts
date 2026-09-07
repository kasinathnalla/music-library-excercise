export interface Track {
  id: string;
  title: string;
  albumTitle?: string;
  artistName?: string;
  trackNumber?: number;
  discNumber?: number;
  durationMs?: number;
  releaseYear?: number;
  userEditedFields?: string[];
}

export interface TrackPage {
  items: Track[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ListOptions {
  query?: string;
  page?: number;
  size?: number;
}

/** A partial edit. Omitted keys are unchanged; '' on album/artist detaches it. */
export interface TrackEdit {
  title?: string;
  albumTitle?: string;
  artistName?: string;
  trackNumber?: number;
  discNumber?: number;
  releaseYear?: number;
}
