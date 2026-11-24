package com.example.ui.teacher

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.QuestionType
import com.example.data.QuizQuestion
import com.example.data.StudentSubmission

@Composable
fun AddQuestionDialog(
  isSaving: Boolean,
  onDismiss: () -> Unit,
  onSave: (
    prompt: String,
    type: QuestionType,
    options: List<String>,
    correctIndex: Int,
  ) -> Unit,
) {
  var prompt by rememberSaveable { mutableStateOf("") }
  var questionType by rememberSaveable { mutableStateOf(QuestionType.MULTIPLE_CHOICE) }
  val options = remember { mutableStateListOf("", "", "", "") }
  var correctIndex by rememberSaveable { mutableIntStateOf(0) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.action_add_question)) },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FilterChip(
            selected = questionType == QuestionType.MULTIPLE_CHOICE,
            onClick = { questionType = QuestionType.MULTIPLE_CHOICE },
            label = { Text("שאלה אמריקאית") },
            modifier = Modifier.testTag("chip_multiple_choice"),
          )
          FilterChip(
            selected = questionType == QuestionType.OPEN_TEXT,
            onClick = { questionType = QuestionType.OPEN_TEXT },
            label = { Text("שאלה פתוחה") },
            modifier = Modifier.testTag("chip_open_text"),
          )
        }

        OutlinedTextField(
          value = prompt,
          onValueChange = { prompt = it.take(400) },
          label = { Text("נוסח השאלה") },
          maxLines = 3,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("question_prompt_input"),
        )

        if (questionType == QuestionType.MULTIPLE_CHOICE) {
          Text(
            text = "אפשרויות תשובה (סמנו V ליד התשובה הנכונה, או השאירו ללא ציון):",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          options.forEachIndexed { index, value ->
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              IconButton(
                onClick = {
                  correctIndex = if (correctIndex == index) -1 else index
                },
                modifier = Modifier.testTag("mark_correct_option_$index"),
              ) {
                Icon(
                  imageVector =
                    if (correctIndex == index) Icons.Rounded.CheckCircle
                    else Icons.Rounded.RadioButtonUnchecked,
                  contentDescription = "סימון תשובה נכונה",
                  tint =
                    if (correctIndex == index) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              OutlinedTextField(
                value = value,
                onValueChange = { options[index] = it.take(120) },
                label = { Text("אפשרות ${index + 1}${if (index < 2) " *" else ""}") },
                singleLine = true,
                modifier = Modifier
                  .weight(1f)
                  .testTag("question_option_input_$index"),
              )
            }
          }
          if (options.size < 6) {
            OutlinedButton(
              onClick = { options.add("") },
              modifier = Modifier.fillMaxWidth(),
            ) {
              Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
              Text("הוספת אפשרות נוספת")
            }
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val nonEmpty = options.map { it.trim() }.filter { it.isNotEmpty() }
          val mappedCorrect =
            if (questionType == QuestionType.MULTIPLE_CHOICE && correctIndex in options.indices) {
              val chosenText = options[correctIndex].trim()
              nonEmpty.indexOf(chosenText)
            } else {
              -1
            }
          onSave(prompt, questionType, nonEmpty, mappedCorrect)
        },
        enabled = !isSaving && prompt.trim().isNotEmpty(),
        modifier = Modifier.testTag("confirm_add_question_button"),
      ) {
        if (isSaving) {
          CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
          Text(stringResource(R.string.action_save))
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

@Composable
fun SubmissionDetailDialog(
  submission: StudentSubmission,
  questions: List<QuizQuestion>,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = submission.studentName, style = MaterialTheme.typography.titleLarge)
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.Email,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary,
          )
          Text(
            text = submission.studentEmail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
          )
        }
      }
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        if (submission.totalScorable > 0) {
          Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = "ציון אוטומטי: ${submission.score} מתוך ${submission.totalScorable}",
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onTertiaryContainer,
              modifier = Modifier.padding(12.dp),
            )
          }
        }

        questions.forEachIndexed { idx, q ->
          val studentAnswer = submission.answers[q.id]
          val hasCorrect =
            q.type() == QuestionType.MULTIPLE_CHOICE &&
              q.correctOptionIndex >= 0 &&
              q.correctOptionIndex < q.options.size
          val isCorrect = hasCorrect && studentAnswer == q.options[q.correctOptionIndex.toInt()]

          Surface(
            shape = MaterialTheme.shapes.small,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier.padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Text(
                text = "שאלה ${idx + 1}: ${q.prompt}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
              )
              HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
              Text(
                text = studentAnswer ?: "— לא נענה —",
                style = MaterialTheme.typography.bodyLarge,
                color =
                  when {
                    studentAnswer == null -> MaterialTheme.colorScheme.onSurfaceVariant
                    hasCorrect && isCorrect -> MaterialTheme.colorScheme.tertiary
                    hasCorrect && !isCorrect -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                  },
              )
              if (hasCorrect && !isCorrect) {
                Text(
                  text = "תשובה נכונה: ${q.options[q.correctOptionIndex.toInt()]}",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
        Spacer(modifier = Modifier.height(4.dp))
      }
    },
    confirmButton = {
      Button(onClick = onDismiss) {
        Text("סגירה")
      }
    },
  )
}
