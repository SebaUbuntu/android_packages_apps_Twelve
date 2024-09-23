/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.twelve.fragments

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.lineageos.twelve.R
import org.lineageos.twelve.ext.getParcelable
import org.lineageos.twelve.ext.getViewProperty
import org.lineageos.twelve.ext.setProgressCompat
import org.lineageos.twelve.models.Album
import org.lineageos.twelve.models.Playlist
import org.lineageos.twelve.models.RequestStatus
import org.lineageos.twelve.ui.recyclerview.SimpleListAdapter
import org.lineageos.twelve.ui.recyclerview.UniqueItemDiffCallback
import org.lineageos.twelve.ui.views.HorizontalListItem
import org.lineageos.twelve.viewmodels.ArtistViewModel

/**
 * Single artist viewer.
 */
class ArtistFragment : Fragment(R.layout.fragment_artist) {
    // View models
    private val viewModel by viewModels<ArtistViewModel>()

    // Views
    private val albumsLinearLayout by getViewProperty<LinearLayout>(R.id.albumsLinearLayout)
    private val albumsRecyclerView by getViewProperty<RecyclerView>(R.id.albumsRecyclerView)
    private val appBarLayout by getViewProperty<AppBarLayout>(R.id.appBarLayout)
    private val appearsInAlbumLinearLayout by getViewProperty<LinearLayout>(R.id.appearsInAlbumLinearLayout)
    private val appearsInAlbumRecyclerView by getViewProperty<RecyclerView>(R.id.appearsInAlbumRecyclerView)
    private val appearsInPlaylistLinearLayout by getViewProperty<LinearLayout>(R.id.appearsInPlaylistLinearLayout)
    private val appearsInPlaylistRecyclerView by getViewProperty<RecyclerView>(R.id.appearsInPlaylistRecyclerView)
    private val linearProgressIndicator by getViewProperty<LinearProgressIndicator>(R.id.linearProgressIndicator)
    private val noElementsLinearLayout by getViewProperty<LinearLayout>(R.id.noElementsLinearLayout)
    private val thumbnailImageView by getViewProperty<ImageView>(R.id.thumbnailImageView)
    private val toolbar by getViewProperty<MaterialToolbar>(R.id.toolbar)

    // Recyclerview
    private val createAlbumAdapter = {
        object : SimpleListAdapter<Album, HorizontalListItem>(
            UniqueItemDiffCallback(),
            ::HorizontalListItem,
        ) {
            override fun ViewHolder.onPrepareView() {
                view.setOnClickListener {
                    item?.let {
                        findNavController().navigate(
                            R.id.action_artistFragment_to_fragment_album,
                            AlbumFragment.createBundle(it.uri)
                        )
                    }
                }
            }

            override fun ViewHolder.onBindView(item: Album) {
                item.thumbnail?.let {
                    view.setThumbnailImage(it)
                } ?: view.setThumbnailImage(R.drawable.ic_album)

                view.headlineText = item.title
                view.supportingText = item.year?.toString()
            }
        }
    }
    private val albumsAdapter by lazy { createAlbumAdapter() }
    private val appearsInAlbumAdapter by lazy { createAlbumAdapter() }
    private val appearsInPlaylistAdapter by lazy {
        object : SimpleListAdapter<Playlist, HorizontalListItem>(
            UniqueItemDiffCallback(),
            ::HorizontalListItem,
        ) {
            override fun ViewHolder.onPrepareView() {
                view.setThumbnailImage(R.drawable.ic_playlist_play)
                view.setOnClickListener {
                    // TODO
                }
            }

            override fun ViewHolder.onBindView(item: Playlist) {
                view.headlineText = item.name
            }
        }
    }

    // Arguments
    private val artistUri: Uri
        get() = requireArguments().getParcelable(ARG_ARTIST_URI, Uri::class)!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        toolbar.setupWithNavController(findNavController())

        albumsRecyclerView.adapter = albumsAdapter
        appearsInAlbumRecyclerView.adapter = appearsInAlbumAdapter
        appearsInPlaylistRecyclerView.adapter = appearsInPlaylistAdapter

        viewModel.loadAlbum(artistUri)

        loadData()
    }

    override fun onDestroyView() {
        albumsRecyclerView.adapter = null
        appearsInAlbumRecyclerView.adapter = null
        appearsInPlaylistRecyclerView.adapter = null

        super.onDestroyView()
    }

    private fun loadData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.artist.collectLatest {
                    linearProgressIndicator.setProgressCompat(it, true)

                    when (it) {
                        null -> {
                            // Do nothing
                        }

                        is RequestStatus.Loading -> {
                            // Do nothing
                        }

                        is RequestStatus.Success -> {
                            val (artist, artistWorks) = it.data

                            toolbar.title = artist.name

                            launch {
                                thumbnailImageView.setImageBitmap(artist.thumbnail)
                            }

                            albumsAdapter.submitList(artistWorks.albums)
                            appearsInAlbumAdapter.submitList(artistWorks.appearsInAlbum)
                            appearsInPlaylistAdapter.submitList(artistWorks.appearsInPlaylist)

                            val isAlbumsEmpty = artistWorks.albums.isEmpty()
                            albumsLinearLayout.isVisible = !isAlbumsEmpty

                            val isAppearsInAlbumEmpty = artistWorks.appearsInAlbum.isEmpty()
                            appearsInAlbumLinearLayout.isVisible = !isAppearsInAlbumEmpty

                            val isAppearsInPlaylistEmpty = artistWorks.appearsInPlaylist.isEmpty()
                            appearsInPlaylistLinearLayout.isVisible = !isAppearsInPlaylistEmpty

                            val isEmpty = listOf(
                                isAlbumsEmpty,
                                isAppearsInAlbumEmpty,
                                isAppearsInPlaylistEmpty,
                            ).all { isEmpty -> isEmpty }
                            noElementsLinearLayout.isVisible = isEmpty
                        }

                        is RequestStatus.Error -> {
                            if (it.type == RequestStatus.Error.Type.NOT_FOUND) {
                                // Get out of here
                                findNavController().navigateUp()
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val ARG_ARTIST_URI = "artist_uri"

        /**
         * Create a [Bundle] to use as the arguments for this fragment.
         * @param artistUri The URI of the artist to display
         */
        fun createBundle(
            artistUri: Uri,
        ) = bundleOf(
            ARG_ARTIST_URI to artistUri,
        )
    }
}
