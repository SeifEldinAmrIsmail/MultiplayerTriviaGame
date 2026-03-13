package model;

public class Question {
    private int id;
    private String category;
    private String difficulty;
    private String text;
    private String choiceA;
    private String choiceB;
    private String choiceC;
    private String choiceD;
    private String correctAnswer;

    public Question(int id, String category, String difficulty, String text,
                    String choiceA, String choiceB, String choiceC, String choiceD,
                    String correctAnswer) {
        this.id = id;
        this.category = category;
        this.difficulty = difficulty;
        this.text = text;
        this.choiceA = choiceA;
        this.choiceB = choiceB;
        this.choiceC = choiceC;
        this.choiceD = choiceD;
        this.correctAnswer = correctAnswer;
    }

    public int getId() {
        return id;
    }

    public String getCategory() {
        return category;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public String getText() {
        return text;
    }

    public String getChoiceA() {
        return choiceA;
    }

    public String getChoiceB() {
        return choiceB;
    }

    public String getChoiceC() {
        return choiceC;
    }

    public String getChoiceD() {
        return choiceD;
    }

    public String getCorrectAnswer() {
        return correctAnswer;
    }
}