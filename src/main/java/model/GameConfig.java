package model;

public class GameConfig {
    private int minPlayers;
    private int maxPlayers;
    private int minTeamSize;
    private int maxTeamSize;
    private int questionDuration;
    private int historyLimit;

    public GameConfig(int minPlayers, int maxPlayers, int minTeamSize, int maxTeamSize,
                      int questionDuration, int historyLimit) {
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
        this.minTeamSize = minTeamSize;
        this.maxTeamSize = maxTeamSize;
        this.questionDuration = questionDuration;
        this.historyLimit = historyLimit;
    }

    public int getMinPlayers() {
        return minPlayers;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public int getMinTeamSize() {
        return minTeamSize;
    }

    public int getMaxTeamSize() {
        return maxTeamSize;
    }

    public int getQuestionDuration() {
        return questionDuration;
    }

    public int getHistoryLimit() {
        return historyLimit;
    }
}