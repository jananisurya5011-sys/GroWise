package com.simats.growise.nav_bottom

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState

@Composable
fun UserBottomNav(navController: NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Updated routes for User Panel
    val primaryTabs = listOf("home", "inbox", "status", "profile")

    // Check handles both exact route match and nested dynamic routes (like inbox/123)
    if (currentRoute !in primaryTabs && currentRoute?.startsWith("inbox") != true) return

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .background(Color.Transparent),
        contentAlignment = Alignment.BottomCenter
    ) {
        // High-end curved top container mimicking the premium design framework with subtle shadow
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp)
                .shadow(elevation = 16.dp, shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp), spotColor = Color(0x1A000000))
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(Color.White)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            UserBottomNavItem(
                title = "Home",
                icon = Icons.Filled.Home,
                isSelected = currentRoute == "home",
                onClick = { if (currentRoute != "home") navController.navigate("home") }
            )

            UserBottomNavItem(
                title = "Inbox",
                icon = Icons.Filled.Inbox,
                isSelected = currentRoute == "inbox" || currentRoute?.startsWith("inbox") == true,
                onClick = { if (currentRoute != "inbox") navController.navigate("inbox") }
            )

            // Dynamic spacing element to ensure clearance for overlapping action hub
            Spacer(modifier = Modifier.width(72.dp))

            UserBottomNavItem(
                title = "Status",
                icon = Icons.Filled.LocalShipping,
                isSelected = currentRoute == "status",
                onClick = { if (currentRoute != "status") navController.navigate("status") }
            )

            UserBottomNavItem(
                title = "Profile",
                icon = Icons.Filled.Person,
                isSelected = currentRoute == "profile",
                onClick = { if (currentRoute != "profile") navController.navigate("profile") }
            )
        }

        // Overlapping Central Chat Assistant AI Button Container
        Box(
            modifier = Modifier
                .offset(y = (-24).dp)
                .size(68.dp)
                .shadow(elevation = 8.dp, shape = CircleShape, spotColor = com.simats.growise.ui.theme.GoldenYellow)
                .clip(CircleShape)
                .background(com.simats.growise.ui.theme.GoldenYellow)
                .clickable { if (currentRoute != "ai") navController.navigate("ai") },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.SmartToy,
                contentDescription = "AI Chat Assistant Hub",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
fun RowScope.UserBottomNavItem(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = if (isSelected) com.simats.growise.ui.theme.TerracottaPrimary else Color.Gray.copy(alpha = 0.6f),
            modifier = Modifier.size(26.dp)
        )
        if (isSelected) {
            Spacer(modifier = Modifier.height(3.dp))
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(com.simats.growise.ui.theme.TerracottaPrimary)
            )
        } else {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Gray.copy(alpha = 0.6f)
            )
        }
    }
}