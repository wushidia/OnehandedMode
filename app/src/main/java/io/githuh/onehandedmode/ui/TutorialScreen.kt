package io.githuh.onehandedmode.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.githuh.onehandedmode.R
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt
import kotlin.math.abs
import kotlin.math.floor

private enum class Lesson(val tab: Int, val title: Int, val summary: Int) {
    Enter(R.string.tutorial_enter_tab, R.string.tutorial_enter_title, R.string.tutorial_enter_summary),
    Switch(R.string.tutorial_switch_tab, R.string.tutorial_switch_title, R.string.tutorial_switch_summary),
    Resize(R.string.tutorial_resize_tab, R.string.tutorial_resize_title, R.string.tutorial_resize_summary),
    Exit(R.string.tutorial_exit_tab, R.string.tutorial_exit_title, R.string.tutorial_exit_summary),
    Wallpaper(R.string.tutorial_wallpaper_tab, R.string.tutorial_wallpaper_title, R.string.tutorial_wallpaper_summary),
}

private val TutorialBlue = Color(0xFF3482FF)

@Composable
internal fun TutorialScreen(onBack: () -> Unit) {
    val pager = rememberPagerState(pageCount = { Lesson.entries.size })
    val scope = rememberCoroutineScope()
    SettingsScaffold(title = stringResource(R.string.help), onBack = onBack) {
        item { Spacer(Modifier.height(12.dp)) }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 16.dp)) {
                    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(40.dp)) {
                        val tabWidth = maxWidth / Lesson.entries.size
                        Box(Modifier.width(tabWidth).fillMaxHeight().graphicsLayer {
                            translationX = size.width * (pager.currentPage + pager.currentPageOffsetFraction)
                        }.clip(RoundedCornerShape(14.dp)).background(TutorialBlue.copy(alpha = .12f)))
                        Row(Modifier.fillMaxSize().selectableGroup()) {
                            Lesson.entries.forEachIndexed { index, lesson ->
                                val selected = pager.currentPage == index
                                val emphasis = 1f - abs(index - pager.currentPage - pager.currentPageOffsetFraction).coerceIn(0f, 1f)
                                Box(
                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                        .clip(RoundedCornerShape(14.dp))
                                        .selectable(selected, interactionSource = remember { MutableInteractionSource() },
                                            indication = null, role = Role.Tab, onClick = {
                                            scope.launch {
                                                pager.animateScrollToPage(index,
                                                    animationSpec = tween(360, easing = FastOutSlowInEasing))
                                            }
                                        }),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(stringResource(lesson.tab), fontSize = 14.sp,
                                        color = lerp(MiuixTheme.colorScheme.onSurfaceVariantSummary, TutorialBlue, emphasis))
                                }
                            }
                        }
                    }
                    HorizontalPager(state = pager, beyondViewportPageCount = 1,
                        modifier = Modifier.fillMaxWidth()) { index ->
                        val lesson = Lesson.entries[index]
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            TutorialAnimation(lesson, active = pager.settledPage == index)
                            Text(stringResource(lesson.title), fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 16.dp))
                            Text(stringResource(lesson.summary), fontSize = 14.sp, lineHeight = 22.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp)
                                    .padding(horizontal = 24.dp, vertical = 10.dp))
                        }
                    }
                    Box(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
                        contentAlignment = Alignment.Center) {
                        TutorialPageIndicator(pager)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
        item { SectionTitle(stringResource(R.string.help_external)) }
        item {
            Card(Modifier.fillMaxWidth()) {
                SelectionContainer {
                    Text(stringResource(R.string.help_external_body),
                        modifier = Modifier.padding(ContentInset),
                        fontSize = 14.sp, lineHeight = 22.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            }
        }
        item { Spacer(Modifier.height(24.dp).navigationBarsPadding()) }
    }
}

@Composable
private fun TutorialPageIndicator(pager: PagerState) {
    val dark = isSystemInDarkTheme()
    val selected = if (dark) Color(0xCCFFFFFF) else Color(0xCC000000)
    val unselected = if (dark) Color(0x4DFFFFFF) else Color(0x4D000000)
    val count = Lesson.entries.size
    // Match the small MIUIX PageIndicator: 2.5 dp radius, 8 dp gap, 10/8 dp padding.
    Canvas(Modifier.size((20 + count * 5 + (count - 1) * 8).dp, 21.dp)) {
        val radius = 2.5.dp.toPx()
        val step = 13.dp.toPx()
        val first = 10.dp.toPx() + radius
        val y = size.height / 2
        repeat(count) { drawCircle(unselected, radius, Offset(first + it * step, y)) }
        val position = (pager.currentPage + pager.currentPageOffsetFraction).coerceIn(0f, (count - 1).toFloat())
        val page = floor(position)
        val fraction = position - page
        val head = FastOutSlowInEasing.transform((fraction * 2).coerceAtMost(1f))
        val tail = FastOutSlowInEasing.transform((fraction * 2 - 1).coerceAtLeast(0f))
        val left = first + (page + tail) * step - radius
        val right = first + (page + head) * step + radius
        drawRoundRect(selected, Offset(left, y - radius), Size(right - left, radius * 2), CornerRadius(radius))
    }
}

@Composable
private fun TutorialAnimation(lesson: Lesson, active: Boolean) {
    // Only the selected lesson runs. InfiniteTransition also respects disabled system animations.
    val phase = if (active) {
        rememberInfiniteTransition(label = "tutorial").animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(6400, easing = LinearEasing), RepeatMode.Restart),
            label = "gesture",
        )
    } else rememberUpdatedState(0f)
    val description = stringResource(lesson.summary)
    val dark = isSystemInDarkTheme()
    Box(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Canvas(Modifier.fillMaxWidth().height(340.dp).semantics { contentDescription = description }) {
            val factor = minOf(size.width / 520f, size.height / 920f)
            val phoneWidth = 400f
            val phoneHeight = 860f
            val left = (size.width - phoneWidth * factor) / 2f
            val top = (size.height - phoneHeight * factor) / 2f
            val pose = lessonPose(lesson, phase.value)
            withTransform({
                translate(left, top)
                scale(factor, factor, pivot = Offset.Zero)
            }) {
                drawRoundRect(if (dark) Color(0xFF737888) else Color(0xFF2E3440),
                    Offset(-9f, -9f), Size(418f, 878f), CornerRadius(54f))
                val screen = Path().apply { addRoundRect(RoundRect(Rect(0f, 0f, 400f, 860f), CornerRadius(44f))) }
                clipPath(screen) {
                    drawWallpaper(pose.wallpaper)
                    val gap = 1f - pose.scale
                    val x = phoneWidth * gap * pose.anchor
                    val y = phoneHeight * gap
                    withTransform({ translate(x, y); scale(pose.scale, pose.scale, pivot = Offset.Zero) }) {
                        drawDemoApp(if (lesson == Lesson.Wallpaper) pose.wallpaper else null)
                    }
                    if (gap > .01f) {
                        val buttonX = if (pose.anchor > .5f) phoneWidth * gap / 2 else phoneWidth * (1 - gap / 2)
                        val buttonY = phoneHeight - phoneWidth * gap / 2
                        drawCircle(Color.White.copy(alpha = .8f), 15f, Offset(buttonX, buttonY))
                        drawCircle(Color(0xFF7B879A), 7f, Offset(buttonX, buttonY), style = Stroke(3f))
                        drawCircle(Color(0xFF7B879A), 2f, Offset(buttonX, buttonY))
                    }
                    if (lesson == Lesson.Switch) {
                        val edge = if (phase.value < .5f) 388f else 0f
                        drawRoundRect(TutorialBlue.copy(alpha = pose.touchAlpha * .4f),
                            Offset(edge, 360f), Size(12f, 240f), CornerRadius(6f))
                    }
                    drawGesture(pose)
                }
                drawCircle(Color(0xFF252A34), 5f, Offset(200f, 18f))
            }
        }
        if (lesson == Lesson.Resize) {
            val percent by remember(phase) { derivedStateOf { (lessonPose(lesson, phase.value).scale * 100).roundToInt() } }
            Text(
                stringResource(R.string.tutorial_percent, percent),
                color = TutorialBlue, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 20.dp, top = 8.dp)
                    .background(TutorialBlue.copy(alpha = .1f), RoundedCornerShape(12.dp)).padding(10.dp, 6.dp),
            )
        }
    }
}

