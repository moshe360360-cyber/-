package com.example.ui.student

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.QuestionType
import com.example.data.QuizGame
import com.example.data.QuizQuestion
import com.example.data.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentPlayScreen(
  viewModel: StudentPlayViewModel,
  studentName: String,
  studentEmail: String,
  onNavigateBack: () -> Unit,
) {
  val snackbarHostState = remember { SnackbarHostState() }
  val gameState by viewModel.gameState.collectAsStateWithLifecycle()
  val questionsState by viewModel.questionsState.collectAsStateWithLifecycle()
  val answers by viewModel.answers.collectAsStateWithLifecycle()
  val currentIndex by viewModel.currentIndex.collectAsStateWithLifecycle()
  val submissionResult by viewModel.submissionResult.collectAsStateWithLifecycle()

  LaunchedEffect(submissionResult) {
    val err = submissionResult as? SubmissionResultState.Error ?: return@LaunchedEffect
    snackbarHostState.showSnackbar(err.message)
    viewModel.clearError()
  }

  val loadedGame = (gameState as? UiState.Success)?.data

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
            modifier = Modifier.testTag("student_back_button"),
          ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "יציאה")
          }
        },
        title = {
          Column {
            Text(
              text = loadedGame?.title ?: "טוען חידון...",
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            if (loadedGame != null) {
              Text(
                text = "מורה: ${loadedGame.teacherName}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
              )
            }
          }
        },
      )
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
          .widthIn(max = 640.dp)
          .padding(horizontal = 16.dp),
      ) {
        StudentIdentityBadge(studentName = studentName, studentEmail = studentEmail)

        Spacer(modifier = Modifier.height(10.dp))

        when (val result = submissionResult) {
          is SubmissionResultState.Submitted -> {
            SubmissionSuccessCard(
              score = result.score,
              totalScorable = result.totalScorable,
              studentName = studentName,
              studentEmail = studentEmail,
              onEditAnswers = { viewModel.resetForEdit(answers) },
              onBackHome = onNavigateBack,
            )
          }
          else -> {
            when (val qState = questionsState) {
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
                    text = qState.message,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(16.dp),
                  )
                }
              }
              is UiState.Success -> {
                val questions = qState.data
                if (questions.isEmpty()) {
                  Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth(),
                  ) {
                    Text(
                      text = "המורה עדיין לא הוסיף שאלות לחידון זה. המתינו רגע ורעננו.",
                      style = MaterialTheme.typography.bodyLarge,
                      modifier = Modifier.padding(24.dp),
                      textAlign = TextAlign.Center,
                    )
                  }
                } else {
                  val safeIndex = currentIndex.coerceIn(0, questions.lastIndex)
                  val activeQuestion = questions[safeIndex]
                  QuizQuestionStep(
                    game = loadedGame,
                    questions = questions,
                    currentIndex = safeIndex,
                    activeQuestion = activeQuestion,
                    currentAnswer = answers[activeQuestion.id].orEmpty(),
                    answeredCount = answers.count { it.value.isNotBlank() },
                    isSubmitting = submissionResult is SubmissionResultState.Submitting,
                    onSelectAnswer = { ans -> viewModel.setAnswer(activeQuestion.id, ans) },
                    onPrev = { viewModel.goToQuestion(safeIndex - 1, questions.size) },
                    onNext = { viewModel.goToQuestion(safeIndex + 1, questions.size) },
                    onSubmit = {
                      if (loadedGame != null) {
                        viewModel.submitAnswers(loadedGame, studentName, studentEmail, questions)
                      }
                    },
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

@Composable
private fun StudentIdentityBadge(studentName: String, studentEmail: String) {
  Surface(
    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
    shape = MaterialTheme.shapes.small,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Icon(
        imageVector = Icons.Rounded.Person,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.size(18.dp),
      )
      Text(
        text = studentName,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
      )
      Text("•", color = MaterialTheme.colorScheme.onPrimaryContainer)
      Icon(
        imageVector = Icons.Rounded.Email,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.size(16.dp),
      )
      Text(
        text = studentEmail,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun QuizQuestionStep(
  game: QuizGame?,
  questions: List<QuizQuestion>,
  currentIndex: Int,
  activeQuestion: QuizQuestion,
  currentAnswer: String,
  answeredCount: Int,
  isSubmitting: Boolean,
  onSelectAnswer: (String) -> Unit,
  onPrev: () -> Unit,
  onNext: () -> Unit,
  onSubmit: () -> Unit,
) {
  Column(modifier = Modifier.fillMaxSize()) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = "שאלה ${currentIndex + 1} מתוך ${questions.size}",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
      )
      Text(
        text = "נענו $answeredCount/${questions.size}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Spacer(modifier = Modifier.height(6.dp))
    LinearProgressIndicator(
      progress = { (currentIndex + 1).toFloat() / questions.size.toFloat() },
      modifier = Modifier
        .fillMaxWidth()
        .height(8.dp),
    )

    Spacer(modifier = Modifier.height(14.dp))

    Column(
      modifier = Modifier
        .weight(1f)
        .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(12.dp),
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
            text = activeQuestion.prompt,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
          )

          if (activeQuestion.type() == QuestionType.MULTIPLE_CHOICE) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
              activeQuestion.options.forEachIndexed { idx, option ->
                val isSelected = currentAnswer == option
                Surface(
                  shape = MaterialTheme.shapes.medium,
                  color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                  else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                  border = BorderStroke(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline,
                  ),
                  modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectAnswer(option) }
                    .testTag("student_option_$idx"),
                ) {
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                  ) {
                    Icon(
                      imageVector = if (isSelected) Icons.Rounded.RadioButtonChecked
                      else Icons.Rounded.RadioButtonUnchecked,
                      contentDescription = null,
                      tint = if (isSelected) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                      text = option,
                      style = MaterialTheme.typography.bodyLarge,
                      fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                  }
                }
              }
            }
          } else {
            OutlinedTextField(
              value = currentAnswer,
              onValueChange = { onSelectAnswer(it.take(500)) },
              label = { Text("הקלידו את תשובתכם כאן") },
              minLines = 4,
              maxLines = 8,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("student_open_answer_input"),
            )
          }
        }
      }
    }

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .padding(vertical = 12.dp),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      if (currentIndex > 0) {
        OutlinedButton(
          onClick = onPrev,
          modifier = Modifier
            .weight(1f)
            .height(52.dp)
            .testTag("prev_question_button"),
        ) {
          Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
          Spacer(modifier = Modifier.width(6.dp))
          Text("הקודמת")
        }
      }

      if (currentIndex < questions.lastIndex) {
        Button(
          onClick = onNext,
          modifier = Modifier
            .weight(1f)
            .height(52.dp)
            .testTag("next_question_button"),
        ) {
          Text("הבאה")
          Spacer(modifier = Modifier.width(6.dp))
          Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null)
        }
      } else {
        Button(
          onClick = onSubmit,
          enabled = !isSubmitting && game != null,
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.tertiary,
            contentColor = MaterialTheme.colorScheme.onTertiary,
          ),
          modifier = Modifier
            .weight(1.4f)
            .height(52.dp)
            .testTag("submit_answers_button"),
        ) {
          if (isSubmitting) {
            CircularProgressIndicator(
              modifier = Modifier.size(20.dp),
              color = MaterialTheme.colorScheme.onTertiary,
              strokeWidth = 2.dp,
            )
          } else {
            Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.action_submit_answers))
          }
        }
      }
    }
  }
}

