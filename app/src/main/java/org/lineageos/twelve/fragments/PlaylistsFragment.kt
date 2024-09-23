/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.twelve.fragments

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.lineageos.twelve.R
import org.lineageos.twelve.ext.getViewProperty
import org.lineageos.twelve.ext.setProgressCompat
import org.lineageos.twelve.models.Playlist
import org.lineageos.twelve.models.RequestStatus
import org.lineageos.twelve.ui.dialogs.EditTextMaterialAlertDialogBuilder
import org.lineageos.twelve.ui.recyclerview.SimpleListAdapter
import org.lineageos.twelve.ui.recyclerview.UniqueItemDiffCallback
import org.lineageos.twelve.ui.views.ListItem
import org.lineageos.twelve.viewmodels.PlaylistsViewModel

/**
 * View all music playlists.
 */
class PlaylistsFragment : Fragment(R.layout.fragment_playlists) {
    // View models
    private val viewModel by viewModels<PlaylistsViewModel>()

    // Views
    private val createNewPlaylistButton by getViewProperty<Button>(R.id.createNewPlaylistButton)
    private val linearProgressIndicator by getViewProperty<LinearProgressIndicator>(R.id.linearProgressIndicator)
    private val noElementsLinearLayout by getViewProperty<LinearLayout>(R.id.noElementsLinearLayout)
    private val recyclerView by getViewProperty<RecyclerView>(R.id.recyclerView)

    // Recyclerview
    private val addNewPlaylistItem = Playlist(Uri.EMPTY, "")
    private val adapter = object : SimpleListAdapter<Playlist, ListItem>(
        UniqueItemDiffCallback(),
        ::ListItem,
    ) {
        override fun ViewHolder.onPrepareView() {
            view.setOnClickListener {
                item?.let {
                    when (it === addNewPlaylistItem) {
                        true -> openCreateNewPlaylistDialog()
                        false -> findNavController().navigate(
                            R.id.action_mainFragment_to_fragment_playlist,
                            PlaylistFragment.createBundle(it.uri)
                        )
                    }
                }
            }
        }

        override fun ViewHolder.onBindView(item: Playlist) {
            when (item === addNewPlaylistItem) {
                true -> {
                    view.setLeadingIconImage(R.drawable.ic_playlist_add)
                    view.setHeadlineText(R.string.create_playlist)
                }

                false -> {
                    view.setLeadingIconImage(R.drawable.ic_playlist_play)
                    view.headlineText = item.name
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView.adapter = adapter

        createNewPlaylistButton.setOnClickListener {
            openCreateNewPlaylistDialog()
        }

        loadData()
    }

    override fun onDestroyView() {
        recyclerView.adapter = null

        super.onDestroyView()
    }

    private fun loadData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.playlists.collectLatest {
                    linearProgressIndicator.setProgressCompat(it, true)

                    when (it) {
                        is RequestStatus.Loading -> {
                            // Do nothing
                        }

                        is RequestStatus.Success -> {
                            val isEmpty = it.data.isEmpty()

                            adapter.submitList(
                                when (isEmpty) {
                                    true -> emptyList()
                                    false -> listOf(
                                        addNewPlaylistItem,
                                        *it.data.toTypedArray(),
                                    )
                                }
                            )

                            recyclerView.isVisible = !isEmpty
                            noElementsLinearLayout.isVisible = isEmpty
                        }

                        is RequestStatus.Error -> {
                            Log.e(LOG_TAG, "Failed to load playlists, error: ${it.type}")

                            adapter.submitList(emptyList())

                            recyclerView.isVisible = false
                            noElementsLinearLayout.isVisible = true
                        }
                    }
                }
            }
        }
    }

    private fun openCreateNewPlaylistDialog() {
        EditTextMaterialAlertDialogBuilder(requireContext())
            .setPositiveButton(R.string.create_playlist_confirm) { text ->
                viewModel.createPlaylist(text)
            }
            .setTitle(R.string.create_playlist)
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    companion object {
        private val LOG_TAG = PlaylistsFragment::class.simpleName!!
    }
}
