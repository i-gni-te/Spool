package com.example.spool.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.spool.data.MediaClassifier
import com.example.spool.data.MediaItemEntity
import com.example.spool.data.MediaScanner
import com.example.spool.data.ResumeVideo
import com.example.spool.domain.EpisodeEntry
import com.example.spool.domain.PlayScope
import com.example.spool.domain.Series
import com.example.spool.domain.displayTitle
import com.example.spool.ui.theme.Ember
import com.example.spool.ui.theme.SpoolGradient
import com.example.spool.domain.SongSort
enum class VideoFilter(val label: String) {
    ALL("All"),
    MUSIC("Music videos"),
    MOVIES("Movies"),
    SHOWS("TV shows"),
    SHORTS("Shorts"),
    FAVORITES("Favorites")
}

private const val PREVIEW_COUNT = 4

@Composable
fun VideosScreen(vm: MusicViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val query by vm.query.collectAsState()
    val music by vm.videos.collectAsState()
    val movies by vm.movies.collectAsState()
    val series by vm.series.collectAsState()
    val shorts by vm.shorts.collectAsState()
    val fresh by vm.newVideos.collectAsState()
    val resume by vm.continueWatching.collectAsState()
    val favVideos by vm.favoriteVideos.collectAsState()
    val total by vm.libraryCount.collectAsState()
    val message by vm.message.collectAsState()
    val playing by vm.playerState.collectAsState()
    val focus = LocalFocusManager.current

    var filterName by rememberSaveable { mutableStateOf(VideoFilter.ALL.name) }
    var openSeries by rememberSaveable { mutableStateOf<String?>(null) }
    val filter = VideoFilter.valueOf(filterName)

    // A show's own page: seasons and episodes
    val detail = series.firstOrNull { it.key == openSeries }
    if (detail != null) {
        BackHandler { openSeries = null }
        SeriesDetail(detail, playing.uri, vm, { openSeries = null }, modifier)
        return
    }

    val episodeCount = series.sumOf { it.episodes.size }
    val videoCount = music.size + movies.size + episodeCount + shorts.size

    fun playVideo(uri: String) {
        vm.play(startUri = uri)
        focus.clearFocus()
    }

    fun openShort(uri: String) {
        vm.openShortsFeed(uri)
        focus.clearFocus()
    }

    fun shuffle(scope: PlayScope, sort: SongSort = SongSort.AZ) {
        vm.play(scope = scope, sort = sort)
        focus.clearFocus()
    }

    fun shuffleCurrent() {
        when (filter) {
            VideoFilter.ALL -> shuffle(PlayScope.ANY_VIDEO)
            VideoFilter.MUSIC -> shuffle(PlayScope.VIDEOS)
            VideoFilter.MOVIES -> shuffle(PlayScope.MOVIES)
            VideoFilter.SHOWS -> shuffle(PlayScope.SHOWS)
            VideoFilter.SHORTS -> {
                vm.openShortsFeedShuffled()
                focus.clearFocus()
            }
            VideoFilter.FAVORITES -> shuffle(PlayScope.ANY_VIDEO, SongSort.FAVORITES)
        }
    }

    // Movies and episodes left part-way, for the filter that's showing
    val resumeShown = remember(resume, filter) {
        resume.filter {
            when (filter) {
                VideoFilter.ALL -> true
                VideoFilter.MOVIES -> it.item.kind == MediaClassifier.MOVIE
                VideoFilter.SHOWS -> it.item.kind == MediaClassifier.SHOW
                else -> false
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ScreenHeader(
                title = "Videos",
                count = "$videoCount videos",
                onReport = {
                    shareReport(context, (total - videoCount).coerceAtLeast(0), videoCount)
                }
            )
        }
        item {
            SearchField(
                query = query,
                onQueryChange = vm::onQueryChange,
                placeholder = "search video",
                onGo = { shuffleCurrent() }
            )
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(VideoFilter.entries.toList(), key = { it.name }) { option ->
                    FilterChip(
                        selected = filter == option,
                        onClick = { filterName = option.name },
                        label = { Text(option.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Ember.copy(alpha = 0.18f),
                            selectedLabelColor = Ember
                        )
                    )
                }
            }
        }
        message?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }

        if (videoCount == 0) {
            item {
                Text(
                    if (total == 0) "No videos found yet" else "No videos match",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                )
            }
        }

        // Continue watching
        if (query.isBlank() && resumeShown.isNotEmpty()) {
            item { SectionHeader("Continue watching", resumeShown.size) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(resumeShown, key = { "c:" + it.item.uri }) { entry ->
                        ContinueCard(entry) { playVideo(entry.item.uri) }
                    }
                }
            }
        }

        // New downloads, newest first
        if (query.isBlank() && filter == VideoFilter.ALL && fresh.isNotEmpty()) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(start = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "New",
                        style = MaterialTheme.typography.titleLarge,
                        color = Ember,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { vm.dismissNew() }) { Text("Clear") }
                }
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(fresh, key = { "n:" + it.uri }) { video ->
                        NewVideoCard(video, vm) {
                            if (video.kind == MediaClassifier.SHORT) openShort(video.uri)
                            else playVideo(video.uri)
                        }
                    }
                }
            }
        }

        when (filter) {
            VideoFilter.ALL -> {
                if (shorts.isNotEmpty()) {
                    item {
                        SectionHeader("Shorts", shorts.size, "Shuffle") {
                            vm.openShortsFeedShuffled()
                            focus.clearFocus()
                        }
                    }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(shorts, key = { "sh:" + it.uri }) { short ->
                                ShortTile(short, vm, Modifier.width(112.dp)) { openShort(short.uri) }
                            }
                        }
                    }
                }
                if (series.isNotEmpty()) {
                    item {
                        SectionHeader("TV shows", series.size, seeAll = "See all") {
                            filterName = VideoFilter.SHOWS.name
                        }
                    }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(series, key = { "se:" + it.key }) { show ->
                                SeriesCard(show) { openSeries = show.key }
                            }
                        }
                    }
                }
                if (movies.isNotEmpty()) {
                    item { SectionHeader("Movies", movies.size, "Shuffle") { shuffle(PlayScope.MOVIES) } }
                    items(movies.take(PREVIEW_COUNT), key = { "mv:" + it.uri }) { movie ->
                        VideoCard(movie, playing.uri == movie.uri, vm) { playVideo(movie.uri) }
                    }
                    if (movies.size > PREVIEW_COUNT) {
                        item {
                            TextButton(onClick = { filterName = VideoFilter.MOVIES.name }) {
                                Text("See all ${movies.size} movies")
                            }
                        }
                    }
                }
                if (music.isNotEmpty()) {
                    item { SectionHeader("Music videos", music.size, "Shuffle") { shuffle(PlayScope.VIDEOS) } }
                    items(music.take(PREVIEW_COUNT + 2), key = { "v:" + it.uri }) { video ->
                        VideoCard(video, playing.uri == video.uri, vm) { playVideo(video.uri) }
                    }
                    if (music.size > PREVIEW_COUNT + 2) {
                        item {
                            TextButton(onClick = { filterName = VideoFilter.MUSIC.name }) {
                                Text("See all ${music.size} music videos")
                            }
                        }
                    }
                }
            }

            VideoFilter.MUSIC -> {
                if (music.isNotEmpty()) {
                    item { SectionHeader("Music videos", music.size, "Shuffle") { shuffle(PlayScope.VIDEOS) } }
                    items(music, key = { "v:" + it.uri }) { video ->
                        VideoCard(video, playing.uri == video.uri, vm) { playVideo(video.uri) }
                    }
                } else {
                    item { EmptyHint("No music videos here. Use ⋮ > Move to… to move a video in.") }
                }
            }

            VideoFilter.MOVIES -> {
                if (movies.isNotEmpty()) {
                    item { SectionHeader("Movies", movies.size, "Shuffle") { shuffle(PlayScope.MOVIES) } }
                    items(movies, key = { "mv:" + it.uri }) { movie ->
                        VideoCard(movie, playing.uri == movie.uri, vm) { playVideo(movie.uri) }
                    }
                } else {
                    item { EmptyHint("No movies found. A video over about 70 minutes counts as a movie, or use ⋮ > Move to….") }
                }
            }

            VideoFilter.SHOWS -> {
                if (series.isNotEmpty()) {
                    item { SectionHeader("TV shows", series.size) }
                    items(series, key = { "se:" + it.key }) { show ->
                        SeriesRow(show) { openSeries = show.key }
                    }
                } else {
                    item { EmptyHint("No TV shows found. Files named like S01E02 or kept in a Series folder show up here.") }
                }
            }

            VideoFilter.SHORTS -> {
                if (shorts.isNotEmpty()) {
                    item {
                        SectionHeader("Shorts", shorts.size, "Shuffle") {
                            vm.openShortsFeedShuffled()
                            focus.clearFocus()
                        }
                    }
                    items(shorts.chunked(3), key = { "shr:" + it.first().uri }) { rowItems ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            rowItems.forEach { short ->
                                ShortTile(short, vm, Modifier.weight(1f)) { openShort(short.uri) }
                            }
                            repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                } else {
                    item { EmptyHint("No shorts found.") }
                }
            }
            VideoFilter.FAVORITES -> {
                val favShorts = favVideos.filter { it.kind == MediaClassifier.SHORT }
                val favOthers = favVideos.filter { it.kind != MediaClassifier.SHORT }
                if (favVideos.isEmpty()) {
                    item { EmptyHint("No favorite videos yet. Use ⋮ > Add to favorites on any video.") }
                } else {
                    if (favOthers.isNotEmpty()) {
                        item {
                            SectionHeader("Favorites", favOthers.size, "Shuffle") {
                                shuffle(PlayScope.ANY_VIDEO, SongSort.FAVORITES)
                            }
                        }
                        items(favOthers, key = { "fv:" + it.uri }) { video ->
                            VideoCard(video, playing.uri == video.uri, vm) {
                                vm.play(startUri = video.uri, sort = SongSort.FAVORITES)
                                focus.clearFocus()
                            }
                        }
                    }
                    if (favShorts.isNotEmpty()) {
                        item { SectionHeader("Favorite shorts", favShorts.size) }
                        item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(favShorts, key = { "fs:" + it.uri }) { short ->
                                    ShortTile(short, vm, Modifier.width(112.dp)) { openShort(short.uri) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
    )
}

/** A section title with a count, and optionally a Shuffle or See all button. */
@Composable
private fun SectionHeader(
    title: String,
    count: Int,
    action: String? = null,
    seeAll: String? = null,
    onAction: () -> Unit = {}
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.size(8.dp))
        Text(
            "$count",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        if (action != null) {
            TextButton(onClick = onAction) {
                Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text(action)
            }
        } else if (seeAll != null) {
            TextButton(onClick = onAction) { Text(seeAll) }
        }
    }
}

@Composable
private fun ShortTile(
    short: MediaItemEntity,
    vm: MusicViewModel,
    modifier: Modifier,
    onOpen: () -> Unit
) {
    Box(modifier.aspectRatio(9f / 16f)) {
        MediaThumb(
            uri = short.uri,
            seed = short.title,
            isVideo = true,
            modifier = Modifier.fillMaxSize(),
            corner = 18.dp,
            sizePx = 480,
            badge = formatDuration(short.durationMs),
            onClick = onOpen
        )
        ItemMenuButton(
            item = short,
            vm = vm,
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
            tint = Color.White,
            compact = true
        )
    }
}

/** A movie or episode left part-way: thumbnail, a progress bar, and the time left. */
@Composable
private fun ContinueCard(entry: ResumeVideo, onClick: () -> Unit) {
    val fraction = (entry.positionMs.toFloat() / entry.watchDurationMs.coerceAtLeast(1L))
        .coerceIn(0f, 1f)
    val left = (entry.watchDurationMs - entry.positionMs).coerceAtLeast(0L)

    Column(
        Modifier
            .width(210.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Box {
            MediaThumb(
                uri = entry.item.uri,
                seed = entry.item.title,
                isVideo = true,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                corner = 14.dp,
                sizePx = 420
            )
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(SpoolGradient))
            }
        }
        Text(
            entry.item.displayTitle(),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )
        Text(
            "${formatDuration(left)} left",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SeriesCard(show: Series, onClick: () -> Unit) {
    Column(
        Modifier
            .width(190.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        MediaThumb(
            uri = show.episodes.first().item.uri,
            seed = show.name,
            isVideo = true,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            corner = 14.dp,
            sizePx = 420
        )
        Text(
            show.name,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )
        Text(
            "${show.episodes.size} episodes",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SeriesRow(show: Series, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MediaThumb(
            uri = show.episodes.first().item.uri,
            seed = show.name,
            isVideo = true,
            modifier = Modifier.width(132.dp).aspectRatio(16f / 9f),
            corner = 12.dp,
            sizePx = 420
        )
        Column(Modifier.weight(1f)) {
            Text(
                show.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${show.seasonCount} ${if (show.seasonCount == 1) "season" else "seasons"} · " +
                        "${show.episodes.size} episodes",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** A show's page: Play from the start, then Season headers with episode rows. */
@Composable
private fun SeriesDetail(
    series: Series,
    playingUri: String,
    vm: MusicViewModel,
    onBack: () -> Unit,
    modifier: Modifier
) {
    val focus = LocalFocusManager.current
    val seasons = remember(series) { series.episodes.groupBy { it.season }.toSortedMap() }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        series.name,
                        style = MaterialTheme.typography.headlineMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${series.seasonCount} ${if (series.seasonCount == 1) "season" else "seasons"} · " +
                                "${series.episodes.size} episodes",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item {
            StartPill("Play from the start") {
                vm.play(startUri = series.episodes.first().item.uri)
                focus.clearFocus()
            }
        }
        seasons.forEach { (season, entries) ->
            item(key = "season:$season") {
                Text(
                    "Season $season",
                    style = MaterialTheme.typography.titleLarge,
                    color = Ember,
                    modifier = Modifier.padding(start = 4.dp, top = 12.dp)
                )
            }
            items(entries, key = { "ep:" + it.item.uri }) { entry ->
                EpisodeRow(entry, current = entry.item.uri == playingUri, vm = vm) {
                    vm.play(startUri = entry.item.uri)
                    focus.clearFocus()
                }
            }
        }
    }
}

@Composable
private fun StartPill(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(CircleShape)
            .background(SpoolGradient)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = com.example.spool.ui.theme.Ink)
        Spacer(Modifier.size(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.titleLarge,
            color = com.example.spool.ui.theme.Ink
        )
    }
}

@Composable
private fun EpisodeRow(
    entry: EpisodeEntry,
    current: Boolean,
    vm: MusicViewModel,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (current) Ember.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
            )
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MediaThumb(
            uri = entry.item.uri,
            seed = entry.item.title,
            isVideo = true,
            modifier = Modifier.width(120.dp).aspectRatio(16f / 9f),
            corner = 10.dp,
            sizePx = 360,
            badge = formatDuration(entry.item.durationMs)
        )
        Column(Modifier.weight(1f)) {
            Text(
                if (entry.episode > 0) "Episode ${entry.episode}" else entry.item.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (current) Ember else Color.Unspecified,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                entry.item.title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        ItemMenuButton(item = entry.item, vm = vm)
    }
}

@Composable
private fun NewVideoCard(video: MediaItemEntity, vm: MusicViewModel, onClick: () -> Unit) {
    var renaming by remember { mutableStateOf(false) }
    val needsName = remember(video.title) { MediaClassifier.looksRandom(video.title) }

    Column(
        Modifier
            .width(160.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        MediaThumb(
            uri = video.uri,
            seed = video.title,
            isVideo = true,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            corner = 14.dp,
            sizePx = 360,
            badge = formatDuration(video.durationMs)
        )
        Text(
            video.displayTitle(),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )
        if (needsName) {
            Text(
                "Needs a name",
                style = MaterialTheme.typography.labelMedium,
                color = Ember,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { renaming = true }
                    .padding(vertical = 3.dp)
            )
        }
    }

    if (renaming) RenameDialog(video, vm) { renaming = false }
}

@Composable
private fun VideoCard(
    video: MediaItemEntity,
    current: Boolean,
    vm: MusicViewModel,
    onClick: () -> Unit
) {
    var renaming by remember { mutableStateOf(false) }
    val needsName = remember(video.title) { MediaClassifier.looksRandom(video.title) }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
    ) {
        MediaThumb(
            uri = video.uri,
            seed = video.title,
            isVideo = true,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            corner = 0.dp,
            sizePx = 640,
            badge = formatDuration(video.durationMs)
        )
        Row(
            Modifier.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    video.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (current) Ember else Color.Unspecified,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (video.artist != MediaScanner.UNKNOWN_ARTIST) {
                    Text(
                        video.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (needsName) {
                    Text(
                        "Needs a name · tap to rename",
                        style = MaterialTheme.typography.labelMedium,
                        color = Ember,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { renaming = true }
                            .padding(vertical = 3.dp)
                    )
                }
            }
            ItemMenuButton(item = video, vm = vm)
        }
    }

    if (renaming) RenameDialog(video, vm) { renaming = false }
}