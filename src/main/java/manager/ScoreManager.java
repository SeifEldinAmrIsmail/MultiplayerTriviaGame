package manager;

import model.ScoreRecord;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ScoreManager {
    private static final String SCORES_FILE = "storage/scores.txt";

    private List<ScoreRecord> records = new ArrayList<>();

    public ScoreManager() throws IOException {
        ensureFileExists();
        loadScores();
    }

    private void ensureFileExists() throws IOException {
        File file = new File(SCORES_FILE);
        File parent = file.getParentFile();

        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        if (!file.exists()) {
            file.createNewFile();
        }
    }

    private void loadScores() throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(SCORES_FILE));
        String line;

        while ((line = reader.readLine()) != null) {
            line = line.trim();

            if (line.isEmpty()) {
                continue;
            }

            String[] parts = line.split(",", 6);
            if (parts.length != 6) {
                continue;
            }

            String username = parts[0].trim();
            String mode = parts[1].trim();
            int score = Integer.parseInt(parts[2].trim());
            int correctCount = Integer.parseInt(parts[3].trim());
            int wrongCount = Integer.parseInt(parts[4].trim());
            String date = parts[5].trim();

            ScoreRecord record = new ScoreRecord(
                    username,
                    mode,
                    score,
                    correctCount,
                    wrongCount,
                    date
            );

            records.add(record);
        }

        reader.close();
    }

    public void saveRecord(ScoreRecord record) throws IOException {
        records.add(record);

        BufferedWriter writer = new BufferedWriter(new FileWriter(SCORES_FILE, true));
        writer.write(
                record.getUsername() + "," +
                        record.getMode() + "," +
                        record.getScore() + "," +
                        record.getCorrectCount() + "," +
                        record.getWrongCount() + "," +
                        record.getDate()
        );
        writer.newLine();
        writer.close();
    }

    public List<ScoreRecord> getUserHistory(String username) {
        List<ScoreRecord> userRecords = new ArrayList<>();

        for (ScoreRecord record : records) {
            if (record.getUsername().equalsIgnoreCase(username)) {
                userRecords.add(record);
            }
        }

        return userRecords;
    }

    public List<ScoreRecord> getAllRecords() {
        return records;
    }
}