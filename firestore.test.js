const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const TEACHER_UID = "teacher_1";
const OTHER_TEACHER_UID = "teacher_2";
const STUDENT_A_UID = "student_a";
const STUDENT_B_UID = "student_b";
const GAME_ID = "K7M9P2";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function validGamePayload(teacherUid = TEACHER_UID, status = "ACTIVE") {
  const now = new Date(Date.now() - 2000);
  return {
    teacherId: teacherUid,
    teacherName: "מורה דנה",
    teacherEmail: "dana@school.org",
    title: "חידון היסטוריה",
    description: "ענו על 3 שאלות קצרות",
    status,
    questionCount: 1,
    createdAt: now,
    updatedAt: now,
  };
}

function validQuestionPayload(gameId = GAME_ID, teacherUid = TEACHER_UID, published = true) {
  const now = new Date(Date.now() - 2000);
  return {
    gameId,
    teacherId: teacherUid,
    prompt: "מהי בירת ישראל?",
    questionType: "MULTIPLE_CHOICE",
    options: ["תל אביב", "ירושלים", "חיפה"],
    correctOptionIndex: 1,
    orderIndex: 0,
    published,
    createdAt: now,
    updatedAt: now,
  };
}

function validSubmissionPayload(gameId = GAME_ID, teacherUid = TEACHER_UID, studentUid = STUDENT_A_UID) {
  const now = new Date(Date.now() - 2000);
  return {
    gameId,
    teacherId: teacherUid,
    studentUid,
    studentName: "נועם לוי",
    studentEmail: "noam@student.org",
    answers: { q1: "ירושלים" },
    score: 1,
    totalScorable: 1,
    completed: true,
    createdAt: now,
    updatedAt: now,
  };
}

test("Unauthenticated users cannot read or write games, questions, or submissions", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("games").get());
  await assertFails(unauthDb.collection("games").doc(GAME_ID).set(validGamePayload()));
  await assertFails(unauthDb.collection("games").doc(GAME_ID).collection("questions").get());
  await assertFails(unauthDb.collection("games").doc(GAME_ID).collection("submissions").get());
});

