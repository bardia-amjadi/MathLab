package com.powermath.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermath.R
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

private val SplashTop = Color(0xFF050926)      // deep navy backdrop
private val SplashBottom = Color(0xFF0B1E5C)
private const val TITLE = "MATHLAB"

/**
 * Intro (about 4 s, tap to skip):
 * 1. orbiting dots and arcs fade in around the logo while the logo springs into place,
 * 2. the title appears letter by letter, followed by the tagline,
 * 3. "Powered by CloudPixel" rises in at the bottom while a thin progress line fills.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val logoAlpha = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0.7f) }
    val orbit = remember { Animatable(0f) }
    val letters = remember { List(TITLE.length) { Animatable(0f) } }
    val tagline = remember { Animatable(0f) }
    val byAlpha = remember { Animatable(0f) }
    val byShift = remember { Animatable(1f) }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        coroutineScope {
            async { progress.animateTo(1f, tween(3900, easing = LinearEasing)) }
            async { orbit.animateTo(1f, tween(1200, easing = FastOutSlowInEasing)) }
            async { logoAlpha.animateTo(1f, tween(800, easing = FastOutSlowInEasing)) }
            async { logoScale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow)) }
            delay(900)
            letters.forEachIndexed { i, a -> async { delay(70L * i); a.animateTo(1f, tween(450, easing = FastOutSlowInEasing)) } }
            delay(900)
            async { tagline.animateTo(1f, tween(600, easing = FastOutSlowInEasing)) }
            delay(300)
            async { byAlpha.animateTo(1f, tween(800, easing = FastOutSlowInEasing)) }
            async { byShift.animateTo(0f, tween(800, easing = FastOutSlowInEasing)) }
        }
        delay(500)
        onFinished()
    }

    val spin = rememberInfiniteTransition(label = "spin")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart), label = "angle")
    val pulse by spin.animateFloat(0.92f, 1.08f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")

    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(SplashTop, SplashBottom)))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onFinished() }
            .semantics { contentDescription = "MathLab, powered by CloudPixel. Tap to skip." },
        contentAlignment = Alignment.Center
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(300.dp), contentAlignment = Alignment.Center) {
                // glow, orbit ring and three orbiting dots
                Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = orbit.value }) {
                    val c = Offset(size.width / 2, size.height / 2)
                    val r = size.minDimension * 0.46f * pulse
                    drawCircle(Brush.radialGradient(listOf(Color(0xFF2E6BFF).copy(alpha = 0.45f), Color.Transparent), center = c, radius = r * 1.15f), radius = r * 1.15f, center = c)
                    drawCircle(Color.White.copy(alpha = 0.10f), radius = r, center = c, style = Stroke(2f))
                    drawArc(Color(0xFFFFD54A), startAngle = angle, sweepAngle = 70f, useCenter = false,
                        topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2), style = Stroke(5f, cap = StrokeCap.Round))
                    drawArc(Color(0xFF7DB7FF), startAngle = angle + 180f, sweepAngle = 40f, useCenter = false,
                        topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2), style = Stroke(5f, cap = StrokeCap.Round))
                    for (k in 0 until 3) {
                        val a = Math.toRadians((angle * 1.6f + k * 120f).toDouble())
                        val rr = r * 0.82f
                        drawCircle(Color.White.copy(alpha = 0.85f), radius = 5f, center = Offset(c.x + (rr * cos(a)).toFloat(), c.y + (rr * sin(a)).toFloat()))
                    }
                }
                Image(
                    painterResource(R.drawable.logo_powermath), contentDescription = "MathLab logo", contentScale = ContentScale.Fit,
                    modifier = Modifier.size(190.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(40.dp)).graphicsLayer { alpha = logoAlpha.value; scaleX = logoScale.value; scaleY = logoScale.value }
                )
            }
            Spacer(Modifier.height(8.dp))
            Row {
                TITLE.forEachIndexed { i, ch ->
                    Text(
                        ch.toString(), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp,
                        modifier = Modifier.graphicsLayer { alpha = letters[i].value; translationY = (1f - letters[i].value) * 24f }
                    )
                }
            }
            Text(
                "Exact math. Fully offline.", color = Color(0xFF9DB4FF), fontSize = 14.sp, letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 6.dp).graphicsLayer { alpha = tagline.value }
            )
            Spacer(Modifier.weight(1f))
            Column(
                Modifier.graphicsLayer { alpha = byAlpha.value; translationY = byShift.value * 40f },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Powered by", color = Color(0xFF9DB4FF), fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 2.sp)
                Image(
                    painterResource(R.drawable.logo_cloudpixel), contentDescription = "CloudPixel logo", contentScale = ContentScale.Fit,
                    modifier = Modifier.height(110.dp)
                )
                Text("CloudPixel", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 3.sp)
            }
            Spacer(Modifier.height(20.dp))
            Box(Modifier.widthIn(max = 220.dp).fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.12f))) {
                Box(Modifier.fillMaxSize().graphicsLayer { scaleX = progress.value; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f) }.background(Color(0xFFFFD54A)))
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}
