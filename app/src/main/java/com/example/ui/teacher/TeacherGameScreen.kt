package com.example.ui.teacher

import android.content.Intent
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.GameStatus
import com.example.data.QuestionType
import com.example.data.QuizGame
import com.example.data.QuizQuestion
import com.example.data.StudentSubmission
import com.example.data.UiState
import com.example.ui.theme.AmberContainer
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherGameScreen(
  viewModel: TeacherGameViewModel,
  onNavigateBack: () -> Unit,
) {
  val context = LocalContext.current
  val clipboardManager = LocalClipboardManager.current
  val snackbarHostState = remember { SnackbarHostState() }

  val gameState by viewModel.gameState.collectAsStateWithLifecycle()
  val questionsState by viewModel.questionsState.collectAsStateWithLifecycle()
  val submissionsState by viewModel.submissionsState.collectAsStateWithLifecycle()
  val mutationState by viewModel.mutationState.collectAsStateWithLifecycle()

  // 0 = Live Student Answers Inbox, 1 = Questions Builder
  var selectedTab by rememberSaveable { mutableIntStateOf(0) }
  var showAddQuestionDialog by rememberSaveable { mutableStateOf(false) }
  var selectedSubmission by remember { mutableStateOf<StudentSubmission?>(null) }

  LaunchedEffect(mutationState) {
    val msg = mutationState as? TeacherMutationState.Message ?: return@LaunchedEffect
    if (!msg.isError) {
      showAddQuestionDialog = false
    }
    snackbarHostState.showSnackbar(msg.text)
    viewModel.clearMessage()
  }

  val loadedGame = (gameState as? UiState.Success)?.data
  val loadedQuestions = (questionsState as? UiState.Success)?.data.orEmpty()
  val loadedSubmissions = (submissionsState as? UiState.Success)?.data.orEmpty()

  Scaffold(
    contentWindowInsets = WindowInsets.safeDrawing,
    snackbarHost = { SnackbarHost(snackbarHostState) },
    topBar = {
      TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.background,
        ),
        navigationIcon = {
          IconButton(
            onClick = onNavigateBack,
            modifier = Modifier.testTag("teacher_back_button"),
          ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "חזרה")
          }
        },
        title = {
          Text(
            text = loadedGame?.title ?: "ניהול חידון",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        },
        actions = {
          if (loadedGame != null) {
            val isActive = loadedGame.gameStatus() == GameStatus.ACTIVE
            IconButton(
              onClick = { viewModel.toggleGameStatus(loadedGame.gameStatus()) },
              modifier = Modifier.testTag("toggle_game_status_button"),
            ) {
              Icon(
                imageVector = if (isActive) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                contentDescription = if (isActive) "סגירת חידון" else "פתיחת חידון",
                tint = if (isActive) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
              )
            }
          }
        },
      )
    },
    floatingActionButton = {
      if (loadedGame != null && selectedTab == 1) {
        ExtendedFloatingActionButton(
          onClick = { showAddQuestionDialog = true },
          icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
          text = { Text(stringResource(R.string.action_add_question)) },
          containerColor = MaterialTheme.colorScheme.primary,
          contentColor = MaterialTheme.colorScheme.onPrimary,
          modifier = Modifier.testTag("add_question_fab"),
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
          .widthIn(max = 700.dp)
          .padding(horizontal = 16.dp),
      ) {
        if (loadedGame != null) {
          JoinCodeBanner(
            game = loadedGame,
            onCopyCode = {
              clipboardManager.setText(AnnotatedString(loadedGame.id))
            },
            onShareCode = {
              val shareIntent =
                Intent(Intent.ACTION_SEND).apply {
                  type = "text/plain"
                  putExtra(
                    Intent.EXTRA_TEXT,
                    "הצטרפו לחידון \"${loadedGame.title}\" באפליקציית חידון כיתה! קוד כניסה: ${loadedGame.id}",
                  )
                }
              context.startActivity(Intent.createChooser(shareIntent, "שיתוף קוד משחק"))
            },
          )
        }

        TabRow(selectedTabIndex = selectedTab) {
          Tab(
            selected = selectedTab == 0,
            onClick = { selectedTab = 0 },
            modifier = Modifier.testTag("tab_answers_inbox"),
            text = {
              BadgedBox(
                badge = {
                  if (loadedSubmissions.isNotEmpty()) {
                    Badge { Text("${loadedSubmissions.size}") }
                  }
                }
              ) {
                Text(
                  text = stringResource(R.string.tab_answers_inbox),
                  modifier = Modifier.padding(horizontal = 6.dp),
                )
              }
            },
          )
          Tab(
            selected = selectedTab == 1,
            onClick = { selectedTab = 1 },
            modifier = Modifier.testTag("tab_questions_editor"),
            text = {
              Text("${stringResource(R.string.tab_questions_editor)} (${loadedQuestions.size})")
            },
          )
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (selectedTab == 0) {
          SubmissionsInboxTab(
            game = loadedGame,
            questions = loadedQuestions,
            submissionsState = submissionsState,
            onSelectSubmission = { selectedSubmission = it },
            onExportToEmail = { game ->
              val report = viewModel.buildExportSummaryText(game, loadedQuestions, loadedSubmissions)
              val intent =
                Intent(Intent.ACTION_SEND).apply {
                  type = "message/rfc822"
                  putExtra(Intent.EXTRA_EMAIL, arrayOf(game.teacherEmail))
                  putExtra(Intent.EXTRA_SUBJECT, "תשובות תלמידים — ${game.title} (${game.id})")
                  putExtra(Intent.EXTRA_TEXT, report)
                }
              context.startActivity(Intent.createChooser(intent, "שליחת דוח התשובות למייל"))
            },
          )
        } else {
          QuestionsEditorTab(
            questionsState = questionsState,
            onAddQuestion = { showAddQuestionDialog = true },
            onDeleteQuestion = { q ->
              viewModel.deleteQuestion(q.id, loadedGame?.questionCount ?: loadedQuestions.size.toLong())
            },
          )
        }
      }
    }
  }

  if (showAddQuestionDialog && loadedGame != null) {
    AddQuestionDialog(
      isSaving = mutationState is TeacherMutationState.Saving,
      onDismiss = { if (mutationState !is TeacherMutationState.Saving) showAddQuestionDialog = false },
      onSave = { prompt, type, options, correctIdx ->
        viewModel.addQuestion(
          prompt = prompt,
          questionType = type,
          options = options,
          correctOptionIndex = correctIdx,
          currentCount = loadedQuestions.size,
          isGameActive = loadedGame.gameStatus() == GameStatus.ACTIVE,
        )
      },
    )
  }

  val submissionToInspect = selectedSubmission
  if (submissionToInspect != null) {
    SubmissionDetailDialog(
      submission = submissionToInspect,
      questions = loadedQuestions,
      onDismiss = { selectedSubmission = null },
    )
  }
}

