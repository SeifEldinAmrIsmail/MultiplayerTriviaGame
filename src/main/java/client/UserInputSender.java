package client;

import java.io.PrintWriter;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class UserInputSender extends Thread {
    private PrintWriter out;
    private Scanner scanner;
    private AtomicBoolean running;
    private AtomicBoolean canSend;
    private AtomicBoolean answerPrompt;
    private AtomicReference<String> activeAnswerToken;

    public UserInputSender(PrintWriter out,
                           Scanner scanner,
                           AtomicBoolean running,
                           AtomicBoolean canSend,
                           AtomicBoolean answerPrompt,
                           AtomicReference<String> activeAnswerToken) {
        this.out = out;
        this.scanner = scanner;
        this.running = running;
        this.canSend = canSend;
        this.answerPrompt = answerPrompt;
        this.activeAnswerToken = activeAnswerToken;
    }

    @Override
    public void run() {
        try {
            while (running.get()) {
                String userInput = scanner.nextLine();

                if (!running.get()) {
                    break;
                }

                String trimmed = userInput.trim();

                if (!canSend.get()) {
                    if (!trimmed.isEmpty()) {
                        if (answerPrompt.get()) {
                            System.out.println("(Answer was not submitted in time. Press Enter before the round closes.)");
                        } else {
                            System.out.println("(No prompt is waiting for input right now.)");
                        }
                    }
                    continue;
                }

                if (answerPrompt.get()) {
                    String token = activeAnswerToken.get();

                    if (token == null || token.isEmpty()) {
                        System.out.println("(Answer was not submitted in time. Press Enter before the round closes.)");
                        continue;
                    }

                    out.println("ANSWER|" + token + "|" + trimmed);
                    canSend.set(false);
                } else {
                    if (trimmed.isEmpty()) {
                        continue;
                    }

                    out.println(trimmed);
                    canSend.set(false);
                }
            }
        } catch (Exception e) {
            // stop quietly
        }
    }
}