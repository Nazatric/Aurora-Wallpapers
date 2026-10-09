package com.auroro.wallpapers.feature.categories

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Forest
import androidx.compose.material.icons.rounded.HdrStrong
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Water
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/** Short real search phrases shared by Home and the topic browser; these are not provider taxonomies. */
data class SearchTopic(val title: String, val query: String, val icon: ImageVector, val accent: Color)

val SEARCH_TOPICS = listOf(
    SearchTopic("Ocean", "ocean water", Icons.Rounded.Water, Color(0xFF58DDF3)),
    SearchTopic("Nature", "nature forest", Icons.Rounded.Forest, Color(0xFF75DEAA)),
    SearchTopic("Sky", "sky clouds", Icons.Rounded.Cloud, Color(0xFF9CCEFF)),
    SearchTopic("Mountains", "mountains landscape", Icons.Rounded.Landscape, Color(0xFFFFC979)),
    SearchTopic("Space", "space stars", Icons.Rounded.NightsStay, Color(0xFFC7B6FF)),
    SearchTopic("Abstract", "abstract", Icons.Rounded.HdrStrong, Color(0xFFFF9DBD)),
    SearchTopic("Architecture", "architecture", Icons.Rounded.Explore, Color(0xFFB8E0D2)),
)
