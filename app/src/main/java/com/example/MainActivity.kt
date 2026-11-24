package com.example

import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.auth.attemptAutoSignIn
import com.example.auth.signOut
import com.example.data.QuizRepository
import com.example.data.SettingsRepository
import com.example.data.ThemePreference
import com.example.ui.home.HomeScreen
import com.example.ui.home.HomeViewModel
import com.example.ui.screens.AuthScreen
import com.example.ui.student.StudentPlayScreen
import com.example.ui.student.StudentPlayViewModel
import com.example.ui.teacher.TeacherGameScreen
import com.example.ui.teacher.TeacherGameViewModel
import com.example.ui.theme.QuizClassTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {
  override fun attachBaseContext(newBase: Context) {
    val hebrewLocale = Locale.forLanguageTag("he-IL")
    Locale.setDefault(hebrewLocale)
    val config = Configuration(newBase.resources.configuration)
    config.setLocale(hebrewLocale)
    config.setLayoutDirection(hebrewLocale)
    super.attachBaseContext(newBase.createConfigurationContext(config))
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    val settingsRepository = SettingsRepository(applicationContext)
    setContent {
      val themePreference by
        settingsRepository.themePreference.collectAsStateWithLifecycle(
          initialValue = ThemePreference.SYSTEM
        )
      val scope = rememberCoroutineScope()
      val isDark =
        when (themePreference) {
          ThemePreference.SYSTEM -> isSystemInDarkTheme()
          ThemePreference.LIGHT -> false
          ThemePreference.DARK -> true
        }

      CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        QuizClassTheme(darkTheme = isDark) {
          AppContent(
            themePreference = themePreference,
            onSelectTheme = { pref ->
              scope.launch { settingsRepository.setThemePreference(pref) }
            },
          )
        }
      }
    }
  }
}

@Composable
private fun AppContent(
  themePreference: ThemePreference,
  onSelectTheme: (ThemePreference) -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }

  var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

  DisposableEffect(Unit) {
    val listener = FirebaseAuth.AuthStateListener { auth -> currentUser = auth.currentUser }
    Firebase.auth.addAuthStateListener(listener)
    onDispose { Firebase.auth.removeAuthStateListener(listener) }
  }

  LaunchedEffect(Unit) {
    if (Firebase.auth.currentUser == null) {
      attemptAutoSignIn(
        context = context,
        credentialManager = credentialManager,
        onAuthSuccess = { currentUser = Firebase.auth.currentUser },
        onUnauthenticated = {},
        scope = scope,
      )
    }
  }

  val user = currentUser
  if (user == null) {
    AuthScreen(onAuthSuccess = { currentUser = Firebase.auth.currentUser })
  } else {
    val uid = user.uid
    key(uid) {
      AuthenticatedNavGraph(
        currentUserId = uid,
        themePreference = themePreference,
        onSelectTheme = onSelectTheme,
        onSignOut = {
          signOut(
            credentialManager = credentialManager,
            scope = scope,
            onSignOutComplete = { currentUser = null },
          )
        },
      )
    }
  }
}

private const val ROUTE_HOME = "home"
private const val ROUTE_TEACHER_GAME = "teacher/{gameId}"
private const val ROUTE_STUDENT_PLAY = "student/{gameId}?name={name}&email={email}"

@Composable
private fun AuthenticatedNavGraph(
  currentUserId: String,
  themePreference: ThemePreference,
  onSelectTheme: (ThemePreference) -> Unit,
  onSignOut: () -> Unit,
) {
  val navController = rememberNavController()

  NavHost(navController = navController, startDestination = ROUTE_HOME) {
    composable(ROUTE_HOME) {
      val homeViewModel: HomeViewModel =
        viewModel(
          key = "home_$currentUserId",
          factory =
            viewModelFactory {
              initializer {
                val app =
                  checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                  }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                HomeViewModel(QuizRepository(db), currentUserId)
              }
            },
        )
      HomeScreen(
        viewModel = homeViewModel,
        themePreference = themePreference,
        onSelectTheme = onSelectTheme,
        onOpenTeacherGame = { gameId ->
          navController.navigate("teacher/${Uri.encode(gameId)}")
        },
        onOpenStudentGame = { gameId, name, email ->
          navController.navigate(
            "student/${Uri.encode(gameId)}?name=${Uri.encode(name)}&email=${Uri.encode(email)}"
          )
        },
        onSignOut = onSignOut,
      )
    }

    composable(
      route = ROUTE_TEACHER_GAME,
      arguments = listOf(navArgument("gameId") { type = NavType.StringType }),
    ) { backStackEntry ->
      val gameId = backStackEntry.arguments?.getString("gameId").orEmpty()
      val teacherViewModel: TeacherGameViewModel =
        viewModel(
          key = "teacher_${currentUserId}_$gameId",
          factory =
            viewModelFactory {
              initializer {
                val app =
                  checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                  }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                TeacherGameViewModel(QuizRepository(db), gameId, currentUserId)
              }
            },
        )
      TeacherGameScreen(
        viewModel = teacherViewModel,
        onNavigateBack = { navController.popBackStack() },
      )
    }

    composable(
      route = ROUTE_STUDENT_PLAY,
      arguments =
        listOf(
          navArgument("gameId") { type = NavType.StringType },
          navArgument("name") { type = NavType.StringType; defaultValue = "" },
          navArgument("email") { type = NavType.StringType; defaultValue = "" },
        ),
    ) { backStackEntry ->
      val gameId = backStackEntry.arguments?.getString("gameId").orEmpty()
      val studentName = backStackEntry.arguments?.getString("name").orEmpty()
      val studentEmail = backStackEntry.arguments?.getString("email").orEmpty()
      val studentViewModel: StudentPlayViewModel =
        viewModel(
          key = "student_${currentUserId}_$gameId",
          factory =
            viewModelFactory {
              initializer {
                val app =
                  checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                  }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                StudentPlayViewModel(QuizRepository(db), gameId, currentUserId)
              }
            },
        )
      StudentPlayScreen(
        viewModel = studentViewModel,
        studentName = studentName,
        studentEmail = studentEmail,
        onNavigateBack = { navController.popBackStack() },
      )
    }
  }
}
