package io.github.drnerdlove.earacle

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The top bar of the search and history pages: back arrow, title, and optional actions at the end. A plain row
 * rather than `TopAppBar`, because the `Scaffold` in [MainActivity] already keeps the status bar free.
 */
@Composable
internal fun PageTopBar(
    title: String,
    backLabel: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(painter = painterResource(R.drawable.ic_arrow_back), contentDescription = backLabel)
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLargeEmphasized,
            // The emphasized style alone looks like the normal weight under MaterialTheme.
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp),
        )
        actions()
    }
}

/** Big corners at the outer ends of a group of list items, small ones between the items. */
internal fun segmentShape(index: Int, count: Int): RoundedCornerShape {
    val corners = segmentCorners(index, count)
    val top = if (corners.bigTop) SegmentBigCorner else SegmentSmallCorner
    val bottom = if (corners.bigBottom) SegmentBigCorner else SegmentSmallCorner
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

private val SegmentBigCorner = 20.dp
private val SegmentSmallCorner = 6.dp

/** The gap between two items of a group. */
internal val SegmentGap = 2.dp
