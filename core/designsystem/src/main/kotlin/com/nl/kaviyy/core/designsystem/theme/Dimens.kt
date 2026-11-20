package com.nl.kaviyy.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** DESIGN.md §4 radii. */
object KavitaShapes {
    val heroCard = RoundedCornerShape(22.dp)
    val card = RoundedCornerShape(18.dp)
    val groupedList = RoundedCornerShape(16.dp)
    val coverLarge = RoundedCornerShape(12.dp)
    val coverGrid = RoundedCornerShape(10.dp)
    val segmentedTrack = RoundedCornerShape(10.dp)
    val segment = RoundedCornerShape(8.dp)
    val settingsTile = RoundedCornerShape(8.dp)
    val rowThumb = RoundedCornerShape(6.dp)
    val appIcon = RoundedCornerShape(22.dp)
    val sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val pill = RoundedCornerShape(percent = 50)
}

/** DESIGN.md §4 spacing and control sizes. */
object KavitaSpacing {
    val screenHorizontal: Dp = 20.dp
    val topBelowStatusBar: Dp = 8.dp
    val cardPadding: Dp = 16.dp
    val railGap: Dp = 14.dp
    val gridRowGap: Dp = 18.dp
    val gridColumnGap: Dp = 12.dp
    val sectionGapHome: Dp = 22.dp
    val sectionGapLibrary: Dp = 16.dp
    val sectionGapLists: Dp = 20.dp
    val sectionGapProfile: Dp = 18.dp
    val rowHeight: Dp = 52.dp
    val settingsRowHeight: Dp = 50.dp
    val minTouchTarget: Dp = 48.dp
    val tabBarHeight: Dp = 64.dp
    val tabBarSideInset: Dp = 16.dp
    val tabBarBottomInset: Dp = 28.dp

    /** Bottom content padding so lists clear the floating tab bar. */
    val contentBottomPadding: Dp = tabBarHeight + tabBarBottomInset + 16.dp
}
