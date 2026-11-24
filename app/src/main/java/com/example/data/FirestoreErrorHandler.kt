package com.example.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
  CREATE("create"),
  UPDATE("update"),
  DELETE("delete"),
  LIST("list"),
  GET("get"),
  WRITE("write"),
}

fun handleFirestoreError(
  exception: Throwable,
  operationType: OperationType,
  path: String?,
): String {
  val causes = generateSequence(exception) { it.cause }
  val rootError =
    causes.firstOrNull { it is FirebaseFirestoreException }
      ?: causes.firstOrNull { it !is CancellationException }
      ?: exception

  val currentUser = FirebaseAuth.getInstance().currentUser

  val providerInfoList =
    currentUser?.providerData?.map { provider ->
      JSONObject().apply {
        put("providerId", provider.providerId)
        put("email", provider.email)
      }
    } ?: emptyList()

  val authInfoJson =
    JSONObject().apply {
      put("userId", currentUser?.uid)
      put("email", currentUser?.email)
      put("emailVerified", currentUser?.isEmailVerified)
      put("tenantId", currentUser?.tenantId)
      put("providerInfo", JSONArray(providerInfoList))
    }

  val errorInfoJson =
    JSONObject().apply {
      put("error", rootError.message ?: rootError.toString())
      put("operationType", operationType.value)
      put("path", path)
      put("authInfo", authInfoJson)
    }

  val jsonString = errorInfoJson.toString()
  Log.e("FirestoreError", "Firestore Error: $jsonString")
  return jsonString
}

fun toHebrewUserMessage(error: Throwable, fallback: String): String {
  val firestoreError =
    generateSequence(error) { it.cause }
      .filterIsInstance<FirebaseFirestoreException>()
      .firstOrNull()
  if (firestoreError != null) {
    return when (firestoreError.code) {
      FirebaseFirestoreException.Code.PERMISSION_DENIED ->
        "אין הרשאה לבצע פעולה זו או שהמשחק אינו פעיל."
      FirebaseFirestoreException.Code.UNAVAILABLE,
      FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
        "בעיית תקשורת זמנית מול השרת. בדקו את החיבור לאינטרנט ונסו שוב."
      FirebaseFirestoreException.Code.NOT_FOUND ->
        "הפריט המבוקש לא נמצא."
      FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED ->
        "חריגה ממכסת הבקשות. נסו שוב מאוחר יותר."
      else -> fallback
    }
  }
  val raw = error.message.orEmpty()
  val hasHebrew = raw.any { it in '\u0590'..'\u05FF' }
  return if (hasHebrew) raw else fallback
}
