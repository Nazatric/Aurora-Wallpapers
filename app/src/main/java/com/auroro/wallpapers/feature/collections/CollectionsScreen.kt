package com.auroro.wallpapers.feature.collections

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as columnItems
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.auroro.wallpapers.core.database.CollectionSummary
import com.auroro.wallpapers.core.design.Aero
import com.auroro.wallpapers.core.design.EmptyState
import com.auroro.wallpapers.core.design.GlassIconButton
import com.auroro.wallpapers.core.design.GlassSurface
import com.auroro.wallpapers.core.design.HeaderBar
import com.auroro.wallpapers.core.design.SectionTitle
import com.auroro.wallpapers.core.model.Wallpaper
import com.auroro.wallpapers.feature.home.WallpaperTile

@Composable
fun CollectionsScreen(
    collections: List<CollectionSummary>,
    onMenu: () -> Unit,
    onCreate: (String) -> Unit,
    onOpen: (CollectionSummary) -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        item {
            HeaderBar("Collections", "Your saved places, kept on this device", onMenu = onMenu, trailing = {
                GlassIconButton(onClick = { creating = true }, description = "Create collection", icon = Icons.Rounded.Add)
            })
        }
        if (collections.isEmpty()) {
            item {
                EmptyState(
                    "No collections yet",
                    "Create a collection, then add wallpapers from their detail pages. Collections stay on this device.",
                    icon = Icons.Rounded.CollectionsBookmark,
                    action = { Button(onClick = { creating = true }) { Icon(Icons.Rounded.Add, null); Text("  New collection") } },
                )
            }
        } else {
            item { SectionTitle("Your collections", "${collections.size} collection${if (collections.size == 1) "" else "s"}") }
            columnItems(collections, key = { it.id }) { collection ->
                CollectionCard(collection, onOpen = { onOpen(collection) }, onRename = { name -> onRename(collection.id, name) }, onDelete = { onDelete(collection.id) })
            }
            item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TextButton(onClick = { creating = true }) { Icon(Icons.Rounded.Add, null); Text("  New collection") } } }
        }
    }

    if (creating) NameDialog("New collection", "Create", "", onDismiss = { creating = false }, onSubmit = { name -> creating = false; onCreate(name) })
}

@Composable
private fun CollectionCard(collection: CollectionSummary, onOpen: () -> Unit, onRename: (String) -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]
    val createdDate = remember(collection.createdAt, locale) {
        java.text.SimpleDateFormat("MMM d, yyyy", locale).format(java.util.Date(collection.createdAt))
    }
    GlassSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(23.dp), onClick = onOpen) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(86.dp).clip(RoundedCornerShape(17.dp))
                    .background(Brush.verticalGradient(listOf(Aero.colors.accent.copy(alpha = .26f), Aero.colors.emerald.copy(alpha = .15f)))),
            ) {
                val covers = listOfNotNull(collection.cover1, collection.cover2, collection.cover3, collection.cover4)
                if (covers.isEmpty()) {
                    Icon(Icons.Rounded.CollectionsBookmark, null, tint = Aero.colors.accentLight, modifier = Modifier.align(Alignment.Center).size(30.dp))
                } else {
                    Column(Modifier.fillMaxSize().padding(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            covers.take(2).forEach { url -> CoverCell(url, Modifier.weight(1f).fillMaxSize()) }
                        }
                        if (covers.size > 2) {
                            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                covers.drop(2).take(2).forEach { url -> CoverCell(url, Modifier.weight(1f).fillMaxSize()) }
                            }
                        }
                    }
                }
            }
            Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
                Text(collection.name, style = MaterialTheme.typography.titleMedium, color = Aero.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${collection.itemCount} wallpaper${if (collection.itemCount == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall, color = Aero.colors.textSecondary)
                Spacer(Modifier.height(8.dp))
                Text("Created $createdDate", style = MaterialTheme.typography.labelSmall, color = Aero.colors.textTertiary)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Collection options", tint = Aero.colors.textSecondary) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { menu = false; rename = true })
                    DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null) }, onClick = { menu = false; delete = true })
                }
            }
        }
    }
    if (rename) NameDialog("Rename collection", "Save", collection.name, { rename = false }, { name -> rename = false; onRename(name) })
    if (delete) ConfirmDialog("Delete “${collection.name}”?", "This removes the collection and its memberships, but not the saved wallpaper files.", { delete = false }, { delete = false; onDelete() })
}

@Composable
private fun CoverCell(url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(Aero.colors.surfaceSolid)) {
        AsyncImage(
            ImageRequest.Builder(context).data(url).crossfade(120).build(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
fun CollectionDetailScreen(
    collection: CollectionSummary?,
    wallpapers: List<Wallpaper>,
    onBack: () -> Unit,
    onOpen: (Wallpaper) -> Unit,
    onAddMore: () -> Unit,
) {
    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        HeaderBar(
            title = collection?.name ?: "Collection",
            subtitle = collection?.let { "${it.itemCount} saved wallpaper${if (it.itemCount == 1) "" else "s"}" },
            onBack = onBack,
            trailing = { GlassIconButton(onClick = onAddMore, description = "Find wallpapers to add", icon = Icons.Rounded.Add) },
        )
        if (wallpapers.isEmpty()) {
            EmptyState("Collection is empty", "Add a wallpaper from its detail page, or find one to save here.", icon = Icons.Rounded.PhotoLibrary, action = {
                Button(onClick = onAddMore) { Text("Find wallpapers") }
            })
        } else {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Adaptive(152.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(15.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalItemSpacing = 11.dp,
            ) {
                items(wallpapers, key = { it.key }) { wallpaper ->
                    WallpaperTile(wallpaper, onClick = { onOpen(wallpaper) })
                }
            }
        }
    }
}

@Composable
fun AddToCollectionDialog(
    collections: List<CollectionSummary>,
    memberships: Set<Long>,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
    onToggle: (Long, Boolean) -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.CollectionsBookmark, null, tint = Aero.colors.accent) },
        title = { Text("Add to collection", color = Aero.colors.textPrimary) },
        text = {
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (collections.isEmpty()) Text("Create a collection to keep this wallpaper close.", color = Aero.colors.textSecondary)
                collections.forEach { collection ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onToggle(collection.id, collection.id in memberships) }.padding(horizontal = 3.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = collection.id in memberships, onCheckedChange = { onToggle(collection.id, collection.id in memberships) })
                        Text(collection.name, style = MaterialTheme.typography.bodyMedium, color = Aero.colors.textPrimary, modifier = Modifier.weight(1f))
                        Text(collection.itemCount.toString(), style = MaterialTheme.typography.labelSmall, color = Aero.colors.textTertiary)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { creating = true }) { Text("New collection", color = Aero.colors.accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Done", color = Aero.colors.textSecondary) } },
        containerColor = Aero.colors.surfaceSolid,
    )
    if (creating) NameDialog("New collection", "Create", "", { creating = false }, { name -> creating = false; onCreate(name) })
}

@Composable
private fun NameDialog(title: String, confirm: String, initial: String, onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var name by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = Aero.colors.textPrimary) },
        text = { OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, label = { Text("Name") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onSubmit(name) }) { Text(confirm, color = Aero.colors.accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Aero.colors.textSecondary) } },
        containerColor = Aero.colors.surfaceSolid,
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmText: String = "Delete",
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = Aero.colors.textPrimary) },
        text = { Text(message, color = Aero.colors.textSecondary) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmText, color = Aero.colors.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Aero.colors.textSecondary) } },
        containerColor = Aero.colors.surfaceSolid,
    )
}
