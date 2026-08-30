

package epsilon.music.***REMOVED***.ui.utils

import androidx.navigation.NavController
import epsilon.music.***REMOVED***.ui.screens.Screens

fun NavController.backToMain() {
    val mainRoutes = Screens.MainScreens.map { it.route }

    while (previousBackStackEntry != null &&
        currentBackStackEntry?.destination?.route !in mainRoutes
    ) {
        popBackStack()
    }
}
