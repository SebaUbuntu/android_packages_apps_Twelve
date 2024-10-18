/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.twelve.datasources.subsonic.models

import kotlinx.serialization.Serializable

@Suppress("PROVIDED_RUNTIME_TOO_LOW")
@Serializable
data class ArtistID3(
    val id: String,
    val name: String,
    val coverArt: String? = null,
    val artistImageUrl: UriAsString? = null,
    val albumCount: Int,
    val starred: InstantAsString? = null,

    // OpenSubsonic
    val sortName: String? = null,
)
