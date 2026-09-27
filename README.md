# RoutePilot — Driver Android Application

**Project Title:** *IoT-Based Road and Bridge Hazard Detection with Intelligent Route Diversion System*

> **Note:** This repository contains **only the Driver Mobile APK** (`Kotlin + Jetpack Compose`).  
> The Admin Web Dashboard and ESP32 IoT Sensor Ingestion nodes are separate components that communicate with this Driver APK exclusively through the common Backend / Firebase Firestore database.

---

## 1. System Architecture

```text
                  ┌─────────────────────┐
                  │    ADMIN WEBSITE    │
                  │     Separate App    │
                  └──────────┬──────────┘
                             │
                             ▼
                  ┌─────────────────────┐
                  │       BACKEND       │
                  │    API / Firebase   │
                  └──────────┬──────────┘
                             │
                             ▼
                  ┌─────────────────────┐
                  │      DATABASE       │
                  │      Firestore      │
                  └──────────┬──────────┘
                             │
                    Real-time Updates
                             │
                             ▼
                  ┌─────────────────────┐
                  │     DRIVER APK      │
                  │   Kotlin + Compose  │
                  └──────────┬──────────┘
                             │
                 ┌───────────┴────────────┐
                 │                        │
                 ▼                        ▼
          Real Google Map          Real GPS/Places
                 │
                 ▼
             A* Engine
                 │
                 ▼
       Hazard-aware Route
                 │
                 ▼
       Automatic Rerouting
```

---

## 2. Google Maps & Places API Configuration

RoutePilot uses the **Google Maps SDK for Android** and **Google Places SDK for Android (New)**.  
Secrets are injected at build time into `BuildConfig` and `AndroidManifest.xml` using the **Secrets Gradle Plugin** (`.env` / `.env.example`).

### Setup Steps
1. Open the [Google Cloud Console](https://console.cloud.google.com/) and create or select a project.
2. Enable **Maps SDK for Android** and **Places API (New)**.
3. Create an API key under **APIs & Services > Credentials** and restrict it to your Android package (`com.aistudio.routepilot.drvnav`) and SHA-1 certificate fingerprint.
4. Enable billing on the Google Cloud project if required by Google Maps Platform.
5. In **Google AI Studio**, open the **Secrets panel** and add:
   - `MAPS_API_KEY`: Your Google Maps SDK for Android key
   - `PLACES_API_KEY`: Your Google Places SDK (New) key
6. Rebuild and run the application.

---

## 3. Firestore Database Schema

The Driver APK listens in real time to the `hazards` collection where `active == true`:

- **`hazards/{hazardId}`**:
  - `name`: String (e.g., `"Bridge B1"`)
  - `type`: String (`BRIDGE_DAMAGE`, `ROAD_BLOCK`, `FLOOD`, `ACCIDENT`, `CONSTRUCTION`, `HIGH_VIBRATION`, `ABNORMAL_TILT`, `HIGH_STRAIN`, `HIGH_DISPLACEMENT`, `HIGH_WATER_LEVEL`, `RESTRICTED_ROAD`, `OTHER`)
  - `severity`: String (`LOW`, `MEDIUM`, `HIGH`, `CRITICAL`)
  - `status`: String (`WARNING`, `PARTIALLY_BLOCKED`, `BLOCKED`, `CLEARED`)
  - `latitude`: Double
  - `longitude`: Double
  - `radius`: Double (meters)
  - `roadId`: String
  - `bridgeId`: String
  - `description`: String
  - `active`: Boolean
  - `createdAt`: Long
  - `updatedAt`: Long
- **`journeys/{journeyId}`**:
  - `userId`, `source`, `destination`, `distance`, `duration`, `startedAt`, `completedAt`, `status`
- **`users/{userId}`**:
  - `name`, `email`, `language`

---

## 4. 16-Step Project Demonstration Flow

1. **Step 1:** Open the separate Admin Website (or activate **DEMO MODE** in the Driver APK header).
2. **Step 2:** Create hazard: **Bridge B1** | Severity: **Critical** | Status: **Blocked**.
3. **Step 3:** Backend / Firestore database updates.
4. **Step 4:** Open the **RoutePilot Driver APK** (Splash -> Driver Login or Guest -> Home).
5. **Step 5:** Search or select destination (**District Hospital, Jhansi**).
6. **Step 6:** Review Route Preview (`Recommended: 18.4 km, 32 min` vs `Alternate: 21.7 km, 36 min`) and tap **Start Driving**.
7. **Step 7:** Driver APK receives the active hazard in real time without manual refresh.
8. **Step 8:** Red warning triangle marker appears on the map at Bridge B1.
9. **Step 9:** `RouteImpactDetector` verifies that Bridge B1 intersects the remaining NH-27 route ahead (`2.1 km ahead`).
10. **Step 10:** Red alert banner displays **"Hazard Detected Ahead! Bridge B1 - Critical Hazard"** with the Bridge B1 inspection card (**Road Blocked**).
11. **Step 11:** Screen transitions to **"Recalculating Route... Finding a safer and optimal path for you"**.
12. **Step 12:** Internal `AStarRoutingEngine` disables the blocked Bridge B1 edge and computes the optimal safer path.
13. **Step 13:** Green banner displays **"Route Updated — A safer route has been selected."** (`17.6 km`, `30 min`) with a green polyline bypassing Bridge B1.
14. **Step 14:** Driver continues navigation along the safer route.
15. **Step 15:** Driver arrives within proximity of the destination.
16. **Step 16:** **Journey Completed!** screen displays destination, total distance (`18.4 km`), and total time (`31 min`), and saves the record to History.
