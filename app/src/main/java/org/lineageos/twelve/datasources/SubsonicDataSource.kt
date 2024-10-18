/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.twelve.datasources

import android.net.Uri
import android.os.Bundle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.mapLatest
import org.lineageos.twelve.R
import org.lineageos.twelve.datasources.subsonic.SubsonicClient
import org.lineageos.twelve.datasources.subsonic.models.AlbumID3
import org.lineageos.twelve.datasources.subsonic.models.ArtistID3
import org.lineageos.twelve.datasources.subsonic.models.Child
import org.lineageos.twelve.datasources.subsonic.models.Error
import org.lineageos.twelve.datasources.subsonic.models.MediaType
import org.lineageos.twelve.models.Album
import org.lineageos.twelve.models.Artist
import org.lineageos.twelve.models.ArtistWorks
import org.lineageos.twelve.models.Audio
import org.lineageos.twelve.models.Genre
import org.lineageos.twelve.models.Playlist
import org.lineageos.twelve.models.ProviderArgument
import org.lineageos.twelve.models.ProviderArgument.Companion.requireArgument
import org.lineageos.twelve.models.RequestStatus
import org.lineageos.twelve.models.Thumbnail

/**
 * Subsonic based data source.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SubsonicDataSource(arguments: Bundle) : MediaDataSource {
    private val server = arguments.requireArgument(ARG_SERVER)
    private val username = arguments.requireArgument(ARG_USERNAME)
    private val password = arguments.requireArgument(ARG_PASSWORD)
    private val useLegacyAuthentication = arguments.requireArgument(ARG_USE_LEGACY_AUTHENTICATION)

    private val subsonicClient = SubsonicClient(
        server, username, password, "Twelve", useLegacyAuthentication
    )

    private val dataSourceBaseUri = Uri.parse(server)

    private val albumsUri = dataSourceBaseUri.buildUpon()
        .appendPath(ALBUMS_PATH)
        .build()
    private val artistsUri = dataSourceBaseUri.buildUpon()
        .appendPath(ARTISTS_PATH)
        .build()
    private val audiosUri = dataSourceBaseUri.buildUpon()
        .appendPath(AUDIOS_PATH)
        .build()
    private val genresUri = dataSourceBaseUri.buildUpon()
        .appendPath(GENRES_PATH)
        .build()
    private val playlistsUri = dataSourceBaseUri.buildUpon()
        .appendPath(PLAYLISTS_PATH)
        .build()

    /**
     * This flow is used to signal a change in the playlists.
     */
    private val _playlistsChanged = MutableStateFlow(Any())

    override fun albums() = suspend {
        subsonicClient.getAlbumList2("alphabeticalByName", 500).toRequestStatus {
            album.map { it.toMediaItem() }
        }
    }.asFlow()

    override fun artists() = suspend {
        subsonicClient.getArtists().toRequestStatus {
            index.flatMap { it.artist }.map { it.toMediaItem() }
        }
    }.asFlow()

    override fun genres() = suspend {
        subsonicClient.getGenres().toRequestStatus {
            genre.map { it.toMediaItem() }
        }
    }.asFlow()

    override fun playlists() = _playlistsChanged.mapLatest {
        subsonicClient.getPlaylists().toRequestStatus {
            playlist.map { it.toMediaItem() }
        }
    }

    override fun search(query: String) = suspend {
        subsonicClient.search3(query).toRequestStatus {
            song.map { it.toMediaItem() } +
                    artist.map { it.toMediaItem() } +
                    album.map { it.toMediaItem() }
        }
    }.asFlow()

    override fun audio(audioUri: Uri) = suspend {
        subsonicClient.getSong(audioUri.lastPathSegment!!).toRequestStatus {
            toMediaItem()
        }
    }.asFlow()

    override fun album(albumUri: Uri) = suspend {
        subsonicClient.getAlbum(albumUri.lastPathSegment!!).toRequestStatus {
            toAlbumID3().toMediaItem() to song.map {
                it.toMediaItem()
            }
        }
    }.asFlow()

    override fun artist(artistUri: Uri) = suspend {
        subsonicClient.getArtist(artistUri.lastPathSegment!!).toRequestStatus {
            toArtistID3().toMediaItem() to ArtistWorks(
                albums = album.map { it.toMediaItem() },
                appearsInAlbum = listOf(),
                appearsInPlaylist = listOf(),
            )
        }
    }.asFlow()

    override fun genre(genreUri: Uri) = suspend {
        val genreName = genreUri.lastPathSegment!!
        subsonicClient.getSongsByGenre(genreName).toRequestStatus {
            Genre(genreUri, genreName) to song.map { it.toMediaItem() }
        }
    }.asFlow()

    override fun playlist(playlistUri: Uri) = _playlistsChanged.mapLatest {
        subsonicClient.getPlaylist(playlistUri.lastPathSegment!!.toInt()).toRequestStatus {
            toPlaylist().toMediaItem() to entry.map {
                it.toMediaItem()
            } as List<Audio?>
        }
    }

    override fun audioPlaylistsStatus(audioUri: Uri) = _playlistsChanged.mapLatest {
        val audioId = audioUri.lastPathSegment!!

        subsonicClient.getPlaylists().toRequestStatus {
            playlist.map { playlist ->
                playlist.toMediaItem() to subsonicClient.getPlaylist(playlist.id).toRequestStatus {
                    entry.any { child -> child.id == audioId }
                }.let { requestStatus ->
                    (requestStatus as? RequestStatus.Success)?.data ?: false
                }
            }
        }
    }

    override suspend fun createPlaylist(name: String) = subsonicClient.createPlaylist(
        null, name, listOf()
    ).toRequestStatus {
        onPlaylistsChanged()
        getPlaylistUri(id.toString())
    }

    override suspend fun renamePlaylist(
        playlistUri: Uri, name: String
    ) = subsonicClient.updatePlaylist(playlistUri.lastPathSegment!!, name).toRequestStatus {
        onPlaylistsChanged()
    }

    override suspend fun deletePlaylist(playlistUri: Uri) = subsonicClient.deletePlaylist(
        playlistUri.lastPathSegment!!.toInt()
    ).toRequestStatus {
        onPlaylistsChanged()
    }

    override suspend fun addAudioToPlaylist(playlistUri: Uri, audioUri: Uri) =
        subsonicClient.updatePlaylist(
            playlistUri.lastPathSegment!!,
            songIdsToAdd = listOf(audioUri.lastPathSegment!!)
        ).toRequestStatus {
            onPlaylistsChanged()
        }

    override suspend fun removeAudioFromPlaylist(
        playlistUri: Uri,
        audioUri: Uri
    ) = subsonicClient.getPlaylist(
        playlistUri.lastPathSegment!!.toInt()
    ).toRequestStatus {
        val audioId = audioUri.lastPathSegment!!

        val audioIndexes = entry.mapIndexedNotNull { index, child ->
            index.takeIf { child.id == audioId }
        }

        if (audioIndexes.isNotEmpty()) {
            subsonicClient.updatePlaylist(
                playlistUri.lastPathSegment!!,
                songIndexesToRemove = audioIndexes,
            ).toRequestStatus {
                onPlaylistsChanged()
            }
        }
    }

    private fun AlbumID3.toMediaItem() = Album(
        uri = getAlbumUri(id),
        title = name,
        artistUri = artistId?.let { getArtistUri(it) } ?: Uri.EMPTY,
        artistName = artist ?: "",
        year = year,
        thumbnail = Thumbnail(
            uri = Uri.parse(subsonicClient.getCoverArt(id)),
            type = Thumbnail.Type.FRONT_COVER,
        ),
    )

    private fun ArtistID3.toMediaItem() = Artist(
        uri = getArtistUri(id),
        name = name,
        thumbnail = Thumbnail(
            uri = Uri.parse(subsonicClient.getCoverArt(id)),
            type = Thumbnail.Type.BAND_ARTIST_LOGO,
        ),
    )

    private fun Child.toMediaItem() = Audio(
        uri = getAudioUri(id),
        playbackUri = Uri.parse(subsonicClient.stream(id)),
        mimeType = contentType ?: "",
        title = title,
        type = type.toAudioType(),
        durationMs = (duration?.let { it * 1000 }) ?: 0,
        artistUri = artistId?.let { getArtistUri(it) } ?: Uri.EMPTY,
        artistName = artist ?: "",
        albumUri = albumId?.let { getAlbumUri(it) } ?: Uri.EMPTY,
        albumTitle = album ?: "",
        albumTrack = track ?: 0,
        genreUri = genre?.let { getGenreUri(it) },
        genreName = genre,
        year = year,
    )

    private fun org.lineageos.twelve.datasources.subsonic.models.Genre.toMediaItem() = Genre(
        uri = getGenreUri(value),
        name = value,
    )

    private fun org.lineageos.twelve.datasources.subsonic.models.Playlist.toMediaItem() = Playlist(
        uri = getPlaylistUri(id.toString()),
        name = name,
    )

    private fun MediaType?.toAudioType() = when (this) {
        MediaType.MUSIC -> Audio.Type.MUSIC
        MediaType.PODCAST -> Audio.Type.PODCAST
        MediaType.AUDIOBOOK -> Audio.Type.AUDIOBOOK
        MediaType.VIDEO -> throw Exception("Invalid media type, got VIDEO")
        else -> Audio.Type.MUSIC
    }

    private suspend fun <T : Any, O : Any> SubsonicClient.MethodResult<T>.toRequestStatus(
        resultGetter: suspend T.() -> O
    ) = when (this) {
        is SubsonicClient.MethodResult.Success -> RequestStatus.Success(result.resultGetter())
        is SubsonicClient.MethodResult.HttpError -> RequestStatus.Error(RequestStatus.Error.Type.IO)
        is SubsonicClient.MethodResult.SubsonicError -> RequestStatus.Error(
            error?.code?.toRequestStatusType() ?: RequestStatus.Error.Type.IO
        )
    }

    private fun Error.Code.toRequestStatusType() = when (this) {
        Error.Code.GENERIC_ERROR -> RequestStatus.Error.Type.IO
        Error.Code.REQUIRED_PARAMETER_MISSING -> RequestStatus.Error.Type.IO
        Error.Code.OUTDATED_CLIENT -> RequestStatus.Error.Type.IO
        Error.Code.OUTDATED_SERVER -> RequestStatus.Error.Type.IO
        Error.Code.WRONG_CREDENTIALS -> RequestStatus.Error.Type.INVALID_CREDENTIALS
        Error.Code.TOKEN_AUTHENTICATION_NOT_SUPPORTED ->
            RequestStatus.Error.Type.INVALID_CREDENTIALS

        Error.Code.USER_NOT_AUTHORIZED -> RequestStatus.Error.Type.INVALID_CREDENTIALS
        Error.Code.SUBSONIC_PREMIUM_TRIAL_ENDED -> RequestStatus.Error.Type.INVALID_CREDENTIALS
        Error.Code.NOT_FOUND -> RequestStatus.Error.Type.NOT_FOUND
    }

    private fun getAlbumUri(albumId: String) = albumsUri.buildUpon()
        .appendPath(albumId)
        .build()

    private fun getArtistUri(artistId: String) = artistsUri.buildUpon()
        .appendPath(artistId)
        .build()

    private fun getAudioUri(audioId: String) = audiosUri.buildUpon()
        .appendPath(audioId)
        .build()

    private fun getGenreUri(genre: String) = genresUri.buildUpon()
        .appendPath(genre)
        .build()

    private fun getPlaylistUri(playlistId: String) = playlistsUri.buildUpon()
        .appendPath(playlistId)
        .build()

    private fun onPlaylistsChanged() {
        _playlistsChanged.value = Any()
    }

    companion object {
        private const val ALBUMS_PATH = "albums"
        private const val ARTISTS_PATH = "artists"
        private const val AUDIOS_PATH = "audio"
        private const val GENRES_PATH = "genres"
        private const val PLAYLISTS_PATH = "playlists"

        val ARG_SERVER = ProviderArgument(
            "server",
            String::class,
            R.string.provider_argument_server,
            required = true,
            hidden = false,
        )

        val ARG_USERNAME = ProviderArgument(
            "username",
            String::class,
            R.string.provider_argument_username,
            required = true,
            hidden = false,
        )

        val ARG_PASSWORD = ProviderArgument(
            "password",
            String::class,
            R.string.provider_argument_password,
            required = true,
            hidden = true,
        )

        val ARG_USE_LEGACY_AUTHENTICATION = ProviderArgument(
            "use_legacy_authentication",
            Boolean::class,
            R.string.provider_argument_use_legacy_authentication,
            required = true,
            hidden = false,
            defaultValue = false,
        )
    }
}
