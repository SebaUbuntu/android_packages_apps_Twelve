/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.twelve.models

import android.net.Uri

/**
 * A music genre.
 * TODO: Maybe make it an enum class and follow https://en.wikipedia.org/wiki/List_of_ID3v1_genres
 *
 * @param uri The URI of the genre
 * @param name The name of the genre. Can be null
 */
data class Genre(
    val uri: Uri,
    val name: String?,
) : UniqueItem {
    override fun areItemsTheSame(other: UniqueItem) =
        UniqueItem.areItemsTheSame(Genre::class, other) {
            this.uri == it.uri
        }

    override fun areContentsTheSame(other: UniqueItem) =
        UniqueItem.areContentsTheSame(Genre::class, other) {
            compareValuesBy(
                this, it,
                Genre::name,
            ) == 0
        }
}
