const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_rider_1";
const BOB_UID = "bob_rider_2";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function nowTimestamp() {
  return new Date(Date.now() - 2000);
}

function validProfile(uid, displayName = "Alice Sportive") {
  return {
    userId: uid,
    displayName,
    pointsBalance: 0,
    totalPointsEarned: 0,
    totalDistanceMeters: 0,
    totalRides: 0,
    cyclingPointsEarned: 0,
    cyclingDistanceMeters: 0,
    cyclingRides: 0,
    runningPointsEarned: 0,
    runningDistanceMeters: 0,
    runningRides: 0,
    unlockedCosmetics: ["bike_mint", "helmet_classic", "trail_emerald", "badge_freewheel"],
    equippedBikeId: "bike_mint",
    equippedHelmetId: "helmet_classic",
    equippedTrailId: "trail_emerald",
    equippedBadgeId: "badge_freewheel",
    createdAt: nowTimestamp(),
    updatedAt: nowTimestamp(),
  };
}

function validLeaderboardEntry(
  uid,
  displayName = "Alice Sportive",
  cyclingPts = 0,
  cyclingDist = 0,
  cyclingRides = 0,
  runningPts = 0,
  runningDist = 0,
  runningRides = 0
) {
  return {
    userId: uid,
    displayName,
    totalPointsEarned: cyclingPts + runningPts,
    totalDistanceMeters: cyclingDist + runningDist,
    totalRides: cyclingRides + runningRides,
    cyclingPointsEarned: cyclingPts,
    cyclingDistanceMeters: cyclingDist,
    cyclingRides,
    runningPointsEarned: runningPts,
    runningDistanceMeters: runningDist,
    runningRides,
    updatedAt: nowTimestamp(),
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

test("Unauthenticated user cannot read or write user profile, rides, or leaderboard", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).set(validProfile(ALICE_UID)));
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("rides").get());
  await assertFails(unauthDb.collection("leaderboard").limit(50).get());
});

test("Authenticated user can create multi-sport profile and sync leaderboard entry", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const batch = aliceDb.batch();
  batch.set(aliceDb.collection("users").doc(ALICE_UID), validProfile(ALICE_UID));
  batch.set(aliceDb.collection("leaderboard").doc(ALICE_UID), validLeaderboardEntry(ALICE_UID));
  await assertSucceeds(batch.commit());
});

test("Authenticated user cannot inflate cycling points beyond 10 pts/km or spoof leaderboard", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const batch = aliceDb.batch();
  batch.set(aliceDb.collection("users").doc(ALICE_UID), validProfile(ALICE_UID));
  batch.set(aliceDb.collection("leaderboard").doc(ALICE_UID), validLeaderboardEntry(ALICE_UID));
  await assertSucceeds(batch.commit());

  // Try claiming 500 cycling pts for 1000m (max allowed is ~11)
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      pointsBalance: 500,
      totalPointsEarned: 500,
      totalDistanceMeters: 1000,
      totalRides: 1,
      cyclingPointsEarned: 500,
      cyclingDistanceMeters: 1000,
      cyclingRides: 1,
      updatedAt: nowTimestamp(),
    })
  );

  // Try spoofing leaderboard without matching user doc
  await assertFails(
    aliceDb.collection("leaderboard").doc(ALICE_UID).set(
      validLeaderboardEntry(ALICE_UID, "Alice", 9999, 1000, 1, 0, 0, 0)
    )
  );
});

test("Cross-user isolation: Bob can query sport leaderboards (limit <= 50) but cannot read Alice private rides", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const batch = aliceDb.batch();
  batch.set(aliceDb.collection("users").doc(ALICE_UID), validProfile(ALICE_UID));
  batch.set(aliceDb.collection("leaderboard").doc(ALICE_UID), validLeaderboardEntry(ALICE_UID));
  await assertSucceeds(batch.commit());

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("rides").get());
  await assertFails(bobDb.collection("leaderboard").doc(ALICE_UID).set(validLeaderboardEntry(ALICE_UID)));

  await assertSucceeds(bobDb.collection("leaderboard").orderBy("totalPointsEarned", "desc").limit(50).get());
  await assertSucceeds(bobDb.collection("leaderboard").orderBy("cyclingPointsEarned", "desc").limit(50).get());
  await assertSucceeds(bobDb.collection("leaderboard").orderBy("runningPointsEarned", "desc").limit(50).get());
  await assertFails(bobDb.collection("leaderboard").get());
});

