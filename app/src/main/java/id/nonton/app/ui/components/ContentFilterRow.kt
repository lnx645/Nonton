package id.nonton.app.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import id.nonton.app.R
import id.nonton.app.ui.home.HomeFilter

/** Bar filter ala YouTube: Semua / Video / Live. Dipakai di Beranda & Pencarian. */
@Composable
fun ContentFilterRow(
    selected: HomeFilter,
    onSelect: (HomeFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HomeFilter.entries.forEach { filter ->
            val labelRes = when (filter) {
                HomeFilter.ALL -> R.string.home_filter_all
                HomeFilter.VIDEO -> R.string.home_filter_video
                HomeFilter.LIVE -> R.string.home_filter_live
            }
            FilterChip(
                selected = selected == filter,
                onClick = { onSelect(filter) },
                label = { Text(stringResource(labelRes)) },
            )
        }
    }
}
