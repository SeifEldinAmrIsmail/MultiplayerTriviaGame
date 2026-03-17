package manager;

import model.Question;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class QuestionBank {
    private static final String QUESTIONS_FILE = "src/main/resources/data/questions.txt";

    private List<Question> questions = new ArrayList<>();

    public QuestionBank() throws IOException {
        loadQuestions();
    }

    private void loadQuestions() throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(QUESTIONS_FILE));
        String line;

        while ((line = reader.readLine()) != null) {
            line = line.trim();

            if (line.isEmpty()) {
                continue;
            }

            String[] parts = line.split("\\|");
            if (parts.length != 9) {
                continue;
            }

            int id = Integer.parseInt(parts[0].trim());
            String category = parts[1].trim();
            String difficulty = parts[2].trim();
            String text = parts[3].trim();
            String choiceA = parts[4].trim();
            String choiceB = parts[5].trim();
            String choiceC = parts[6].trim();
            String choiceD = parts[7].trim();
            String correctAnswer = parts[8].trim();

            Question question = new Question(
                    id,
                    category,
                    difficulty,
                    text,
                    choiceA,
                    choiceB,
                    choiceC,
                    choiceD,
                    correctAnswer
            );

            questions.add(question);
        }

        reader.close();
    }

    public List<Question> getAllQuestions() {
        return questions;
    }

    public List<Question> getQuestions(String category, String difficulty, int count) {
        List<Question> filtered = new ArrayList<>();

        for (Question question : questions) {
            if (question.getCategory().equalsIgnoreCase(category)
                    && question.getDifficulty().equalsIgnoreCase(difficulty)) {
                filtered.add(question);
            }
        }

        Collections.shuffle(filtered);

        if (count < filtered.size()) {
            return new ArrayList<>(filtered.subList(0, count));
        }

        return filtered;
    }

    public boolean hasEnoughQuestions(String category, String difficulty, int count) {
        int total = 0;

        for (Question question : questions) {
            if (question.getCategory().equalsIgnoreCase(category)
                    && question.getDifficulty().equalsIgnoreCase(difficulty)) {
                total++;
            }
        }

        return total >= count;
    }
}