private data class LessonPose(
    val scale: Float,
    val anchor: Float,
    val from: Offset,
    val to: Offset,
    val travel: Float,
    val touchAlpha: Float,
    val wallpaper: Float = 1f,
)

private fun smooth(value: Float, start: Float, end: Float): Float {
    val t = ((value - start) / (end - start)).coerceIn(0f, 1f)
    return t * t * (3 - 2 * t)
}

private fun lessonPose(lesson: Lesson, phase: Float): LessonPose {
    val reverse = phase >= .5f
    val t = phase % .5f * 2
    val touch = smooth(t, .03f, .12f) * (1 - smooth(t, .48f, .60f))
    return when (lesson) {
        Lesson.Enter -> {
            val progress = smooth(t, .30f, .53f) * (1 - smooth(t, .87f, 1f))
            val x = if (reverse) 300f else 100f
            LessonPose(1 - .3f * progress, if (reverse) 1f else 0f,
                Offset(x, 790f), Offset(x, 850f), smooth(t, .12f, .38f), touch)
        }
        Lesson.Switch -> {
            val progress = smooth(t, .42f, .64f)
            LessonPose(.7f, if (reverse) 1 - progress else progress,
                Offset(if (reverse) 10f else 390f, 500f),
                Offset(if (reverse) 120f else 280f, 500f), smooth(t, .12f, .42f), touch)
        }
        Lesson.Resize -> {
            val up = smooth(t, .12f, .40f)
            val down = smooth(t, .52f, .82f)
            val scale = .68f + .16f * up - .26f * down + .1f * smooth(t, .93f, 1f)
            val x = if (reverse) 360f else 40f
            val a = smooth(t, .03f, .12f) * (1 - smooth(t, .83f, .9f))
            LessonPose(scale, if (reverse) 0f else 1f, Offset(x, 560f),
                Offset(x, 560f - 860f * .16f * up + 860f * .26f * down), 1f, a)
        }
        Lesson.Exit -> {
            val progress = smooth(t, .28f, .50f) * (1 - smooth(t, .9f, 1f))
            val point = if (reverse) Offset(55f, 500f) else Offset(200f, 110f)
            LessonPose(.7f + .3f * progress, 1f, point, point, smooth(t, .12f, .28f),
                smooth(t, .05f, .16f) * (1 - smooth(t, .28f, .40f)))
        }
        Lesson.Wallpaper -> {
            val blur = if (reverse) 1 - smooth(t, .25f, .48f) else smooth(t, .25f, .48f)
            val point = Offset(120f + 328f * .7f, 258f + 342f * .7f)
            LessonPose(.7f, 1f, point, point, smooth(t, .12f, .28f), touch, blur)
        }
    }
}

