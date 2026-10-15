package com.epsilonmusic.app.ui.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.epsilonmusic.app.R
import com.epsilonmusic.app.models.MediaMetadata
import com.epsilonmusic.app.ui.component.DefaultDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder

/**
 * Backstory of the currently playing track: album info, what the song is about,
 * and a short artist biography.
 *
 * Song meaning comes from the Genius API, artist bios from Genius with a
 * Wikipedia fallback, and the "about the song" text from Wikipedia extracts.
 * No API keys are required for any of these sources.
 */
data class SongStory(
    val album: String,
    val aboutSong: String?,
    val aboutArtist: String?,
)

/**
 * Fetches the [SongStory] for a track. Returns null when the fetch fails
 * entirely (e.g. no network); individual fields are null when that particular
 * piece of info could not be found.
 */
suspend fun fetchSongStory(
    title: String,
    artistName: String,
    fallbackAlbum: String?,
): SongStory? =
    withContext(Dispatchers.IO) {
        try {
            var album: String? = null
            var artistBio: String? = null

            // Genius lookup: search -> song -> primary artist
            val query = URLEncoder.encode("$artistName $title", "UTF-8")
            val searchRoot =
                JSONObject(URL("https://genius.com/api/search/multi?q=$query").readText())
                    .getJSONObject("response")
            val sections = searchRoot.getJSONArray("sections")
            var songId = -1
            for (i in 0 until sections.length()) {
                val section = sections.getJSONObject(i)
                val type = section.optString("type")
                if (type == "top_hit" || type == "song") {
                    val hits = section.getJSONArray("hits")
                    if (hits.length() > 0) {
                        songId = hits.getJSONObject(0).getJSONObject("result").getInt("id")
                        break
                    }
                }
            }

            if (songId != -1) {
                val song =
                    JSONObject(URL("https://genius.com/api/songs/$songId?text_format=plain").readText())
                        .getJSONObject("response")
                        .getJSONObject("song")

                if (song.has("album") && !song.isNull("album")) {
                    album = song.getJSONObject("album").optString("name").takeIf { it.isNotBlank() }
                }

                var artistId = -1
                if (song.has("primary_artist") && !song.isNull("primary_artist")) {
                    artistId = song.getJSONObject("primary_artist").optInt("id", -1)
                }
                if (artistId != -1) {
                    val artist =
                        JSONObject(
                                URL("https://genius.com/api/artists/$artistId?text_format=plain").readText(),
                            )
                            .getJSONObject("response")
                            .getJSONObject("artist")
                    if (artist.has("description") && !artist.isNull("description")) {
                        artistBio =
                            artist
                                .getJSONObject("description")
                                .optString("plain")
                                .takeIf { it.isNotBlank() && it != "?" }
                    }
                }
            }

            // Wikipedia is the primary source for "about the song": Genius rarely
            // carries per-song prose, while Wikipedia usually has an article.
            val aboutSong = fetchWikipediaExtract("$title ${album ?: fallbackAlbum.orEmpty()}".trim())

            // Wikipedia fallback for the artist bio when Genius has none.
            if (artistBio == null && artistName.isNotBlank()) {
                artistBio = fetchWikipediaExtract(artistName)
            }

            SongStory(
                album = album ?: fallbackAlbum.orEmpty(),
                aboutSong = aboutSong,
                aboutArtist = artistBio,
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

/**
 * Returns a short plain-text extract for the best Wikipedia match for [query],
 * or null when there is no usable article. Disambiguation pages are skipped.
 */
private fun fetchWikipediaExtract(query: String): String? {
    if (query.isBlank()) return null
    return try {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val searchRoot =
            JSONObject(
                    URL(
                        "https://en.wikipedia.org/w/api.php?action=query&list=search" +
                            "&srsearch=$encodedQuery&utf8=&format=json",
                    ).readText(),
                )
                .getJSONObject("query")
        val results = searchRoot.getJSONArray("search")
        if (results.length() == 0) return null

        val pageTitle = URLEncoder.encode(results.getJSONObject(0).getString("title"), "UTF-8")
        val pages =
            JSONObject(
                    URL(
                        "https://en.wikipedia.org/w/api.php?action=query&prop=extracts" +
                            "&exsentences=4&exlimit=1&titles=$pageTitle&explaintext=1&format=json",
                    ).readText(),
                )
                .getJSONObject("query")
                .getJSONObject("pages")
        val extract = pages.getJSONObject(pages.keys().next()).optString("extract", "")
        extract.takeIf { it.isNotBlank() && !it.contains("may refer to") }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

@Composable
fun SongStoryDialog(
    mediaMetadata: MediaMetadata,
    onDismiss: () -> Unit,
) {
    var story by remember { mutableStateOf<SongStory?>(null) }
    var failed by remember { mutableStateOf(false) }
    var fetched by remember { mutableStateOf(false) }

    val loadingText = stringResource(R.string.song_story_loading)
    val notFoundText = stringResource(R.string.song_story_not_found)
    val failedText = stringResource(R.string.song_story_failed)

    if (!fetched) {
        LaunchedEffect(mediaMetadata.id) {
            val result =
                fetchSongStory(
                    title = mediaMetadata.title,
                    artistName = mediaMetadata.artists.firstOrNull()?.name.orEmpty(),
                    fallbackAlbum = mediaMetadata.album?.title,
                )
            if (result == null) {
                failed = true
            } else {
                story = result
            }
            fetched = true
        }
    }

    DefaultDialog(
        onDismiss = onDismiss,
        icon = {
            Icon(
                painter = painterResource(R.drawable.sparks),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        },
        title = {
            Text(stringResource(R.string.song_story))
        },
        buttons = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column {
                    Text(
                        stringResource(R.string.song_story_album),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        when {
                            failed -> failedText
                            story == null -> loadingText
                            story!!.album.isNotBlank() -> story!!.album
                            else -> notFoundText
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            item {
                Column {
                    Text(
                        stringResource(R.string.song_story_about_song),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        when {
                            failed -> failedText
                            story == null -> loadingText
                            else -> story!!.aboutSong ?: notFoundText
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Column {
                    Text(
                        stringResource(R.string.song_story_about_artist),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        when {
                            failed -> failedText
                            story == null -> loadingText
                            else -> story!!.aboutArtist ?: notFoundText
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
