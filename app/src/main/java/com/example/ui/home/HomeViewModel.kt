package com.example.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.QuizGame
import com.example.data.QuizRepository
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

sealed interface HomeActionState {
  data object Idle : HomeActionState

  data object Loading : HomeActionState

  data class GameCreated(val gameId: String) : HomeActionState

  data class StudentJoined(
    val gameId: String,
    val studentName: String,
    val studentEmail: String,
  ) : HomeActionState

  data class Error(val message: String) : HomeActionState
}

class HomeViewModel(
  private val repository: QuizRepository,
  private val currentUserId: String,
) : ViewModel() {

  val teacherGames: StateFlow<UiState<List<QuizGame>>> =
    repository
      .observeTeacherGames(currentUserId)
      .map<List<QuizGame>, UiState<List<QuizGame>>> { games ->
        UiState.Success(games.sortedByDescending { it.createdAt ?: Timestamp(0, 0) })
      }
      .catch { error ->
        val firestoreError =
          generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing teacher games", rootError)
        emit(UiState.Error(toHebrewUserMessage(rootError, "שגיאה בטעינת החידונים.")))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading,
      )

  private val _actionState = MutableStateFlow<HomeActionState>(HomeActionState.Idle)
  val actionState: StateFlow<HomeActionState> = _actionState.asStateFlow()

  fun createGame(title: String, description: String) {
    val cleanTitle = title.trim()
    if (cleanTitle.isEmpty()) {
      _actionState.value = HomeActionState.Error("יש להזין שם לחידון.")
      return
    }
    if (cleanTitle.length > 100) {
      _actionState.value = HomeActionState.Error("שם החידון מוגבל לעד 100 תווים.")
      return
    }
    if (description.trim().length > 300) {
      _actionState.value = HomeActionState.Error("ההנחיות מוגבלות לעד 300 תווים.")
      return
    }

    _actionState.value = HomeActionState.Loading
    viewModelScope.launch {
      repository
        .createGame(cleanTitle, description)
        .onSuccess { gameCode -> _actionState.value = HomeActionState.GameCreated(gameCode) }
        .onFailure { err ->
          _actionState.value =
            HomeActionState.Error(toHebrewUserMessage(err, "יצירת החידון נכשלה."))
        }
    }
  }

  fun joinGameAsStudent(rawCode: String, studentName: String, studentEmail: String) {
    val code = rawCode.trim().uppercase()
    val cleanName = studentName.trim()
    val cleanEmail = studentEmail.trim()

    if (!QuizRepository.isValidGameCode(code)) {
      _actionState.value = HomeActionState.Error("יש להזין קוד משחק תקין באנגלית ובספרות.")
      return
    }
    if (cleanName.isEmpty() || cleanName.length > 80) {
      _actionState.value = HomeActionState.Error("יש להזין את שמכם המלא (עד 80 תווים).")
      return
    }
    if (!QuizRepository.isValidEmail(cleanEmail)) {
      _actionState.value = HomeActionState.Error("יש להזין כתובת מייל תקינה.")
      return
    }

    _actionState.value = HomeActionState.Loading
    viewModelScope.launch {
      repository
        .joinGameByCode(code)
        .onSuccess { game ->
          _actionState.value =
            HomeActionState.StudentJoined(
              gameId = game.id,
              studentName = cleanName,
              studentEmail = cleanEmail,
            )
        }
        .onFailure { err ->
          _actionState.value =
            HomeActionState.Error(toHebrewUserMessage(err, "לא ניתן להצטרף לחידון."))
        }
    }
  }

  fun resetActionState() {
    _actionState.value = HomeActionState.Idle
  }

  private companion object {
    const val TAG = "HomeVM"
    const val STOP_TIMEOUT_MILLIS = 5000L
  }
}
