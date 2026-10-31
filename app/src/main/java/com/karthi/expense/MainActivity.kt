package com.karthi.expense

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.karthi.expense.data.AccountSettings
import com.karthi.expense.data.Categories
import com.karthi.expense.data.PendingTxn
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : FragmentActivity() {
    private val appLock by lazy { AppLock(this) }
    private lateinit var unlockPrompt: BiometricPrompt
    private var locked by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Categories.init(this)
        AccountSettings.init(this)
        Backdrop.init(this)
        unlockPrompt = appLock.createPrompt(this) { ok -> if (ok) locked = false }
        // Decided before the first frame, so the app's content never flashes up behind the lock.
        locked = appLock.shouldLock()
        setContent {
            ExpenseTheme {
                // Surface (not a plain background) also sets the default text and icon colour,
                // which otherwise stays black and disappears in dark mode.
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (locked) LockScreen(onUnlock = ::unlock) else MainScreen()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        locked = appLock.shouldLock()
        // Posted so the prompt opens once the window is ready.
        if (locked) window.decorView.post(::unlock)
    }

    override fun onStop() {
        super.onStop()
        // Not when the activity is just being recreated (rotation, theme change).
        if (!isChangingConfigurations) appLock.onBackground()
    }

    private fun unlock() {
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        appLock.show(unlockPrompt)
    }
}

@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
        Spacer(Modifier.height(16.dp))
        Text("Expense Tracker is locked", style = MaterialTheme.typography.titleLarge)
        Text(
            "Unlock with your fingerprint, pattern or PIN.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp, bottom = 24.dp),
        )
        Button(onClick = onUnlock) { Text("Unlock") }
    }
}

/** Space the floating glass bars cover, so lists can scroll underneath them. */
private data class Insets(val top: Dp, val bottom: Dp) {
    fun padding(extraTop: Dp = 8.dp) = PaddingValues(start = 16.dp, end = 16.dp, top = top + extraTop, bottom = bottom + 16.dp)
}

@Composable
private fun MainScreen(vm: MainViewModel = viewModel()) {
    val pager = rememberPagerState { 2 }
    val tab = pager.currentPage
    val scope = rememberCoroutineScope()
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val pending by vm.unsynced.collectAsState()
    val hazeState = remember { HazeState() }
    val density = LocalDensity.current
    var topHeight by remember { mutableStateOf(0.dp) }
    var bottomHeight by remember { mutableStateOf(0.dp) }
    val insets = Insets(topHeight, bottomHeight)

    BackHandler(enabled = showSettings) { showSettings = false }

    // The one strong vibration in the app: when a sync finishes. Saved so rotating doesn't repeat it.
    val context = LocalContext.current
    LaunchedEffect(Unit) { Backdrop.load(context) }
    var hapticsDone by rememberSaveable { mutableIntStateOf(vm.syncAttempts) }
    LaunchedEffect(vm.syncAttempts) {
        if (vm.syncAttempts > hapticsDone) {
            hapticsDone = vm.syncAttempts
            context.syncHaptic(vm.lastSyncOk)
        }
    }

    CompositionLocalProvider(LocalHazeState provides hazeState) {
        Box(Modifier.fillMaxSize()) {
            // Everything that scrolls; the glass bars blur this.
            Box(Modifier.fillMaxSize().haze(hazeState)) {
                BackdropLayer()
                AnimatedContent(
                    targetState = showSettings,
                    transitionSpec = {
                        val dir = if (targetState) 1 else -1
                        (slideInHorizontally(fluidSpring()) { dir * it / 3 } + fadeIn(fluidSpring())) togetherWith
                            (slideOutHorizontally(fluidSpring()) { -dir * it / 3 } + fadeOut(fluidSpring()))
                    },
                    label = "settings",
                ) { settings ->
                    if (settings) {
                        SettingsScreen(vm, insets.padding())
                    } else {
                        // Swipe sideways between the tabs; the page follows the finger and settles without bounce.
                        HorizontalPager(
                            state = pager,
                            flingBehavior = PagerDefaults.flingBehavior(pager, snapAnimationSpec = settleSpring()),
                            modifier = Modifier.fillMaxSize(),
                        ) { page ->
                            if (page == 0) TagScreen(vm, insets.padding()) else DashboardScreen(vm, insets.padding())
                        }
                    }
                }
            }

            TopBar(
                title = if (showSettings) "Settings" else if (tab == 0) "To tag" else "Dashboard",
                showSettings = showSettings,
                onBack = { showSettings = false },
                onOpenSettings = { showSettings = true },
                banner = !showSettings && !vm.sheetConfigured,
                modifier = Modifier.align(Alignment.TopCenter).onSizeChanged { topHeight = with(density) { it.height.toDp() } },
            )

            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { bottomHeight = with(density) { it.height.toDp() } }
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AnimatedVisibility(
                    visible = !showSettings && tab == 0 && (pending.isNotEmpty() || vm.syncMessage != null),
                    enter = slideInVertically(fluidSpring()) { it / 2 } + fadeIn(fluidSpring()),
                    exit = slideOutVertically(fluidSpring()) { it / 2 } + fadeOut(fluidSpring()),
                ) {
                    SyncBar(vm, pending.size, pending.count { it.category != null })
                }
                AnimatedVisibility(
                    visible = !showSettings,
                    enter = slideInVertically(fluidSpring()) { it } + fadeIn(fluidSpring()),
                    exit = slideOutVertically(fluidSpring()) { it } + fadeOut(fluidSpring()),
                ) {
                    TabPill(
                        position = { pager.currentPage + pager.currentPageOffsetFraction },
                        labels = listOf("To tag" + if (pending.isEmpty()) "" else " · ${pending.size}", "Dashboard"),
                    ) { scope.launch { pager.animateScrollToPage(it, animationSpec = settleSpring()) } }
                }
            }
        }
    }
}

