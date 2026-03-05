# 🚀 RoboRally - Start Here!

Welcome to the RoboRally project! If you are a new agent (or returning to the project), this guide provides the full context needed to hit the ground running for upcoming sprints.

## 📌 Project Overview
RoboRally is a multiplayer implementation of the classic board game. Players program their robots using a set of cards (Movement, Turning) simultaneously, and then all robots execute their programs step-by-step.

**Tech Stack:**
- **Backend:** Java 17, Spring Boot 3.2.3, H2 Database (in-memory)
- **Protocol:** WebSockets (JSON messages) used for real-time multiplayer updates.
- **Frontend:** Vanilla HTML, CSS, JavaScript (no heavy frameworks).
- **Core Engine:** Custom movement and board interpretation logic.

---

## 🏗️ Project Structure
The repository is divided into three main components:

1. **`common/`**
   - Contains shared enumerations (`CardType`, `Direction`, `GamePhase`, etc.) and the `Message` object definition used for WebSocket communication.
   
2. **`server/`**
   - The Spring Boot application.
   - **`model/`**: All domain models (`Board`, `Tile`, `Robot`, `ProgramCard`, `GameState`, `Lobby`, `User`).
   - **`service/`**: Core business logic (`GameService`, `MovementService`, `CardService`, `LobbyService`, `BoardLoader`).
   - **`controller/`**: `GameWebSocketHandler` and `MessageRouter` manage incoming WebSocket traffic and dispatch it to services.
   - **`config/`**: Sets up WebSockets and static resource serving (serves the `client/` folder to `http://localhost:8080`).

3. **`client/`**
   - The frontend browser client.
   - `index.html`: The main entry point containing the UI structure.
   - `css/style.css`: All styling (vanilla CSS).
   - `js/app.js`: Main UI logic, DOM manipulation, rendering the board (Canvas).
   - `js/websocket.js`: Handles sending and receiving WebSocket messages to/from the server.

---

## 🚦 Current Status
We have just completed **Sprint 4**. Here is what is functional:
- ✅ **Users & Lobbies:** Registration, login, lobby creation, joining, and global/lobby chat.
- ✅ **Game Initialization:** Dealing cards, placing robots, interpreting JSON boards.
- ✅ **Programming Phase:** Players drag-and-drop 5 cards to program their robots. Timer auto-submits.
- ✅ **Movement Engine (Sprint 4):**
  - **Collisions & Pushing:** Robots cannot move through walls and will push other robots.
  - **Checkpoints:** Robots track checkpoint progression for winning.
  - **Destruction/Respawn:** Lasers/pits destroy robots, deducting a life and forcing a respawn.
  - **Test Coverage:** Extensive JUnit + Mockito tests covering `MovementService` and `GameService` with an overall ~70% coverage. GOAL IS 85%+!!!

---

## 🎯 Upcoming Work (Sprint 5-7)

### Sprint 5: Factory Elements
- **Goal:** Implement the board elements that affect robots at the end of every register (step).
- **Elements to Implement:**
  - **Conveyor Belts:** Move robots 1 tile (normal) or 2 tiles (express). Handle curves.
  - **Gears:** Rotate robots 90 degrees left or right.
  - **Pushers:** Push robots to adjacent tiles on specific registers.
  - **Presses / Lasers:** Deal damage / destroy robots.
- **Where to look:** `MovementService` (needs logic for end-of-register board element effects).

### Sprint 6: Full Game Loop
- **Goal:** Tie the phases together seamlessly.
- **Tasks:**
  - Transition from Execution Phase back to Programming Phase.
  - Check for win conditions (all checkpoints reached).
  - Handle player elimination (0 lives).

### Sprint 7: Bots + Polish
- **Goal:** Implement AI logic for bots to play against human players + final UI polish.

---

## 🛠️ How to Run & Test
**Start the Application:**
```bash
cd roborally
./gradlew bootRun
```
*The UI will be accessible at: `http://localhost:8080`*

**Run the Tests:**
```bash
cd roborally
./gradlew clean test jacocoTestReport
```
*Check `server/build/reports/jacoco/test/html/index.html` for coverage.*

---

## 🔑 Key Concepts for Next Agents
1. **The Execution Loop (`GameService.java` & `MovementService.java`)**
   - When all players submit programs, `MovementService` executes them step-by-step.
   - **Sprint 5 context:** You will need to inject logic *between* or *after* card executions to process board elements (conveyors, gears, lasers).

2. **WebSocket Routing (`MessageRouter.java`)**
   - All client messages come through `handleMessage()`. If adding new actions, add a `MessageType` and route it here.

3. **Frontend Rendering (`app.js`)**
   - The board is drawn using HTML5 Canvas (`drawBoard()`, `drawRobots()`). You will likely need to expand the rendering logic to accommodate new board elements visually.

Good luck on Sprint 5! 🤖🏁
