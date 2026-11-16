package io.github.artcraftandroid.procraft.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.artcraftandroid.procraft.canvas.CanvasViewModel
import kotlin.math.roundToInt

private val QuickFill get() = if (Pc.light) Color(0xF5F2F2F2) else Color(0xF5212121)
private val QuickInk get() = if (Pc.light) Color(0xFF3B3B3B) else Color(0xFFC9C9C9)
private val QuickDim get() = if (Pc.light) Color(0xFF7A7A7A) else Color(0xFF8A8A8A)
private val QuickTrack get() = if (Pc.light) Color(0xFFD6D6D6) else Color(0xFF3C3C3C)
private val QuickChip get() = if (Pc.light) Color(0xFFE3E3E3) else Color(0xFF333333)

/** The side bar's quick settings buttons are this big. */
private val QuickButton = 38.dp
private val QuickGap = 4.dp

/** How high the three buttons stand together. */
val QuickButtonsHeight = QuickButton * 3 + QuickGap * 2

/** Whether the quick settings can show a control (the others, a drawing or an image editor, are for the studio). */
private fun supported(c: StudioControl) =
    c is StudioControl.Slider || c is StudioControl.Toggle || c is StudioControl.Choice || c is StudioControl.Kinds

private fun keysOf(c: StudioControl): List<String> = when (c) {
    is StudioControl.Slider -> listOfNotNull(c.key, c.enabledBy?.first)
    is StudioControl.Toggle -> listOfNotNull(c.key, c.enabledBy?.first)
    is StudioControl.Choice -> listOf(c.key)
    is StudioControl.Kinds -> listOf(c.key)
    else -> emptyList()
}

/** The studio's pages that have a control the quick settings can show: what a button may be set to. */
private val QuickPages: List<StudioTab> by lazy {
    STUDIO_TABS.filter { tab -> tab.sections.any { section -> section.controls.any { supported(it) } } }
}

private fun pageNamed(name: String?): StudioTab = QuickPages.firstOrNull { it.name == name } ?: QuickPages.first()

/**
 * Three buttons for the top of the side bar: each shows one page of the brush's settings (the
 * studio's pages: Stabilization, Wet Mix and Grain to begin with) in a small window. A tap opens
 * it; held, the button offers the pages' icons to choose from.
 */
@Composable
fun QuickBrushButtons(vm: CanvasViewModel, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(QuickGap)) {
        for (i in 0 until 3) {
            val tab = pageNamed(vm.quickTabs.getOrNull(i))
            val on = vm.quickOpen == i || vm.quickChoosing == i
            Box(
                Modifier.size(QuickButton).clip(CircleShape).background(if (on) Pc.Accent else Pc.SideBar)
                    .pointerInput(i) {
                        detectTapGestures(
                            onTap = {
                                vm.quickChoosing = null
                                vm.quickOpen = if (vm.quickOpen == i) null else i
                            },
                            onLongPress = {
                                vm.quickOpen = null
                                vm.quickChoosing = if (vm.quickChoosing == i) null else i
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(tab.icon, contentDescription = tab.name, tint = if (on) Color.White else Pc.SideGlyph, modifier = Modifier.requiredSize(tab.iconSize * 0.78f))
            }
        }
    }
}

/**
 * The window of the quick settings that is open (or the pages to choose from, for a button held),
 * beside the side bar, [top] from the top of the screen. A tap outside it, or the pen leaving it,
 * closes it.
 */
@Composable
fun QuickBrushPanel(vm: CanvasViewModel, right: Boolean, top: Dp, screenWidth: Dp, screenHeight: Dp) {
    val open = vm.quickOpen
    val choosing = vm.quickChoosing
    if (open == null && choosing == null) return
    val close = {
        vm.quickOpen = null
        vm.quickChoosing = null
    }
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { close() } })
    val width = minOf(320.dp, screenWidth - Pc.SideBarWidth - 20.dp)
    val side = Pc.SideBarWidth + 8.dp
    Box(
        Modifier.fillMaxSize().padding(top = top),
        contentAlignment = if (right) Alignment.TopEnd else Alignment.TopStart,
    ) {
        Column(
            Modifier.padding(start = if (right) 0.dp else side, end = if (right) side else 0.dp)
                .width(width).heightIn(max = screenHeight - top - 12.dp)
                .clip(RoundedCornerShape(18.dp)).background(QuickFill)
                // a tap inside is not one outside; the pen leaving it (not drawing in it, not a finger lifted) closes it
                .pointerInput(Unit) { detectTapGestures { } }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: continue
                            if (event.type == PointerEventType.Exit && change.type != PointerType.Touch && !change.pressed) close()
                        }
                    }
                }
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            if (choosing != null) PagePicker(vm, choosing) else if (open != null) QuickSettingsPage(vm, pageNamed(vm.quickTabs.getOrNull(open)))
        }
    }
}

/** The pages as icons, to choose what button [button] shows. */
@Composable
private fun PagePicker(vm: CanvasViewModel, button: Int) {
    Text("Show", color = QuickDim, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
    val chosen = vm.quickTabs.getOrNull(button)
    // as many to a row as fit (five)
    for (row in QuickPages.chunked(5)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (tab in row) {
                Box(
                    Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (tab.name == chosen) Pc.Accent else QuickChip)
                        .clickable {
                            vm.chooseQuickTab(button, tab.name)
                            vm.quickChoosing = null
                            vm.quickOpen = button
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(tab.icon, contentDescription = tab.name, tint = if (tab.name == chosen) Color.White else QuickInk, modifier = Modifier.requiredSize(tab.iconSize * 0.8f))
                }
            }
            for (k in row.size until 5) Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(6.dp))
    }
}

