package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>

  data class Success<T>(val data: T) : UiState<T>

  data class Error(val message: String) : UiState<Nothing>
}

enum class GameStatus {
  ACTIVE,
  ENDED;

  companion object {
    fun fromString(raw: String?): GameStatus =
      entries.firstOrNull { it.name == raw } ?: ACTIVE
  }
}

enum class QuestionType {
  MULTIPLE_CHOICE,
  OPEN_TEXT;

  companion object {
    fun fromString(raw: String?): QuestionType =
      entries.firstOrNull { it.name == raw } ?: MULTIPLE_CHOICE
  }
}

@Keep
@IgnoreExtraProperties
data class QuizGame(
  @DocumentId val id: String = "",
  val teacherId: String = "",
  val teacherName: String = "",
  val teacherEmail: String = "",
  val title: String = "",
  val description: String = "",
  val status: String = GameStatus.ACTIVE.name,
  val questionCount: Long = 0L,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun gameStatus(): GameStatus = GameStatus.fromString(status)
}

@Keep
@IgnoreExtraProperties
data class QuizQuestion(
  @DocumentId val id: String = "",
  val gameId: String = "",
  val teacherId: String = "",
  val prompt: String = "",
  val questionType: String = QuestionType.MULTIPLE_CHOICE.name,
  val options: List<String> = emptyList(),
  val correctOptionIndex: Long = -1L,
  val orderIndex: Long = 0L,
  val published: Boolean = true,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun type(): QuestionType = QuestionType.fromString(questionType)
}

@Keep
@IgnoreExtraProperties
data class StudentSubmission(
  @DocumentId val id: String = "",
  val gameId: String = "",
  val teacherId: String = "",
  val studentUid: String = "",
  val studentName: String = "",
  val studentEmail: String = "",
  val answers: Map<String, String> = emptyMap(),
  val score: Long = 0L,
  val totalScorable: Long = 0L,
  val completed: Boolean = false,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)
