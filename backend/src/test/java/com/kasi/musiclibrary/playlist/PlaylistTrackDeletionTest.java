package com.kasi.musiclibrary.playlist;

import com.kasi.musiclibrary.entity.Album;
import com.kasi.musiclibrary.entity.Artist;
import com.kasi.musiclibrary.entity.Track;
import com.kasi.musiclibrary.repository.AlbumRepository;
import com.kasi.musiclibrary.repository.ArtistRepository;
import com.kasi.musiclibrary.repository.PlaylistRepository;
import com.kasi.musiclibrary.repository.TrackRepository;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What happens to a listener's playlist when an admin removes a track from the library.
 *
 * <p>The foreign key in V6 cascades the rows away on its own. What it does not do is renumber what
 * is left, so without {@code PlaylistService.removeTrackEverywhere} a playlist that held positions
 * 0, 1, 2 would be left holding 0, 2 -- correct order, wrong contract.
 */
class PlaylistTrackDeletionTest extends SecuredMockMvcTest {

	@Autowired
	private PlaylistRepository playlists;
	@Autowired
	private TrackRepository tracks;
	@Autowired
	private AlbumRepository albums;
	@Autowired
	private ArtistRepository artists;
	@Autowired
	private ObjectMapper json;

	private UUID first;
	private UUID middle;
	private UUID last;

	@BeforeEach
	void setUp() {
		playlists.deleteAll();
		tracks.deleteAll();
		albums.deleteAll();
		artists.deleteAll();

		Artist artist = artists.save(new Artist("The Oscillators"));
		Album album = albums.save(new Album("Sine Qua Non", artist, 1998));
		first = newTrack("Prelude in C", album);
		middle = newTrack("Reference Tone", album);
		last = newTrack("Interlude No. 2", album);
	}

	private UUID newTrack(String title, Album album) {
		String hash = UUID.randomUUID().toString().replace("-", "").repeat(2);
		return tracks.save(new Track(title, album, 1, 1, 1000,
				"audio/" + UUID.randomUUID() + ".mp3", 1234L, "audio/mpeg", hash)).getId();
	}

	@Test
	void deletingATrackRemovesItFromPlaylistsAndClosesTheGap() throws Exception {
		UUID playlist = createPlaylist("Road trip");
		addTrack(playlist, first);
		addTrack(playlist, middle);
		addTrack(playlist, last);

		mockMvc.perform(delete("/api/tracks/" + middle).with(httpBasic("admin", "admin")))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/playlists/" + playlist).with(httpBasic("customer", "customer")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(2))
				.andExpect(jsonPath("$.items[0].position").value(0))
				.andExpect(jsonPath("$.items[0].track.title").value("Prelude in C"))
				.andExpect(jsonPath("$.items[1].position").value(1))
				.andExpect(jsonPath("$.items[1].track.title").value("Interlude No. 2"));
	}

	@Test
	void deletingATrackThatAppearsTwiceRemovesBothCopies() throws Exception {
		UUID playlist = createPlaylist("On repeat");
		addTrack(playlist, first);
		addTrack(playlist, middle);
		addTrack(playlist, middle);
		addTrack(playlist, last);

		mockMvc.perform(delete("/api/tracks/" + middle).with(httpBasic("admin", "admin")))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/playlists/" + playlist).with(httpBasic("customer", "customer")))
				.andExpect(jsonPath("$.items.length()").value(2))
				.andExpect(jsonPath("$.items[0].position").value(0))
				.andExpect(jsonPath("$.items[1].position").value(1));
	}

	private UUID createPlaylist(String name) throws Exception {
		String body = mockMvc.perform(post("/api/playlists").with(httpBasic("customer", "customer"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"" + name + "\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return UUID.fromString(json.readTree(body).get("id").asText());
	}

	private void addTrack(UUID playlistId, UUID trackId) throws Exception {
		mockMvc.perform(post("/api/playlists/" + playlistId + "/items")
						.with(httpBasic("customer", "customer"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"trackId\":\"" + trackId + "\"}"))
				.andExpect(status().isOk());
	}
}
