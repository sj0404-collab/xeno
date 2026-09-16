package com.xeno.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import com.xeno.ui.achievements.AchievementsScreen
import com.xeno.ui.bios.BiosManagerScreen
import com.xeno.ui.controls.ControllerManagerScreen
import com.xeno.ui.about.AboutScreen
import com.xeno.ui.friends.FriendsScreen
import com.xeno.ui.news.NewsScreen
import com.xeno.ui.home.HomeScreen
import com.xeno.ui.language.LanguageScreen
import com.xeno.ui.saves.SaveManagerScreen
import com.xeno.ui.textures.TextureManagerScreen
import com.xeno.ui.trophies.TrophiesScreen
import com.xeno.ui.settingshub.SettingsScreen

@Composable
fun AppNavigation() {
    val route = UiNavigator.route.value
    val drawerOpen = UiNavigator.drawerOpen.value

    BackHandler(enabled = true) { UiNavigator.back() }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AnimatedContent(
            targetState = route,
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            transitionSpec = {
                val enter = fadeIn(
                    animationSpec = tween(
                        durationMillis = 260,
                        delayMillis = 70,
                        easing = EaseOut,
                    ),
                ) + scaleIn(
                    initialScale = 0.96f,
                    animationSpec = tween(
                        durationMillis = 260,
                        delayMillis = 70,
                        easing = EaseOut,
                    ),
                )
                val isReturning = targetState is AppRoute.Home ||
                    ((initialState is AppRoute.Language || initialState is AppRoute.About) &&
                        targetState is AppRoute.Settings)
                val exit = if (isReturning) {
                    fadeOut(
                        animationSpec = tween(
                            durationMillis = 110,
                            easing = EaseIn,
                        ),
                    ) + scaleOut(
                        targetScale = 1.0f,
                        animationSpec = tween(
                            durationMillis = 110,
                            easing = EaseIn,
                        ),
                    )
                } else {
                    ExitTransition.None
                }
                (enter togetherWith exit).using(SizeTransform(clip = false))
            },
            label = "app-route",
        ) { destination ->
            when (destination) {
                AppRoute.Home -> HomeScreen(
                    onOpenMenu = { UiNavigator.drawerOpen.value = true },
                    onOpenGameSettings = { UiNavigator.navigate(AppRoute.Settings(game = it)) },
                )
                is AppRoute.Settings -> SettingsScreen(
                    initialCategory = destination.category,
                    game = destination.game,
                    onBack = UiNavigator::home,
                    onOpenAbout = { UiNavigator.navigate(AppRoute.About) },
                )
                is AppRoute.BiosManager -> BiosManagerScreen(onBack = UiNavigator::home, game = destination.game)
                AppRoute.PackageInstaller ->
                    com.xeno.ui.packages.PackageInstallerScreen(onBack = UiNavigator::home)
                // The drawer route is the global one: it is opened from the library, where no
                // title is selected, so an edit here is meant for every game. The in-game menu
                // opens the same screen in the running title's scope (see WindowImpl).
                AppRoute.CoreSettings ->
                    com.xeno.ui.settings.CoreSettingsScreen(
                        onBack = UiNavigator::home,
                        scope = com.xeno.config.SettingsScope.Global,
                        serial = null,
                    )
                AppRoute.SaveManager -> SaveManagerScreen(onBack = UiNavigator::home)
                AppRoute.ControllerManager -> ControllerManagerScreen(onBack = UiNavigator::home)
                AppRoute.TextureManager -> TextureManagerScreen(onBack = UiNavigator::home)
                AppRoute.Achievements -> AchievementsScreen(onBack = UiNavigator::home)
                AppRoute.Trophies -> TrophiesScreen(onBack = UiNavigator::home)
                AppRoute.Language -> LanguageScreen(
                    onBack = { UiNavigator.navigate(AppRoute.Settings(SettingsCategory.General)) },
                )
                // Back goes to the library, like every other drawer destination (Memory Cards,
                // Controls, Patches...). It used to return to the Settings tab, which was already
                // odd and became simply wrong once About moved out of the settings tab strip and
                // into the drawer — you were sent to a screen you had not come from.
                AppRoute.News -> NewsScreen(onBack = UiNavigator::home)
                AppRoute.Friends -> FriendsScreen(onBack = UiNavigator::home)
                AppRoute.About -> AboutScreen(onBack = UiNavigator::home)
            }
        }

        NavigationDrawer(
            visible = drawerOpen,
            selected = route,
            onDismiss = { UiNavigator.drawerOpen.value = false },
            onNavigate = UiNavigator::navigate,
        )
    }
}
