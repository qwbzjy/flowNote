package com.wenbo.flownote.feature.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wenbo.flownote.domain.model.MemoFilter
import com.wenbo.flownote.domain.model.TagWithCount
import com.wenbo.flownote.ui.theme.FlomoTagBg
import com.wenbo.flownote.ui.theme.FlomoTagGreen
import com.wenbo.flownote.ui.theme.FlomoTextPrimary
import com.wenbo.flownote.ui.theme.FlomoTextSecondary

/** flomo 式标签抽屉：全部笔记 / 标签+计数 / 管理标签 / 备份导入导出。 */
@Composable
fun TagDrawer(
    tags: List<TagWithCount>,
    totalCount: Int,
    currentFilter: MemoFilter,
    isExporting: Boolean,
    isImporting: Boolean,
    onSelectAll: () -> Unit,
    onSelectTag: (Long, String) -> Unit,
    onManageTags: () -> Unit,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit,
) {
    ModalDrawerSheet(drawerShape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 16.dp)) {
            Text(
                "flowNote",
                modifier = Modifier.padding(start = 8.dp, bottom = 16.dp),
                style = MaterialTheme.typography.titleLarge,
                color = FlomoTextPrimary,
            )

            DrawerRow(
                icon = Icons.AutoMirrored.Outlined.Notes,
                label = "全部笔记",
                count = totalCount,
                selected = currentFilter is MemoFilter.All,
                onClick = onSelectAll,
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            if (tags.isEmpty()) {
                Text(
                    "还没有标签\n在正文里输入 #标签 即可创建",
                    modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
                    color = FlomoTextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                )
            }
            tags.forEach { tag ->
                val selected = (currentFilter as? MemoFilter.ByTag)?.tagId == tag.id
                DrawerRow(
                    icon = null,
                    label = "#${tag.name}",
                    count = tag.memoCount,
                    selected = selected,
                    labelColor = FlomoTagGreen,
                    onClick = { onSelectTag(tag.id, tag.name) },
                )
            }

            Spacer(Modifier.weight(1f))

            DrawerRow(
                icon = Icons.Outlined.Settings,
                label = "管理标签",
                count = null,
                selected = false,
                onClick = onManageTags,
            )
            DrawerRow(
                icon = Icons.Outlined.FileUpload,
                label = "导出备份",
                count = null,
                selected = false,
                enabled = !isExporting && !isImporting,
                loading = isExporting,
                onClick = onExportClick,
            )
            DrawerRow(
                icon = Icons.Outlined.FileDownload,
                label = "导入备份",
                count = null,
                selected = false,
                enabled = !isExporting && !isImporting,
                loading = isImporting,
                onClick = onImportClick,
            )
        }
    }
}

@Composable
private fun DrawerRow(
    icon: ImageVector?,
    label: String,
    count: Int?,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    loading: Boolean = false,
    labelColor: androidx.compose.ui.graphics.Color = FlomoTextPrimary,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) FlomoTagBg else androidx.compose.ui.graphics.Color.Transparent,
                RoundedCornerShape(8.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            Icon(
                it,
                contentDescription = null,
                tint = if (enabled) FlomoTextSecondary else FlomoTextSecondary.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.size(10.dp))
        }
        Text(
            label,
            color = if (enabled) labelColor else labelColor.copy(alpha = 0.4f),
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        when {
            loading -> CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            else -> count?.let {
                Text("$it", color = FlomoTextSecondary, fontSize = 12.sp)
            }
        }
    }
}
