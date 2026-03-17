package server;

import manager.QuestionBank;
import manager.ScoreManager;
import model.Question;
import model.ScoreRecord;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class GameSession {
    private QuestionBank questionBank;
    private ScoreManager scoreManager;
    private int questionDuration;

    public GameSession(QuestionBank questionBank, ScoreManager scoreManager, int questionDuration) {
        this.questionBank = questionBank;
        this.scoreManager = scoreManager;
        this.questionDuration = questionDuration;
    }

    private static class MultiplayerRoundResult {
        HashMap<ClientHandler, String> answers = new HashMap<>();
        ClientHandler winner = null;
        boolean timedOut = false;
    }

    public void playSinglePlayer(String username, Socket socket, BufferedReader in, PrintWriter out,
                                 String category, String difficulty, int count) {
        try {
            List<Question> questions = questionBank.getQuestions(category, difficulty, count);

            int score = 0;
            int correctCount = 0;
            int wrongCount = 0;

            List<String> summary = new ArrayList<>();

            out.println("=== SINGLE PLAYER GAME STARTED ===");
            out.println("Category: " + category);
            out.println("Difficulty: " + difficulty);
            out.println("Questions: " + questions.size());
            out.println("You have " + questionDuration + " seconds for each question.");
            out.println("Type - during the game to quit.");
            out.println("");

            for (int i = 0; i < questions.size(); i++) {
                Question question = questions.get(i);
                String answerToken = createAnswerToken();

                out.println("Question " + (i + 1) + ": " + question.getText());
                out.println("A) " + question.getChoiceA());
                out.println("B) " + question.getChoiceB());
                out.println("C) " + question.getChoiceC());
                out.println("D) " + question.getChoiceD());
                out.println("__ANSWER_OPEN__|" + answerToken);
                out.println("Enter your answer:");

                String answer = waitForTimedAnswer(socket, in, out, answerToken);

                if (answer == null) {
                    wrongCount++;
                    out.println("No valid answer received.");
                    summary.add("Q" + (i + 1) + ": No answer / timeout");
                    out.println("Current score: " + score);
                    out.println("");
                    continue;
                }

                if (answer.equals("-")) {
                    out.println("Game ended by player.");
                    break;
                }

                answer = answer.trim().toUpperCase();
                String correctAnswer = question.getCorrectAnswer().trim().toUpperCase();

                if (!isValidChoice(answer)) {
                    wrongCount++;
                    out.println("Invalid answer. Counted as a wrong attempt. Correct answer was " + correctAnswer);
                    summary.add("Q" + (i + 1) + ": Invalid answer (" + answer + "), Correct: " + correctAnswer);
                } else if (answer.equals(correctAnswer)) {
                    score += 10;
                    correctCount++;
                    out.println("Correct.");
                    summary.add("Q" + (i + 1) + ": Correct");
                } else {
                    wrongCount++;
                    out.println("Wrong. Correct answer was " + correctAnswer);
                    summary.add("Q" + (i + 1) + ": Wrong (Your answer: " + answer + ", Correct: " + correctAnswer + ")");
                }

                out.println("Current score: " + score);
                out.println("");
            }

            out.println("=== GAME OVER ===");
            out.println("Player: " + username);
            out.println("Final score: " + score);
            out.println("Correct answers: " + correctCount);
            out.println("Wrong answers: " + wrongCount);
            out.println("=== QUESTION SUMMARY ===");

            if (summary.isEmpty()) {
                out.println("No answered questions.");
            } else {
                for (String line : summary) {
                    out.println(line);
                }
            }

            ScoreRecord record = new ScoreRecord(
                    username,
                    "single",
                    score,
                    correctCount,
                    wrongCount,
                    LocalDateTime.now().toString()
            );

            scoreManager.saveRecord(record);
            out.println("Score saved successfully.");

        } catch (Exception e) {
            out.println("Error during single player game.");
        }
    }

    public void playMultiplayer(ClientHandler player1, ClientHandler player2,
                                String category, String difficulty, int count) {
        HashMap<ClientHandler, Integer> scores = new HashMap<>();
        HashMap<ClientHandler, Integer> correctCounts = new HashMap<>();
        HashMap<ClientHandler, Integer> wrongCounts = new HashMap<>();

        scores.put(player1, 0);
        scores.put(player2, 0);
        correctCounts.put(player1, 0);
        correctCounts.put(player2, 0);
        wrongCounts.put(player1, 0);
        wrongCounts.put(player2, 0);

        try {
            List<Question> questions = questionBank.getQuestions(category, difficulty, count);

            if (questions.isEmpty()) {
                player1.sendMessage("No questions available for multiplayer.");
                player2.sendMessage("No questions available for multiplayer.");
                return;
            }

            broadcast(player1, player2, "=== MULTIPLAYER GAME STARTED ===");
            broadcast(player1, player2, "Players: " + player1.getCurrentUsername() + " vs " + player2.getCurrentUsername());
            broadcast(player1, player2, "Category: " + category);
            broadcast(player1, player2, "Difficulty: " + difficulty);
            broadcast(player1, player2, "Questions: " + questions.size());
            broadcast(player1, player2, "First correct answer wins the round.");
            broadcast(player1, player2, "You have " + questionDuration + " seconds for each question.");
            broadcast(player1, player2, "Type - during the game to quit.");
            broadcast(player1, player2, "");

            for (int i = 0; i < questions.size(); i++) {
                Question question = questions.get(i);
                String correctAnswer = question.getCorrectAnswer().trim().toUpperCase();
                String answerToken = createAnswerToken();

                broadcast(player1, player2, "Question " + (i + 1) + ": " + question.getText());
                broadcast(player1, player2, "A) " + question.getChoiceA());
                broadcast(player1, player2, "B) " + question.getChoiceB());
                broadcast(player1, player2, "C) " + question.getChoiceC());
                broadcast(player1, player2, "D) " + question.getChoiceD());

                player1.sendMessage("__ANSWER_OPEN__|" + answerToken);
                player2.sendMessage("__ANSWER_OPEN__|" + answerToken);
                player1.sendMessage("Enter your answer:");
                player2.sendMessage("Enter your answer:");

                MultiplayerRoundResult round = waitForFirstCorrectMultiplayerAnswer(
                        player1, player2, correctAnswer, answerToken
                );

                if (round.winner != null) {
                    scores.put(round.winner, scores.get(round.winner) + 10);
                    correctCounts.put(round.winner, correctCounts.get(round.winner) + 1);

                    ClientHandler loser = (round.winner == player1) ? player2 : player1;
                    wrongCounts.put(loser, wrongCounts.get(loser) + 1);

                    player1.sendMessage(buildRoundMessage(player1, round, correctAnswer));
                    player2.sendMessage(buildRoundMessage(player2, round, correctAnswer));
                } else {
                    handleNoWinnerRound(player1, round.answers.get(player1), correctAnswer, wrongCounts);
                    handleNoWinnerRound(player2, round.answers.get(player2), correctAnswer, wrongCounts);

                    player1.sendMessage(buildNoWinnerMessage(round.answers.get(player1), correctAnswer, round.timedOut));
                    player2.sendMessage(buildNoWinnerMessage(round.answers.get(player2), correctAnswer, round.timedOut));
                }

                broadcast(player1, player2,
                        "Score Update -> " +
                                player1.getCurrentUsername() + ": " + scores.get(player1) + " | " +
                                player2.getCurrentUsername() + ": " + scores.get(player2));
                broadcast(player1, player2, "");
            }

            sendMultiplayerFinalResults(player1, player2, scores, correctCounts, wrongCounts);

        } catch (Exception e) {
            player1.sendMessage("Multiplayer game ended due to connection problem.");
            player2.sendMessage("Multiplayer game ended due to connection problem.");
        }
    }

    private MultiplayerRoundResult waitForFirstCorrectMultiplayerAnswer(ClientHandler player1,
                                                                        ClientHandler player2,
                                                                        String correctAnswer,
                                                                        String answerToken) throws Exception {
        MultiplayerRoundResult result = new MultiplayerRoundResult();

        Socket socket1 = player1.getSocket();
        Socket socket2 = player2.getSocket();
        BufferedReader in1 = player1.getInputReader();
        BufferedReader in2 = player2.getInputReader();

        long endTime = System.currentTimeMillis() + (questionDuration * 1000L);
        int lastSentSecond = -1;

        try {
            socket1.setSoTimeout(100);
            socket2.setSoTimeout(100);

            while (System.currentTimeMillis() < endTime) {
                long remainingMillis = endTime - System.currentTimeMillis();
                int remainingSeconds = (int) Math.ceil(remainingMillis / 1000.0);

                if (remainingSeconds <= 10 && remainingSeconds >= 1 && remainingSeconds != lastSentSecond) {
                    player1.sendMessage("__TIMER__|" + remainingSeconds);
                    player2.sendMessage("__TIMER__|" + remainingSeconds);
                    lastSentSecond = remainingSeconds;
                }

                if (!result.answers.containsKey(player1)) {
                    try {
                        String input1 = in1.readLine();
                        if (input1 == null) {
                            throw new Exception("Player 1 disconnected.");
                        }

                        String parsed1 = parseAnswerInput(input1, answerToken);
                        if (parsed1 != null) {
                            result.answers.put(player1, parsed1);

                            String normalized1 = parsed1.trim().toUpperCase();
                            if (isValidChoice(normalized1) && normalized1.equals(correctAnswer)) {
                                result.winner = player1;
                                closeAnswerPhase(player1, player2);
                                return result;
                            }
                        }
                    } catch (SocketTimeoutException ignored) {
                    }
                }

                if (!result.answers.containsKey(player2)) {
                    try {
                        String input2 = in2.readLine();
                        if (input2 == null) {
                            throw new Exception("Player 2 disconnected.");
                        }

                        String parsed2 = parseAnswerInput(input2, answerToken);
                        if (parsed2 != null) {
                            result.answers.put(player2, parsed2);

                            String normalized2 = parsed2.trim().toUpperCase();
                            if (isValidChoice(normalized2) && normalized2.equals(correctAnswer)) {
                                result.winner = player2;
                                closeAnswerPhase(player1, player2);
                                return result;
                            }
                        }
                    } catch (SocketTimeoutException ignored) {
                    }
                }

                if (result.answers.containsKey(player1) && result.answers.containsKey(player2)) {
                    closeAnswerPhase(player1, player2);
                    return result;
                }
            }

            result.timedOut = true;
            closeAnswerPhase(player1, player2);
            broadcast(player1, player2, "Time is up. Moving to next question.");
            return result;

        } finally {
            try {
                socket1.setSoTimeout(0);
            } catch (Exception ignored) {
            }

            try {
                socket2.setSoTimeout(0);
            } catch (Exception ignored) {
            }
        }
    }

    private String waitForTimedAnswer(Socket socket, BufferedReader in, PrintWriter out, String answerToken) {
        long endTime = System.currentTimeMillis() + (questionDuration * 1000L);
        int lastSentSecond = -1;

        try {
            socket.setSoTimeout(100);

            while (System.currentTimeMillis() < endTime) {
                long remainingMillis = endTime - System.currentTimeMillis();
                int remainingSeconds = (int) Math.ceil(remainingMillis / 1000.0);

                if (remainingSeconds <= 10 && remainingSeconds >= 1 && remainingSeconds != lastSentSecond) {
                    out.println("__TIMER__|" + remainingSeconds);
                    lastSentSecond = remainingSeconds;
                }

                try {
                    String input = in.readLine();
                    if (input != null) {
                        String parsed = parseAnswerInput(input, answerToken);
                        if (parsed != null) {
                            out.println("__ANSWER_CLOSED__");
                            socket.setSoTimeout(0);
                            return parsed;
                        }
                    }
                } catch (SocketTimeoutException ignored) {
                }
            }

            out.println("__ANSWER_CLOSED__");
            out.println("Time is up. Moving to next question.");
            socket.setSoTimeout(0);
            return null;

        } catch (Exception e) {
            try {
                socket.setSoTimeout(0);
            } catch (Exception ignored) {
            }
            return null;
        }
    }

    private String parseAnswerInput(String input, String expectedToken) {
        if (input == null || !input.startsWith("ANSWER|")) {
            return null;
        }

        String[] parts = input.split("\\|", 3);
        if (parts.length < 3) {
            return null;
        }

        String token = parts[1];
        String answer = parts[2];

        if (!token.equals(expectedToken)) {
            return null;
        }

        return answer;
    }

    private String createAnswerToken() {
        return String.valueOf(System.nanoTime());
    }

    private void closeAnswerPhase(ClientHandler player1, ClientHandler player2) {
        player1.sendMessage("__ANSWER_CLOSED__");
        player2.sendMessage("__ANSWER_CLOSED__");
    }

    private void handleNoWinnerRound(ClientHandler player,
                                     String answer,
                                     String correctAnswer,
                                     HashMap<ClientHandler, Integer> wrongCounts) {
        if (answer == null) {
            wrongCounts.put(player, wrongCounts.get(player) + 1);
            return;
        }

        answer = answer.trim().toUpperCase();

        if (!isValidChoice(answer) || !answer.equals(correctAnswer)) {
            wrongCounts.put(player, wrongCounts.get(player) + 1);
        }
    }

    private String buildRoundMessage(ClientHandler player, MultiplayerRoundResult round, String correctAnswer) {
        if (round.winner == player) {
            return "Correct. You won this round.";
        }

        String answer = round.answers.get(player);

        if (answer == null) {
            return "Round lost. Opponent answered correctly first.";
        }

        answer = answer.trim().toUpperCase();

        if (!isValidChoice(answer)) {
            return "Invalid answer. Opponent answered correctly first. Correct answer was " + correctAnswer;
        }

        if (answer.equals(correctAnswer)) {
            return "Your answer was correct, but opponent answered correctly first.";
        }

        return "Wrong. Opponent answered correctly first. Your answer: " + answer + ", Correct: " + correctAnswer;
    }

    private String buildNoWinnerMessage(String answer, String correctAnswer, boolean timedOut) {
        if (answer == null || answer.trim().isEmpty()) {
            return timedOut ? "No answer / timeout" : "No answer";
        }

        answer = answer.trim().toUpperCase();

        if (!isValidChoice(answer)) {
            return "Invalid answer. Counted as a wrong attempt. Correct answer was " + correctAnswer;
        }

        if (answer.equals(correctAnswer)) {
            return "Correct, but round closed with no winner decision.";
        }

        return "Wrong. Your answer: " + answer + ", Correct: " + correctAnswer;
    }

    private boolean isValidChoice(String answer) {
        return answer.equals("A") || answer.equals("B") || answer.equals("C") || answer.equals("D");
    }

    private void sendMultiplayerFinalResults(ClientHandler player1,
                                             ClientHandler player2,
                                             HashMap<ClientHandler, Integer> scores,
                                             HashMap<ClientHandler, Integer> correctCounts,
                                             HashMap<ClientHandler, Integer> wrongCounts) throws Exception {
        int score1 = scores.get(player1);
        int score2 = scores.get(player2);

        broadcast(player1, player2, "=== MULTIPLAYER GAME OVER ===");
        broadcast(player1, player2, player1.getCurrentUsername() + " -> Score: " + score1 +
                " | Correct: " + correctCounts.get(player1) +
                " | Wrong: " + wrongCounts.get(player1));
        broadcast(player1, player2, player2.getCurrentUsername() + " -> Score: " + score2 +
                " | Correct: " + correctCounts.get(player2) +
                " | Wrong: " + wrongCounts.get(player2));

        if (score1 > score2) {
            broadcast(player1, player2, "Winner: " + player1.getCurrentUsername());
        } else if (score2 > score1) {
            broadcast(player1, player2, "Winner: " + player2.getCurrentUsername());
        } else {
            broadcast(player1, player2, "Result: Draw");
        }

        ScoreRecord record1 = new ScoreRecord(
                player1.getCurrentUsername(),
                "multiplayer",
                score1,
                correctCounts.get(player1),
                wrongCounts.get(player1),
                LocalDateTime.now().toString()
        );

        ScoreRecord record2 = new ScoreRecord(
                player2.getCurrentUsername(),
                "multiplayer",
                score2,
                correctCounts.get(player2),
                wrongCounts.get(player2),
                LocalDateTime.now().toString()
        );

        scoreManager.saveRecord(record1);
        scoreManager.saveRecord(record2);

        broadcast(player1, player2, "Multiplayer scores saved successfully.");
    }

    private void broadcast(ClientHandler player1, ClientHandler player2, String message) {
        player1.sendMessage(message);
        player2.sendMessage(message);
    }

    public void playTeamGame(Team teamA, Team teamB,
                             String category, String difficulty, int count) {
        HashMap<ClientHandler, Integer> scores = new HashMap<>();
        HashMap<ClientHandler, Integer> correctCounts = new HashMap<>();
        HashMap<ClientHandler, Integer> wrongCounts = new HashMap<>();

        for (ClientHandler player : teamA.getPlayers()) {
            scores.put(player, 0);
            correctCounts.put(player, 0);
            wrongCounts.put(player, 0);
        }

        for (ClientHandler player : teamB.getPlayers()) {
            scores.put(player, 0);
            correctCounts.put(player, 0);
            wrongCounts.put(player, 0);
        }

        try {
            List<Question> questions = questionBank.getQuestions(category, difficulty, count);

            if (questions.isEmpty()) {
                broadcastToPlayers(getAllTeamPlayers(teamA, teamB), "No questions available for team game.");
                return;
            }

            List<ClientHandler> allPlayers = getAllTeamPlayers(teamA, teamB);

            broadcastToPlayers(allPlayers, "=== TEAM GAME STARTED ===");
            broadcastToPlayers(allPlayers, "Team A: " + teamA.getName() + " -> Players: " + getPlayerNames(teamA));
            broadcastToPlayers(allPlayers, "Team B: " + teamB.getName() + " -> Players: " + getPlayerNames(teamB));
            broadcastToPlayers(allPlayers, "Category: " + category);
            broadcastToPlayers(allPlayers, "Difficulty: " + difficulty);
            broadcastToPlayers(allPlayers, "Questions: " + questions.size());
            broadcastToPlayers(allPlayers, "First correct answer wins the round for the team.");
            broadcastToPlayers(allPlayers, "You have " + questionDuration + " seconds for each question.");
            broadcastToPlayers(allPlayers, "");

            for (int i = 0; i < questions.size(); i++) {
                Question question = questions.get(i);
                String correctAnswer = question.getCorrectAnswer().trim().toUpperCase();
                String answerToken = createAnswerToken();

                broadcastToPlayers(allPlayers, "Question " + (i + 1) + ": " + question.getText());
                broadcastToPlayers(allPlayers, "A) " + question.getChoiceA());
                broadcastToPlayers(allPlayers, "B) " + question.getChoiceB());
                broadcastToPlayers(allPlayers, "C) " + question.getChoiceC());
                broadcastToPlayers(allPlayers, "D) " + question.getChoiceD());

                for (ClientHandler player : allPlayers) {
                    player.sendMessage("__ANSWER_OPEN__|" + answerToken);
                    player.sendMessage("Enter your answer:");
                }

                MultiplayerRoundResult round = waitForFirstCorrectTeamAnswer(allPlayers, correctAnswer, answerToken);

                if (round.winner != null) {
                    scores.put(round.winner, scores.get(round.winner) + 10);
                    correctCounts.put(round.winner, correctCounts.get(round.winner) + 1);

                    for (ClientHandler player : allPlayers) {
                        String answer = round.answers.get(player);
                        if (player == round.winner) {
                            player.sendMessage("Correct. You won this round for your team.");
                        } else if (answer == null) {
                            player.sendMessage("Round closed. " + round.winner.getCurrentUsername() + " answered correctly first.");
                        } else {
                            String normalized = answer.trim().toUpperCase();
                            if (!isValidChoice(normalized)) {
                                wrongCounts.put(player, wrongCounts.get(player) + 1);
                                player.sendMessage("Invalid answer. " + round.winner.getCurrentUsername() + " answered correctly first. Correct answer was " + correctAnswer);
                            } else if (!normalized.equals(correctAnswer)) {
                                wrongCounts.put(player, wrongCounts.get(player) + 1);
                                player.sendMessage("Wrong. " + round.winner.getCurrentUsername() + " answered correctly first. Your answer: " + normalized + ", Correct: " + correctAnswer);
                            }
                        }
                    }
                } else {
                    for (ClientHandler player : allPlayers) {
                        String answer = round.answers.get(player);

                        if (answer == null) {
                            wrongCounts.put(player, wrongCounts.get(player) + 1);
                            player.sendMessage(round.timedOut ? "No answer / timeout" : "No answer");
                        } else {
                            String normalized = answer.trim().toUpperCase();

                            if (!isValidChoice(normalized)) {
                                wrongCounts.put(player, wrongCounts.get(player) + 1);
                                player.sendMessage("Invalid answer. Counted as a wrong attempt. Correct answer was " + correctAnswer);
                            } else if (normalized.equals(correctAnswer)) {
                                correctCounts.put(player, correctCounts.get(player) + 1);
                                scores.put(player, scores.get(player) + 10);
                                player.sendMessage("Correct.");
                            } else {
                                wrongCounts.put(player, wrongCounts.get(player) + 1);
                                player.sendMessage("Wrong. Your answer: " + normalized + ", Correct: " + correctAnswer);
                            }
                        }
                    }
                }

                broadcastToPlayers(allPlayers,
                        "Team Score Update -> " +
                                teamA.getName() + ": " + getTeamScore(teamA, scores) + " | " +
                                teamB.getName() + ": " + getTeamScore(teamB, scores));
                broadcastToPlayers(allPlayers, "");
            }

            sendTeamFinalResults(teamA, teamB, scores, correctCounts, wrongCounts);

        } catch (Exception e) {
            broadcastToPlayers(getAllTeamPlayers(teamA, teamB), "Team game ended due to connection problem.");
        }
    }

    private MultiplayerRoundResult waitForFirstCorrectTeamAnswer(List<ClientHandler> players,
                                                                 String correctAnswer,
                                                                 String answerToken) throws Exception {
        MultiplayerRoundResult result = new MultiplayerRoundResult();

        long endTime = System.currentTimeMillis() + (questionDuration * 1000L);
        int lastSentSecond = -1;

        try {
            for (ClientHandler player : players) {
                player.getSocket().setSoTimeout(100);
            }

            while (System.currentTimeMillis() < endTime) {
                long remainingMillis = endTime - System.currentTimeMillis();
                int remainingSeconds = (int) Math.ceil(remainingMillis / 1000.0);

                if (remainingSeconds <= 10 && remainingSeconds >= 1 && remainingSeconds != lastSentSecond) {
                    for (ClientHandler player : players) {
                        player.sendMessage("__TIMER__|" + remainingSeconds);
                    }
                    lastSentSecond = remainingSeconds;
                }

                for (ClientHandler player : players) {
                    if (result.answers.containsKey(player)) {
                        continue;
                    }

                    try {
                        String input = player.getInputReader().readLine();
                        if (input == null) {
                            throw new Exception("Player disconnected.");
                        }

                        String parsed = parseAnswerInput(input, answerToken);
                        if (parsed != null) {
                            result.answers.put(player, parsed);

                            String normalized = parsed.trim().toUpperCase();
                            if (isValidChoice(normalized) && normalized.equals(correctAnswer)) {
                                result.winner = player;
                                closeAnswerPhase(players);
                                return result;
                            }
                        }
                    } catch (SocketTimeoutException ignored) {
                    }
                }

                if (result.answers.size() == players.size()) {
                    closeAnswerPhase(players);
                    return result;
                }
            }

            result.timedOut = true;
            closeAnswerPhase(players);
            broadcastToPlayers(players, "Time is up. Moving to next question.");
            return result;

        } finally {
            for (ClientHandler player : players) {
                try {
                    player.getSocket().setSoTimeout(0);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void closeAnswerPhase(List<ClientHandler> players) {
        for (ClientHandler player : players) {
            player.sendMessage("__ANSWER_CLOSED__");
        }
    }

    private void broadcastToPlayers(List<ClientHandler> players, String message) {
        for (ClientHandler player : players) {
            player.sendMessage(message);
        }
    }

    private List<ClientHandler> getAllTeamPlayers(Team teamA, Team teamB) {
        List<ClientHandler> allPlayers = new ArrayList<>();
        allPlayers.addAll(teamA.getPlayers());
        allPlayers.addAll(teamB.getPlayers());
        return allPlayers;
    }

    private String getPlayerNames(Team team) {
        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < team.getPlayers().size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(team.getPlayers().get(i).getCurrentUsername());
        }

        return builder.toString();
    }

    private int getTeamScore(Team team, HashMap<ClientHandler, Integer> scores) {
        int total = 0;

        for (ClientHandler player : team.getPlayers()) {
            total += scores.get(player);
        }

        return total;
    }

    private void sendTeamFinalResults(Team teamA,
                                      Team teamB,
                                      HashMap<ClientHandler, Integer> scores,
                                      HashMap<ClientHandler, Integer> correctCounts,
                                      HashMap<ClientHandler, Integer> wrongCounts) throws Exception {
        List<ClientHandler> allPlayers = getAllTeamPlayers(teamA, teamB);

        int teamAScore = getTeamScore(teamA, scores);
        int teamBScore = getTeamScore(teamB, scores);

        broadcastToPlayers(allPlayers, "=== TEAM GAME OVER ===");
        broadcastToPlayers(allPlayers, "Team A: " + teamA.getName() + " -> Total Score: " + teamAScore);
        for (ClientHandler player : teamA.getPlayers()) {
            broadcastToPlayers(allPlayers,
                    "  Player: " + player.getCurrentUsername() +
                            " | Score: " + scores.get(player) +
                            " | Correct: " + correctCounts.get(player) +
                            " | Wrong: " + wrongCounts.get(player));
        }

        broadcastToPlayers(allPlayers, "Team B: " + teamB.getName() + " -> Total Score: " + teamBScore);
        for (ClientHandler player : teamB.getPlayers()) {
            broadcastToPlayers(allPlayers,
                    "  Player: " + player.getCurrentUsername() +
                            " | Score: " + scores.get(player) +
                            " | Correct: " + correctCounts.get(player) +
                            " | Wrong: " + wrongCounts.get(player));
        }

        if (teamAScore > teamBScore) {
            broadcastToPlayers(allPlayers, "Winning Team: " + teamA.getName());
        } else if (teamBScore > teamAScore) {
            broadcastToPlayers(allPlayers, "Winning Team: " + teamB.getName());
        } else {
            broadcastToPlayers(allPlayers, "Result: Draw");
        }

        for (ClientHandler player : teamA.getPlayers()) {
            ScoreRecord record = new ScoreRecord(
                    player.getCurrentUsername(),
                    "team",
                    scores.get(player),
                    correctCounts.get(player),
                    wrongCounts.get(player),
                    LocalDateTime.now().toString()
            );
            scoreManager.saveRecord(record);
        }

        for (ClientHandler player : teamB.getPlayers()) {
            ScoreRecord record = new ScoreRecord(
                    player.getCurrentUsername(),
                    "team",
                    scores.get(player),
                    correctCounts.get(player),
                    wrongCounts.get(player),
                    LocalDateTime.now().toString()
            );
            scoreManager.saveRecord(record);
        }

        broadcastToPlayers(allPlayers, "Team scores saved successfully.");
    }
}