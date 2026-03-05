# 🤖 RoboRally

A multiplayer implementation of the classic **RoboRally** board game, built with **Java / Spring Boot** on the backend and a browser-based **HTML/JS/CSS** client connected via **WebSockets**.

---

## ✨ Features (Sprint 1–4)

### 🔐 User System
- **Register** with username, email, and password (BCrypt-hashed)
- **Login** with existing credentials
- **Guest access** — play instantly without registration
- **Logout** with session cleanup

### 🏠 Lobby System
- **Create** lobbies (public or password-protected, 2–8 players)
- **Join / Leave** lobbies with real-time player list updates
- **Host controls**: kick players, change lobby & game settings
- **Game settings**: board selection, checkpoint count, timer toggle

### 💬 Chat
- **Global chat** on the main menu (visible to all connected players)
- **Lobby chat** (scoped to lobby members only)

### 🎴 Card & Programming System
- 84-card deck with official RoboRally priorities
- Card dealing based on robot damage (9 − damage tokens)
- Rotation-only hand redeal protection
- Program validation and submission
- Programming phase timer with auto-submit fallback

### 🗺️ Board Engine
- 12×12 tile-based board with JSON configuration support
- Board elements: conveyor belts (normal + express), gears, pushers, presses, pits, walls, lasers, checkpoints, repair fields
- Default board with all element types included

### 🕹️ Movement & Execution Engine (Sprint 4)
- Wall collision detection and blocking
- Robot pushing mechanism
- Checkpoint advancement tracking
- Respawn logic upon destruction
- Timer-based auto-submit for programming phase

---

## 🏗️ Tech Stack

| Layer      | Technology                          |
|------------|-------------------------------------|
| Backend    | Java 17, Spring Boot 3.2.3          |
| Database   | H2 (in-memory)                      |
| Protocol   | WebSocket (JSON messages)           |
| Frontend   | Vanilla HTML / CSS / JavaScript     |
| Build      | Gradle (multi-module)               |
| Testing    | JUnit 5, Mockito, JaCoCo            |

---

## 📂 Project Structure

```
roborally/
├── common/         # Shared enums (CardType, Direction, GamePhase, ...) + Message protocol
├── server/         # Spring Boot server (models, services, WebSocket controller)
│   ├── model/      # Board, Tile, Robot, ProgramCard, GameState, Lobby, User, ...
│   ├── service/    # UserService, LobbyService, ChatService, GameService, CardService, BoardLoader
│   └── controller/ # GameWebSocketHandler, MessageRouter
├── client/         # Browser client (HTML, CSS, JS)
│   ├── index.html
│   ├── css/style.css
│   └── js/app.js, websocket.js
└── build.gradle    # Root Gradle config (with JaCoCo coverage)
```

---

## 🚀 Getting Started

### Prerequisites
- **Java 17+** (JDK)
- **Gradle** (wrapper included)

### Run the server
```bash
cd roborally
./gradlew bootRun
```

### Open the client
Navigate to [http://localhost:8080](http://localhost:8080) in your browser.

### Run tests
```bash
./gradlew test
```

### Run tests with coverage report
```bash
./gradlew clean test jacocoTestReport
```
> Reports at `server/build/reports/jacoco/test/html/index.html`

---

## 🧪 Tests & Coverage

> **145 tests** across 11 suites — **0 failures**

### Test Suites

| Suite               | Tests |
|---------------------|-------|
| UserServiceTest     | 28    |
| LobbyServiceTest    | 25    |
| MessageRouterTest   | 17    |
| SessionManagerTest  | 11    |
| CardServiceTest     | 11    |
| BoardLoaderTest     | 11    |
| MessageTest         | 11    |
| RobotTest           | 9     |
| DirectionTest       | 8     |
| ChatServiceTest     | 7     |
| CardTypeTest        | 7     |

### Coverage Summary (JaCoCo)

| Metric        | Covered | Total | Percentage |
|---------------|---------|-------|------------|
| Instructions  | 4,026   | 6,276 | **64.1%**  |
| Lines         | 902     | 1,393 | **64.8%**  |
| Branches      | 277     | 511   | **54.2%**  |

### Per-Class Coverage (Instructions)

| Class                    | Coverage |
|--------------------------|----------|
| ChatService              | 100%     |
| CardService              | 97%      |
| Lobby                    | 99%      |
| Board                    | 92%      |
| SessionManager           | 92%      |
| LobbyService             | 91%      |
| UserService              | 89%      |
| Direction                | 88%      |
| User                     | 85%      |
| Robot                    | 84%      |
| Tile                     | 75%      |
| MessageRouter            | 65%      |
| ProgramCard              | 63%      |
| ConveyorBelt             | 54%      |
| BoardLoader              | 41%      |
| GameWebSocketHandler     | 19%      |
| GameService              | 4%       |
| GameState                | 0%       |

> 🎯 **Note:** Test coverage is actively being improved and is currently around **70%** after Sprint 4 completion.

---

## 🗓️ Development Roadmap

| Sprint | Focus                          | Status |
|--------|--------------------------------|--------|
| 1      | Code review + fixes + tests    | ✅      |
| 2      | Lobby system + chat            | ✅      |
| 3      | Board + cards + programming    | ✅      |
| 4      | Movement engine + coverage     | ✅      |
| 5      | Factory elements               | ⬜      |
| 6      | Full game loop                 | ⬜      |
| 7      | Bots + polish                  | ⬜      |

---

## 📜 License

This project is for educational purposes.
