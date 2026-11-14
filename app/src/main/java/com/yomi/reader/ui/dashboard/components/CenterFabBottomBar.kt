package com.yomi.reader.ui.dashboard.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val YomiNavPurple = Color(0xFF8C43FF)

@Composable
fun CenterFabBottomBar(
    selectedTab: Int, // 0 = Home, 1 = Search, 2 = History
    onSelectTab: (Int) -> Unit,
    onFabClick: () -> Unit,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .height(82.dp)
    ) {
        // Rounded card navigation surface
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
            tonalElevation = 2.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.85f)),
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .align(Alignment.BottomCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tab 1: Home
                NavItem(
                    icon = if (selectedTab == 0) Icons.Filled.Home else Icons.Outlined.Home,
                    label = "Home",
                    isSelected = selectedTab == 0,
                    onClick = { onSelectTab(0) },
                    modifier = Modifier.weight(1f)
                )

                // Tab 2: Folders
                NavItem(
                    icon = if (selectedTab == 1) Icons.Filled.Folder else Icons.Outlined.Folder,
                    label = "Folders",
                    isSelected = selectedTab == 1,
                    onClick = { onSelectTab(1) },
                    modifier = Modifier.weight(1f)
                )

                // Center gap reserved for the elevated FAB
                Spacer(modifier = Modifier.width(64.dp))

                // Tab 3: History
                NavItem(
                    icon = if (selectedTab == 2) Icons.Filled.Schedule else Icons.Outlined.Schedule,
                    label = "History",
                    isSelected = selectedTab == 2,
                    onClick = { onSelectTab(2) },
                    modifier = Modifier.weight(1f)
                )

                // Tab 4: Profile
                NavItem(
                    icon = Icons.Outlined.Person,
                    label = "Profile",
                    isSelected = false,
                    onClick = onProfileClick,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Elevated Center Plus Button with Halo Ring
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(60.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface) // Halo cutout matching container
                .padding(3.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(YomiNavPurple)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true, color = Color.White),
                        onClick = onFabClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Import document",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun NavItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeColor = YomiNavPurple
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    
    val animatedColor by animateColorAsState(
        targetValue = if (isSelected) activeColor else inactiveColor,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "NavColor"
    )

    val iconScale by animateFloatAsState(
        targetValue = if (isSelected) 1.15f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "NavIconScale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = activeColor.copy(alpha = 0.15f)),
                onClick = onClick
            )
            .padding(vertical = 4.dp)
    ) {
        // No background color behind the icon; icon color fills to purple when selected
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = animatedColor,
            modifier = Modifier
                .size(24.dp)
                .scale(iconScale)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = animatedColor,
            maxLines = 1
        )
    }
}
