package server;

import java.util.ArrayList;
import java.util.List;

public class Team {
    private String name;
    private List<ClientHandler> players;

    public Team(String name) {
        this.name = name;
        this.players = new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    public void addPlayer(ClientHandler player) {
        players.add(player);
    }

    public boolean containsPlayer(ClientHandler player) {
        return players.contains(player);
    }

    public int size() {
        return players.size();
    }

    public List<ClientHandler> getPlayers() {
        return players;
    }
}