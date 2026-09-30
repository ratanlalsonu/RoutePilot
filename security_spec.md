# RoutePilot Firestore Security Specification

## 1. Data Invariants

1. **Authentication Mandatory**: No unauthenticated caller (`request.auth == null`) may read (`get`, `list`) or write (`create`, `update`, `delete`) any document in `/users`, `/hazards`, or `/journeys`.
2. **User Profile PII Isolation (`/users/{userId}`)**:
   - A user document at `/users/{userId}` can only be read, created, updated, or deleted by its owner (`request.auth.uid == userId`).
   - `uid` must equal `userId` and `request.auth.uid`, and is immutable on update.
   - `role` is restricted to `'user'` (no self-assigned `'admin'` privilege escalation).
   - `createdAt` and `updatedAt` must be server timestamps (`<= request.time`), with `createdAt` immutable on update.
3. **Journey Multi-User Isolation (`/journeys/{journeyId}`)**:
   - Each journey document belongs exclusively to `userId == request.auth.uid`.
   - `allow list` enforces `resource.data.userId == request.auth.uid` (no blanket reads).
   - `userId` and `createdAt` are immutable on update.
4. **Hazard Telemetry Integrity (`/hazards/{hazardId}`)**:
   - Authenticated users may read active hazards (`resource.data.active == true`).
   - Creating or updating a hazard requires `incoming().userId == request.auth.uid`, valid geographic coordinates (`latitude` in `[-90, 90]`, `longitude` in `[-180, 180]`), bounded `radius` (`1..50000`), and strict schema validation via `isValidHazard(incoming())`.

## 2. The "Dirty Dozen" Payloads

1. **Unauthenticated Read on `/users/{userId}`**: `auth = null` attempting `get /users/alice_123` -> REJECTED.
2. **Cross-User PII Read on `/users/{userId}`**: `auth.uid = "bob_456"` attempting `get /users/alice_123` -> REJECTED.
3. **Identity Spoofing on `/users/{userId}` Create**: `auth.uid = "alice_123"` creating `/users/alice_123` with `{"uid": "bob_456"}` -> REJECTED.
4. **Self-Assigned Admin Privilege Escalation**: `auth.uid = "alice_123"` creating `/users/alice_123` with `{"role": "admin"}` -> REJECTED.
5. **Shadow / Ghost Field Injection**: `auth.uid = "alice_123"` updating `/users/alice_123` with `{"isVerified": true}` -> REJECTED via `hasOnly()`.
6. **Immutable `createdAt` Tampering**: `auth.uid = "alice_123"` updating `/users/alice_123` with modified `createdAt` -> REJECTED.
7. **Future Timestamp Spoofing**: `auth.uid = "alice_123"` creating `/users/alice_123` with `createdAt > request.time` -> REJECTED.
8. **Cross-User Journey Read**: `auth.uid = "bob_456"` reading `/journeys/journey_alice` where `userId == "alice_123"` -> REJECTED.
9. **Unfiltered Journey List Query**: `auth.uid = "alice_123"` querying `db.collection("journeys").get()` without `.where("userId", "==", "alice_123")` -> REJECTED.
10. **Cross-User Journey Creation Spoofing**: `auth.uid = "alice_123"` creating `/journeys/j1` with `{"userId": "bob_456"}` -> REJECTED.
11. **Value Poisoning on Journey Distance**: `auth.uid = "alice_123"` updating `/journeys/j1` with `{"distance": -50}` or string value -> REJECTED by `isValidJourney(incoming())`.
12. **Out-of-Bounds Coordinates on Hazard**: `auth.uid = "alice_123"` creating `/hazards/h1` with `{"latitude": 250.0}` -> REJECTED by `isValidHazard(incoming())`.

## 3. Test Runner Execution

- Tier 1 (JavaScript Rules Unit Tests): `FIRESTORE_EMULATOR_HOST="127.0.0.1:8085" node --test firestore.test.js`
- Tier 2 (Kotlin Robolectric Repository Rule Tests): `curl -s http://127.0.0.1:8085 > /dev/null && gradle :app:testDebugUnitTest --tests '*Rule*'`
