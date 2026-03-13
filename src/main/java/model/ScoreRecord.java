package model;

public class ScoreRecord {
    private String username;
    private String mode;
    private int score;
    private int correctCount;
    private int wrongCount;
    private String date;

    public ScoreRecord(String username, String mode, int score, int correctCount, int wrongCount, String date) {
        this.username = username;
        this.mode = mode;
        this.score = score;
        this.correctCount = correctCount;
        this.wrongCount = wrongCount;
        this.date = date;
    }

    public String getUsername() {
        return username;
    }

    public String getMode() {
        return mode;
    }

    public int getScore() {
        return score;
    }

    public int getCorrectCount() {
        return correctCount;
    }

    public int getWrongCount() {
        return wrongCount;
    }

    public String getDate() {
        return date;
    }
}