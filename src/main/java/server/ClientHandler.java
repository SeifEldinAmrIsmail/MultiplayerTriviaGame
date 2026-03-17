package server;

import manager.QuestionBank;
import manager.ScoreManager;
import manager.UserManager;
import model.ScoreRecord;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.List;

public class ClientHandler extends Thread {
    private Socket socket;
    private UserManager userManager;
    private ScoreManager scoreManager;
    private QuestionBank questionBank;
    private GameManager gameManager;
    private int questionDuration;
    private BufferedReader in;
    private PrintWriter out;
    private String currentUsername;

    public ClientHandler(Socket socket, UserManager userManager, ScoreManager scoreManager,
                         QuestionBank questionBank, GameManager gameManager, int questionDuration) {
        this.socket = socket;
        this.userManager = userManager;
        this.scoreManager = scoreManager;
        this.questionBank = questionBank;
        this.gameManager = gameManager;
        this.questionDuration = questionDuration;
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            out.println("=== Welcome to Multiplayer Trivia Game ===");

            boolean authenticated = false;

            while (!authenticated) {
                out.println("1. Login");
                out.println("2. Register");
                out.println("3. Exit");
                out.println("Enter your choice:");

                String choice = in.readLine();

                if (choice == null) {
                    return;
                }

                choice = choice.trim();
                if (choice.isEmpty()) {
                    continue;
                }

                if (choice.equals("1")) {
                    out.println("Enter username:");
                    String username = in.readLine();

                    out.println("Enter password:");
                    String password = in.readLine();

                    if (username == null || password == null) {
                        return;
                    }

                    username = username.trim();
                    password = password.trim();

                    int result = userManager.login(username, password);

                    if (result == UserManager.LOGIN_SUCCESS) {
                        currentUsername = userManager.getUser(username).getUsername();
                        out.println("Login successful.");
                        authenticated = true;
                    } else if (result == UserManager.WRONG_PASSWORD) {
                        out.println("401 Unauthorized: Wrong password.");
                    } else if (result == UserManager.USER_NOT_FOUND) {
                        out.println("404 Not Found: Username not found.");
                    }

                } else if (choice.equals("2")) {
                    out.println("Enter name:");
                    String name = in.readLine();

                    out.println("Enter username:");
                    String username = in.readLine();

                    out.println("Enter password:");
                    String password = in.readLine();

                    if (name == null || username == null || password == null) {
                        return;
                    }

                    name = name.trim();
                    username = username.trim();
                    password = password.trim();

                    int result = userManager.register(name, username, password);

                    if (result == UserManager.REGISTER_SUCCESS) {
                        out.println("Registration successful.");
                    } else if (result == UserManager.USERNAME_TAKEN) {
                        out.println("409 Conflict: Username already reserved.");
                    }

                } else if (choice.equals("3")) {
                    out.println("Goodbye.");
                    return;

                } else {
                    out.println("Invalid choice.");
                }
            }

            boolean running = true;

            while (running) {
                out.println("=== MAIN MENU ===");
                out.println("1. Single Player");
                out.println("2. Multiplayer");
                out.println("3. Team Game");
                out.println("4. View Score History");
                out.println("5. Logout / Exit");
                out.println("Enter your choice:");

                String menuChoice = in.readLine();

                if (menuChoice == null) {
                    break;
                }

                menuChoice = menuChoice.trim();
                if (menuChoice.isEmpty()) {
                    continue;
                }

                if (menuChoice.equals("1")) {
                    handleSinglePlayer();

                } else if (menuChoice.equals("2")) {
                    handleMultiplayer();

                } else if (menuChoice.equals("3")) {
                    handleTeamGame();

                } else if (menuChoice.equals("4")) {
                    showScoreHistory();

                } else if (menuChoice.equals("5")) {
                    out.println("Goodbye.");
                    running = false;

                } else {
                    out.println("Invalid choice.");
                }
            }

        } catch (Exception e) {
            System.out.println("Client disconnected unexpectedly.");
        } finally {
            try {
                gameManager.removeFromWaiting(this);

                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }

                System.out.println("Client connection closed.");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void handleSinglePlayer() {
        try {
            out.println("=== SINGLE PLAYER SETUP ===");
            out.println("Type - at any time to cancel.");

            out.println("Enter category:");
            String category = in.readLine();
            if (category == null) {
                return;
            }
            category = category.trim();
            if (category.equals("-")) {
                out.println("Single player setup cancelled.");
                return;
            }

            out.println("Enter difficulty:");
            String difficulty = in.readLine();
            if (difficulty == null) {
                return;
            }
            difficulty = difficulty.trim();
            if (difficulty.equals("-")) {
                out.println("Single player setup cancelled.");
                return;
            }

            out.println("Enter number of questions:");
            String countText = in.readLine();
            if (countText == null) {
                return;
            }
            countText = countText.trim();
            if (countText.equals("-")) {
                out.println("Single player setup cancelled.");
                return;
            }

            int count;
            try {
                count = Integer.parseInt(countText);
            } catch (NumberFormatException e) {
                out.println("Invalid number of questions.");
                return;
            }

            if (count <= 0) {
                out.println("Number of questions must be greater than 0.");
                return;
            }

            if (!questionBank.hasEnoughQuestions(category, difficulty, count)) {
                out.println("Not enough questions available for that category/difficulty.");
                return;
            }

            GameSession gameSession = new GameSession(questionBank, scoreManager, questionDuration);
            gameSession.playSinglePlayer(currentUsername, socket, in, out, category, difficulty, count);

        } catch (Exception e) {
            out.println("Error while starting single player game.");
        }
    }

    private void handleMultiplayer() {
        try {
            if (gameManager.hasWaitingRoom()) {
                out.println("=== MULTIPLAYER JOIN ===");
                out.println("A multiplayer room already exists.");
                out.println("Using host settings -> " + gameManager.getWaitingRoomSummary());
                sendMessage("Joining multiplayer queue...");
                gameManager.createOrJoinMultiplayer(this, questionBank, scoreManager, questionDuration, null, null, 0);
                sendMessage("Returned to main menu.");
                return;
            }

            out.println("=== MULTIPLAYER SETUP ===");
            out.println("The first player in queue chooses the match settings.");
            out.println("Type - at any time to cancel.");

            out.println("Enter category:");
            String category = in.readLine();
            if (category == null) {
                return;
            }
            category = category.trim();
            if (category.equals("-")) {
                out.println("Multiplayer setup cancelled.");
                return;
            }

            out.println("Enter difficulty:");
            String difficulty = in.readLine();
            if (difficulty == null) {
                return;
            }
            difficulty = difficulty.trim();
            if (difficulty.equals("-")) {
                out.println("Multiplayer setup cancelled.");
                return;
            }

            out.println("Enter number of questions:");
            String countText = in.readLine();
            if (countText == null) {
                return;
            }
            countText = countText.trim();
            if (countText.equals("-")) {
                out.println("Multiplayer setup cancelled.");
                return;
            }

            int count;
            try {
                count = Integer.parseInt(countText);
            } catch (NumberFormatException e) {
                out.println("Invalid number of questions.");
                return;
            }

            if (count <= 0) {
                out.println("Number of questions must be greater than 0.");
                return;
            }

            if (!questionBank.hasEnoughQuestions(category, difficulty, count)) {
                out.println("Not enough questions available for that category/difficulty.");
                return;
            }

            sendMessage("Joining multiplayer queue...");
            gameManager.createOrJoinMultiplayer(this, questionBank, scoreManager, questionDuration, category, difficulty, count);
            sendMessage("Returned to main menu.");

        } catch (Exception e) {
            out.println("Error while setting up multiplayer.");
        }
    }

    private void showScoreHistory() {
        List<ScoreRecord> history = scoreManager.getUserHistory(currentUsername);

        out.println("=== SCORE HISTORY ===");

        if (history.isEmpty()) {
            out.println("No score history found.");
            return;
        }

        for (ScoreRecord record : history) {
            out.println(
                    "Mode: " + record.getMode() +
                            " | Score: " + record.getScore() +
                            " | Correct: " + record.getCorrectCount() +
                            " | Wrong: " + record.getWrongCount() +
                            " | Date: " + record.getDate()
            );
        }
    }

    public synchronized void sendMessage(String message) {
        if (out != null) {
            out.println(message);
        }
    }

    public String getCurrentUsername() {
        return currentUsername;
    }

    public Socket getSocket() {
        return socket;
    }

    public BufferedReader getInputReader() {
        return in;
    }

    public boolean isConnectionClosed() {
        return socket == null || socket.isClosed();
    }

    private void handleTeamGame() {
        try {
            if (gameManager.hasOpenTeamRoom()) {
                out.println("=== TEAM JOIN ===");
                out.println("An open team room already exists.");
                out.println(gameManager.getTeamRoomSummary());
                out.println("Enter team name to join:");

                String chosenTeam = in.readLine();
                if (chosenTeam == null) {
                    return;
                }

                chosenTeam = chosenTeam.trim();
                if (chosenTeam.isEmpty()) {
                    out.println("Team join cancelled.");
                    return;
                }

                gameManager.createOrJoinTeamGame(
                        this,
                        questionBank,
                        scoreManager,
                        questionDuration,
                        null,
                        null,
                        null,
                        null,
                        null,
                        0,
                        0,
                        chosenTeam
                );

                sendMessage("Returned to main menu.");
                return;
            }

            out.println("=== TEAM GAME SETUP ===");
            out.println("Host creates the room and joins Team A automatically.");
            out.println("Type - at any time to cancel.");

            out.println("Enter room name:");
            String roomName = in.readLine();
            if (roomName == null) {
                return;
            }
            roomName = roomName.trim();
            if (roomName.equals("-")) {
                out.println("Team setup cancelled.");
                return;
            }

            out.println("Enter Team A name:");
            String teamAName = in.readLine();
            if (teamAName == null) {
                return;
            }
            teamAName = teamAName.trim();
            if (teamAName.equals("-")) {
                out.println("Team setup cancelled.");
                return;
            }

            out.println("Enter Team B name:");
            String teamBName = in.readLine();
            if (teamBName == null) {
                return;
            }
            teamBName = teamBName.trim();
            if (teamBName.equals("-")) {
                out.println("Team setup cancelled.");
                return;
            }

            if (teamAName.equalsIgnoreCase(teamBName)) {
                out.println("Team names must be different.");
                return;
            }

            out.println("Enter category:");
            String category = in.readLine();
            if (category == null) {
                return;
            }
            category = category.trim();
            if (category.equals("-")) {
                out.println("Team setup cancelled.");
                return;
            }

            out.println("Enter difficulty:");
            String difficulty = in.readLine();
            if (difficulty == null) {
                return;
            }
            difficulty = difficulty.trim();
            if (difficulty.equals("-")) {
                out.println("Team setup cancelled.");
                return;
            }

            out.println("Enter number of questions:");
            String countText = in.readLine();
            if (countText == null) {
                return;
            }
            countText = countText.trim();
            if (countText.equals("-")) {
                out.println("Team setup cancelled.");
                return;
            }

            int count;
            try {
                count = Integer.parseInt(countText);
            } catch (NumberFormatException e) {
                out.println("Invalid number of questions.");
                return;
            }

            if (count <= 0) {
                out.println("Number of questions must be greater than 0.");
                return;
            }

            if (!questionBank.hasEnoughQuestions(category, difficulty, count)) {
                out.println("Not enough questions available for that category/difficulty.");
                return;
            }

            out.println("Enter players per team:");
            String teamSizeText = in.readLine();
            if (teamSizeText == null) {
                return;
            }
            teamSizeText = teamSizeText.trim();
            if (teamSizeText.equals("-")) {
                out.println("Team setup cancelled.");
                return;
            }

            int teamSizeRequired;
            try {
                teamSizeRequired = Integer.parseInt(teamSizeText);
            } catch (NumberFormatException e) {
                out.println("Invalid players per team.");
                return;
            }

            if (teamSizeRequired <= 0) {
                out.println("Players per team must be greater than 0.");
                return;
            }

            gameManager.createOrJoinTeamGame(
                    this,
                    questionBank,
                    scoreManager,
                    questionDuration,
                    roomName,
                    teamAName,
                    teamBName,
                    category,
                    difficulty,
                    count,
                    teamSizeRequired,
                    null
            );

            sendMessage("Returned to main menu.");

        } catch (Exception e) {
            out.println("Error while setting up team game.");
        }
    }
}