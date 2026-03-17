package server;

import manager.QuestionBank;
import manager.ScoreManager;

import java.util.HashMap;
import java.util.Map;

public class GameManager {
    private WaitingRoom waitingRoom;
    private final Map<ClientHandler, Match> activeMatches = new HashMap<>();

    private TeamWaitingRoom teamWaitingRoom;
    private final Map<ClientHandler, TeamMatch> activeTeamMatches = new HashMap<>();

    private static class WaitingRoom {
        ClientHandler host;
        String category;
        String difficulty;
        int questionCount;

        WaitingRoom(ClientHandler host, String category, String difficulty, int questionCount) {
            this.host = host;
            this.category = category;
            this.difficulty = difficulty;
            this.questionCount = questionCount;
        }
    }

    private static class Match {
        ClientHandler player1;
        ClientHandler player2;
        String category;
        String difficulty;
        int questionCount;
        boolean finished;

        Match(ClientHandler player1, ClientHandler player2, String category, String difficulty, int questionCount) {
            this.player1 = player1;
            this.player2 = player2;
            this.category = category;
            this.difficulty = difficulty;
            this.questionCount = questionCount;
            this.finished = false;
        }
    }

    private static class TeamWaitingRoom {
        String roomName;
        ClientHandler host;
        String category;
        String difficulty;
        int questionCount;
        int teamSizeRequired;
        Team teamA;
        Team teamB;

        TeamWaitingRoom(String roomName, ClientHandler host,
                        String teamAName, String teamBName,
                        String category, String difficulty,
                        int questionCount, int teamSizeRequired) {
            this.roomName = roomName;
            this.host = host;
            this.category = category;
            this.difficulty = difficulty;
            this.questionCount = questionCount;
            this.teamSizeRequired = teamSizeRequired;

            this.teamA = new Team(teamAName);
            this.teamB = new Team(teamBName);
            this.teamA.addPlayer(host);
        }
    }

    private static class TeamMatch {
        Team teamA;
        Team teamB;
        String category;
        String difficulty;
        int questionCount;
        boolean finished;

        TeamMatch(Team teamA, Team teamB, String category, String difficulty, int questionCount) {
            this.teamA = teamA;
            this.teamB = teamB;
            this.category = category;
            this.difficulty = difficulty;
            this.questionCount = questionCount;
            this.finished = false;
        }
    }

    // ---------------- MULTIPLAYER ----------------

    public synchronized boolean hasWaitingRoom() {
        return waitingRoom != null;
    }

    public synchronized String getWaitingRoomSummary() {
        if (waitingRoom == null) {
            return "No waiting room available.";
        }

        return "Category: " + waitingRoom.category +
                ", Difficulty: " + waitingRoom.difficulty +
                ", Questions: " + waitingRoom.questionCount;
    }

