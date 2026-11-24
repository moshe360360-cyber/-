package com.example.ui.teacher

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.GameStatus
import com.example.data.QuestionType
import com.example.data.QuizGame
import com.example.data.QuizQuestion
import com.example.data.QuizRepository
import com.example.data.StudentSubmission
import com.example.data.UiState
import com.example.data.toHebrewUserMessage
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface TeacherMutationState {
  data object Idle : TeacherMutationState

  data object Saving : TeacherMutationState

  data class Message(val text: String, val isError: Boolean = false) : TeacherMutationState
}

class TeacherGameViewModel(
  private val repository: QuizRepository,
  private val gameId: String,
  private val teacherId: String,
) : ViewModel() {

  val gameState: StateFlow<UiState<QuizGame?>> =
    repository
      .observeGame(gameId)
      .map<QuizGame?, UiState<QuizGame?>> { UiState.Success(it) }
      .catch { error ->
        val firestoreError =
          generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing game", rootError)
        emit(UiState.Error(toHebrewUserMessage(rootError, "שגיאה בטעינת פרטי החידון.")))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading,
      )

  val questionsState: StateFlow<UiState<List<QuizQuestion>>> =
    repository
      .observeTeacherQuestions(gameId, teacherId)
      .map<List<QuizQuestion>, UiState<List<QuizQuestion>>> { UiState.Success(it) }
      .catch { error ->
        val firestoreError =
          generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing questions", rootError)
        emit(UiState.Error(toHebrewUserMessage(rootError, "שגיאה בטעינת השאלות.")))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading,
      )

  val submissionsState: StateFlow<UiState<List<StudentSubmission>>> =
    repository
      .observeTeacherSubmissions(gameId, teacherId)
      .map<List<StudentSubmission>, UiState<List<StudentSubmission>>> { list ->
        UiState.Success(list.sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) })
      }
      .catch { error ->
        val firestoreError =
          generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing submissions", rootError)
        emit(UiState.Error(toHebrewUserMessage(rootError, "שגיאה בטעינת תשובות התלמידים.")))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading,
      )

  private val _mutationState = MutableStateFlow<TeacherMutationState>(TeacherMutationState.Idle)
  val mutationState: StateFlow<TeacherMutationState> = _mutationState.asStateFlow()

  fun addQuestion(
    prompt: String,
    questionType: QuestionType,
    options: List<String>,
    correctOptionIndex: Int,
    currentCount: Int,
    isGameActive: Boolean,
  ) {
    val cleanPrompt = prompt.trim()
    if (cleanPrompt.isEmpty() || cleanPrompt.length > 400) {
      _mutationState.value =
        TeacherMutationState.Message("נוסח השאלה חייב להכיל 1 עד 400 תווים.", isError = true)
      return
    }
    if (currentCount >= 50) {
      _mutationState.value =
        TeacherMutationState.Message("הגעתם למגבלת 50 השאלות במשחק.", isError = true)
      return
    }
    val cleanedOptions = options.map { it.trim() }.filter { it.isNotEmpty() }
    if (questionType == QuestionType.MULTIPLE_CHOICE && cleanedOptions.size < 2) {
      _mutationState.value =
        TeacherMutationState.Message("בשאלה אמריקאית יש למלא לפחות 2 אפשרויות תשובה.", isError = true)
      return
    }

    _mutationState.value = TeacherMutationState.Saving
    viewModelScope.launch {
      repository
        .addQuestion(
          gameId = gameId,
          prompt = cleanPrompt,
          questionType = questionType,
          options = cleanedOptions,
          correctOptionIndex = correctOptionIndex,
          orderIndex = currentCount,
          published = isGameActive,
        )
        .onSuccess {
          _mutationState.value = TeacherMutationState.Message("השאלה נוספה לחידון!")
        }
        .onFailure { err ->
          _mutationState.value =
            TeacherMutationState.Message(
              toHebrewUserMessage(err, "שגיאה בהוספת השאלה."),
              isError = true,
            )
        }
    }
  }

  fun deleteQuestion(questionId: String, currentCount: Long) {
    _mutationState.value = TeacherMutationState.Saving
    viewModelScope.launch {
      repository
        .deleteQuestion(gameId, questionId, currentCount)
        .onSuccess {
          _mutationState.value = TeacherMutationState.Message("השאלה הוסרה.")
        }
        .onFailure { err ->
          _mutationState.value =
            TeacherMutationState.Message(
              toHebrewUserMessage(err, "מחיקת השאלה נכשלה."),
              isError = true,
            )
        }
    }
  }

  fun toggleGameStatus(currentStatus: GameStatus) {
    val nextStatus = if (currentStatus == GameStatus.ACTIVE) GameStatus.ENDED else GameStatus.ACTIVE
    _mutationState.value = TeacherMutationState.Saving
    viewModelScope.launch {
      repository
        .toggleGameStatus(gameId, teacherId, nextStatus)
        .onSuccess {
          val msg =
            if (nextStatus == GameStatus.ACTIVE) "המשחק נפתח לקבלת תשובות!"
            else "המשחק נסגר לקבלת תשובות."
          _mutationState.value = TeacherMutationState.Message(msg)
        }
        .onFailure { err ->
          _mutationState.value =
            TeacherMutationState.Message(
              toHebrewUserMessage(err, "עדכון הסטטוס נכשל."),
              isError = true,
            )
        }
    }
  }

  fun buildExportSummaryText(
    game: QuizGame,
    questions: List<QuizQuestion>,
    submissions: List<StudentSubmission>,
  ): String = buildString {
    appendLine("דוח תשובות — ${game.title} (קוד: ${game.id})")
    appendLine("סה\"כ משיבים: ${submissions.size}")
    appendLine("====================================")
    submissions.forEachIndexed { idx, sub ->
      appendLine("${idx + 1}. ${sub.studentName} (${sub.studentEmail})")
      if (sub.totalScorable > 0) {
        appendLine("   ציון אוטומטי: ${sub.score}/${sub.totalScorable}")
      }
      questions.forEachIndexed { qIdx, q ->
        val ans = sub.answers[q.id] ?: "— לא נענה —"
        appendLine("   ש${qIdx + 1}) ${q.prompt}")
        appendLine("       תשובה: $ans")
      }
      appendLine()
    }
  }

  fun clearMessage() {
    _mutationState.value = TeacherMutationState.Idle
  }

  private companion object {
    const val TAG = "TeacherVM"
    const val STOP_TIMEOUT_MILLIS = 5000L
  }
}
