const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const DATABASE_ID = "ai-studio-android-duboraai-c242e196-f26c-469b-b6fa-375a0343034e";
const ALICE_UID = "alice_dubora_123";
const BOB_UID = "bob_dubora_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function ts() {
  return new Date(Date.now() - 2000);
}

function validProfile(uid) {
  return {
    userId: uid,
    displayName: "Alice Cinema",
    email: "alice@dubora.ai",
    defaultSourceLang: "auto",
    defaultTargetLang: "es",
    defaultVoiceGender: "speaker_matched",
    autoDubbing: true,
    wifiOnlyProcessing: false,
    createdAt: ts(),
    updatedAt: ts(),
  };
}

function validSession(uid) {
  return {
    userId: uid,
    movieTitle: "Midnight Echo",
    fileName: "midnight_echo.mp4",
    fileSizeBytes: 125000000,
    durationMs: 5400000,
    lastPositionMs: 120000,
    sourceLang: "en",
    targetLang: "ja",
    voiceProfile: "speaker_matched",
    segmentsProcessed: 18,
    status: "active",
    createdAt: ts(),
    updatedAt: ts(),
  };
}

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

test("Unauthenticated user cannot read or write profiles or sessions", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore(DATABASE_ID);
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("dubbing_sessions").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("dubbing_sessions")
      .doc("sess_1")
      .set(validSession(ALICE_UID))
  );
});

test("Authenticated owner can create, update, and read their UserProfile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const ref = aliceDb.collection("users").doc(ALICE_UID);
  await assertSucceeds(ref.set(validProfile(ALICE_UID)));
  await assertSucceeds(ref.get());
  await assertSucceeds(
    ref.update({
      defaultTargetLang: "fr",
      updatedAt: ts(),
    })
  );
});

test("Cross-user access and ghost fields are rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validProfile(ALICE_UID))
  );
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("dubbing_sessions")
      .doc("sess_alpha")
      .set(validSession(ALICE_UID))
  );

  // Ghost field rejected
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("dubbing_sessions")
      .doc("sess_ghost")
      .set({ ...validSession(ALICE_UID), isAdmin: true })
  );

  // Bob cannot read or write Alice's profile or sessions
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore(DATABASE_ID);
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("dubbing_sessions").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("dubbing_sessions")
      .doc("sess_bob_hack")
      .set(validSession(BOB_UID))
  );
});

test("Authenticated owner can list and update their own dubbing sessions", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const sessRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("dubbing_sessions")
    .doc("sess_101");

  await assertSucceeds(sessRef.set(validSession(ALICE_UID)));
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("dubbing_sessions").get()
  );
  await assertSucceeds(
    sessRef.update({
      lastPositionMs: 245000,
      segmentsProcessed: 24,
      status: "completed",
      updatedAt: ts(),
    })
  );
});
