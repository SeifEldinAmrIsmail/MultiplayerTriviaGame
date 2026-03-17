package manager;

import model.User;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class UserManager {
    private static final String USERS_FILE = "src/main/resources/data/users.txt";

    public static final int LOGIN_SUCCESS = 200;
    public static final int WRONG_PASSWORD = 401;
    public static final int USER_NOT_FOUND = 404;

    public static final int REGISTER_SUCCESS = 201;
    public static final int USERNAME_TAKEN = 409;

    private Map<String, User> users = new LinkedHashMap<>();

    public UserManager() throws IOException {
        loadUsers();
    }

    private void loadUsers() throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(USERS_FILE));
        String line;

        while ((line = reader.readLine()) != null) {
            line = line.trim();

            if (line.isEmpty()) {
                continue;
            }

            String[] parts = line.split(",", 3);
            if (parts.length != 3) {
                continue;
            }

            String name = parts[0].trim();
            String username = parts[1].trim();
            String password = parts[2].trim();

            User user = new User(name, username, password);
            users.put(normalizeUsername(username), user);
        }

        reader.close();
    }

    public int login(String username, String password) {
        String key = normalizeUsername(username);

        if (!users.containsKey(key)) {
            return USER_NOT_FOUND;
        }

        User user = users.get(key);

        if (!user.getPassword().equals(password)) {
            return WRONG_PASSWORD;
        }

        return LOGIN_SUCCESS;
    }

    public int register(String name, String username, String password) throws IOException {
        String key = normalizeUsername(username);

        if (users.containsKey(key)) {
            return USERNAME_TAKEN;
        }

        User user = new User(name, username, password);
        users.put(key, user);
        saveAllUsers();

        return REGISTER_SUCCESS;
    }

    private void saveAllUsers() throws IOException {
        BufferedWriter writer = new BufferedWriter(new FileWriter(USERS_FILE));

        for (User user : users.values()) {
            writer.write(user.getName() + "," + user.getUsername() + "," + user.getPassword());
            writer.newLine();
        }

        writer.close();
    }

    public User getUser(String username) {
        return users.get(normalizeUsername(username));
    }

    public boolean usernameExists(String username) {
        return users.containsKey(normalizeUsername(username));
    }

    public Map<String, User> getAllUsers() {
        return users;
    }

    private String normalizeUsername(String username) {
        return username.trim().toLowerCase();
    }
}