@Composable
private fun JoinCodeBanner(
  game: QuizGame,
  onCopyCode: () -> Unit,
  onShareCode: () -> Unit,
) {
  val isActive = game.gameStatus() == GameStatus.ACTIVE
  Card(
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(containerColor = AmberContainer),
    modifier = Modifier
      .fillMaxWidth()
      .padding(bottom = 8.dp),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column {
        Text(
          text = if (isActive) "קוד כניסה לתלמידים (פעיל)" else "המשחק סגור כעת",
          style = MaterialTheme.typography.labelLarge,
        )
        Text(
          text = game.id,
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.testTag("game_join_code_text"),
        )
      }
      Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        IconButton(
          onClick = onCopyCode,
          modifier = Modifier.testTag("copy_code_button"),
        ) {
          Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.action_copy_code))
        }
        IconButton(
          onClick = onShareCode,
          modifier = Modifier.testTag("share_code_button"),
        ) {
          Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.action_share_code))
        }
      }
    }
  }
}

@Composable
private fun SubmissionsInboxTab(
  game: QuizGame?,
  questions: List<QuizQuestion>,
  submissionsState: UiState<List<StudentSubmission>>,
  onSelectSubmission: (StudentSubmission) -> Unit,
  onExportToEmail: (QuizGame) -> Unit,
) {
  when (submissionsState) {
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
          text = submissionsState.message,
          color = MaterialTheme.colorScheme.onErrorContainer,
          modifier = Modifier.padding(16.dp),
        )
      }
    }
    is UiState.Success -> {
      val list = submissionsState.data
      if (list.isEmpty()) {
        Card(
          shape = MaterialTheme.shapes.large,
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        ) {
          Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.Inbox,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(44.dp),
            )
            Text(
              text = "ממתינים לתשובות התלמידים",
              style = MaterialTheme.typography.titleLarge,
            )
            Text(
              text = "שתפו את קוד המשחק (${game?.id ?: ""}) עם התלמידים. ברגע שישלחו תשובה עם שמם והמייל שלהם, היא תופיע כאן אוטומטית.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      } else {
        val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
        LazyColumn(
          contentPadding = PaddingValues(bottom = 32.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          if (game != null) {
            item {
              OutlinedButton(
                onClick = { onExportToEmail(game) },
                modifier = Modifier
                  .fillMaxWidth()
                  .height(48.dp)
                  .testTag("export_answers_email_button"),
              ) {
                Icon(Icons.Rounded.Email, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.action_export_answers))
              }
            }
          }

          items(list, key = { it.id }) { sub ->
            val timeStr = sub.updatedAt?.toDate()?.let { timeFmt.format(it) } ?: ""
            Card(
              shape = MaterialTheme.shapes.medium,
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
              modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelectSubmission(sub) }
                .testTag("submission_card_${sub.id}"),
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                Surface(
                  color = MaterialTheme.colorScheme.primaryContainer,
                  shape = CircleShape,
                  modifier = Modifier.size(44.dp),
                ) {
                  Box(contentAlignment = Alignment.Center) {
                    Text(
                      text = sub.studentName.take(1).uppercase(),
                      style = MaterialTheme.typography.titleMedium,
                      color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                  }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                  Text(
                    text = sub.studentName,
                    style = MaterialTheme.typography.titleMedium,
                  )
                  Text(
                    text = sub.studentEmail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  Text(
                    text = "ענה על ${sub.answers.size} מתוך ${questions.size} שאלות" +
                      if (timeStr.isNotEmpty()) " • $timeStr" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                if (sub.totalScorable > 0) {
                  Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(10.dp),
                  ) {
                    Text(
                      text = "${sub.score}/${sub.totalScorable}",
                      style = MaterialTheme.typography.titleMedium,
                      fontWeight = FontWeight.Bold,
                      color = MaterialTheme.colorScheme.onTertiaryContainer,
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun QuestionsEditorTab(
  questionsState: UiState<List<QuizQuestion>>,
  onAddQuestion: () -> Unit,
  onDeleteQuestion: (QuizQuestion) -> Unit,
) {
  when (questionsState) {
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
          text = questionsState.message,
          color = MaterialTheme.colorScheme.onErrorContainer,
          modifier = Modifier.padding(16.dp),
        )
      }
    }
    is UiState.Success -> {
      val questions = questionsState.data
      if (questions.isEmpty()) {
        Card(
          shape = MaterialTheme.shapes.large,
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        ) {
          Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.Quiz,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(44.dp),
            )
            Text(
              text = "אין שאלות בחידון עדיין",
              style = MaterialTheme.typography.titleLarge,
            )
            Text(
              text = "הוסיפו שאלות אמריקאיות או פתוחות כדי שהתלמידים יוכלו לענות.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
              onClick = onAddQuestion,
              colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
              modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("empty_add_question_button"),
            ) {
              Icon(Icons.Rounded.Add, contentDescription = null)
              Spacer(modifier = Modifier.width(8.dp))
              Text(stringResource(R.string.action_add_question))
            }
          }
        }
      } else {
        LazyColumn(
          contentPadding = PaddingValues(bottom = 96.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          itemsIndexed(questions, key = { _, q -> q.id }) { index, question ->
            Card(
              shape = MaterialTheme.shapes.medium,
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape,
                  ) {
                    Text(
                      text = "שאלה ${index + 1} • ${if (question.type() == QuestionType.MULTIPLE_CHOICE) "אמריקאית" else "פתוחה"}",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onPrimaryContainer,
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                  }
                  IconButton(
                    onClick = { onDeleteQuestion(question) },
                    modifier = Modifier.testTag("delete_question_${question.id}"),
                  ) {
                    Icon(
                      imageVector = Icons.Rounded.DeleteOutline,
                      contentDescription = stringResource(R.string.action_delete),
                      tint = MaterialTheme.colorScheme.error,
                    )
                  }
                }
                Text(
                  text = question.prompt,
                  style = MaterialTheme.typography.titleMedium,
                )
                if (question.options.isNotEmpty()) {
                  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    question.options.forEachIndexed { optIdx, opt ->
                      val isCorrect = optIdx.toLong() == question.correctOptionIndex
                      Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                      ) {
                        if (isCorrect) {
                          Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = "תשובה נכונה",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(16.dp),
                          )
                        }
                        Text(
                          text = "${optIdx + 1}. $opt",
                          style = MaterialTheme.typography.bodyMedium,
                          color = if (isCorrect) MaterialTheme.colorScheme.tertiary
                          else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
