package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Nexus Chat Vibrant Palette
val NexusCyan = Color(0xFF00E5FF)
val NexusCyanDark = Color(0xFF00B4D8)
val NexusBlue = Color(0xFF0066FE)
val NexusBlueDark = Color(0xFF0048C4)
val NexusPurple = Color(0xFF8A2BE2)

val DarkBackground = Color(0xFF0B0F19)
val DarkSurface = Color(0xFF111827)
val DarkSurfaceElevated = Color(0xFF1E293B)
val DarkOutline = Color(0xFF334155)
val DarkTextPrimary = Color(0xFFF8FAFC)
val DarkTextSecondary = Color(0xFF94A3B8)

val LightBackground = Color(0xFFF8FAFC)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceElevated = Color(0xFFF1F5F9)
val LightOutline = Color(0xFFE2E8F0)
val LightTextPrimary = Color(0xFF0F172A)
val LightTextSecondary = Color(0xFF64748B)

// Chat Bubbles
val BubbleOutgoing = Color(0xFF0066FE)
val BubbleIncomingDark = Color(0xFF1E293B)
val BubbleIncomingLight = Color(0xFFE2E8F0)

val StatusOnline = Color(0xFF10B981)
val StatusOffline = Color(0xFF64748B)

val BrandGradient = Brush.horizontalGradient(
    colors = listOf(NexusCyan, NexusBlue)
)

val CardDarkGradient = Brush.verticalGradient(
    colors = listOf(DarkSurfaceElevated, DarkSurface)
)
