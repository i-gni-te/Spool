package com.example.spool.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.spool.R
import com.example.spool.ui.theme.Ember
import com.example.spool.ui.theme.Ink
import com.example.spool.ui.theme.SpoolGradient
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    hasAccess: Boolean,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
    onDone: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    val onLastPage = pagerState.currentPage == 2
    val grantedAtStart = remember { hasAccess }

    // Access granted while on this screen: carry straight on into the app
    LaunchedEffect(hasAccess) {
        if (hasAccess && !grantedAtStart) onDone()
    }

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LogoMark(markSize = 28.dp)
            Spacer(Modifier.size(8.dp))
            Text(
                stringResource(R.string.app_name).lowercase(),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.weight(1f))
            if (!onLastPage) {
                TextButton(onClick = { scope.launch { pagerState.animateScrollToPage(2) } }) {
                    Text("Skip")
                }
            }
        }

        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            when (page) {
                0 -> PageFrame(
                    title = "Just type what you want",
                    body = "Type an artist or a song and press Enter. " +
                            "Cue plays everything that matches, with no playlists to build."
                ) { TypeIllustration() }

                1 -> PageFrame(
                    title = "Songs, videos and shorts, kept apart",
                    body = "Songs and videos each get their own tab. " +
                            "Short clips like TikToks live in a feed you swipe through."
                ) { KindsIllustration() }

                else -> PageFrame(
                    title = "Your files stay on your phone",
                    body = "Cue needs access to your music and videos to build your library. " +
                            "Nothing is uploaded anywhere."
                ) { LogoMark(markSize = 120.dp) }
            }
        }

        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Dots(current = pagerState.currentPage, count = 3)
            Spacer(Modifier.height(20.dp))
            if (!onLastPage) {
                PrimaryPill("Next") {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                }
            } else {
                PrimaryPill(if (hasAccess) "Get started" else "Allow access") {
                    if (hasAccess) onDone() else onAllow()
                }
                if (!hasAccess) {
                    TextButton(onClick = onOpenSettings) { Text("Open app settings") }
                }
            }
        }
    }
}

@Composable
private fun PageFrame(title: String, body: String, illustration: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
            illustration()
        }
        Spacer(Modifier.height(32.dp))
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun TypeIllustration() {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.5.dp, Ember, RoundedCornerShape(28.dp))
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("drake", style = MaterialTheme.typography.bodyLarge)
        }
        PlayPill(text = "Shuffle “drake”", onClick = {})
    }
}

@Composable
private fun KindsIllustration() {
    Column(
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Destination.entries.forEach { d ->
                Row(
                    Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(d.icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = Ember)
                    Text(d.label, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Burna", "Wiz", "Dav", "Tems").forEach { name ->
                Cover(
                    seed = name,
                    modifier = Modifier.width(56.dp).aspectRatio(9f / 16f),
                    isVideo = true,
                    corner = 12.dp
                )
            }
        }
    }
}

@Composable
private fun Dots(current: Int, count: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(count) { i ->
            val selected = i == current
            val width by animateDpAsState(if (selected) 24.dp else 8.dp, label = "dot")
            Box(
                Modifier
                    .height(8.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(if (selected) Ember else MaterialTheme.colorScheme.outline)
            )
        }
    }
}

@Composable
private fun PrimaryPill(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(CircleShape)
            .background(SpoolGradient)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.titleLarge, color = Ink)
    }
}