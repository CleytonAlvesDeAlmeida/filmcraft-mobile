package app.filmcraft.mobile.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import app.filmcraft.mobile.ClipFormat
import app.filmcraft.mobile.EditorViewModel
import app.filmcraft.mobile.ExportState
import app.filmcraft.mobile.core.ClipView
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: EditorViewModel) {
    val context = LocalContext.current
    val view = vm.view
    val selected = vm.selected

    val exoPlayer = remember { ExoPlayer.Builder(context).build() }
    DisposableEffect(Unit) { onDispose { exoPlayer.release() } }

    // Mantém o preview igual à linha do tempo (mesmos cortes que o núcleo calculou).
    LaunchedEffect(view.clips) {
        val items = view.clips.map { c ->
            MediaItem.Builder()
                .setUri(c.uri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(c.inMs)
                        .setEndPositionMs(c.outMs)
                        .build(),
                )
                .build()
        }
        if (items.isEmpty()) {
            exoPlayer.clearMediaItems()
        } else {
            val index = exoPlayer.currentMediaItemIndex.coerceIn(0, items.lastIndex)
            val position = exoPlayer.currentPosition.coerceAtLeast(0L)
            exoPlayer.setMediaItems(items, index, position)
            exoPlayer.prepare()
        }
    }

    // Posição do cursor na linha do tempo, em ms.
    var playheadMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            val clip = vm.view.clips.getOrNull(exoPlayer.currentMediaItemIndex)
            playheadMs = if (clip != null) clip.startMs + exoPlayer.currentPosition.coerceAtLeast(0L) else 0L
            delay(100)
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        if (uris.isNotEmpty()) vm.addVideos(uris)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FilmCraft Mobile") },
                actions = {
                    Button(
                        onClick = { vm.exportVideo() },
                        enabled = view.clips.isNotEmpty() && vm.export !is ExportState.Running,
                        modifier = Modifier.padding(end = 8.dp),
                    ) { Text("Exportar") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Preview
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (view.clips.isEmpty()) {
                    Text("Adicione vídeos para começar", color = Color.Gray)
                } else {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                this.player = exoPlayer
                                useController = true
                            }
                        },
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Cursor ${ClipFormat.clock(playheadMs)}", style = MaterialTheme.typography.labelLarge)
                Text(
                    "Total ${view.totalTimecode} · ${view.frameRate} fps",
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            // Linha do tempo
            if (view.clips.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(view.clips, key = { it.id }) { clip ->
                        ClipCard(
                            clip = clip,
                            selected = clip.id == vm.selectedId,
                            vm = vm,
                            onClick = {
                                vm.select(clip.id)
                                val index = vm.view.clips.indexOfFirst { it.id == clip.id }
                                if (index >= 0) exoPlayer.seekTo(index, 0L)
                            },
                        )
                    }
                }
            }

            // Corte do clipe selecionado
            if (selected != null) {
                TrimPanel(selected, onTrim = { a, b -> vm.trimSelected(a, b) })
            }

            // Ferramentas
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                }) { Text("+ Vídeo") }
                OutlinedButton(onClick = { vm.splitAt(playheadMs) }, enabled = selected != null) { Text("Dividir") }
                OutlinedButton(onClick = { vm.duplicateSelected() }, enabled = selected != null) { Text("Duplicar") }
                OutlinedButton(onClick = { vm.moveSelected(-1) }, enabled = selected != null) { Text("◀") }
                OutlinedButton(onClick = { vm.moveSelected(1) }, enabled = selected != null) { Text("▶") }
                OutlinedButton(onClick = { vm.toggleMuteSelected() }, enabled = selected != null) {
                    Text(if (selected?.muted == true) "Som: off" else "Som: on")
                }
                OutlinedButton(onClick = { vm.removeSelected() }, enabled = selected != null) { Text("Excluir") }
            }
            Text(
                "\"Som\" vale para a exportação; o preview sempre toca o áudio.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
            )

            ExportStatus(vm)

            vm.message?.let { msg ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(msg, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                    TextButton(onClick = { vm.dismissMessage() }) { Text("OK") }
                }
            }
        }
    }
}

@Composable
private fun ClipCard(clip: ClipView, selected: Boolean, vm: EditorViewModel, onClick: () -> Unit) {
    LaunchedEffect(clip.id, clip.inMs) { vm.loadThumbnail(clip) }
    val thumb = vm.thumbs["${clip.id}:${clip.inMs}"]
    val stroke = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, Color.DarkGray)
    Column(
        Modifier
            .width(120.dp)
            .border(stroke, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(6.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(Color.Black, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (thumb != null) {
                Image(
                    bitmap = thumb.asImageBitmap(),
                    contentDescription = clip.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.size(4.dp))
        Text(clip.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
        Text(
            clip.durationTimecode + if (clip.muted) " 🔇" else "",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray,
        )
    }
}

@Composable
private fun TrimPanel(clip: ClipView, onTrim: (Long, Long) -> Unit) {
    var range by remember(clip.id, clip.inMs, clip.outMs) {
        mutableStateOf(clip.inMs.toFloat()..clip.outMs.toFloat())
    }
    Column {
        Text(
            "Cortar: ${ClipFormat.clock(range.start.toLong())} → ${ClipFormat.clock(range.endInclusive.toLong())}" +
                "  (${ClipFormat.clock((range.endInclusive - range.start).toLong())})",
            style = MaterialTheme.typography.labelLarge,
        )
        RangeSlider(
            value = range,
            onValueChange = { range = it },
            valueRange = 0f..clip.sourceMs.toFloat(),
            onValueChangeFinished = { onTrim(range.start.toLong(), range.endInclusive.toLong()) },
        )
    }
}

@Composable
private fun ExportStatus(vm: EditorViewModel) {
    when (val s = vm.export) {
        ExportState.Idle -> {}
        is ExportState.Running -> Column {
            Text("Exportando… ${(s.progress * 100).toInt()}%")
            LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
        }
        is ExportState.Done -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Pronto! Salvo em Filmes/FilmCraft.", modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.dismissExport() }) { Text("OK") }
        }
        is ExportState.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Falha: ${s.message}", color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.dismissExport() }) { Text("OK") }
        }
    }
}
