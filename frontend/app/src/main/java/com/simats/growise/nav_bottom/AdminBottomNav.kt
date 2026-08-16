package com.simats.growise.nav_bottom

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VerifiedUser
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
import com.simats.growise.ui.theme.GoldenYellow
import com.simats.growise.ui.theme.TerracottaPrimary

@Composable
fun AdminBottomNav(navController: NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val primaryTabs = listOf("home", "verify", "profile")
    if (currentRoute !in primaryTabs) return

    Box(
        modifier = Modifier.fillMaxWidth().height(100.dp).background(Color.Transparent),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(76.dp)
                .shadow(elevation = 16.dp, shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp), spotColor = Color(0x1A000000))
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(Color.White)
        )

        Row(
            modifier = Modifier.fillMaxWidth().height(76.dp).padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            AdminBottomNavItem(
                title = "Dashboard",
                icon = Icons.Filled.Dashboard,
                isSelected = currentRoute == "home",
                onClick = { if (currentRoute != "home") navController.navigate("home") }
            )

            Spacer(modifier = Modifier.width(72.dp))

            AdminBottomNavItem(
                title = "Profile",
                icon = Icons.Filled.Person,
                isSelected = currentRoute == "profile",
                onClick = { if (currentRoute != "profile") navController.navigate("profile") }
            )
        }

        Box(
            modifier = Modifier.offset(y = (-24).dp).size(68.dp)
                .shadow(elevation = 8.dp, shape = CircleShape, spotColor = TerracottaPrimary)
                .clip(CircleShape)
                .background(TerracottaPrimary)
                .clickable { if (currentRoute != "verify") navController.navigate("verify") },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.VerifiedUser,
                contentDescription = "Verification Hub",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
fun RowScope.AdminBottomNavItem(title: String, icon: ImageVector, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier.weight(1f).clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = if (isSelected) TerracottaPrimary else Color.Gray.copy(alpha = 0.6f),
            modifier = Modifier.size(26.dp)
        )
        if (isSelected) {
            Spacer(modifier = Modifier.height(3.dp))
            Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(TerracottaPrimary))
        } else {
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = title, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color.Gray.copy(alpha = 0.6f))
        }
    }
}