    public void createOrJoinMultiplayer(ClientHandler player,
                                        QuestionBank questionBank,
                                        ScoreManager scoreManager,
                                        int questionDuration,
                                        String category,
                                        String difficulty,
                                        int questionCount) {
        Match matchToPlay = null;
        boolean shouldRunMatch = false;

        synchronized (this) {
            if (waitingRoom == null) {
                if (category == null || difficulty == null || questionCount <= 0) {
                    player.sendMessage("No open multiplayer room found. Please try again.");
                    return;
                }

                waitingRoom = new WaitingRoom(player, category, difficulty, questionCount);

                player.sendMessage("=== MULTIPLAYER WAITING ROOM ===");
                player.sendMessage("Match settings:");
                player.sendMessage("Category: " + category);
                player.sendMessage("Difficulty: " + difficulty);
                player.sendMessage("Questions: " + questionCount);
                player.sendMessage("Waiting for another player to join...");

                while (waitingRoom != null && waitingRoom.host == player && !activeMatches.containsKey(player)) {
                    try {
                        wait(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        removeFromWaiting(player);
                        return;
                    }

                    if (player.isConnectionClosed()) {
                        if (waitingRoom != null && waitingRoom.host == player) {
                            waitingRoom = null;
                        }
                        notifyAll();
                        return;
                    }
                }

                Match match = activeMatches.get(player);
                if (match == null) {
                    player.sendMessage("You left the multiplayer waiting room.");
                    return;
                }

                while (!match.finished) {
                    try {
                        wait(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }

                    if (player.isConnectionClosed()) {
                        return;
                    }
                }

                return;
            } else {
                WaitingRoom room = waitingRoom;
                waitingRoom = null;

                ClientHandler host = room.host;

                matchToPlay = new Match(host, player, room.category, room.difficulty, room.questionCount);
                activeMatches.put(host, matchToPlay);
                activeMatches.put(player, matchToPlay);

                host.sendMessage("Match found!");
                host.sendMessage("Opponent: " + player.getCurrentUsername());
                host.sendMessage("Using settings -> Category: " + room.category + ", Difficulty: " + room.difficulty + ", Questions: " + room.questionCount);
                host.sendMessage("Starting multiplayer game...");

                player.sendMessage("Match found!");
                player.sendMessage("Opponent: " + host.getCurrentUsername());
                player.sendMessage("Using settings -> Category: " + room.category + ", Difficulty: " + room.difficulty + ", Questions: " + room.questionCount);
                player.sendMessage("Starting multiplayer game...");

                notifyAll();
                shouldRunMatch = true;
            }
        }

        if (shouldRunMatch) {
            try {
                GameSession gameSession = new GameSession(questionBank, scoreManager, questionDuration);
                gameSession.playMultiplayer(
                        matchToPlay.player1,
                        matchToPlay.player2,
                        matchToPlay.category,
                        matchToPlay.difficulty,
                        matchToPlay.questionCount
                );
            } finally {
                synchronized (this) {
                    matchToPlay.finished = true;
                    activeMatches.remove(matchToPlay.player1);
                    activeMatches.remove(matchToPlay.player2);
                    notifyAll();
                }
            }
        }
    }

    // ---------------- TEAM MODE ----------------

    public synchronized boolean hasOpenTeamRoom() {
        return teamWaitingRoom != null;
    }

    public synchronized String getTeamRoomSummary() {
        if (teamWaitingRoom == null) {
            return "No open team room.";
        }

        return "Room: " + teamWaitingRoom.roomName +
                " | Team A: " + teamWaitingRoom.teamA.getName() + " (" + teamWaitingRoom.teamA.size() + "/" + teamWaitingRoom.teamSizeRequired + ")" +
                " | Team B: " + teamWaitingRoom.teamB.getName() + " (" + teamWaitingRoom.teamB.size() + "/" + teamWaitingRoom.teamSizeRequired + ")" +
                " | Category: " + teamWaitingRoom.category +
                " | Difficulty: " + teamWaitingRoom.difficulty +
                " | Questions: " + teamWaitingRoom.questionCount;
    }

    public void createOrJoinTeamGame(ClientHandler player,
                                     QuestionBank questionBank,
                                     ScoreManager scoreManager,
                                     int questionDuration,
                                     String roomName,
                                     String teamAName,
                                     String teamBName,
                                     String category,
                                     String difficulty,
                                     int questionCount,
                                     int teamSizeRequired,
                                     String chosenTeamName) {
        TeamMatch matchToPlay = null;
        boolean shouldRunMatch = false;

        synchronized (this) {
            if (teamWaitingRoom == null) {
                if (roomName == null || teamAName == null || teamBName == null ||
                        category == null || difficulty == null || questionCount <= 0 || teamSizeRequired <= 0) {
                    player.sendMessage("No open team room found. Please try again.");
                    return;
                }

                teamWaitingRoom = new TeamWaitingRoom(
                        roomName,
                        player,
                        teamAName,
                        teamBName,
                        category,
                        difficulty,
                        questionCount,
                        teamSizeRequired
                );

                player.sendMessage("=== TEAM WAITING ROOM ===");
                player.sendMessage("Room: " + roomName);
                player.sendMessage("You are assigned to Team A: " + teamAName);
                player.sendMessage("Required players per team: " + teamSizeRequired);
                player.sendMessage("Team A size: " + teamWaitingRoom.teamA.size() + "/" + teamSizeRequired);
                player.sendMessage("Team B size: " + teamWaitingRoom.teamB.size() + "/" + teamSizeRequired);
                player.sendMessage("Category: " + category);
                player.sendMessage("Difficulty: " + difficulty);
                player.sendMessage("Questions: " + questionCount);
                player.sendMessage("Waiting for players to join...");

                while (teamWaitingRoom != null &&
                        !activeTeamMatches.containsKey(player)) {
                    try {
                        wait(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        removeFromWaiting(player);
                        return;
                    }

                    if (player.isConnectionClosed()) {
                        if (teamWaitingRoom != null && teamWaitingRoom.host == player) {
                            teamWaitingRoom = null;
                        }
                        notifyAll();
                        return;
                    }
                }

                TeamMatch match = activeTeamMatches.get(player);
                if (match == null) {
                    player.sendMessage("You left the team waiting room.");
                    return;
                }

                while (!match.finished) {
                    try {
                        wait(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }

                    if (player.isConnectionClosed()) {
                        return;
                    }
                }

                return;
            } else {
                TeamWaitingRoom room = teamWaitingRoom;

                if (chosenTeamName == null || chosenTeamName.trim().isEmpty()) {
                    player.sendMessage("You must choose a team name to join.");
                    return;
                }

                chosenTeamName = chosenTeamName.trim();

                Team chosenTeam;
                if (chosenTeamName.equalsIgnoreCase(room.teamA.getName())) {
                    chosenTeam = room.teamA;
                } else if (chosenTeamName.equalsIgnoreCase(room.teamB.getName())) {
                    chosenTeam = room.teamB;
                } else {
                    player.sendMessage("Invalid team name. Choose exactly: " + room.teamA.getName() + " or " + room.teamB.getName());
                    return;
                }

                if (chosenTeam.containsPlayer(player)) {
                    player.sendMessage("You are already in that team.");
                    return;
                }

                if (chosenTeam.size() >= room.teamSizeRequired) {
                    player.sendMessage("That team is already full.");
                    return;
                }

                chosenTeam.addPlayer(player);

                for (ClientHandler teamPlayer : room.teamA.getPlayers()) {
                    teamPlayer.sendMessage(player.getCurrentUsername() + " joined " + chosenTeam.getName() +
                            ". Team A size: " + room.teamA.size() + "/" + room.teamSizeRequired +
                            ", Team B size: " + room.teamB.size() + "/" + room.teamSizeRequired);
                }

                for (ClientHandler teamPlayer : room.teamB.getPlayers()) {
                    teamPlayer.sendMessage(player.getCurrentUsername() + " joined " + chosenTeam.getName() +
                            ". Team A size: " + room.teamA.size() + "/" + room.teamSizeRequired +
                            ", Team B size: " + room.teamB.size() + "/" + room.teamSizeRequired);
                }

                if (room.teamA.size() == room.teamSizeRequired &&
                        room.teamB.size() == room.teamSizeRequired) {
                    teamWaitingRoom = null;

                    matchToPlay = new TeamMatch(
                            room.teamA,
                            room.teamB,
                            room.category,
                            room.difficulty,
                            room.questionCount
                    );

                    for (ClientHandler teamPlayer : room.teamA.getPlayers()) {
                        activeTeamMatches.put(teamPlayer, matchToPlay);
                    }
                    for (ClientHandler teamPlayer : room.teamB.getPlayers()) {
                        activeTeamMatches.put(teamPlayer, matchToPlay);
                    }

                    for (ClientHandler teamPlayer : room.teamA.getPlayers()) {
                        teamPlayer.sendMessage("Team match found!");
                        teamPlayer.sendMessage("Starting team game...");
                    }
                    for (ClientHandler teamPlayer : room.teamB.getPlayers()) {
                        teamPlayer.sendMessage("Team match found!");
                        teamPlayer.sendMessage("Starting team game...");
                    }

                    notifyAll();
                    shouldRunMatch = true;
                } else {
                    while (teamWaitingRoom != null && !activeTeamMatches.containsKey(player)) {
                        try {
                            wait(500);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }

                        if (player.isConnectionClosed()) {
                            return;
                        }
                    }

                    TeamMatch match = activeTeamMatches.get(player);
                    if (match == null) {
                        player.sendMessage("You left the team waiting room.");
                        return;
                    }

                    while (!match.finished) {
                        try {
                            wait(500);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }

                        if (player.isConnectionClosed()) {
                            return;
                        }
                    }

                    return;
                }
            }
        }

        if (shouldRunMatch) {
            try {
                GameSession gameSession = new GameSession(questionBank, scoreManager, questionDuration);
                gameSession.playTeamGame(
                        matchToPlay.teamA,
                        matchToPlay.teamB,
                        matchToPlay.category,
                        matchToPlay.difficulty,
                        matchToPlay.questionCount
                );
            } finally {
                synchronized (this) {
                    matchToPlay.finished = true;

                    for (ClientHandler teamPlayer : matchToPlay.teamA.getPlayers()) {
                        activeTeamMatches.remove(teamPlayer);
                    }
                    for (ClientHandler teamPlayer : matchToPlay.teamB.getPlayers()) {
                        activeTeamMatches.remove(teamPlayer);
                    }

                    notifyAll();
                }
            }
        }
    }

    public synchronized void removeFromWaiting(ClientHandler player) {
        if (waitingRoom != null && waitingRoom.host == player) {
            waitingRoom = null;
            notifyAll();
        }

        if (teamWaitingRoom != null && teamWaitingRoom.host == player) {
            teamWaitingRoom = null;
            notifyAll();
        }
    }
}