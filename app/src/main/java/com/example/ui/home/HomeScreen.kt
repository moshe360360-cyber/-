package com.example.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.GameStatus
import com.example.data.QuizGame
import com.example.data.ThemePreference
import com.example.data.UiState
import com.example.ui.theme.AmberContainer
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
  viewModel: HomeViewModel,
  themePreference: ThemePreference,
  onSelectTheme: (ThemePreference) -> Unit,
  onOpenTeacherGame: (String) -> Unit,
  onOpenStudentGame: (gameId: String, studentName: String, studentEmail: String) -> Unit,
  onSignOut: () -> Unit,
) {
  val currentUser = remember { Firebase.auth.currentUser }
  val gamesState by viewModel.teacherGames.collectAsStateWithLifecycle()
  val actionState by viewModel.actionState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }

  // 0 = Teacher Hub, 1 = Student Join
  var selectedTab by rememberSaveable { mutableIntStateOf(0) }
  var showCreateDialog by rememberSaveable { mutableStateOf(false) }
  var showSettingsDialog by rememberSaveable { mutableStateOf(false) }

  LaunchedEffect(actionState) {
    when (val state = actionState) {
      is HomeActionState.GameCreated -> {
        showCreateDialog = false
        viewModel.resetActionState()
        onOpenTeacherGame(state.gameId)
      }
      is HomeActionState.StudentJoined -> {
        viewModel.resetActionState()
        onOpenStudentGame(state.gameId, state.studentName, state.studentEmail)
      }
      is HomeActionState.Error -> {
        snackbarHostState.showSnackbar(state.message)
        viewModel.resetActionState()
      }
      else -> Unit
    }
  }

  Scaffold(
    contentWindowInsets = WindowInsets.safeDrawing,
    snackbarHost = { SnackbarHost(snackbarHostState) },
    topBar = {
      TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.background,
        ),
        title = {
          Column {
            Text(
              text = stringResource(R.string.app_name),
              style = MaterialTheme.typography.titleLarge,
            )
            Text(
              text = currentUser?.displayName ?: currentUser?.email ?: "",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        },
        actions = {
          IconButton(
            onClick = { showSettingsDialog = true },
            modifier = Modifier.testTag("settings_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.Settings,
              contentDescription = stringResource(R.string.settings_title),
            )
          }
          IconButton(
            onClick = onSignOut,
            modifier = Modifier.testTag("sign_out_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.Logout,
              contentDescription = stringResource(R.string.sign_out),
            )
          }
        },
      )
    },
    floatingActionButton = {
      if (selectedTab == 0) {
        ExtendedFloatingActionButton(
          onClick = { showCreateDialog = true },
          icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
          text = { Text(stringResource(R.string.action_create_game)) },
          containerColor = MaterialTheme.colorScheme.primary,
          contentColor = MaterialTheme.colorScheme.onPrimary,
          modifier = Modifier.testTag("create_game_fab"),
        )
      }
    },
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
      contentAlignment = Alignment.TopCenter,
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .widthIn(max = 680.dp)
          .padding(horizontal = 16.dp),
      ) {
        SingleChoiceSegmentedButtonRow(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        ) {
          SegmentedButton(
            selected = selectedTab == 0,
            onClick = { selectedTab = 0 },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            icon = {
              Icon(
                imageVector = Icons.Rounded.School,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
            },
            modifier = Modifier.testTag("tab_teacher_mode"),
          ) {
            Text(stringResource(R.string.tab_teacher_hub))
          }
          SegmentedButton(
            selected = selectedTab == 1,
            onClick = { selectedTab = 1 },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            icon = {
              Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
            },
            modifier = Modifier.testTag("tab_student_mode"),
          ) {
            Text(stringResource(R.string.tab_student_join))
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (selectedTab == 0) {
          TeacherGamesSection(
            gamesState = gamesState,
            onOpenGame = onOpenTeacherGame,
            onCreateClick = { showCreateDialog = true },
          )
        } else {
          StudentJoinSection(
            defaultName = currentUser?.displayName.orEmpty(),
            defaultEmail = currentUser?.email.orEmpty(),
            isLoading = actionState is HomeActionState.Loading,
            onJoin = { code, name, email ->
              viewModel.joinGameAsStudent(code, name, email)
            },
          )
        }
      }
    }
  }

  if (showCreateDialog) {
    CreateGameDialog(
      isSaving = actionState is HomeActionState.Loading,
      onDismiss = { if (actionState !is HomeActionState.Loading) showCreateDialog = false },
      onConfirm = { title, desc -> viewModel.createGame(title, desc) },
    )
  }

  if (showSettingsDialog) {
    SettingsDialog(
      userName = currentUser?.displayName.orEmpty(),
      userEmail = currentUser?.email.orEmpty(),
      themePreference = themePreference,
      onSelectTheme = onSelectTheme,
      onSignOut = onSignOut,
      onDismiss = { showSettingsDialog = false },
    )
  }
}

