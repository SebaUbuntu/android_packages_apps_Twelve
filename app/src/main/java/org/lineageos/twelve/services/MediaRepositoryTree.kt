/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.twelve.services

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import org.lineageos.twelve.ext.buildMediaItem
import org.lineageos.twelve.ext.permissionsGranted
import org.lineageos.twelve.models.Album
import org.lineageos.twelve.models.Artist
import org.lineageos.twelve.models.Audio
import org.lineageos.twelve.models.Genre
import org.lineageos.twelve.models.Playlist
import org.lineageos.twelve.models.RequestStatus
import org.lineageos.twelve.repositories.MediaRepository
import org.lineageos.twelve.utils.PermissionsUtils

class MediaRepositoryTree(
    private val context: Context,
    private val repository: MediaRepository,
) {
    /**
     * Get the root media item of the tree.
     */
    fun getRootMediaItem() = rootMediaItem

    /**
     * Given a media ID, gets it's corresponding media item.
     */
    suspend fun getItem(mediaId: String) = when (mediaId) {
        ROOT_MEDIA_ITEM_ID -> rootMediaItem

        NO_PERMISSIONS_MEDIA_ITEM_ID -> noPermissionsMediaItem

        NO_PERMISSIONS_DESCRIPTION_MEDIA_ITEM_ID -> noPermissionsDescriptionMediaItem

        ALBUMS_MEDIA_ITEM_ID -> albumsMediaItem

        ARTISTS_MEDIA_ITEM_ID -> artistsMediaItem

        GENRES_MEDIA_ITEM_ID -> genresMediaItem

        PLAYLISTS_MEDIA_ITEM_ID -> playlistsMediaItem

        else -> mediaIdToUniqueItem(mediaId)?.toMedia3MediaItem()
    }

    /**
     * Given an item's media ID, gets its children.
     */
    suspend fun getChildren(mediaId: String) = when (mediaId) {
        ROOT_MEDIA_ITEM_ID -> when (context.permissionsGranted(PermissionsUtils.mainPermissions)) {
            true -> listOf(
                albumsMediaItem,
                artistsMediaItem,
                genresMediaItem,
                playlistsMediaItem,
            )

            false -> listOf(noPermissionsMediaItem)
        }

        NO_PERMISSIONS_MEDIA_ITEM_ID -> listOf(noPermissionsDescriptionMediaItem)

        NO_PERMISSIONS_DESCRIPTION_MEDIA_ITEM_ID -> listOf()

        ALBUMS_MEDIA_ITEM_ID -> repository.albums().toOneShotResult().map {
            it.toMedia3MediaItem()
        }

        ARTISTS_MEDIA_ITEM_ID -> repository.artists().toOneShotResult().map {
            it.toMedia3MediaItem()
        }

        GENRES_MEDIA_ITEM_ID -> repository.genres().toOneShotResult().map {
            it.toMedia3MediaItem()
        }

        PLAYLISTS_MEDIA_ITEM_ID -> repository.playlists().toOneShotResult().map {
            it.toMedia3MediaItem()
        }

        else -> when (val it = mediaIdToUniqueItem(mediaId)) {
            null -> listOf()

            is Album -> repository.album(it.uri).toOneShotResult().second.map { albumAudios ->
                albumAudios.toMedia3MediaItem()
            }

            is Artist -> repository.artist(it.uri).toOneShotResult().second.let { artistWorks ->
                listOf(
                    artistWorks.albums,
                    artistWorks.appearsInAlbum,
                ).flatten().map { allRelatedAlbums ->
                    allRelatedAlbums.toMedia3MediaItem()
                }
            }

            is Audio -> listOf()

            is Genre -> listOf()

            is Playlist -> repository.playlist(
                it.uri
            ).toOneShotResult().second.filterNotNull().map { playlistAudio ->
                playlistAudio.toMedia3MediaItem()
            }
        }
    }

    /**
     * Given a list of media items, gets an equivalent list of items that can be passed to the
     * player. This should be used with onAddMediaItems and onSetMediaItems.
     * TODO: [MediaItem.requestMetadata] support.
     */
    suspend fun resolveMediaItems(mediaItems: List<MediaItem>) = mediaItems.mapNotNull {
        it.takeIf { it.localConfiguration?.uri != null } ?: getItem(it.mediaId)
    }

    /**
     * Given a query, search for media items.
     */
    suspend fun search(query: String) = repository.search("%${query}%").toOneShotResult().map {
        it.toMedia3MediaItem()
    }

    /**
     * Given a media ID, get the item from the repository.
     */
    private suspend fun mediaIdToUniqueItem(mediaId: String) = when {
        mediaId.startsWith(Album.ALBUM_MEDIA_ITEM_ID_PREFIX) -> {
            repository.album(Uri.parse(mediaId.removePrefix(Album.ALBUM_MEDIA_ITEM_ID_PREFIX)))
                .toOneShotResult().first
        }

        mediaId.startsWith(Artist.ARTIST_MEDIA_ITEM_ID_PREFIX) -> {
            repository.artist(Uri.parse(mediaId.removePrefix(Artist.ARTIST_MEDIA_ITEM_ID_PREFIX)))
                .toOneShotResult().first
        }

        mediaId.startsWith(Audio.AUDIO_MEDIA_ITEM_ID_PREFIX) -> {
            repository.audio(Uri.parse(mediaId.removePrefix(Audio.AUDIO_MEDIA_ITEM_ID_PREFIX)))
                .toOneShotResult()
        }

        mediaId.startsWith(Genre.GENRE_MEDIA_ITEM_ID_PREFIX) -> {
            // TODO
            /*
            repository.genre(Uri.parse(mediaId.removePrefix(GENRE_MEDIA_ITEM_ID_PREFIX)))
                .toOneShotResult().first

             */
            null
        }

        mediaId.startsWith(Playlist.PLAYLIST_MEDIA_ITEM_ID_PREFIX) -> {
            repository.playlist(Uri.parse(mediaId.removePrefix(Playlist.PLAYLIST_MEDIA_ITEM_ID_PREFIX)))
                .toOneShotResult().first
        }

        else -> null
    }

    companion object {
        // Root ID
        private const val ROOT_MEDIA_ITEM_ID = "[root]"

        // No permissions ID
        private const val NO_PERMISSIONS_MEDIA_ITEM_ID = "[no_permissions]"
        private const val NO_PERMISSIONS_DESCRIPTION_MEDIA_ITEM_ID = "[no_permissions_description]"

        // Root elements IDs
        private const val ALBUMS_MEDIA_ITEM_ID = "[albums]"
        private const val ARTISTS_MEDIA_ITEM_ID = "[artists]"
        private const val GENRES_MEDIA_ITEM_ID = "[genres]"
        private const val PLAYLISTS_MEDIA_ITEM_ID = "[playlists]"

        /**
         * The root media item.
         */
        private val rootMediaItem = buildMediaItem(
            title = "Root",
            mediaId = ROOT_MEDIA_ITEM_ID,
            isPlayable = false,
            isBrowsable = true,
            mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
        )

        /**
         * No permissions media item.
         */
        private val noPermissionsMediaItem = buildMediaItem(
            title = "No permissions",
            mediaId = NO_PERMISSIONS_MEDIA_ITEM_ID,
            isPlayable = false,
            isBrowsable = true,
            mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
        )

        private val noPermissionsDescriptionMediaItem = buildMediaItem(
            title = "The app doesn't have the necessary permissions",
            mediaId = NO_PERMISSIONS_DESCRIPTION_MEDIA_ITEM_ID,
            isPlayable = false,
            isBrowsable = false,
            mediaType = MediaMetadata.MEDIA_TYPE_MIXED,
        )

        /**
         * Albums media item.
         */
        private val albumsMediaItem = buildMediaItem(
            title = "Albums",
            mediaId = ALBUMS_MEDIA_ITEM_ID,
            isPlayable = false,
            isBrowsable = true,
            mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS,
        )

        /**
         * Artists media item.
         */
        private val artistsMediaItem = buildMediaItem(
            title = "Artists",
            mediaId = ARTISTS_MEDIA_ITEM_ID,
            isPlayable = false,
            isBrowsable = true,
            mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS,
        )

        /**
         * Genres media item.
         */
        private val genresMediaItem = buildMediaItem(
            title = "Genres",
            mediaId = GENRES_MEDIA_ITEM_ID,
            isPlayable = false,
            isBrowsable = true,
            mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_GENRES,
        )

        /**
         * Playlists media item.
         */
        private val playlistsMediaItem = buildMediaItem(
            title = "Playlists",
            mediaId = PLAYLISTS_MEDIA_ITEM_ID,
            isPlayable = false,
            isBrowsable = true,
            mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS,
        )

        /**
         * Converts a flow of [RequestStatus] to a one-shot result of [T].
         * Raises an exception on error.
         */
        private suspend fun <T : Any> Flow<RequestStatus<T>>.toOneShotResult() = mapNotNull {
            when (it) {
                is RequestStatus.Loading -> {
                    null
                }

                is RequestStatus.Success -> {
                    it.data
                }

                is RequestStatus.Error -> throw Exception(
                    "Error while loading data, ${it.type}"
                )
            }
        }.first()
    }
}
