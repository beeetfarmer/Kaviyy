package com.nl.kaviyy.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * For decorative text drawn inside fixed-size art (cover placeholders, avatar initials, chart day letters) whose
 * meaning is also given elsewhere in readable text or semantics: it ignores the user's font scale so it can't
 * overflow its shape. Never use it for text a user needs to read.
 */
@Composable
@ReadOnlyComposable
fun TextUnit.fixedScale(): TextUnit = (value / LocalDensity.current.fontScale).sp
