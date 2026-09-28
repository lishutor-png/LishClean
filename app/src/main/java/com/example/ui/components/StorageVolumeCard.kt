package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.StorageType
import com.example.model.StorageVolumeInfo

@Composable
fun StorageVolumeCard(
    volume: StorageVolumeInfo,
    isSelected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onToggle)
            .testTag("storage_card_${volume.id}"),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                when (volume.storageType) {
                                    StorageType.INTERNAL -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    StorageType.EXTERNAL_SD -> Color(0xFF0D9488).copy(alpha = 0.18f)
                                    StorageType.USB_OTG -> Color(0xFFD97706).copy(alpha = 0.18f)
                                    StorageType.CUSTOM -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (volume.storageType) {
                                StorageType.INTERNAL -> Icons.Default.Smartphone
                                StorageType.EXTERNAL_SD -> Icons.Default.SdCard
                                StorageType.USB_OTG -> Icons.Default.Usb
                                StorageType.CUSTOM -> Icons.Default.SdCard
                            },
                            contentDescription = volume.name,
                            tint = when (volume.storageType) {
                                StorageType.INTERNAL -> MaterialTheme.colorScheme.primary
                                StorageType.EXTERNAL_SD -> Color(0xFF0D9488)
                                StorageType.USB_OTG -> Color(0xFFD97706)
                                StorageType.CUSTOM -> MaterialTheme.colorScheme.tertiary
                            },
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = volume.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            // Badge indicating Internal or External
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        when (volume.storageType) {
                                            StorageType.INTERNAL -> MaterialTheme.colorScheme.primaryContainer
                                            StorageType.EXTERNAL_SD -> Color(0xFFCCFBF1)
                                            StorageType.USB_OTG -> Color(0xFFFEF3C7)
                                            StorageType.CUSTOM -> MaterialTheme.colorScheme.tertiaryContainer
                                        }
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = when (volume.storageType) {
                                        StorageType.INTERNAL -> "INTERNAL"
                                        StorageType.EXTERNAL_SD -> "EKSTERNAL SD"
                                        StorageType.USB_OTG -> "USB OTG"
                                        StorageType.CUSTOM -> "FOLDER"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when (volume.storageType) {
                                        StorageType.INTERNAL -> MaterialTheme.colorScheme.onPrimaryContainer
                                        StorageType.EXTERNAL_SD -> Color(0xFF115E59)
                                        StorageType.USB_OTG -> Color(0xFF92400E)
                                        StorageType.CUSTOM -> MaterialTheme.colorScheme.onTertiaryContainer
                                    },
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 9.sp
                                )
                            }
                        }
                        Text(
                            text = volume.path,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }

                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggle() },
                    modifier = Modifier.testTag("checkbox_${volume.id}")
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Storage Gauge
            LinearProgressIndicator(
                progress = { volume.usagePercentage },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = when {
                    volume.usagePercentage > 0.9f -> MaterialTheme.colorScheme.error
                    volume.usagePercentage > 0.75f -> Color(0xFFF59E0B)
                    volume.storageType != StorageType.INTERNAL -> Color(0xFF0D9488)
                    else -> MaterialTheme.colorScheme.primary
                },
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Terpakai: ${volume.formattedUsed} (${(volume.usagePercentage * 100).toInt()}%)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Bebas: ${volume.formattedFree} / ${volume.formattedTotal}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