@Composable
private fun TeacherGamesSection(
  gamesState: UiState<List<QuizGame>>,
  onOpenGame: (String) -> Unit,
  onCreateClick: () -> Unit,
) {
  when (gamesState) {
    is UiState.Loading -> {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
      }
    }
    is UiState.Error -> {
      Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          text = gamesState.message,
          color = MaterialTheme.colorScheme.onErrorContainer,
          modifier = Modifier.padding(16.dp),
        )
      }
    }
    is UiState.Success -> {
      val games = gamesState.data
      if (games.isEmpty()) {
        Card(
          shape = MaterialTheme.shapes.large,
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        ) {
          Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Surface(
              color = MaterialTheme.colorScheme.primaryContainer,
              shape = CircleShape,
              modifier = Modifier.size(60.dp),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = Icons.Rounded.Quiz,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onPrimaryContainer,
                  modifier = Modifier.size(30.dp),
                )
              }
            }
            Text(
              text = stringResource(R.string.teacher_hub_title),
              style = MaterialTheme.typography.titleLarge,
            )
            Text(
              text = stringResource(R.string.teacher_hub_empty),
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
              onClick = onCreateClick,
              modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("empty_create_game_button"),
            ) {
              Icon(Icons.Rounded.Add, contentDescription = null)
              Spacer(modifier = Modifier.width(8.dp))
              Text(stringResource(R.string.action_create_game))
            }
          }
        }
      } else {
        LazyColumn(
          contentPadding = PaddingValues(bottom = 96.dp, top = 4.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          item {
            Text(
              text = stringResource(R.string.teacher_hub_title),
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(vertical = 4.dp),
            )
          }
          items(games, key = { it.id }) { game ->
            TeacherGameCard(game = game, onClick = { onOpenGame(game.id) })
          }
        }
      }
    }
  }
}

@Composable
private fun TeacherGameCard(game: QuizGame, onClick: () -> Unit) {
  val isActive = game.gameStatus() == GameStatus.ACTIVE
  Card(
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .testTag("teacher_game_card_${game.id}"),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Surface(
        color = AmberContainer,
        shape = RoundedCornerShape(14.dp),
      ) {
        Column(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Text(
            text = "קוד",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            text = game.id,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
          )
        }
      }

      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = game.title,
          style = MaterialTheme.typography.titleMedium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Surface(
            color = if (isActive) MaterialTheme.colorScheme.tertiaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
            shape = CircleShape,
          ) {
            Text(
              text = if (isActive) stringResource(R.string.status_active) else stringResource(R.string.status_ended),
              style = MaterialTheme.typography.labelSmall,
              color = if (isActive) MaterialTheme.colorScheme.onTertiaryContainer
              else MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
          }
          Text(
            text = "${game.questionCount} שאלות",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Icon(
        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
        contentDescription = "פתיחת חידון",
        tint = MaterialTheme.colorScheme.primary,
      )
    }
  }
}

@Composable
private fun StudentJoinSection(
  defaultName: String,
  defaultEmail: String,
  isLoading: Boolean,
  onJoin: (code: String, name: String, email: String) -> Unit,
) {
  var gameCode by rememberSaveable { mutableStateOf("") }
  var studentName by rememberSaveable { mutableStateOf(defaultName) }
  var studentEmail by rememberSaveable { mutableStateOf(defaultEmail) }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Card(
      shape = MaterialTheme.shapes.large,
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Text(
          text = stringResource(R.string.student_join_title),
          style = MaterialTheme.typography.headlineSmall,
        )
        Text(
          text = stringResource(R.string.student_join_subtitle),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
          value = gameCode,
          onValueChange = { input ->
            gameCode = input.uppercase().filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(12)
          },
          label = { Text(stringResource(R.string.label_game_code)) },
          leadingIcon = { Icon(Icons.Rounded.Key, contentDescription = null) },
          singleLine = true,
          keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("student_game_code_input"),
        )

        OutlinedTextField(
          value = studentName,
          onValueChange = { studentName = it.take(80) },
          label = { Text(stringResource(R.string.label_student_name)) },
          leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("student_name_input"),
        )

        OutlinedTextField(
          value = studentEmail,
          onValueChange = { studentEmail = it.take(120) },
          label = { Text(stringResource(R.string.label_student_email)) },
          leadingIcon = { Icon(Icons.Rounded.Email, contentDescription = null) },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("student_email_input"),
        )

        Button(
          onClick = { onJoin(gameCode, studentName, studentEmail) },
          enabled = !isLoading,
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary,
          ),
          shape = MaterialTheme.shapes.medium,
          modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .testTag("student_join_button"),
        ) {
          if (isLoading) {
            CircularProgressIndicator(
              modifier = Modifier.size(22.dp),
              color = MaterialTheme.colorScheme.onSecondary,
              strokeWidth = 2.dp,
            )
          } else {
            Text(
              text = stringResource(R.string.action_join_game),
              style = MaterialTheme.typography.titleMedium,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun CreateGameDialog(
  isSaving: Boolean,
  onDismiss: () -> Unit,
  onConfirm: (title: String, description: String) -> Unit,
) {
  var title by rememberSaveable { mutableStateOf("") }
  var description by rememberSaveable { mutableStateOf("") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.dialog_create_game_title)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
          value = title,
          onValueChange = { title = it.take(100) },
          label = { Text(stringResource(R.string.label_game_title)) },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("create_game_title_input"),
        )
        OutlinedTextField(
          value = description,
          onValueChange = { description = it.take(300) },
          label = { Text(stringResource(R.string.label_game_description)) },
          maxLines = 3,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("create_game_desc_input"),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { onConfirm(title, description) },
        enabled = !isSaving && title.trim().isNotEmpty(),
        modifier = Modifier.testTag("confirm_create_game_button"),
      ) {
        if (isSaving) {
          CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
          Text(stringResource(R.string.action_create))
        }
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss, enabled = !isSaving) {
        Text(stringResource(R.string.action_cancel))
      }
    },
  )
}
