package com.roomie.app.ui.theme

import androidx.compose.ui.graphics.Color

// Warm & Cozy palette.
val Background = Color(0xFFF6F1E8)
val Surface = Color(0xFFFFFBF7)
val SurfaceMuted = Color(0xFFEFE6D9)
val Outline = Color(0xFFE4D9CC)

val Primary = Color(0xFFC86D51)
val Secondary = Color(0xFF7A8B7B)
val OnSurface = Color(0xFF2D2522)
val OnSurfaceVariant = Color(0xFF6B645C)

// Selected segment/accent UI — a terracotta tone lighter than Primary itself, not the lavender
// Material3 defaults to when secondaryContainer is left unset. Noticeably lighter than the
// saturated Delete tone below so the two are never visually confused.
val SelectedContainer = Color(0xFFE8C4B3)
val OnSelectedContainer = Color(0xFF6E3B2C)

// Swipe action chips — container color for the chip background plus the tone itself for text/icon.
val SwipeLeftDelete = Color(0xFFC45C4A)
val DeleteContainer = Color(0xFFF0D0C8)
val SwipeRightKeep = Color(0xFF6B8F6E)
val KeepContainer = Color(0xFFD7E4D6)
// Move to folder — a distinct, dusty blue, deliberately not lavender/purple (to avoid echoing the
// unset-role lavender this pass removes).
val FolderAction = Color(0xFF6E8AA3)
val FolderContainer = Color(0xFFD9E3EA)
val SwipePostpone = Color(0xFF8A8680)
val PostponeContainer = Color(0xFFE8E4DC)
val NoneAction = Color(0xFF9A948C)
