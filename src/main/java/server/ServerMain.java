package server;

import manager.ConfigManager;
import manager.QuestionBank;
import manager.ScoreManager;
import manager.UserManager;
import model.GameConfig;

import java.net.ServerSocket;
import java.net.Socket;

public class ServerMain {
    public static void main(String[] args) {
        try {
            UserManager userManager = new UserManager();
            ScoreManager scoreManager = new ScoreManager();
            QuestionBank questionBank = new QuestionBank();
            ConfigManager configManager = new ConfigManager();
            GameConfig config = configManager.getConfig();
            GameManager gameManager = new GameManager();

            ServerSocket serverSocket = new ServerSocket(5000);
            System.out.println("Server is running on port 5000...");

            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("New client connected.");

                ClientHandler clientHandler = new ClientHandler(
                        socket,
                        userManager,
                        scoreManager,
                        questionBank,
                        gameManager,
                        config.getQuestionDuration()
                );
                clientHandler.start();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}