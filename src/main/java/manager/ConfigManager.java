package manager;

import model.GameConfig;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class ConfigManager {
    private static final String CONFIG_FILE = "src/main/resources/data/config.txt";

    private GameConfig config;

    public ConfigManager() throws IOException {
        loadConfig();
    }

    private void loadConfig() throws IOException {
        int minPlayers = 1;
        int maxPlayers = 4;
        int minTeamSize = 1;
        int maxTeamSize = 2;
        int questionDuration = 15;
        int historyLimit = 5;

        BufferedReader reader = new BufferedReader(new FileReader(CONFIG_FILE));
        String line;

        while ((line = reader.readLine()) != null) {
            line = line.trim();

            if (line.isEmpty()) {
                continue;
            }

            String[] parts = line.split("=", 2);
            if (parts.length != 2) {
                continue;
            }

            String key = parts[0].trim();
            String value = parts[1].trim();

            switch (key) {
                case "minPlayers":
                    minPlayers = Integer.parseInt(value);
                    break;
                case "maxPlayers":
                    maxPlayers = Integer.parseInt(value);
                    break;
                case "minTeamSize":
                    minTeamSize = Integer.parseInt(value);
                    break;
                case "maxTeamSize":
                    maxTeamSize = Integer.parseInt(value);
                    break;
                case "questionDuration":
                    questionDuration = Integer.parseInt(value);
                    break;
                case "historyLimit":
                    historyLimit = Integer.parseInt(value);
                    break;
            }
        }

        reader.close();

        config = new GameConfig(
                minPlayers,
                maxPlayers,
                minTeamSize,
                maxTeamSize,
                questionDuration,
                historyLimit
        );
    }

    public GameConfig getConfig() {
        return config;
    }
}