/** Full-width frosted bar under the status bar, like an iOS navigation bar. */
@Composable
private fun TopBar(
    title: String,
    showSettings: Boolean,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    banner: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().glass(RoundedCornerShape(0.dp), floating = false).statusBarsPadding()) {
        // One button that rolls across the bar: the gear on the right turns into the back arrow
        // on the left as Settings opens, and rolls back when it closes.
        val morph = animateFloatAsState(if (showSettings) 1f else 0f, settleSpring(), label = "gear-to-back")
        BoxWithConstraints(Modifier.fillMaxWidth().height(56.dp)) {
            val travel = maxWidth - 56.dp
            AnimatedContent(
                targetState = title,
                transitionSpec = { fadeIn(fluidSpring()) togetherWith fadeOut(fluidSpring()) },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset { IntOffset((16.dp + 40.dp * morph.value).roundToPx(), 0) }
                    .padding(end = 120.dp),
                label = "title",
            ) { Text(it, style = MaterialTheme.typography.headlineSmall, maxLines = 1) }
            IconButton(
                onClick = if (showSettings) onBack else onOpenSettings,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset { IntOffset((4.dp + travel * (1f - morph.value)).roundToPx(), 0) },
            ) {
                // Both icons turn with the roll; the gear shrinks away as the arrow grows in.
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = if (showSettings) null else "Settings",
                    modifier = Modifier.graphicsLayer {
                        val m = morph.value
                        rotationZ = -360f * m
                        alpha = (1f - m * 1.6f).coerceIn(0f, 1f)
                        scaleX = 1f - 0.5f * m
                        scaleY = 1f - 0.5f * m
                    },
                )
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = if (showSettings) "Back" else null,
                    modifier = Modifier.graphicsLayer {
                        val m = morph.value
                        rotationZ = -360f * m
                        alpha = ((m - 0.35f) * 1.6f).coerceIn(0f, 1f)
                        scaleX = 0.5f + 0.5f * m
                        scaleY = 0.5f + 0.5f * m
                    },
                )
            }
        }
        if (banner) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Connect your Google Sheet to sync",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onOpenSettings) { Text("Set up") }
            }
        }
    }
}