test("Authenticated user can record 2.5km cycling (25 pts) and 400m running (100 pts) and update pseudo", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const profileRef = aliceDb.collection("users").doc(ALICE_UID);
  const lbRef = aliceDb.collection("leaderboard").doc(ALICE_UID);

  const initBatch = aliceDb.batch();
  initBatch.set(profileRef, validProfile(ALICE_UID));
  initBatch.set(lbRef, validLeaderboardEntry(ALICE_UID));
  await assertSucceeds(initBatch.commit());

  // 1. Cycling 2500m => 25 pts
  await assertSucceeds(
    profileRef.collection("rides").doc("ride_cycling_1").set({
      userId: ALICE_UID,
      sportType: "cycling",
      distanceMeters: 2500,
      durationSeconds: 600,
      pointsEarned: 25,
      avgSpeedKmh: 15.0,
      trailCosmeticId: "trail_emerald",
      startLabel: "Balade à vélo",
      createdAt: nowTimestamp(),
    })
  );

  const cyclingBatch = aliceDb.batch();
  cyclingBatch.update(profileRef, {
    pointsBalance: 25,
    totalPointsEarned: 25,
    totalDistanceMeters: 2500,
    totalRides: 1,
    cyclingPointsEarned: 25,
    cyclingDistanceMeters: 2500,
    cyclingRides: 1,
    updatedAt: nowTimestamp(),
  });
  cyclingBatch.set(lbRef, validLeaderboardEntry(ALICE_UID, "Alice Sportive", 25, 2500, 1, 0, 0, 0));
  await assertSucceeds(cyclingBatch.commit());

  // 2. Running 400m => 4 * 25 = 100 pts with recorded GPS waypoints
  await assertSucceeds(
    profileRef.collection("rides").doc("ride_running_1").set({
      userId: ALICE_UID,
      sportType: "running",
      distanceMeters: 400,
      durationSeconds: 120,
      pointsEarned: 100,
      avgSpeedKmh: 12.0,
      trailCosmeticId: "trail_coral",
      startLabel: "Course à pied",
      waypointsJson: "[[47.21,-1.55],[47.2136,-1.55]]",
      createdAt: nowTimestamp(),
    })
  );

  await assertFails(
    profileRef.collection("rides").doc("ride_running_invalid_wp").set({
      userId: ALICE_UID,
      sportType: "running",
      distanceMeters: 400,
      durationSeconds: 120,
      pointsEarned: 100,
      avgSpeedKmh: 12.0,
      trailCosmeticId: "trail_coral",
      startLabel: "Course à pied",
      waypointsJson: "",
      createdAt: nowTimestamp(),
    })
  );

  const runningBatch = aliceDb.batch();
  runningBatch.update(profileRef, {
    pointsBalance: 125,
    totalPointsEarned: 125,
    totalDistanceMeters: 2900,
    totalRides: 2,
    runningPointsEarned: 100,
    runningDistanceMeters: 400,
    runningRides: 1,
    updatedAt: nowTimestamp(),
  });
  runningBatch.set(lbRef, validLeaderboardEntry(ALICE_UID, "Alice Sportive", 25, 2500, 1, 100, 400, 1));
  await assertSucceeds(runningBatch.commit());

  // 3. Rename pseudo
  const renameBatch = aliceDb.batch();
  renameBatch.update(profileRef, {
    displayName: "RunnerPro99",
    updatedAt: nowTimestamp(),
  });
  renameBatch.update(lbRef, {
    displayName: "RunnerPro99",
    updatedAt: nowTimestamp(),
  });
  await assertSucceeds(renameBatch.commit());
});

test("Authenticated user can create, list, and delete planned routes with owner-only isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const routeRef = aliceDb.collection("users").doc(ALICE_UID).collection("routes").doc("route_1");

  await assertSucceeds(
    routeRef.set({
      userId: ALICE_UID,
      name: "Boucle de la Loire",
      sportType: "cycling",
      distanceMeters: 4200,
      estimatedPoints: 42,
      waypointCount: 4,
      waypointsJson: "[[47.21,-1.55],[47.22,-1.54],[47.23,-1.56],[47.21,-1.55]]",
      createdAt: nowTimestamp(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("routes").orderBy("createdAt", "desc").get()
  );

  // Reject route with invalid waypointCount (< 2)
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("routes").doc("route_bad").set({
      userId: ALICE_UID,
      name: "Trop court",
      sportType: "running",
      distanceMeters: 100,
      estimatedPoints: 25,
      waypointCount: 1,
      waypointsJson: "[[47.21,-1.55]]",
      createdAt: nowTimestamp(),
    })
  );

  // Bob cannot read or write Alice's planned routes
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("routes").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("routes").doc("route_1").delete()
  );

  // Alice can delete her own route
  await assertSucceeds(routeRef.delete());
});
