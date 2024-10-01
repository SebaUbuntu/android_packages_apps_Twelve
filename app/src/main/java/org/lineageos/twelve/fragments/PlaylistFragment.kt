/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.twelve.fragments

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.lineageos.twelve.R
import org.lineageos.twelve.ext.getParcelable
import org.lineageos.twelve.ext.getViewProperty
import org.lineageos.twelve.ext.setProgressCompat
import org.lineageos.twelve.models.Audio
import org.lineageos.twelve.models.RequestStatus
import org.lineageos.twelve.ui.dialogs.EditTextMaterialAlertDialogBuilder
import org.lineageos.twelve.ui.recyclerview.SimpleListAdapter
import org.lineageos.twelve.ui.recyclerview.UniqueItemDiffCallback
import org.lineageos.twelve.ui.views.ListItem
import org.lineageos.twelve.utils.PermissionsChecker
import org.lineageos.twelve.utils.PermissionsUtils
import org.lineageos.twelve.utils.TimestampFormatter
import org.lineageos.twelve.viewmodels.PlaylistViewModel

/**
 * Single playlist viewer.
 */
@AndroidEntryPoint
class PlaylistFragment : Fragment(R.layout.fragment_playlist) {
    // View models
    private val viewModel by viewModels<PlaylistViewModel>()

    // Views
    private val linearProgressIndicator by getViewProperty<LinearProgressIndicator>(R.id.linearProgressIndicator)
    private val noElementsNestedScrollView by getViewProperty<NestedScrollView>(R.id.noElementsNestedScrollView)
    private val playlistNameTextView by getViewProperty<TextView>(R.id.playlistNameTextView)
    private val recyclerView by getViewProperty<RecyclerView>(R.id.recyclerView)
    private val toolbar by getViewProperty<MaterialToolbar>(R.id.toolbar)
    private val tracksInfoTextView by getViewProperty<TextView>(R.id.tracksInfoTextView)

    // Recyclerview
    private val adapter by lazy {
        object : SimpleListAdapter<Audio, ListItem>(
            UniqueItemDiffCallback(),
            ::ListItem,
        ) {
            override fun ViewHolder.onPrepareView() {
                view.setLeadingIconImage(R.drawable.ic_music_note)
                view.setOnClickListener {
                    item?.let {
                        viewModel.playAudio(currentList, bindingAdapterPosition)

                        findNavController().navigate(
                            R.id.action_playlistFragment_to_fragment_now_playing
                        )
                    }
                }
                view.setOnLongClickListener {
                    item?.let {
                        findNavController().navigate(
                            R.id.action_playlistFragment_to_fragment_audio_bottom_sheet_dialog,
                            AudioBottomSheetDialogFragment.createBundle(
                                it.uri,
                                playlistUri = playlistUri,
                            )
                        )
                    }

                    true
                }
            }

            override fun ViewHolder.onBindView(item: Audio) {
                view.headlineText = item.title
                view.supportingText = item.artistName
                view.trailingSupportingText = TimestampFormatter.formatTimestampMillis(
                    item.durationMs
                )
            }
        }
    }

    // Arguments
    private val playlistUri: Uri
        get() = requireArguments().getParcelable(ARG_PLAYLIST_URI, Uri::class)!!

    // Permissions
    private val permissionsChecker = PermissionsChecker(
        this, PermissionsUtils.mainPermissions
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Insets
        ViewCompat.setOnApplyWindowInsetsListener(recyclerView) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

            v.updatePadding(
                left = insets.left,
                right = insets.right,
                bottom = insets.bottom,
            )

            windowInsets
        }

        ViewCompat.setOnApplyWindowInsetsListener(noElementsNestedScrollView) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

            v.updatePadding(
                left = insets.left,
                right = insets.right,
                bottom = insets.bottom,
            )

            windowInsets
        }

        toolbar.setupWithNavController(findNavController())
        toolbar.inflateMenu(R.menu.fragment_podcast_toolbar)
        toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.renamePlaylist -> {
                    showRenamePlaylistAlertDialog()
                    true
                }

                R.id.deletePlaylist -> {
                    showDeletePlaylistAlertDialog()
                    true
                }

                else -> false
            }
        }

        recyclerView.adapter = adapter

        viewModel.loadPlaylist(playlistUri)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                permissionsChecker.withPermissionsGranted {
                    loadData()
                }
            }
        }
    }

    override fun onDestroyView() {
        recyclerView.adapter = null

        super.onDestroyView()
    }

    private suspend fun loadData() {
        viewModel.playlist.collectLatest {
            linearProgressIndicator.setProgressCompat(it, true)

            when (it) {
                is RequestStatus.Loading -> {
                    // Do nothing
                }

                is RequestStatus.Success -> {
                    val (playlist, audios) = it.data

                    toolbar.title = playlist.name
                    playlistNameTextView.text = playlist.name

                    val totalDurationMs = audios.sumOf { audio ->
                        audio?.durationMs ?: 0
                    }
                    val totalDurationMinutes = totalDurationMs / 1000 / 60

                    val tracksCount = resources.getQuantityString(
                        R.plurals.tracks_count,
                        audios.size,
                        audios.size
                    )
                    val tracksDuration = resources.getQuantityString(
                        R.plurals.tracks_duration,
                        totalDurationMinutes,
                        totalDurationMinutes
                    )
                    tracksInfoTextView.text = getString(
                        R.string.tracks_info,
                        tracksCount, tracksDuration
                    )

                    adapter.submitList(audios)

                    val isEmpty = audios.isEmpty()
                    recyclerView.isVisible = !isEmpty
                    noElementsNestedScrollView.isVisible = isEmpty
                }

                is RequestStatus.Error -> {
                    Log.e(LOG_TAG, "Error loading playlist, error: ${it.type}")

                    toolbar.title = ""
                    playlistNameTextView.text = ""

                    adapter.submitList(listOf())

                    recyclerView.isVisible = false
                    noElementsNestedScrollView.isVisible = true

                    if (it.type == RequestStatus.Error.Type.NOT_FOUND) {
                        // Get out of here
                        findNavController().navigateUp()
                    }
                }
            }
        }
    }

    private fun showRenamePlaylistAlertDialog() {
        EditTextMaterialAlertDialogBuilder(requireContext())
            .setText(toolbar.title.toString())
            .setPositiveButton(R.string.rename_playlist_positive) { text ->
                viewModel.renamePlaylist(text)
            }
            .setTitle(R.string.rename_playlist)
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showDeletePlaylistAlertDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_playlist)
            .setMessage(R.string.delete_playlist_message)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                viewModel.deletePlaylist()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    companion object {
        private val LOG_TAG = PlaylistFragment::class.simpleName!!

        private const val ARG_PLAYLIST_URI = "playlist_uri"

        /**
         * Create a [Bundle] to use as the arguments for this fragment.
         * @param playlistUri The URI of the playlist to display
         */
        fun createBundle(
            playlistUri: Uri,
        ) = bundleOf(
            ARG_PLAYLIST_URI to playlistUri,
        )
    }
}
