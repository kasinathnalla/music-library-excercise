package com.kasi.musiclibrary.playlist;

import com.kasi.musiclibrary.entity.Album;
import com.kasi.musiclibrary.entity.AppUser;
import com.kasi.musiclibrary.entity.Artist;
import com.kasi.musiclibrary.entity.Playlist;
import com.kasi.musiclibrary.entity.PlaylistItem;
import com.kasi.musiclibrary.entity.Role;
import com.kasi.musiclibrary.entity.Track;
import com.kasi.musiclibrary.repository.AlbumRepository;
import com.kasi.musiclibrary.repository.AppUserRepository;
import com.kasi.musiclibrary.repository.ArtistRepository;
import com.kasi.musiclibrary.repository.PlaylistRepository;
import com.kasi.musiclibrary.repository.TrackRepository;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What playlists do, and -- more importantly -- whose they are.
 *
 * <p><strong>These tests authenticate with real credentials, not {@code @WithMockUser}, and that is
 * not a style preference.</strong> {@code @WithMockUser} installs Spring's own {@code User} as the
 * principal, so {@code @AuthenticationPrincipal AppUserPrincipal} binds to null and every test here
 * fails in a way that looks like a controller bug. The seeded V3 accounts authenticate for real and
 * carry the database id that ownership is built on.
 *
 * <p>Tracks are created directly through the repository rather than uploaded. These are playlist
 * tests, not ingest tests, and there are only two audio fixtures -- uploading either one twice is a
 * 409 on the content hash, which would make "a playlist with three tracks" impossible to set up.
 */
class PlaylistControllerTest extends SecuredMockMvcTest {

	private static final String OWNER = "customer";
	private static final String STRANGER = "someone.else";

	@Autowired
	private PlaylistRepository playlists;
	@Autowired
	private TrackRepository tracks;
	@Autowired
	private AlbumRepository albums;
	@Autowired
	private ArtistRepository artists;
	@Autowired
	private AppUserRepository users;
	@Autowired
	private PasswordEncoder passwordEncoder;
	@Autowired
	private ObjectMapper json;

	private UUID trackA;
	private UUID trackB;
	private UUID trackC;

	@BeforeEach
	void setUp() {
		// Playlists first: playlist_item references track, so the tracks cannot go while rows
		// still point at them.
		playlists.deleteAll();
		tracks.deleteAll();
		albums.deleteAll();
		artists.deleteAll();

		Artist artist = artists.save(new Artist("The Oscillators"));
		Album album = albums.save(new Album("Sine Qua Non", artist, 1998));
		trackA = newTrack("Prelude in C", album);
		trackB = newTrack("Reference Tone", album);
		trackC = newTrack("Interlude No. 2", album);

		// A second real account, so ownership can actually be tested. Created directly rather
		// than through /api/auth/register so this file does not depend on registration's rules.
		if (users.findByUsernameIgnoreCase(STRANGER).isEmpty()) {
			users.save(new AppUser(STRANGER, passwordEncoder.encode("password"), Role.CUSTOMER,
					"Someone", "Else", LocalDate.of(1990, 1, 1), "Elsewhere"));
		}
	}

	private UUID newTrack(String title, Album album) {
		String hash = UUID.randomUUID().toString().replace("-", "").repeat(2);
		return tracks.save(new Track(title, album, 1, 1, 1000,
				"audio/" + UUID.randomUUID() + ".mp3", 1234L, "audio/mpeg", hash)).getId();
	}

	// --- creating and reading back ----------------------------------------------------