/** The settings of studio page [tab], compactly: its sections' sliders, switches and choices. */
@Composable
private fun QuickSettingsPage(vm: CanvasViewModel, tab: StudioTab) {
    val sections = remember(tab) {
        tab.sections.map { it.title to it.controls.filter(::supported) }.filter { it.second.isNotEmpty() }
    }
    val keys = remember(tab) { sections.flatMap { it.second }.flatMap(::keysOf).distinct() }
    // read again when the brush changes
    val brush = vm.currentBrush
    LaunchedEffect(tab, brush?.set, brush?.index) { vm.readQuickSettings(keys) }
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(tab.icon, contentDescription = null, tint = QuickInk, modifier = Modifier.requiredSize(tab.iconSize * 0.6f))
        Text(tab.name, color = QuickInk, fontSize = 15.sp, maxLines = 1, modifier = Modifier.weight(1f).padding(start = 6.dp))
        // the whole page, with everything on it, is in the brush studio
        Text(
            "All settings", color = Pc.Accent, fontSize = 13.sp, maxLines = 1,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                vm.quickOpen = null
                vm.openStudio()
            }.padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
    for ((title, controls) in sections) {
        if (title != null && sections.size > 1) Text(title, color = QuickDim, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
        for (c in controls) QuickControl(vm, c, title)
    }
}

@Composable
private fun QuickControl(vm: CanvasViewModel, c: StudioControl, title: String?) {
    when (c) {
        is StudioControl.Slider -> {
            val enabled = c.enabledBy?.let { (key, choice) -> vm.quickSetting(key).roundToInt() == choice } ?: true
            QuickSlider(c.label, vm.quickSetting(c.key), c.shown, enabled) { vm.setQuickSetting(c.key, it) }
        }
        is StudioControl.Toggle -> {
            val enabled = c.enabledBy?.let { (key, choice) -> vm.quickSetting(key).roundToInt() == choice } ?: true
            val on = vm.quickSetting(c.key) >= 0.5f
            Row(
                Modifier.fillMaxWidth().height(34.dp).then(if (enabled) Modifier.clickable { vm.setQuickSetting(c.key, if (on) 0f else 1f) } else Modifier)
                    .padding(start = 4.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(c.label, color = if (enabled) QuickInk else QuickDim, fontSize = 13.sp, maxLines = 1, modifier = Modifier.weight(1f))
                Switch(on, enabled = enabled)
            }
        }
        // a choice goes to the next on a tap
        is StudioControl.Choice -> {
            val now = vm.quickSetting(c.key).roundToInt().coerceIn(0, c.options.lastIndex)
            Row(
                Modifier.fillMaxWidth().height(34.dp).clickable { vm.setQuickSetting(c.key, ((now + 1) % c.options.size).toFloat()) }.padding(start = 4.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title ?: "", color = QuickInk, fontSize = 13.sp, maxLines = 1, modifier = Modifier.weight(1f))
                Box(Modifier.clip(RoundedCornerShape(10.dp)).background(QuickChip).padding(horizontal = 10.dp, vertical = 5.dp)) {
                    Text(c.options[now], color = QuickInk, fontSize = 12.sp, maxLines = 1)
                }
            }
        }
        is StudioControl.Kinds -> {
            val now = vm.quickSetting(c.key).roundToInt()
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                c.options.forEachIndexed { i, (icon, label) ->
                    Row(
                        Modifier.weight(1f).height(30.dp).clip(RoundedCornerShape(15.dp)).background(if (i == now) Pc.Accent else QuickChip)
                            .clickable { vm.setQuickSetting(c.key, i.toFloat()) },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(icon, contentDescription = null, tint = if (i == now) Color.White else QuickInk, modifier = Modifier.requiredSize(24.dp))
                        Text(label, color = if (i == now) Color.White else QuickInk, fontSize = 12.sp, maxLines = 1)
                    }
                }
            }
        }
        else -> Unit
    }
}

/** A slider row: its name, the slider (0 to 1 across, a tap or a drag sets it) and what it reads. */
@Composable
private fun QuickSlider(label: String, value: Float, shown: Shown, enabled: Boolean, onChange: (Float) -> Unit) {
    val change by rememberUpdatedState(onChange)
    Row(Modifier.fillMaxWidth().height(34.dp).padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = if (enabled) QuickInk else QuickDim, fontSize = 13.sp, maxLines = 1, modifier = Modifier.width(92.dp))
        Box(
            Modifier.weight(1f).height(34.dp)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        change((down.position.x / size.width).coerceIn(0f, 1f))
                        while (true) {
                            val c = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!c.pressed) break
                            change((c.position.x / size.width).coerceIn(0f, 1f))
                            c.consume()
                        }
                    }
                }
                .drawBehind {
                    val y = size.height / 2f
                    val pad = 6.dp.toPx()
                    val x = pad + (size.width - pad * 2f) * value.coerceIn(0f, 1f)
                    val track = Size(size.width - pad * 2f, 3.dp.toPx())
                    drawRoundRect(QuickTrack, Offset(pad, y - track.height / 2f), track, CornerRadius(track.height / 2f))
                    drawRoundRect(if (enabled) Pc.Accent else QuickDim, Offset(pad, y - track.height / 2f), Size(x - pad, track.height), CornerRadius(track.height / 2f))
                    drawCircle(Color.White, 7.dp.toPx(), Offset(x, y))
                },
        )
        Text(
            shownValue(value, shown), color = if (enabled) QuickInk else QuickDim, fontSize = 12.sp, maxLines = 1, softWrap = false,
            textAlign = TextAlign.End, modifier = Modifier.width(60.dp).padding(end = 2.dp),
        )
    }
}