private fun DrawScope.drawWallpaper(amount: Float) {
    drawRect(Color(0xFFE1E4ED), size = Size(400f, 860f))
    drawRect(Brush.linearGradient(listOf(Color(0xFFB4D4F5), Color(0xFFD2C5EC), Color(0xFFECD4D0)),
        Offset.Zero, Offset(400f, 860f)), size = Size(400f, 860f), alpha = amount)
    drawCircle(Brush.radialGradient(listOf(Color(0xAA638CCB), Color.Transparent), Offset(20f, 100f), 510f),
        510f, Offset(20f, 100f), alpha = amount)
    drawCircle(Brush.radialGradient(listOf(Color(0xAAFBEDDF), Color.Transparent), Offset(420f, 720f), 400f),
        400f, Offset(420f, 720f), alpha = amount)
}

private fun DrawScope.drawDemoApp(toggle: Float?) {
    val ink = Color(0xFFB5BECE)
    drawRoundRect(Color(0xFFFAFBFD), Offset.Zero, Size(400f, 860f), CornerRadius(44f))
    drawRoundRect(ink, Offset(28f, 30f), Size(40f, 7f), CornerRadius(3f))
    repeat(3) { drawRoundRect(ink, Offset(324f + it * 12, 28f), Size(7f, 10f), CornerRadius(2f)) }
    drawRoundRect(Color(0xFF66758C), Offset(28f, 80f), Size(180f, 14f), CornerRadius(5f))
    drawRoundRect(Color(0xFFE6EEFA), Offset(24f, 126f), Size(352f, 146f), CornerRadius(20f))
    drawCircle(Color(0xFFB1CDF7), 52f, Offset(302f, 177f))
    drawRoundRect(TutorialBlue.copy(alpha = .65f), Offset(46f, 156f), Size(132f, 12f), CornerRadius(5f))
    drawRoundRect(TutorialBlue.copy(alpha = .25f), Offset(46f, 183f), Size(98f, 8f), CornerRadius(4f))
    repeat(4) { index ->
        val y = 302f + index * 112f
        drawRoundRect(Color(0xFFEFF2F7), Offset(24f, y), Size(352f, 92f), CornerRadius(18f))
        drawCircle(if (index == 0) TutorialBlue.copy(alpha = .65f) else ink.copy(alpha = .65f),
            17f, Offset(61f, y + 38f))
        drawRoundRect(ink, Offset(94f, y + 27f), Size(108f, 9f), CornerRadius(4f))
        drawRoundRect(ink.copy(alpha = .4f), Offset(94f, y + 47f), Size(155f, 6f), CornerRadius(3f))
    }
    if (toggle != null) {
        drawRoundRect(lerp(Color(0xFFCFD5E0), TutorialBlue, toggle),
            Offset(297f, 326f), Size(62f, 32f), CornerRadius(16f))
        drawCircle(Color.White, 12f, Offset(313f + 30f * toggle, 342f))
    }
    drawRoundRect(Color(0xFF98A4B6), Offset(130f, 839f), Size(140f, 6f), CornerRadius(3f))
}

private fun DrawScope.drawGesture(pose: LessonPose) {
    val point = pose.from + (pose.to - pose.from) * pose.travel
    val alpha = pose.touchAlpha
    drawLine(TutorialBlue.copy(alpha = alpha * .20f), pose.from, pose.to, 12f, StrokeCap.Round)
    drawLine(TutorialBlue.copy(alpha = alpha * .55f), pose.from, point, 6f, StrokeCap.Round)
    drawCircle(TutorialBlue.copy(alpha = alpha * .12f), 29f, point)
    drawCircle(TutorialBlue.copy(alpha = alpha * .28f), 20f, point, style = Stroke(2f))
    drawCircle(Color.White.copy(alpha = alpha), 12f, point)
    drawCircle(TutorialBlue.copy(alpha = alpha), 12f, point, style = Stroke(3f))
}
