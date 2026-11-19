import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public class Sac_Dos {
    private static final int PORT = 8080;
    private static final int MAX_ITEMS = 100;
    private static final int MAX_FRONTIER_STATES = 250000;
    private static final int MAX_REQUEST_BYTES = 1024 * 1024;
    private static final String DATABASE_URL = "jdbc:sqlite:sac_dos.db";

    private static final class Item {
        final String name;
        final double value;
        final double weight;
        final int position;
        boolean selected;

        Item(String name, double value, double weight, int position) {
            this.name = name;
            this.value = value;
            this.weight = weight;
            this.position = position;
        }
    }

    private static final class State {
        final double weight;
        final double value;
        final List<Integer> selectedItems;

        State(double weight, double value, List<Integer> selectedItems) {
            this.weight = weight;
            this.value = value;
            this.selectedItems = selectedItems;
        }
    }

    private static final class Result {
        final double capacity;
        final double totalValue;
        final double totalWeight;
        final List<Item> items;

        Result(double capacity, double totalValue, double totalWeight, List<Item> items) {
            this.capacity = capacity;
            this.totalValue = totalValue;
            this.totalWeight = totalWeight;
            this.items = items;
        }
    }

    private static final class HistoryEntry {
        final long id;
        final String createdAt;
        final Result result;

        HistoryEntry(long id, String createdAt, Result result) {
            this.id = id;
            this.createdAt = createdAt;
            this.result = result;
        }
    }

    public static void main(String[] args) throws Exception {
        int port = args.length == 0 ? PORT : Integer.parseInt(args[0]);
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535.");
        }
        Class.forName("org.sqlite.JDBC");
        initializeDatabase();

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/api/state", Sac_Dos::handleState);
        server.createContext("/api/history", Sac_Dos::handleHistory);
        server.createContext("/api/solve", Sac_Dos::handleSolve);
        server.createContext("/", Sac_Dos::handleStaticFile);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("Packwise is running at http://localhost:" + port);
        System.out.println("SQLite database: " + Paths.get("sac_dos.db").toAbsolutePath());
        System.out.println("Press Ctrl+C to stop the server.");
    }

    private static Connection openDatabase() throws SQLException {
        return DriverManager.getConnection(DATABASE_URL);
    }

    private static void initializeDatabase() throws SQLException {
        try (Connection connection = openDatabase();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS settings ("
                    + "id INTEGER PRIMARY KEY CHECK (id = 1), "
                    + "capacity REAL NOT NULL, "
                    + "total_value REAL NOT NULL DEFAULT 0, "
                    + "total_weight REAL NOT NULL DEFAULT 0)");
            statement.execute("CREATE TABLE IF NOT EXISTS items ("
                    + "position INTEGER PRIMARY KEY, "
                    + "name TEXT NOT NULL, "
                    + "value REAL NOT NULL, "
                    + "weight REAL NOT NULL, "
                    + "selected INTEGER NOT NULL DEFAULT 0)");
            statement.execute("CREATE TABLE IF NOT EXISTS solution_history ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%S', 'now')), "
                    + "capacity REAL NOT NULL, "
                    + "total_value REAL NOT NULL, "
                    + "total_weight REAL NOT NULL)");
            statement.execute("CREATE TABLE IF NOT EXISTS solution_history_items ("
                    + "history_id INTEGER NOT NULL REFERENCES solution_history(id) ON DELETE CASCADE, "
                    + "position INTEGER NOT NULL, "
                    + "name TEXT NOT NULL, "
                    + "value REAL NOT NULL, "
                    + "weight REAL NOT NULL, "
                    + "selected INTEGER NOT NULL, "
                    + "PRIMARY KEY (history_id, position))");
            statement.execute("INSERT OR IGNORE INTO settings "
                    + "(id, capacity, total_value, total_weight) VALUES (1, 15, 0, 0)");

            try (ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM items")) {
                if (rows.next() && rows.getInt(1) == 0) {
                    seedExample(connection);
                    List<Item> items = new ArrayList<>();
                    String[] names = {"x1", "x2", "x3", "x4", "x5", "x6"};
                    double[] values = {4, 2, 1, 10, 2, 2};
                    double[] weights = {12, 2, 1, 4, 1, 3};
                    for (int i = 0; i < names.length; i++) {
                        items.add(new Item(names[i], values[i], weights[i], i));
                    }
                    saveResult(connection, solve(items, 15));
                }
            }
        }
    }

    private static void seedExample(Connection connection) throws SQLException {
        String[] names = {"x1", "x2", "x3", "x4", "x5", "x6"};
        double[] values = {4, 2, 1, 10, 2, 2};
        double[] weights = {12, 2, 1, 4, 1, 3};
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO items (position, name, value, weight, selected) VALUES (?, ?, ?, ?, 0)")) {
            for (int i = 0; i < names.length; i++) {
                insert.setInt(1, i);
                insert.setString(2, names[i]);
                insert.setDouble(3, values[i]);
                insert.setDouble(4, weights[i]);
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private static void handleState(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Use GET for this endpoint.");
            return;
        }

        try (Connection connection = openDatabase()) {
            sendJson(exchange, 200, readState(connection));
        } catch (SQLException exception) {
            reportError("Could not read the saved knapsack data", exception);
            sendError(exchange, 500, "Could not read saved data from SQLite.");
        }
    }

    private static void handleHistory(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Use GET for this endpoint.");
            return;
        }

        try (Connection connection = openDatabase()) {
            sendJson(exchange, 200, readHistory(connection));
        } catch (SQLException exception) {
            reportError("Could not read the knapsack history", exception);
            sendError(exchange, 500, "Could not read the history from SQLite.");
        }
    }

    private static void handleSolve(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Use POST for this endpoint.");
            return;
        }

        try {
            Map<String, String> form = readForm(exchange);
            double capacity = parsePositiveNumber(form.get("capacity"), "Bag capacity");
            int count = parseItemCount(form.get("count"));
            List<Item> items = parseItems(form, count);
            Result result = solve(items, capacity);

            try (Connection connection = openDatabase()) {
                connection.setAutoCommit(false);
                try {
                    saveResult(connection, result);
                    saveHistory(connection, result);
                    connection.commit();
                } catch (SQLException | RuntimeException exception) {
                    connection.rollback();
                    throw exception;
                } finally {
                    connection.setAutoCommit(true);
                }
            }

            sendJson(exchange, 200, toJson(result));
        } catch (IllegalArgumentException exception) {
            sendError(exchange, 400, exception.getMessage());
        } catch (SQLException exception) {
            reportError("Could not save the knapsack result", exception);
            sendError(exchange, 500, "Could not save the result to SQLite.");
        }
    }

    private static int parseItemCount(String value) {
        try {
            int count = Integer.parseInt(value);
            if (count < 1 || count > MAX_ITEMS) {
                throw new IllegalArgumentException("Enter between 1 and " + MAX_ITEMS + " items.");
            }
            return count;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("The item list is invalid.");
        }
    }

    private static List<Item> parseItems(Map<String, String> form, int count) {
        List<Item> items = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String prefix = "item" + i + ".";
            String name = form.get(prefix + "name");
            if (name == null || name.trim().isEmpty() || name.trim().length() > 40) {
                throw new IllegalArgumentException("Give item " + (i + 1)
                        + " a name of 1 to 40 characters.");
            }

            double value = parseNonNegativeNumber(form.get(prefix + "value"),
                    "Value for item " + (i + 1));
            double weight = parsePositiveNumber(form.get(prefix + "weight"),
                    "Weight for item " + (i + 1));
            items.add(new Item(name.trim(), value, weight, i));
        }
        return items;
    }

    private static double parsePositiveNumber(String value, String label) {
        double number = parseNumber(value, label);
        if (number <= 0) {
            throw new IllegalArgumentException(label + " must be greater than zero.");
        }
        return number;
    }

    private static double parseNonNegativeNumber(String value, String label) {
        double number = parseNumber(value, label);
        if (number < 0) {
            throw new IllegalArgumentException(label + " cannot be negative.");
        }
        return number;
    }

    private static double parseNumber(String value, String label) {
        try {
            double number = Double.parseDouble(value);
            if (!Double.isFinite(number)) {
                throw new NumberFormatException();
            }
            return number;
        } catch (NumberFormatException | NullPointerException exception) {
            throw new IllegalArgumentException(label + " must be a valid number.");
        }
    }

    private static Result solve(List<Item> items, double capacity) {
        List<State> frontier = new ArrayList<>();
        frontier.add(new State(0, 0, Collections.emptyList()));

        for (Item item : items) {
            List<State> candidates = new ArrayList<>(frontier);
            for (State state : frontier) {
                double weight = state.weight + item.weight;
                double value = state.value + item.value;
                if (Double.isFinite(weight) && Double.isFinite(value) && weight <= capacity) {
                    if (candidates.size() >= MAX_FRONTIER_STATES) {
                        throw new IllegalArgumentException(
                                "This set is too complex to solve exactly. Try fewer items or simpler weights.");
                    }
                    List<Integer> selected = new ArrayList<>(state.selectedItems);
                    selected.add(item.position);
                    candidates.add(new State(weight, value, selected));
                }
            }

            candidates.sort(Comparator.comparingDouble((State state) -> state.weight)
                    .thenComparing((State left, State right) ->
                            Double.compare(right.value, left.value)));
            List<State> nextFrontier = new ArrayList<>();
            double bestValue = Double.NEGATIVE_INFINITY;
            for (State state : candidates) {
                if (state.value > bestValue) {
                    nextFrontier.add(state);
                    bestValue = state.value;
                }
            }
            frontier = nextFrontier;
        }

        State best = frontier.get(0);
        for (State state : frontier) {
            if (state.value > best.value) {
                best = state;
            }
        }
        for (int position : best.selectedItems) {
            items.get(position).selected = true;
        }
        return new Result(capacity, best.value, best.weight, items);
    }

    private static void saveResult(Connection connection, Result result) throws SQLException {
        try (PreparedStatement updateSettings = connection.prepareStatement(
                "UPDATE settings SET capacity = ?, total_value = ?, total_weight = ? WHERE id = 1");
             Statement deleteItems = connection.createStatement();
             PreparedStatement insertItem = connection.prepareStatement(
                     "INSERT INTO items (position, name, value, weight, selected) VALUES (?, ?, ?, ?, ?)")) {
            updateSettings.setDouble(1, result.capacity);
            updateSettings.setDouble(2, result.totalValue);
            updateSettings.setDouble(3, result.totalWeight);
            updateSettings.executeUpdate();

            deleteItems.executeUpdate("DELETE FROM items");
            for (Item item : result.items) {
                insertItem.setInt(1, item.position);
                insertItem.setString(2, item.name);
                insertItem.setDouble(3, item.value);
                insertItem.setDouble(4, item.weight);
                insertItem.setInt(5, item.selected ? 1 : 0);
                insertItem.addBatch();
            }
            insertItem.executeBatch();
        }
    }

    private static void saveHistory(Connection connection, Result result) throws SQLException {
        long historyId;
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO solution_history (capacity, total_value, total_weight) VALUES (?, ?, ?)")) {
            insert.setDouble(1, result.capacity);
            insert.setDouble(2, result.totalValue);
            insert.setDouble(3, result.totalWeight);
            insert.executeUpdate();
        }
        try (Statement statement = connection.createStatement();
             ResultSet row = statement.executeQuery("SELECT last_insert_rowid()")) {
            if (!row.next()) {
                throw new SQLException("Could not determine the new history record.");
            }
            historyId = row.getLong(1);
        }

        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO solution_history_items "
                        + "(history_id, position, name, value, weight, selected) "
                        + "VALUES (?, ?, ?, ?, ?, ?)")) {
            for (Item item : result.items) {
                insert.setLong(1, historyId);
                insert.setInt(2, item.position);
                insert.setString(3, item.name);
                insert.setDouble(4, item.value);
                insert.setDouble(5, item.weight);
                insert.setInt(6, item.selected ? 1 : 0);
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private static String readHistory(Connection connection) throws SQLException {
        List<HistoryEntry> history = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT id, created_at, capacity, total_value, total_weight "
                             + "FROM solution_history ORDER BY id DESC")) {
            while (rows.next()) {
                long id = rows.getLong("id");
                List<Item> items = new ArrayList<>();
                try (PreparedStatement itemQuery = connection.prepareStatement(
                        "SELECT position, name, value, weight, selected "
                                + "FROM solution_history_items WHERE history_id = ? ORDER BY position")) {
                    itemQuery.setLong(1, id);
                    try (ResultSet itemRows = itemQuery.executeQuery()) {
                        while (itemRows.next()) {
                            Item item = new Item(itemRows.getString("name"),
                                    itemRows.getDouble("value"), itemRows.getDouble("weight"),
                                    itemRows.getInt("position"));
                            item.selected = itemRows.getInt("selected") == 1;
                            items.add(item);
                        }
                    }
                }
                Result result = new Result(rows.getDouble("capacity"),
                        rows.getDouble("total_value"), rows.getDouble("total_weight"), items);
                history.add(new HistoryEntry(id, rows.getString("created_at"), result));
            }
        }

        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < history.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            HistoryEntry entry = history.get(i);
            json.append("{\"id\":").append(entry.id)
                    .append(",\"createdAt\":\"").append(escapeJson(entry.createdAt))
                    .append("\",\"capacity\":").append(entry.result.capacity)
                    .append(",\"totalValue\":").append(entry.result.totalValue)
                    .append(",\"totalWeight\":").append(entry.result.totalWeight)
                    .append(",\"items\":").append(itemsToJson(entry.result.items)).append('}');
        }
        return json.append(']').toString();
    }

    private static String readState(Connection connection) throws SQLException {
        double capacity;
        double totalValue;
        double totalWeight;
        try (Statement statement = connection.createStatement();
             ResultSet row = statement.executeQuery(
                     "SELECT capacity, total_value, total_weight FROM settings WHERE id = 1")) {
            if (!row.next()) {
                throw new SQLException("The saved capacity record is missing.");
            }
            capacity = row.getDouble("capacity");
            totalValue = row.getDouble("total_value");
            totalWeight = row.getDouble("total_weight");
        }

        List<Item> items = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT position, name, value, weight, selected FROM items ORDER BY position")) {
            while (rows.next()) {
                Item item = new Item(rows.getString("name"), rows.getDouble("value"),
                        rows.getDouble("weight"), rows.getInt("position"));
                item.selected = rows.getInt("selected") == 1;
                items.add(item);
            }
        }
        return toJson(new Result(capacity, totalValue, totalWeight, items));
    }

    private static String toJson(Result result) {
        StringBuilder json = new StringBuilder();
        json.append("{\"capacity\":").append(result.capacity)
                .append(",\"totalValue\":").append(result.totalValue)
                .append(",\"totalWeight\":").append(result.totalWeight)
                .append(",\"items\":").append(itemsToJson(result.items)).append('}');
        return json.toString();
    }

    private static String itemsToJson(List<Item> items) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            if (i > 0) {
                json.append(',');
            }
            json.append("{\"name\":\"").append(escapeJson(item.name))
                    .append("\",\"value\":").append(item.value)
                    .append(",\"weight\":").append(item.weight)
                    .append(",\"selected\":").append(item.selected).append('}');
        }
        return json.append(']').toString();
    }

    private static String escapeJson(String value) {
        StringBuilder escaped = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '"': escaped.append("\\\""); break;
                case '\\': escaped.append("\\\\"); break;
                case '\b': escaped.append("\\b"); break;
                case '\f': escaped.append("\\f"); break;
                case '\n': escaped.append("\\n"); break;
                case '\r': escaped.append("\\r"); break;
                case '\t': escaped.append("\\t"); break;
                default:
                    if (character < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
            }
        }
        return escaped.toString();
    }

    private static Map<String, String> readForm(HttpExchange exchange) throws IOException {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.toLowerCase().startsWith(
                "application/x-www-form-urlencoded")) {
            throw new IllegalArgumentException("Submit the form using URL-encoded data.");
        }

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int read;
        while ((read = exchange.getRequestBody().read(chunk)) != -1) {
            if (buffer.size() + read > MAX_REQUEST_BYTES) {
                throw new IllegalArgumentException("The submitted data is too large.");
            }
            buffer.write(chunk, 0, read);
        }

        Map<String, String> form = new HashMap<>();
        String body = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        for (String pair : body.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int separator = pair.indexOf('=');
            String key = decodeFormPart(separator < 0 ? pair : pair.substring(0, separator));
            String value = decodeFormPart(separator < 0 ? "" : pair.substring(separator + 1));
            if (form.put(key, value) != null) {
                throw new IllegalArgumentException("The submitted form contains duplicate fields.");
            }
        }
        return form;
    }

    private static String decodeFormPart(String value) throws IOException {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (IllegalArgumentException exception) {
            throw new IOException("Malformed URL-encoded form data.", exception);
        }
    }

    private static void handleStaticFile(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Use GET for this endpoint.");
            return;
        }
        String path = exchange.getRequestURI().getPath();
        String fileName;
        String contentType;
        if ("/".equals(path) || "/index.html".equals(path)) {
            fileName = "index.html";
            contentType = "text/html; charset=utf-8";
        } else if ("/app.js".equals(path)) {
            fileName = "app.js";
            contentType = "text/javascript; charset=utf-8";
        } else if ("/styles.css".equals(path)) {
            fileName = "styles.css";
            contentType = "text/css; charset=utf-8";
        } else {
            sendError(exchange, 404, "File not found.");
            return;
        }

        Path file = Paths.get(fileName);
        if (!Files.isRegularFile(file)) {
            sendError(exchange, 404, "Frontend file not found: " + fileName);
            return;
        }
        byte[] content = Files.readAllBytes(file);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(200, content.length);
        exchange.getResponseBody().write(content);
        exchange.close();
    }

    private static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        sendResponse(exchange, status, json);
    }

    private static void sendError(HttpExchange exchange, int status, String message)
            throws IOException {
        sendJson(exchange, status, "{\"error\":\"" + escapeJson(message) + "\"}");
    }

    private static void sendResponse(HttpExchange exchange, int status, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static void reportError(String message, Exception exception) {
        System.err.println(message + ": " + exception.getMessage());
        exception.printStackTrace(System.err);
    }
}