/** Damped all the way: pages settle firmly with no overshoot. */
private fun settleSpring() = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

/** Tagging progress plus the Sync button, on a glass card above the tabs. */
@Composable
private fun SyncBar(vm: MainViewModel, total: Int, tagged: Int) {
    val progress by animateFloatAsState(
        if (total > 0) tagged.toFloat() / total else 0f, fluidSpring(), label = "progress",
    )
    Column(
        Modifier.fillMaxWidth().glass(MaterialTheme.shapes.large).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        vm.syncMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (total > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$tagged of $total tagged", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(5.dp),
                strokeCap = StrokeCap.Round,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }
        val interaction = remember { MutableInteractionSource() }
        Button(
            onClick = vm::sync,
            enabled = tagged > 0 && vm.sheetConfigured && !vm.syncing,
            interactionSource = interaction,
            modifier = Modifier.fillMaxWidth().height(48.dp).pressScale(interaction),
        ) {
            Text(if (vm.syncing) "Syncing…" else "Sync $tagged to Google Sheet", style = MaterialTheme.typography.labelLarge)
        }
    }
}

private val dayHeader = DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.ENGLISH)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagScreen(vm: MainViewModel, contentPadding: PaddingValues) {
    val context = LocalContext.current
    var hasSmsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        )
    }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasSmsPermission = granted
    }

    // Read new SMS every time this tab opens (and right after permission is granted).
    LaunchedEffect(hasSmsPermission) {
        if (hasSmsPermission) vm.importSms() else askPermission.launch(Manifest.permission.READ_SMS)
    }

    if (!hasSmsPermission) {
        Column(Modifier.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("SMS permission is needed to find your bank transactions.")
            Button(onClick = { askPermission.launch(Manifest.permission.READ_SMS) }) { Text("Allow SMS access") }
        }
        return
    }

    val txns by vm.unsynced.collectAsState()
    val people by vm.knownPeople.collectAsState()
    val ledger by vm.peopleLedger.collectAsState()

    // Pull down to read new SMS, like Instagram or YouTube. The spinner stays up for a
    // short minimum so a fast read still reads as "checked" instead of a flicker.
    var pulled by remember { mutableStateOf(false) }
    LaunchedEffect(pulled) {
        if (pulled) {
            delay(600)
            snapshotFlow { vm.importing }.first { !it }
            pulled = false
        }
    }
    val pullState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = pulled,
        onRefresh = {
            pulled = true
            vm.importSms()
        },
        state = pullState,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullState,
                isRefreshing = pulled,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = contentPadding.calculateTopPadding()),
            )
        },
    ) {
        if (txns.isEmpty()) {
            // A list (not a plain column) so the empty screen can be pulled too.
            LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
                item(key = "empty") {
                    Column(
                        Modifier.fillParentMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (vm.importing && !pulled) {
                            Text("Reading your SMS…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text("All caught up", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "Pull down to check for new bank SMS.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        } else {
            TagList(vm, txns, people, contentPadding)
        }
    }
}

@Composable
private fun TagList(vm: MainViewModel, txns: List<PendingTxn>, people: List<String>, contentPadding: PaddingValues) {
    val days = remember(txns) { txns.groupBy { it.date } }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        days.forEach { (date, dayTxns) ->
            item(key = "h-$date", contentType = "day") {
                Text(
                    runCatching { LocalDate.parse(date).format(dayHeader) }.getOrDefault(date).uppercase(Locale.ENGLISH),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 10.dp).animateItem(),
                )
            }
            items(dayTxns, key = { it.id }, contentType = { "txn" }) { t ->
                TxnCard(t, people, ledger, Modifier.animateItem()) { category, reason, shares, parts -> vm.tag(t.id, category, reason, shares, parts) }
            }
        }
    }
}
