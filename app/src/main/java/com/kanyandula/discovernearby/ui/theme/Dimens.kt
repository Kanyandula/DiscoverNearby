package com.kanyandula.discovernearby.ui.theme

import androidx.compose.ui.unit.dp

// Canvas artboard sizes (1408 × 792 frame), taken 1:1 as dp. Text sizes live in Type.kt.
val MinTouchTarget = 76.dp // docs/02 §15
val HeaderHeight = 56.dp
val HeaderIconSize = 22.dp
val ContentGap = 12.dp
val PanelRadius = 24.dp
val PanelPadding = 24.dp
val TileRadius = 20.dp
val GridGap = 20.dp
val CategoryIconSize = 72.dp

// Recommendations and message artboards.
val RowRadius = 18.dp
val RowGap = 14.dp
val RowPadding = 24.dp
val RowVerticalPadding = 14.dp
val RowLineGap = 6.dp
val ChevronSize = 28.dp
val MessageIconSize = 88.dp
val MessageGap = 18.dp
val SpinnerStroke = 7.dp // the canvas arc: stroke 2 on a 24 viewBox, drawn at 88
val ButtonMinWidth = 260.dp
val ButtonRadius = 16.dp
val ButtonGap = 16.dp
val RowImageWidth = 156.dp // the row photo ("[Provider photo]") on the Recommendations artboard
val RowImageHeight = 100.dp
val RowImageRadius = 10.dp
val RowImageIconSize = 44.dp

// Place Details artboards.
val DetailsImageHeight = 240.dp // the photo panel above Navigate (03-place-details), the action column's width
val DetailsImageRadius = 16.dp
val DetailsImageIconSize = 96.dp
val DetailsImageGap = 24.dp
val DetailsInset = 16.dp
val DetailsColumnGap = 40.dp
val SectionPadding = 18.dp
val ActionColumnWidth = 400.dp
val NavigateHeight = 88.dp
val NavigateRadius = 20.dp
val NavigateIconSize = 28.dp
val NavigateIconGap = 14.dp
val InfoIconGap = 12.dp

// Rotary focus ring (DN-M0-011): a 4.dp outline in Accent, or light (OnSurface) on Action-blue fills. docs/02 §16
// asks only for a clearly visible indicator; the width and colours are the app's choice.
val FocusRingWidth = 4.dp
