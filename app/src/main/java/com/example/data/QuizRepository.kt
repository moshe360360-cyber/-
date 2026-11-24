package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

class QuizRepository(
  private val db: FirebaseFirestore,
  private val auth: FirebaseAuth = Firebase.auth,
) {
  constructor(context: Context) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    )
  )

  private fun requireUserId(): String {
    return auth.currentUser?.uid
      ?: throw IllegalStateException("יש להתחבר עם חשבון גוגל לפני ביצוע פעולה.")
  }

  private fun unwrapFirestoreError(error: Throwable, operationType: OperationType, path: String, uid: String): Throwable {
    val firestoreError =
      generateSequence(error) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()
    if (firestoreError == null && error is CancellationException) return error
    val toLog = firestoreError ?: (error as? Exception)
    if (toLog != null && auth.currentUser?.uid == uid) {
      handleFirestoreError(toLog, operationType, path)
    }
    return firestoreError ?: error
  }

  fun observeTeacherGames(teacherId: String): Flow<List<QuizGame>> = flow {
    val uid = requireUserId()
    check(uid == teacherId) { "זיהוי המשתמש השתנה; הפעולה נחסמה." }
    val path = "games"
    emitAll(
      db.collection(path)
        .whereEqualTo("teacherId", uid)
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(QuizGame::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }
        .catch { error ->
          throw unwrapFirestoreError(error, OperationType.LIST, path, uid)
        }
    )
  }

  fun observeGame(gameId: String): Flow<QuizGame?> = flow {
    val uid = requireUserId()
    val normalizedCode = gameId.trim().uppercase()
    require(isValidGameCode(normalizedCode)) { "קוד משחק לא תקין." }
    val path = "games/$normalizedCode"
    emitAll(
      db.collection("games")
        .document(normalizedCode)
        .snapshots()
        .map { snapshot ->
          if (snapshot.exists()) {
            snapshot.toObject(QuizGame::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          } else {
            null
          }
        }
        .catch { error ->
          throw unwrapFirestoreError(error, OperationType.GET, path, uid)
        }
    )
  }

  fun observeTeacherQuestions(gameId: String, teacherId: String): Flow<List<QuizQuestion>> = flow {
    val uid = requireUserId()
    check(uid == teacherId) { "זיהוי המשתמש השתנה; הפעולה נחסמה." }
    val normalizedCode = gameId.trim().uppercase()
    val path = "games/$normalizedCode/questions"
    emitAll(
      db.collection("games")
        .document(normalizedCode)
        .collection("questions")
        .whereEqualTo("teacherId", uid)
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(QuizQuestion::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedBy { it.orderIndex }
        }
        .catch { error ->
          throw unwrapFirestoreError(error, OperationType.LIST, path, uid)
        }
    )
  }

  fun observePublishedQuestions(gameId: String): Flow<List<QuizQuestion>> = flow {
    val uid = requireUserId()
    val normalizedCode = gameId.trim().uppercase()
    val path = "games/$normalizedCode/questions"
    emitAll(
      db.collection("games")
        .document(normalizedCode)
        .collection("questions")
        .whereEqualTo("published", true)
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(QuizQuestion::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedBy { it.orderIndex }
        }
        .catch { error ->
          throw unwrapFirestoreError(error, OperationType.LIST, path, uid)
        }
    )
  }

  fun observeTeacherSubmissions(gameId: String, teacherId: String): Flow<List<StudentSubmission>> = flow {
    val uid = requireUserId()
    check(uid == teacherId) { "זיהוי המשתמש השתנה; הפעולה נחסמה." }
    val normalizedCode = gameId.trim().uppercase()
    val path = "games/$normalizedCode/submissions"
    emitAll(
      db.collection("games")
        .document(normalizedCode)
        .collection("submissions")
        .whereEqualTo("teacherId", uid)
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(StudentSubmission::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }
        .catch { error ->
          throw unwrapFirestoreError(error, OperationType.LIST, path, uid)
        }
    )
  }

  fun observeMySubmission(gameId: String, studentUid: String): Flow<StudentSubmission?> = flow {
    val uid = requireUserId()
    check(uid == studentUid) { "זיהוי המשתמש השתנה; הפעולה נחסמה." }
    val normalizedCode = gameId.trim().uppercase()
    val path = "games/$normalizedCode/submissions/$uid"
    emitAll(
      db.collection("games")
        .document(normalizedCode)
        .collection("submissions")
        .document(uid)
        .snapshots()
        .map { snapshot ->
          if (snapshot.exists()) {
            snapshot.toObject(StudentSubmission::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          } else {
            null
          }
        }
        .catch { error ->
          throw unwrapFirestoreError(error, OperationType.GET, path, uid)
        }
    )
  }

  suspend fun createGame(title: String, description: String): Result<String> {
    var docPath = "games"
    return try {
      val user = auth.currentUser ?: throw IllegalStateException("נדרשת התחברות עם חשבון גוגל.")
      val cleanTitle = title.trim()
      val cleanDesc = description.trim()
      require(cleanTitle.length in 1..100) { "שם החידון חייב להכיל בין 1 ל-100 תווים." }
      require(cleanDesc.length <= 300) { "התיאור מוגבל לעד 300 תווים." }

      val teacherName = (user.displayName?.trim()?.takeIf { it.isNotEmpty() } ?: "מורה").take(80)
      val teacherEmail = (user.email?.trim()?.takeIf { isValidEmail(it) } ?: "teacher@classroom.app").take(120)

      val gameCode = generateUniqueCode()
      val docRef = db.collection("games").document(gameCode)
      docPath = docRef.path

      val payload = mapOf(
        "teacherId" to user.uid,
        "teacherName" to teacherName,
        "teacherEmail" to teacherEmail,
        "title" to cleanTitle,
        "description" to cleanDesc,
        "status" to GameStatus.ACTIVE.name,
        "questionCount" to 0L,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
      docRef.set(payload).await()
      Result.success(gameCode)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docPath)
      Result.failure(e)
    }
  }

  suspend fun addQuestion(
    gameId: String,
    prompt: String,
    questionType: QuestionType,
    options: List<String>,
    correctOptionIndex: Int,
    orderIndex: Int,
    published: Boolean,
  ): Result<Unit> {
    val normalizedCode = gameId.trim().uppercase()
    var docPath = "games/$normalizedCode/questions"
    return try {
      val uid = requireUserId()
      val cleanPrompt = prompt.trim()
      require(cleanPrompt.length in 1..400) { "נוסח השאלה חייב להיות בין 1 ל-400 תווים." }

      val sanitizedOptions =
        if (questionType == QuestionType.MULTIPLE_CHOICE) {
          options.map { it.trim() }.filter { it.isNotEmpty() }.take(6)
        } else {
          emptyList()
        }

      if (questionType == QuestionType.MULTIPLE_CHOICE) {
        require(sanitizedOptions.size in 2..6) { "בשאלה אמריקאית יש להזין לפחות 2 אפשרויות (ועד 6)." }
        require(sanitizedOptions.all { it.length <= 120 }) { "אורך כל אפשרות מוגבל לעד 120 תווים." }
      }

      val resolvedCorrectIndex =
        if (questionType == QuestionType.MULTIPLE_CHOICE && correctOptionIndex in sanitizedOptions.indices) {
          correctOptionIndex.toLong()
        } else {
          -1L
        }

      val questionRef = db.collection("games").document(normalizedCode).collection("questions").document()
      docPath = questionRef.path
      val gameRef = db.collection("games").document(normalizedCode)

      val payload = mapOf(
        "gameId" to normalizedCode,
        "teacherId" to uid,
        "prompt" to cleanPrompt,
        "questionType" to questionType.name,
        "options" to sanitizedOptions,
        "correctOptionIndex" to resolvedCorrectIndex,
        "orderIndex" to orderIndex.coerceIn(0, 100).toLong(),
        "published" to published,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )

      val batch = db.batch()
      batch.set(questionRef, payload)
      batch.update(
        gameRef,
        mapOf(
          "questionCount" to FieldValue.increment(1L),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.commit().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docPath)
      Result.failure(e)
    }
  }

  suspend fun deleteQuestion(gameId: String, questionId: String, currentCount: Long): Result<Unit> {
    val normalizedCode = gameId.trim().uppercase()
    val docPath = "games/$normalizedCode/questions/$questionId"
    return try {
      requireUserId()
      require(questionId.isNotBlank()) { "מזהה שאלה חסר." }
      val questionRef = db.collection("games").document(normalizedCode).collection("questions").document(questionId)
      val gameRef = db.collection("games").document(normalizedCode)
      val nextCount = (currentCount - 1L).coerceAtLeast(0L)

      val batch = db.batch()
      batch.delete(questionRef)
      batch.update(
        gameRef,
        mapOf(
          "questionCount" to nextCount,
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.commit().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docPath)
      Result.failure(e)
    }
  }

  suspend fun toggleGameStatus(gameId: String, teacherId: String, newStatus: GameStatus): Result<Unit> {
    val normalizedCode = gameId.trim().uppercase()
    val docPath = "games/$normalizedCode"
    return try {
      val uid = requireUserId()
      check(uid == teacherId) { "רק יוצר המשחק רשאי לשנות סטטוס." }
      val gameRef = db.collection("games").document(normalizedCode)
      val questionsSnap =
        gameRef.collection("questions").whereEqualTo("teacherId", uid).get().await()

      val published = newStatus == GameStatus.ACTIVE
      val batch = db.batch()
      batch.update(
        gameRef,
        mapOf(
          "status" to newStatus.name,
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      questionsSnap.documents.forEach { qDoc ->
        batch.update(
          qDoc.reference,
          mapOf(
            "published" to published,
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      }
      batch.commit().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docPath)
      Result.failure(e)
    }
  }

  suspend fun joinGameByCode(rawCode: String): Result<QuizGame> {
    val code = rawCode.trim().uppercase()
    val path = "games/$code"
    return try {
      requireUserId()
      require(isValidGameCode(code)) { "קוד משחק חייב להכיל אותיות באנגלית או ספרות (ללא רווחים)." }
      val snap = db.collection("games").document(code).get().await()
      if (!snap.exists()) {
        return Result.failure(IllegalArgumentException("לא נמצא חידון פעיל עם הקוד $code."))
      }
      val game = snap.toObject(QuizGame::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        ?: return Result.failure(IllegalStateException("שגיאה בקריאת נתוני החידון."))
      if (game.gameStatus() != GameStatus.ACTIVE) {
        return Result.failure(IllegalStateException("החידון סגור כרגע לקבלת תשובות."))
      }
      Result.success(game)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, path)
      Result.failure(
        if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
          IllegalStateException("לא נמצא חידון פעיל עם הקוד $code או שאין הרשאה.")
        } else {
          e
        }
      )
    }
  }

  suspend fun submitStudentAnswers(
    game: QuizGame,
    studentName: String,
    studentEmail: String,
    questions: List<QuizQuestion>,
    answers: Map<String, String>,
  ): Result<Pair<Int, Int>> {
    val uid = auth.currentUser?.uid
    val normalizedCode = game.id.trim().uppercase()
    val docPath = "games/$normalizedCode/submissions/${uid ?: "unauthenticated"}"
    return try {
      val currentUid = requireUserId()
      val cleanName = studentName.trim()
      val cleanEmail = studentEmail.trim()
      require(cleanName.length in 1..80) { "יש להזין שם מלא (עד 80 תווים)." }
      require(isValidEmail(cleanEmail)) { "יש להזין כתובת מייל תקינה." }

      val cleanAnswers =
        answers.entries
          .associate { (k, v) -> k.trim() to v.trim() }
          .filter { (k, v) -> k.isNotEmpty() && v.isNotEmpty() }

      require(cleanAnswers.isNotEmpty() && cleanAnswers.size <= 50) {
        "יש לענות לפחות על שאלה אחת לפני השליחה."
      }

      var score = 0
      var totalScorable = 0
      questions.forEach { q ->
        if (q.type() == QuestionType.MULTIPLE_CHOICE && q.correctOptionIndex >= 0 && q.correctOptionIndex < q.options.size) {
          totalScorable++
          val correctText = q.options[q.correctOptionIndex.toInt()]
          if (cleanAnswers[q.id] == correctText) {
            score++
          }
        }
      }

      val docRef =
        db.collection("games").document(normalizedCode).collection("submissions").document(currentUid)
      val existingSnap = docRef.get().await()

      if (existingSnap.exists()) {
        docRef.update(
          mapOf(
            "studentName" to cleanName,
            "studentEmail" to cleanEmail,
            "answers" to cleanAnswers,
            "score" to score.toLong(),
            "totalScorable" to totalScorable.toLong(),
            "completed" to true,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        ).await()
      } else {
        docRef.set(
          mapOf(
            "gameId" to normalizedCode,
            "teacherId" to game.teacherId,
            "studentUid" to currentUid,
            "studentName" to cleanName,
            "studentEmail" to cleanEmail,
            "answers" to cleanAnswers,
            "score" to score.toLong(),
            "totalScorable" to totalScorable.toLong(),
            "completed" to true,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        ).await()
      }
      Result.success(score to totalScorable)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docPath)
      Result.failure(e)
    }
  }

  private suspend fun generateUniqueCode(): String {
    val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    repeat(6) {
      val candidate = buildString {
        repeat(6) {
          append(chars[Random.nextInt(chars.length)])
        }
      }
      val exists = try {
        db.collection("games").document(candidate).get().await().exists()
      } catch (_: FirebaseFirestoreException) {
        // An existing game owned by another teacher denies get; pick another random code
        true
      }
      if (!exists) return candidate
    }
    return buildString {
      repeat(8) { append(chars[Random.nextInt(chars.length)]) }
    }
  }

  companion object {
    private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    private val CODE_REGEX = Regex("^[a-zA-Z0-9_\\-]{1,128}$")

    fun isValidEmail(email: String): Boolean {
      val trimmed = email.trim()
      return trimmed.length in 3..120 && EMAIL_REGEX.matches(trimmed)
    }

    fun isValidGameCode(code: String): Boolean {
      val trimmed = code.trim()
      return trimmed.isNotEmpty() && CODE_REGEX.matches(trimmed)
    }
  }
}
