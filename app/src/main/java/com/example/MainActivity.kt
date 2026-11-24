package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.outlined.Functions
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.screens.FormulasScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.TestWorkspaceScreen
import com.example.ui.theme.InfiltrometroTheme
import com.example.ui.viewmodel.InfiltrationViewModel

object AppRoutes {
    const val ROUTE_HOME = "home"
    const val ROUTE_FORMULAS = "formulas"
    const val ROUTE_WORKSPACE = "workspace/{testId}"
    fun workspace(testId: Long) = "workspace/$testId"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            InfiltrometroTheme {
                InfiltrometroApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfiltrometroApp(viewModel: InfiltrationViewModel = viewModel()) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val homeState by viewModel.homeUiState.collectAsStateWithLifecycle()
    val workspaceState by viewModel.workspaceUiState.collectAsStateWithLifecycle()

    val isTopLevel = currentRoute == AppRoutes.ROUTE_HOME || currentRoute == AppRoutes.ROUTE_FORMULAS
    val isWideScreen = LocalConfiguration.current.screenWidthDp >= 600

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (isTopLevel && !isWideScreen) {
                NavigationBar(
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    NavigationBarItem(
                        selected = currentRoute == AppRoutes.ROUTE_HOME,
                        onClick = {
                            navController.navigate(AppRoutes.ROUTE_HOME) {
                                popUpTo(AppRoutes.ROUTE_HOME) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                        icon = {
                            Icon(
                                if (currentRoute == AppRoutes.ROUTE_HOME) Icons.Filled.WaterDrop else Icons.Outlined.WaterDrop,
                                contentDescription = stringResource(R.string.nav_tests)
                            )
                        },
                        label = { Text(stringResource(R.string.nav_tests)) },
                        modifier = Modifier.testTag("nav_tests")
                    )
                    NavigationBarItem(
                        selected = currentRoute == AppRoutes.ROUTE_FORMULAS,
                        onClick = {
                            navController.navigate(AppRoutes.ROUTE_FORMULAS) {
                                popUpTo(AppRoutes.ROUTE_HOME)
                                launchSingleTop = true
                            }
                        },
                        icon = {
                            Icon(
                                if (currentRoute == AppRoutes.ROUTE_FORMULAS) Icons.Filled.Functions else Icons.Outlined.Functions,
                                contentDescription = stringResource(R.string.nav_formulas)
                            )
                        },
                        label = { Text(stringResource(R.string.nav_formulas)) },
                        modifier = Modifier.testTag("nav_formulas")
                    )
                }
            }
        }
    ) { innerPadding ->
        Row(modifier = Modifier.fillMaxSize()) {
            if (isTopLevel && isWideScreen) {
                NavigationRail {
                    NavigationRailItem(
                        selected = currentRoute == AppRoutes.ROUTE_HOME,
                        onClick = {
                            navController.navigate(AppRoutes.ROUTE_HOME) {
                                popUpTo(AppRoutes.ROUTE_HOME) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                        icon = {
                            Icon(
                                if (currentRoute == AppRoutes.ROUTE_HOME) Icons.Filled.WaterDrop else Icons.Outlined.WaterDrop,
                                contentDescription = stringResource(R.string.nav_tests)
                            )
                        },
                        label = { Text(stringResource(R.string.nav_tests)) }
                    )
                    NavigationRailItem(
                        selected = currentRoute == AppRoutes.ROUTE_FORMULAS,
                        onClick = {
                            navController.navigate(AppRoutes.ROUTE_FORMULAS) {
                                popUpTo(AppRoutes.ROUTE_HOME)
                                launchSingleTop = true
                            }
                        },
                        icon = {
                            Icon(
                                if (currentRoute == AppRoutes.ROUTE_FORMULAS) Icons.Filled.Functions else Icons.Outlined.Functions,
                                contentDescription = stringResource(R.string.nav_formulas)
                            )
                        },
                        label = { Text(stringResource(R.string.nav_formulas)) }
                    )
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                NavHost(
                    navController = navController,
                    startDestination = AppRoutes.ROUTE_HOME
                ) {
                    composable(AppRoutes.ROUTE_HOME) {
                        HomeScreen(
                            uiState = homeState,
                            onOpenTest = { id ->
                                viewModel.selectTest(id)
                                navController.navigate(AppRoutes.workspace(id))
                            },
                            onCreateTest = { name, diam, outerDiam, dens, tempC, h0, notes ->
                                viewModel.createNewTest(name, diam, outerDiam, dens, tempC, h0, notes) { newId ->
                                    navController.navigate(AppRoutes.workspace(newId))
                                }
                            },
                            onDeleteTest = { id -> viewModel.deleteTest(id) },
                            onLoadSample = { viewModel.loadSampleTest() },
                            bottomPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding())
                        )
                    }

                    composable(AppRoutes.ROUTE_FORMULAS) {
                        Scaffold(
                            topBar = {
                                TopAppBar(
                                    title = {
                                        Text(
                                            text = "Fórmulas y Conexiones",
                                            style = MaterialTheme.typography.titleLarge
                                        )
                                    }
                                )
                            }
                        ) { topPad ->
                            FormulasScreen(
                                contentPadding = PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = topPad.calculateTopPadding() + 12.dp,
                                    bottom = innerPadding.calculateBottomPadding() + 16.dp
                                )
                            )
                        }
                    }

                    composable(
                        route = AppRoutes.ROUTE_WORKSPACE,
                        arguments = listOf(navArgument("testId") { type = NavType.LongType })
                    ) { entry ->
                        val testId = entry.arguments?.getLong("testId") ?: 0L
                        viewModel.selectTest(testId)
                        TestWorkspaceScreen(
                            uiState = workspaceState,
                            uiMessages = viewModel.uiMessages,
                            onBack = { navController.popBackStack() },
                            onUpdateParams = { test, name, diam, outerDiam, dens, tempC, notes ->
                                viewModel.updateTestParameters(test, name, diam, outerDiam, dens, tempC, notes)
                            },
                            onCaptureGps = { test -> viewModel.captureGpsLocation(test) },
                            onAttachPhoto = { test, uri -> viewModel.attachSitePhoto(test, uri) },
                            onRemovePhoto = { test -> viewModel.removeSitePhoto(test) },
                            onAddRow = { id, dt, refill, h ->
                                viewModel.addIntervalRow(id, dt, refill, h)
                            },
                            onUpdateRow = { row, dt, refill, h ->
                                viewModel.updateIntervalRow(row, dt, refill, h)
                            },
                            onDeleteRow = { row ->
                                viewModel.deleteIntervalRow(row)
                            },
                            onExport = { fmt -> viewModel.exportTest(fmt) }
                        )
                    }
                }
            }
        }
    }
}
