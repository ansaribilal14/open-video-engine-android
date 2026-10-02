package app.ove.studio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.ove.studio.editor.EditorScreen
import app.ove.studio.editor.EditorViewModel
import app.ove.studio.engine.OveClient
import app.ove.studio.home.HomeScreen
import app.ove.studio.home.HomeViewModel
import app.ove.studio.settings.SettingsScreen
import app.ove.studio.ui.theme.OveTheme
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val client = OveClient()
        setContent {
            OveTheme {
                OveApp(client)
            }
        }
    }
}

@Composable
fun OveApp(client: OveClient) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val vm: HomeViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = HomeViewModel.Factory(ctx.applicationContext, client),
            )
            HomeScreen(
                viewModel = vm,
                onOpenProject = { p, isNew ->
                    nav.navigate("editor?name=${java.net.URLEncoder.encode(p.name, "UTF-8")}&dir=${p.dirName}&new=$isNew")
                },
                onOpenSettings = { nav.navigate("settings") },
            )
        }
        composable(
            route = "editor?name={name}&dir={dir}&new={new}",
            arguments = listOf(
                navArgument("name") { defaultValue = "" },
                navArgument("dir") { defaultValue = "" },
                navArgument("new") { defaultValue = "false" },
            ),
        ) { entry ->
            val application = androidx.compose.ui.platform.LocalContext.current.applicationContext as android.app.Application
            val vm: EditorViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = EditorViewModel.Factory(client, application),
                key = entry.arguments?.getString("dir"),
            )
            val projectDir = (entry.arguments?.getString("dir").orEmpty()).let { d ->
                val registry = app.ove.studio.project.ProjectRegistry(application)
                registry.dirFor(
                    app.ove.studio.project.ProjectEntry(d, d, 0L, 0L),
                ).absolutePath
            }
            // v0.1.2 self-heal: a project whose engine folder is missing
            // (legacy interrupted create) is RECREATED by opening it — the
            // folder genuinely doesn't exist, so Engine::create is valid.
            // "Engine folder missing" is no longer a dead-end state.
            val needsCreate = remember(projectDir) {
                (entry.arguments?.getString("new") == "true") || !File(projectDir).exists()
            }
            EditorScreen(
                projectName = entry.arguments?.getString("name").orEmpty(),
                projectDir = projectDir,
                needsCreate = needsCreate,
                viewModel = vm,
                onBack = { nav.popBackStack() },
                onOpenSettings = { nav.navigate("settings") },
            )
        }
        composable("settings") {
            SettingsScreen(
                client = client,
                onBack = { nav.popBackStack() },
            )
        }
    }
}