	@Test
	void aCreatedPlaylistAppearsInTheOwnersList() throws Exception {
		createPlaylist(OWNER, "Road trip");

		mockMvc.perform(get("/api/playlists").with(httpBasic(OWNER, OWNER)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].name").value("Road trip"))
				.andExpect(jsonPath("$[0].trackCount").value(0));
	}

	@Test
	void tracksComeBackInTheOrderTheyWereAdded() throws Exception {
		UUID playlist = createPlaylist(OWNER, "Road trip");
		addTrack(OWNER, playlist, trackA);
		addTrack(OWNER, playlist, trackB);

		mockMvc.perform(get("/api/playlists/" + playlist).with(httpBasic(OWNER, OWNER)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].position").value(0))
				.andExpect(jsonPath("$.items[0].track.title").value("Prelude in C"))
				.andExpect(jsonPath("$.items[1].position").value(1))
				.andExpect(jsonPath("$.items[1].track.title").value("Reference Tone"));
	}

	@Test
	void theSameTrackMayAppearTwice() throws Exception {
		UUID playlist = createPlaylist(OWNER, "On repeat");
		addTrack(OWNER, playlist, trackA);
		addTrack(OWNER, playlist, trackA);

		mockMvc.perform(get("/api/playlists/" + playlist).with(httpBasic(OWNER, OWNER)))
				.andExpect(jsonPath("$.items.length()").value(2))
				.andExpect(jsonPath("$.items[0].itemId").isNotEmpty())
				.andExpect(jsonPath("$.items[1].itemId").isNotEmpty());
	}

	// --- the one that matters ---------------------------------------------------------

	/**
	 * Every way in, tried as the wrong user. A failure here is a release blocker, not a bug:
	 * it means one listener can read or rewrite another's playlists.
	 */
	@Test
	void anotherUsersPlaylistIsInvisibleThroughEveryEndpoint() throws Exception {
		UUID playlist = createPlaylist(OWNER, "Private");
		addTrack(OWNER, playlist, trackA);

		var stranger = httpBasic(STRANGER, "password");

		mockMvc.perform(get("/api/playlists/" + playlist).with(stranger))
				.andExpect(status().isNotFound());
		mockMvc.perform(patch("/api/playlists/" + playlist).with(stranger)
						.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Mine now\"}"))
				.andExpect(status().isNotFound());
		mockMvc.perform(post("/api/playlists/" + playlist + "/items").with(stranger)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"trackId\":\"" + trackB + "\"}"))
				.andExpect(status().isNotFound());
		mockMvc.perform(put("/api/playlists/" + playlist + "/items").with(stranger)
						.contentType(MediaType.APPLICATION_JSON).content("{\"itemIds\":[]}"))
				.andExpect(status().isNotFound());
		mockMvc.perform(delete("/api/playlists/" + playlist).with(stranger))
				.andExpect(status().isNotFound());

		// And none of that touched it.
		mockMvc.perform(get("/api/playlists/" + playlist).with(httpBasic(OWNER, OWNER)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Private"))
				.andExpect(jsonPath("$.items.length()").value(1));
	}

	@Test
	void aStrangersListDoesNotIncludeYourPlaylists() throws Exception {
		createPlaylist(OWNER, "Private");

		mockMvc.perform(get("/api/playlists").with(httpBasic(STRANGER, "password")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
	}

	// --- reordering -------------------------------------------------------------------

	/**
	 * Three items, not two. A two-item swap can pass even against a non-deferred unique
	 * constraint depending on the order Hibernate happens to flush; three cannot.
	 */
	@Test
	void reorderingThreeItemsPersistsTheNewOrder() throws Exception {
		UUID playlist = createPlaylist(OWNER, "Road trip");
		List<UUID> items = List.of(addTrack(OWNER, playlist, trackA),
				addTrack(OWNER, playlist, trackB),
				addTrack(OWNER, playlist, trackC));

		reorder(OWNER, playlist, List.of(items.get(2), items.get(0), items.get(1)))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/playlists/" + playlist).with(httpBasic(OWNER, OWNER)))
				.andExpect(jsonPath("$.items[0].track.title").value("Interlude No. 2"))
				.andExpect(jsonPath("$.items[1].track.title").value("Prelude in C"))
				.andExpect(jsonPath("$.items[2].track.title").value("Reference Tone"))
				.andExpect(jsonPath("$.items[0].position").value(0))
				.andExpect(jsonPath("$.items[1].position").value(1))
				.andExpect(jsonPath("$.items[2].position").value(2));
	}

	@Test
	void aReorderThatDropsAnItemIsRefusedAndChangesNothing() throws Exception {
		UUID playlist = createPlaylist(OWNER, "Road trip");
		UUID first = addTrack(OWNER, playlist, trackA);
		addTrack(OWNER, playlist, trackB);

		reorder(OWNER, playlist, List.of(first)).andExpect(status().isBadRequest());

		mockMvc.perform(get("/api/playlists/" + playlist).with(httpBasic(OWNER, OWNER)))
				.andExpect(jsonPath("$.items.length()").value(2))
				.andExpect(jsonPath("$.items[0].track.title").value("Prelude in C"));
	}

	@Test
	void aReorderNamingAnItemFromAnotherPlaylistIsRefused() throws Exception {
		UUID playlist = createPlaylist(OWNER, "Road trip");
		UUID mine = addTrack(OWNER, playlist, trackA);
		UUID other = createPlaylist(OWNER, "Focus");
		UUID theirs = addTrack(OWNER, other, trackB);

		reorder(OWNER, playlist, List.of(mine, theirs)).andExpect(status().isBadRequest());
	}

	// --- removing ---------------------------------------------------------------------

	@Test
	void removingTheMiddleItemLeavesPositionsContiguous() throws Exception {
		UUID playlist = createPlaylist(OWNER, "Road trip");
		addTrack(OWNER, playlist, trackA);
		UUID middle = addTrack(OWNER, playlist, trackB);
		addTrack(OWNER, playlist, trackC);

		mockMvc.perform(delete("/api/playlists/" + playlist + "/items/" + middle)
						.with(httpBasic(OWNER, OWNER)))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/playlists/" + playlist).with(httpBasic(OWNER, OWNER)))
				.andExpect(jsonPath("$.items.length()").value(2))
				.andExpect(jsonPath("$.items[0].position").value(0))
				.andExpect(jsonPath("$.items[1].position").value(1))
				.andExpect(jsonPath("$.items[1].track.title").value("Interlude No. 2"));
	}

	// --- naming -----------------------------------------------------------------------

	@Test
	void theSameNameTwiceForOneOwnerIsAConflict() throws Exception {
		createPlaylist(OWNER, "Road trip");

		mockMvc.perform(post("/api/playlists").with(httpBasic(OWNER, OWNER))
						.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"road trip\"}"))
				.andExpect(status().isConflict());
	}

	@Test
	void twoOwnersMayEachHaveAPlaylistOfTheSameName() throws Exception {
		createPlaylist(OWNER, "Road trip");

		mockMvc.perform(post("/api/playlists").with(httpBasic(STRANGER, "password"))
						.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Road trip\"}"))
				.andExpect(status().isCreated());
	}

	// --- the left-join regression, restated for playlists -------------------------------

	/**
	 * A track with no album is legitimate. An inner join anywhere in the fetch would silently
	 * drop it, which is the defect {@code TrackControllerTest.listIncludesTracksThatHaveNoAlbum}
	 * guards for the library. This is the same guard for the playlist fetch.
	 */
	@Test
	void aPlaylistHoldingATrackWithNoAlbumStillReturnsIt() throws Exception {
		UUID orphan = newTrack("Solo Piece", null);
		UUID playlist = createPlaylist(OWNER, "Mixed");
		addTrack(OWNER, playlist, trackA);
		addTrack(OWNER, playlist, orphan);

		mockMvc.perform(get("/api/playlists/" + playlist).with(httpBasic(OWNER, OWNER)))
				.andExpect(jsonPath("$.items.length()").value(2))
				.andExpect(jsonPath("$.items[1].track.title").value("Solo Piece"));
	}

	@Test
	void addingATrackThatIsNotInTheLibraryIsNotFound() throws Exception {
		UUID playlist = createPlaylist(OWNER, "Road trip");

		mockMvc.perform(post("/api/playlists/" + playlist + "/items").with(httpBasic(OWNER, OWNER))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"trackId\":\"" + UUID.randomUUID() + "\"}"))
				.andExpect(status().isNotFound());
	}

	@Test
	void anAdminKeepsPlaylistsToo() throws Exception {
		createPlaylist("admin", "Curation queue");

		mockMvc.perform(get("/api/playlists").with(httpBasic("admin", "admin")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].name").value("Curation queue"));
	}

	// --- helpers ----------------------------------------------------------------------

	private UUID createPlaylist(String username, String name) throws Exception {
		String body = mockMvc.perform(post("/api/playlists")
						.with(httpBasic(username, passwordFor(username)))
						.contentType(MediaType.APPLICATION_JSON)
						.content(json.writeValueAsString(new java.util.HashMap<>(
								java.util.Map.of("name", name)))))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return UUID.fromString(json.readTree(body).get("id").asText());
	}

	/** @return the id of the item that was appended */
	private UUID addTrack(String username, UUID playlistId, UUID trackId) throws Exception {
		String body = mockMvc.perform(post("/api/playlists/" + playlistId + "/items")
						.with(httpBasic(username, passwordFor(username)))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"trackId\":\"" + trackId + "\"}"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		var items = json.readTree(body).get("items");
		return UUID.fromString(items.get(items.size() - 1).get("itemId").asText());
	}

	private org.springframework.test.web.servlet.ResultActions reorder(
			String username, UUID playlistId, List<UUID> itemIds) throws Exception {
		String ids = itemIds.stream().map(id -> "\"" + id + "\"")
				.collect(java.util.stream.Collectors.joining(","));
		return mockMvc.perform(put("/api/playlists/" + playlistId + "/items")
				.with(httpBasic(username, passwordFor(username)))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"itemIds\":[" + ids + "]}"));
	}

	/** The seeded accounts use their own name as the password; the one made here does not. */
	private String passwordFor(String username) {
		return STRANGER.equals(username) ? "password" : username;
	}

	@Test
	void positionsAreAssertedAgainstTheDatabaseNotJustTheResponse() throws Exception {
		UUID playlist = createPlaylist(OWNER, "Road trip");
		addTrack(OWNER, playlist, trackA);
		addTrack(OWNER, playlist, trackB);

		Playlist stored = playlists.findByIdAndOwnerIdWithItems(playlist,
				users.findByUsernameIgnoreCase(OWNER).orElseThrow().getId()).orElseThrow();
		assertThat(stored.getItems()).extracting(PlaylistItem::getPosition).containsExactly(0, 1);
	}
}
