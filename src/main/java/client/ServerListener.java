package client;

import java.io.BufferedReader;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class ServerListener extends Thread {
    private BufferedReader in;
    private AtomicBoolean running;
    private AtomicBoolean canSend;
    private AtomicBoolean answerPrompt;
    private AtomicReference<String> activeAnswerToken;

    public ServerListener(BufferedReader in,
                          AtomicBoolean running,
                          AtomicBoolean canSend,
                          AtomicBoolean answerPrompt,
                          AtomicReference<String> activeAnswerToken) {
        this.in = in;
        this.running = running;
        this.canSend = canSend;
        this.answerPrompt = answerPrompt;
        this.activeAnswerToken = activeAnswerToken;
    }

    @Override
    public void run() {
        try {
            String serverMessage;

            while (running.get() && (serverMessage = in.readLine()) != null) {
                if (serverMessage.startsWith("__TIMER__|")) {
                    String value = serverMessage.substring("__TIMER__|".length());
                    System.out.println("Time left: " + value);
                    continue;
                }

                if (serverMessage.startsWith("__ANSWER_OPEN__|")) {
                    String token = serverMessage.substring("__ANSWER_OPEN__|".length());
                    canSend.set(true);
                    answerPrompt.set(true);
                    activeAnswerToken.set(token);
                    continue;
                }

                if (serverMessage.equals("__ANSWER_CLOSED__") || serverMessage.equals("__TIME_UP__")) {
                    canSend.set(false);
                    answerPrompt.set(false);
                    activeAnswerToken.set("");
                    continue;
                }

                System.out.println(serverMessage);

                if (isGeneralPrompt(serverMessage)) {
                    canSend.set(true);
                    answerPrompt.set(false);
                    activeAnswerToken.set("");
                }

                if (serverMessage.equals("Goodbye.")) {
                    running.set(false);
                    canSend.set(false);
                    answerPrompt.set(false);
                    activeAnswerToken.set("");
                    break;
                }
            }

        } catch (Exception e) {
            System.out.println("Disconnected from server.");
        } finally {
            running.set(false);
            canSend.set(false);
            answerPrompt.set(false);
            activeAnswerToken.set("");
        }
    }

    private boolean isGeneralPrompt(String message) {
        return message.equals("Enter your choice:")
                || message.equals("Enter username:")
                || message.equals("Enter password:")
                || message.equals("Enter name:")
                || message.equals("Enter category:")
                || message.equals("Enter difficulty:")
                || message.equals("Enter number of questions:")
                || message.equals("Enter room name:")
                || message.equals("Enter Team A name:")
                || message.equals("Enter Team B name:")
                || message.equals("Enter team name to join:");
    }
}