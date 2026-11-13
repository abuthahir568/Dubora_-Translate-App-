package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.domain.model.LocalMovie
import com.example.ui.components.formatDurationMs
import com.example.ui.components.formatFileSize
import com.example.ui.theme.CharcoalBorder
import com.example.ui.theme.CharcoalCard
import com.example.ui.theme.CharcoalElevated
import com.example.ui.theme.CharcoalSurface
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.InkPrimary
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.RoseAlert
import java.io.File

@Composable
fun MyMoviesScreen(
    movies: List<LocalMovie>,
    onImportMovie: () -> Unit,
    onPlayMovie: (LocalMovie) -> Unit,
    onRemoveMovie: (LocalMovie) -> Unit,
    modifier: Modifier = Modifier
) {
    var search by remember { mutableStateOf("") }
    val filtered = remember(movies, search) {
        if (search.isBlank()) movies
        else movies.filter {
            it.title.contains(search, ignoreCase = true) ||
                it.fileName.contains(search, ignoreCase = true)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBlack),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "My Movies",
                        style = MaterialTheme.typography.headlineMedium,
                        color = InkPrimary
                    )
                    Text(
                        text = "${movies.size} local video file(s) • MP4, MOV, MKV",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkSecondary
                    )
                }
                Button(
                    onClick = onImportMovie,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricViolet,
                        contentColor = InkPrimary
                    ),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("import_movie_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import")
                }
            }
        }

        if (movies.isNotEmpty()) {
            item {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Filter local library...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = InkSecondary) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricViolet,
                        unfocusedBorderColor = CharcoalBorder,
                        focusedContainerColor = CharcoalSurface,
                        unfocusedContainerColor = CharcoalSurface,
                        focusedTextColor = InkPrimary,
                        unfocusedTextColor = InkPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (filtered.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = CharcoalSurface,
                    border = BorderStroke(1.dp, CharcoalBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_empty_cinema),
                            contentDescription = "Empty library cinema",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(168.dp)
                                .clip(RoundedCornerShape(16.dp))
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Icon(
                            Icons.Rounded.Movie,
                            contentDescription = null,
                            tint = ElectricViolet,
                            modifier = Modifier.size(30.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (movies.isEmpty()) "No Movies Imported Yet" else "No Matching Movies",
                            style = MaterialTheme.typography.titleLarge,
                            color = InkPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Browse your Android storage to import MP4, MOV, or MKV videos. Dubora AI keeps all video files strictly on your device.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = InkSecondary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onImportMovie,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet)
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Browse Phone Storage")
                        }
                    }
                }
            }
        } else {
            items(filtered, key = { it.id }) { movie ->
                val progress = if (movie.durationMs > 0L) {
                    (movie.lastPositionMs.toFloat() / movie.durationMs.toFloat()).coerceIn(0f, 1f)
                } else 0f

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = CharcoalSurface,
                    border = BorderStroke(1.dp, CharcoalBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { onPlayMovie(movie) }
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(116.dp)
                                    .height(74.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(CharcoalElevated)
                            ) {
                                if (!movie.thumbnailPath.isNullOrBlank() && File(movie.thumbnailPath).exists()) {
                                    AsyncImage(
                                        model = File(movie.thumbnailPath),
                                        contentDescription = movie.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Image(
                                        painter = painterResource(id = R.drawable.img_empty_cinema),
                                        contentDescription = movie.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(ObsidianBlack.copy(alpha = 0.72f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Rounded.PlayArrow,
                                        contentDescription = "Play ${movie.title}",
                                        tint = InkPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = movie.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = InkPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = movie.fileName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = InkSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    MetadataChip(formatFileSize(movie.sizeBytes))
                                    MetadataChip(formatDurationMs(movie.durationMs))
                                    if (movie.hasCachedDub && !movie.cachedDubLang.isNullOrBlank()) {
                                        MetadataChip("DUB ${movie.cachedDubLang.uppercase()}", highlight = true)
                                    }
                                }
                            }
                            IconButton(
                                onClick = { onRemoveMovie(movie) },
                                modifier = Modifier.testTag("remove_movie_button_${movie.id}")
                            ) {
                                Icon(
                                    Icons.Rounded.DeleteOutline,
                                    contentDescription = "Remove ${movie.title}",
                                    tint = RoseAlert.copy(alpha = 0.85f)
                                )
                            }
                        }
                        if (progress > 0.01f) {
                            LinearProgressIndicator(
                                progress = { progress },
                                color = ElectricViolet,
                                trackColor = CharcoalElevated,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetadataChip(text: String, highlight: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (highlight) ElectricViolet.copy(alpha = 0.22f) else CharcoalCard
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (highlight) CyanPulse else InkSecondary,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
