package client;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class ClientMain {
    public static void main(String[] args) {
        try {
            Socket socket = new Socket("localhost", 5000);

            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            Scanner scanner = new Scanner(System.in);

            AtomicBoolean running = new AtomicBoolean(true);
            AtomicBoolean canSend = new AtomicBoolean(false);
            AtomicBoolean answerPrompt = new AtomicBoolean(false);
            AtomicReference<String> activeAnswerToken = new AtomicReference<>("");

            ServerListener listener = new ServerListener(
                    in,
                    running,
                    canSend,
                    answerPrompt,
                    activeAnswerToken
            );

            UserInputSender sender = new UserInputSender(
                    out,
                    scanner,
                    running,
                    canSend,
                    answerPrompt,
                    activeAnswerToken
            );

            listener.start();
            sender.start();

            listener.join();
            sender.join();

            socket.close();
            scanner.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}