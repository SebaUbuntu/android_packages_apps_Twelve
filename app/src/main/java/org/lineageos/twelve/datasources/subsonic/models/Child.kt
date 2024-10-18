/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.twelve.datasources.subsonic.models

import kotlinx.serialization.Serializable

@Suppress("PROVIDED_RUNTIME_TOO_LOW")
@Serializable
data class Child(
    val id: String,
    val parent: String? = null,
    val isDir: Boolean,
    val title: String,
    val album: String? = null,
    val artist: String? = null,
    val track: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
    val coverArt: String? = null,
    val size: Long? = null,
    val contentType: String? = null,
    val suffix: String? = null,
    val transcodedContentType: String? = null,
    val transcodedSuffix: String? = null,
    val duration: Int? = null,
    val bitRate: Int? = null,
    val path: String? = null,
    val isVideo: Boolean? = null,
    val userRating: UserRating? = null,
    val averageRating: AverageRating? = null,
    val playCount: Long? = null,
    val discNumber: Int? = null,
    val created: InstantAsString? = null,
    val starred: InstantAsString? = null,
    val albumId: String? = null,
    val artistId: String? = null,
    val type: MediaType? = null,
    val bookmarkPosition: Long? = null,
    val originalWidth: Int? = null,
    val originalHeight: Int? = null,

    // OpenSubsonic
    val played: String? = null,
    val sortName: String? = null,
)