@Composable
private fun SubmissionSuccessCard(
  score: Int,
  totalScorable: Int,
  studentName: String,
  studentEmail: String,
  onEditAnswers: () -> Unit,
  onBackHome: () -> Unit,
) {
  Card(
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = 16.dp),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = CircleShape,
        modifier = Modifier.size(72.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.size(40.dp),
          )
        }
      }
      Text(
        text = stringResource(R.string.submission_success_title),
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
      )
      Text(
        text = "נשלח בשם $studentName ($studentEmail)",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
      if (totalScorable > 0) {
        Surface(
          color = MaterialTheme.colorScheme.primaryContainer,
          shape = MaterialTheme.shapes.medium,
        ) {
          Text(
            text = "ציון בשאלות אמריקאיות: $score מתוך $totalScorable",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
          )
        }
      }
      Spacer(modifier = Modifier.height(6.dp))
      OutlinedButton(
        onClick = onEditAnswers,
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .testTag("edit_submitted_answers_button"),
      ) {
        Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("עדכון תשובות")
      }
      Button(
        onClick = onBackHome,
        modifier = Modifier
          .fillMaxWidth()
          .height(50.dp)
          .testTag("back_home_button"),
      ) {
        Text(stringResource(R.string.action_back_home))
      }
    }
  }
}