test("Teacher can create, list own games, update status, and cannot list others games", async () => {
  const teacherDb = testEnv.authenticatedContext(TEACHER_UID).firestore();
  const otherDb = testEnv.authenticatedContext(OTHER_TEACHER_UID).firestore();

  await assertSucceeds(teacherDb.collection("games").doc(GAME_ID).set(validGamePayload()));
  await assertSucceeds(teacherDb.collection("games").where("teacherId", "==", TEACHER_UID).get());

  // Unfiltered list on games must fail
  await assertFails(teacherDb.collection("games").get());
  // Other teacher cannot list teacher 1's games or update them
  await assertFails(otherDb.collection("games").where("teacherId", "==", TEACHER_UID).get());
  await assertFails(
    otherDb.collection("games").doc(GAME_ID).update({
      status: "ENDED",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Teacher can update allowed fields while preserving createdAt
  await assertSucceeds(
    teacherDb.collection("games").doc(GAME_ID).update({
      status: "ENDED",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject ghost field / @DocumentId id field
  await assertFails(
    teacherDb.collection("games").doc("NEW123").set({
      ...validGamePayload(),
      id: "NEW123",
    })
  );
});

test("Teacher creates questions; students read active game & published questions only", async () => {
  const teacherDb = testEnv.authenticatedContext(TEACHER_UID).firestore();
  const studentDb = testEnv.authenticatedContext(STUDENT_A_UID).firestore();

  await assertSucceeds(teacherDb.collection("games").doc(GAME_ID).set(validGamePayload()));
  await assertSucceeds(
    teacherDb.collection("games").doc(GAME_ID).collection("questions").doc("q1").set(validQuestionPayload())
  );

  // Teacher queries own questions
  await assertSucceeds(
    teacherDb.collection("games").doc(GAME_ID).collection("questions").where("teacherId", "==", TEACHER_UID).get()
  );

  // Student gets active game by code & lists published questions
  await assertSucceeds(studentDb.collection("games").doc(GAME_ID).get());
  await assertSucceeds(
    studentDb.collection("games").doc(GAME_ID).collection("questions").where("published", "==", true).get()
  );

  // Student cannot create or modify questions
  await assertFails(
    studentDb.collection("games").doc(GAME_ID).collection("questions").doc("q2").set(
      validQuestionPayload(GAME_ID, TEACHER_UID, true)
    )
  );

  // Teacher ends game and unpublishes question -> student can no longer get ended game
  await assertSucceeds(
    teacherDb.collection("games").doc(GAME_ID).update({
      status: "ENDED",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(studentDb.collection("games").doc(GAME_ID).get());
});

test("Student submission delivers directly to teacher and isolates PII from other students", async () => {
  const teacherDb = testEnv.authenticatedContext(TEACHER_UID).firestore();
  const studentADb = testEnv.authenticatedContext(STUDENT_A_UID).firestore();
  const studentBDb = testEnv.authenticatedContext(STUDENT_B_UID).firestore();

  await assertSucceeds(teacherDb.collection("games").doc(GAME_ID).set(validGamePayload()));

  // Student A submits valid name, email, and answers
  await assertSucceeds(
    studentADb
      .collection("games")
      .doc(GAME_ID)
      .collection("submissions")
      .doc(STUDENT_A_UID)
      .set(validSubmissionPayload())
  );

  // Student A can read own submission and update answers while ACTIVE
  await assertSucceeds(
    studentADb.collection("games").doc(GAME_ID).collection("submissions").doc(STUDENT_A_UID).get()
  );
  await assertSucceeds(
    studentADb.collection("games").doc(GAME_ID).collection("submissions").doc(STUDENT_A_UID).update({
      answers: { q1: "חיפה" },
      score: 0,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Teacher receives submission via scoped list query and direct get
  await assertSucceeds(
    teacherDb.collection("games").doc(GAME_ID).collection("submissions").where("teacherId", "==", TEACHER_UID).get()
  );
  await assertSucceeds(
    teacherDb.collection("games").doc(GAME_ID).collection("submissions").doc(STUDENT_A_UID).get()
  );

  // PII Isolation: Student B CANNOT get or list Student A's email/answers
  await assertFails(
    studentBDb.collection("games").doc(GAME_ID).collection("submissions").doc(STUDENT_A_UID).get()
  );
  await assertFails(
    studentBDb.collection("games").doc(GAME_ID).collection("submissions").where("teacherId", "==", TEACHER_UID).get()
  );
  await assertFails(
    studentBDb.collection("games").doc(GAME_ID).collection("submissions").get()
  );

  // Spoofing teacherId on submission must fail
  await assertFails(
    studentBDb
      .collection("games")
      .doc(GAME_ID)
      .collection("submissions")
      .doc(STUDENT_B_UID)
      .set(validSubmissionPayload(GAME_ID, OTHER_TEACHER_UID, STUDENT_B_UID))
  );

  // Invalid email must fail
  await assertFails(
    studentBDb
      .collection("games")
      .doc(GAME_ID)
      .collection("submissions")
      .doc(STUDENT_B_UID)
      .set({
        ...validSubmissionPayload(GAME_ID, TEACHER_UID, STUDENT_B_UID),
        studentEmail: "not-an-email",
      })
  );

  // Future timestamp must fail
  await assertFails(
    studentBDb
      .collection("games")
      .doc(GAME_ID)
      .collection("submissions")
      .doc(STUDENT_B_UID)
      .set({
        ...validSubmissionPayload(GAME_ID, TEACHER_UID, STUDENT_B_UID),
        createdAt: new Date(Date.now() + 60000),
      })
  );
});
