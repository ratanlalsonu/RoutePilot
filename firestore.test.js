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

const validTimestamp = () => new Date(Date.now() - 1000);

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

test("1. Unauthenticated user cannot read user profiles, journeys, or hazards", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("journeys").get());
  await assertFails(unauthDb.collection("hazards").where("active", "==", true).get());
});

test("2. Authenticated user can create and read their own user profile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = validTimestamp();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      uid: ALICE_UID,
      name: "Alice User",
      email: "alice@example.com",
      authProvider: "google",
      role: "user",
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
});

test("3. Authenticated user cannot read or overwrite another user's profile (PII isolation)", async () => {
  const ts = validTimestamp();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(ALICE_UID).set({
      uid: ALICE_UID,
      name: "Alice User",
      email: "alice@example.com",
      authProvider: "google",
      role: "user",
      createdAt: ts,
      updatedAt: ts,
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      name: "Spoofed by Bob",
      updatedAt: validTimestamp(),
    })
  );
});

test("4. Privilege escalation (role: admin) and ghost fields are rejected on user profile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = validTimestamp();
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      uid: ALICE_UID,
      name: "Alice User",
      email: "alice@example.com",
      authProvider: "google",
      role: "admin",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      uid: ALICE_UID,
      name: "Alice User",
      email: "alice@example.com",
      authProvider: "google",
      role: "user",
      isVerified: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );
});

test("5. Authenticated user can create and query their own journeys, and cannot access Bob's journeys", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = validTimestamp();
  await assertSucceeds(
    aliceDb.collection("journeys").doc("journey_1").set({
      userId: ALICE_UID,
      source: "City Center",
      destination: "District Hospital",
      destinationAddress: "Sector 4",
      distance: 14.5,
      duration: 25,
      startedAt: 1700000000000,
      completedAt: 1700001500000,
      status: "COMPLETED",
      hazardsAvoidedCount: 1,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("journeys").where("userId", "==", ALICE_UID).get()
  );

  // Query without userId filter fails
  await assertFails(aliceDb.collection("journeys").get());

  // Bob cannot read Alice's journey
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("journeys").doc("journey_1").get());
});

test("6. Authenticated user can query active hazards and create valid hazards", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = validTimestamp();
  await assertSucceeds(
    aliceDb.collection("hazards").doc("hazard_b1").set({
      userId: ALICE_UID,
      name: "Bridge B1 Damage",
      type: "BRIDGE_DAMAGE",
      severity: "CRITICAL",
      status: "BLOCKED",
      latitude: 25.45,
      longitude: 78.58,
      radius: 350.0,
      description: "Structural alert",
      active: true,
      source: "ADMIN_BACKEND",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertSucceeds(
    bobDb.collection("hazards").where("active", "==", true).get()
  );

  // Bob cannot update or delete Alice's hazard
  await assertFails(
    bobDb.collection("hazards").doc("hazard_b1").update({
      status: "RESOLVED",
      updatedAt: validTimestamp(),
    })
  );
  await assertFails(bobDb.collection("hazards").doc("hazard_b1").delete());
});
