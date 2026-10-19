const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

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

test("Unauthenticated user: cannot read users profile", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
});

test("Authenticated user: Alice can create and read her own profile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set({
    uid: ALICE_UID,
    displayName: "Alice",
    username: "alice",
    usernameNormalized: "alice",
    createdAt: Date.now()
  }));

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
});

test("Authenticated user: Bob cannot update Alice's profile", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(ALICE_UID).set({
      uid: ALICE_UID,
      displayName: "Alice",
      createdAt: Date.now()
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).update({
    displayName: "Hacked Alice"
  }));
});

test("Authenticated user: Alice can create a conversation she belongs to", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(aliceDb.collection("conversations").doc("conv_alice_bob").set({
    id: "conv_alice_bob",
    title: "Alice & Bob",
    isGroup: false,
    memberUids: [ALICE_UID, BOB_UID],
    lastMessageTimestamp: Date.now()
  }));
});

test("Authenticated user: Alice can list conversations matching memberUids array-contains", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("conversations").doc("conv_alice_bob").set({
      id: "conv_alice_bob",
      memberUids: [ALICE_UID, BOB_UID],
      lastMessageTimestamp: Date.now()
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("conversations").where("memberUids", "array-contains", ALICE_UID).get()
  );
});

test("Authenticated user: Bob can send message and list messages in conversation", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("conversations").doc("conv_alice_bob").set({
      id: "conv_alice_bob",
      memberUids: [ALICE_UID, BOB_UID]
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertSucceeds(bobDb.collection("conversations").doc("conv_alice_bob").collection("messages").doc("msg_1").set({
    senderUid: BOB_UID,
    senderName: "Bob",
    ciphertext: "encrypted_hello",
    iv: "iv_123",
    timestamp: Date.now(),
    isEncrypted: true
  }));

  // List messages inside conversation
  await assertSucceeds(bobDb.collection("conversations").doc("conv_alice_bob").collection("messages").get());

  // List typing inside conversation
  await assertSucceeds(bobDb.collection("conversations").doc("conv_alice_bob").collection("typing").get());
});

test("Unauthenticated user: cannot read conversation or messages", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("conversations").doc("conv_alice_bob").set({
      id: "conv_alice_bob",
      memberUids: [ALICE_UID, BOB_UID]
    });
  });

  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("conversations").doc("conv_alice_bob").get());
  await assertFails(unauthDb.collection("conversations").doc("conv_alice_bob").collection("messages").get());
});
