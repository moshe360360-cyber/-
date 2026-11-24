package com.example.ui.student

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.QuizGame
import com.example.data.QuizQuestion
import com.example.data.QuizRepository
import com.example.data.StudentSubmission
import com.example.data.UiState
import com.example.data.toHebrewUserMessage
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface SubmissionResultState {
  data object Idle : SubmissionResultState

  data object Submitting : SubmissionResultState

  data class Submitted(val score: Int, val totalScorable: Int) : SubmissionResultState

  data class Error(val message: String) : SubmissionResultState
}

class StudentPlayViewModel(
  private val repository: QuizRepository,
  private val gameId: String,
  private val studentUid: String,
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
        emit(UiState.Error(toHebrewUserMessage(rootError, "שגיאה בטעינת החידון.")))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading,
      )

  val questionsState: StateFlow<UiState<List<QuizQuestion>>> =
    repository
      .observePublishedQuestions(gameId)
      .map<List<QuizQuestion>, UiState<List<QuizQuestion>>> { UiState.Success(it) }
      .catch { error ->
        val firestoreError =
          generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing published questions", rootError)
        emit(UiState.Error(toHebrewUserMessage(rootError, "שגיאה בטעינת השאלות.")))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading,
      )

  val existingSubmissionState: StateFlow<UiState<StudentSubmission?>> =
    repository
      .observeMySubmission(gameId, studentUid)
      .map<StudentSubmission?, UiState<StudentSubmission?>> { UiState.Success(it) }
      .catch { error ->
        val firestoreError =
          generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing own submission", rootError)
        emit(UiState.Error(toHebrewUserMessage(rootError, "שגיאה בטעינת ההגשה.")))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading,
      )

  private val _answers = MutableStateFlow<Map<String, String>>(emptyMap())
  val answers: StateFlow<Map<String, String>> = _answers.asStateFlow()

  private val _currentIndex = MutableStateFlow(0)
  val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

  private val _submissionResult = MutableStateFlow<SubmissionResultState>(SubmissionResultState.Idle)
  val submissionResult: StateFlow<SubmissionResultState> = _submissionResult.asStateFlow()

  fun setAnswer(questionId: String, answer: String) {
    _answers.update { current -> current + (questionId to answer) }
  }

  fun goToQuestion(index: Int, totalCount: Int) {
    if (totalCount > 0) {
      _currentIndex.value = index.coerceIn(0, totalCount - 1)
    }
  }

  fun submitAnswers(
    game: QuizGame,
    studentName: String,
    studentEmail: String,
    questions: List<QuizQuestion>,
  ) {
    val currentAnswers = _answers.value
    val nonBlankAnswers = currentAnswers.filterValues { it.isNotBlank() }
    if (nonBlankAnswers.isEmpty()) {
      _submissionResult.value =
        SubmissionResultState.Error("יש לענות לפחות על שאלה אחת לפני השליחה למורה.")
      return
    }
    _submissionResult.value = SubmissionResultState.Submitting
    viewModelScope.launch {
      repository
        .submitStudentAnswers(
          game = game,
          studentName = studentName,
          studentEmail = studentEmail,
          questions = questions,
          answers = nonBlankAnswers,
        )
        .onSuccess { (score, totalScorable) ->
          _submissionResult.value = SubmissionResultState.Submitted(score, totalScorable)
        }
        .onFailure { err ->
          _submissionResult.value =
            SubmissionResultState.Error(toHebrewUserMessage(err, "שליחת התשובות נכשלה."))
        }
    }
  }

  fun resetForEdit(existingAnswers: Map<String, String>) {
    _answers.value = existingAnswers
    _currentIndex.value = 0
    _submissionResult.value = SubmissionResultState.Idle
  }

  fun clearError() {
    if (_submissionResult.value is SubmissionResultState.Error) {
      _submissionResult.value = SubmissionResultState.Idle
    }
  }

  private companion object {
    const val TAG = "StudentPlayVM"
    const val STOP_TIMEOUT_MILLIS = 5000L
  }
}
