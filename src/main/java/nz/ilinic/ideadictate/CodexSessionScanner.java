package nz.ilinic.ideadictate;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.application.PathManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/** Finds plausible recent Codex JSONL sessions, without using internal/private chat APIs. */
final class CodexSessionScanner {
    private static final int MAX_CANDIDATES_TO_PARSE = 50;
    private static final int MAX_REPORT_MATCHES = 10;
    private static final int MAX_REPORT_OTHER = 5;
    private static final int MAX_TAIL_BYTES = 256 * 1024;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
            .withZone(ZoneId.systemDefault());

    private CodexSessionScanner() { }

    /** Diagnostic candidates; a matching directory alone never identifies the current chat. */
    static List<Candidate> recentMatching(String projectBasePath) {
        List<Candidate> matches = new ArrayList<>();
        for (Path path : findRecentPaths()) {
            Session session = inspect(path);
            if (matchesProject(projectBasePath, session.cwd)) {
                matches.add(new Candidate(session.path, session.id, session.lastUser, session.modifiedMillis));
            }
        }
        return matches;
    }

    static String excerptFromPinned(Path path) {
        return excerpt(path, 8);
    }

    static String excerpt(Path path, int maxTurns) {
        if (!Files.isRegularFile(path)) return "";
        List<String> turns = new ArrayList<>();
        for (JsonObject event : lastEvents(path)) {
            String user = userMessage(event);
            String assistant = assistantMessage(event);
            String role = user != null ? "User: " : "Assistant: ";
            String content = user != null ? user : assistant;
            if (content == null || content.isBlank()) continue;
            String turn = role + preview(content, 320);
            if (turns.isEmpty() || !turns.get(turns.size() - 1).equals(turn)) turns.add(turn);
        }
        int first = Math.max(0, turns.size() - maxTurns);
        return String.join("\n", turns.subList(first, turns.size()));
    }

    /**
     * Only use history when the Codex session ID is actually available to the IDE process.
     * The ID is normally private to the Codex agent process, so an unknown ID means no history.
     */
    static String currentContext(String projectBasePath, int turns) {
        String id = System.getProperty("CODEX_THREAD_ID");
        if (id == null || id.isBlank()) id = System.getenv("CODEX_THREAD_ID");
        if (id == null || id.isBlank()) id = System.getenv("CODEX_SESSION_ID");
        if (id == null || id.isBlank()) return "";
        try {
            UUID.fromString(id);
        } catch (IllegalArgumentException invalid) {
            return "";
        }
        for (Path path : findRecentPaths()) {
            Session session = inspectHeader(path);
            if (id.equalsIgnoreCase(session.id) && matchesProject(projectBasePath, session.cwd)) {
                return excerpt(path, turns);
            }
        }
        return "";
    }

    static boolean belongsToProject(Path path, String basePath) {
        return Files.isRegularFile(path) && matchesProject(basePath, inspect(path).cwd);
    }

    static String buildReport(String projectBasePath) {
        StringBuilder report = new StringBuilder("LOCAL CODEX SESSIONS (read-only)\n");

        Path systemRoot = Path.of(PathManager.getSystemPath());
        Set<Path> roots = new LinkedHashSet<>();
        roots.add(systemRoot.resolve("aia").resolve("codex"));
        roots.add(systemRoot.resolve("aia").resolve("agents").resolve("codex"));
        roots.add(Path.of(System.getProperty("user.home"), ".codex", "sessions"));

        List<Path> paths = new ArrayList<>();
        for (Path root : roots) {
            report.append("  ").append(root).append(": ");
            if (!Files.isDirectory(root)) {
                report.append("not present\n");
                continue;
            }
            try (Stream<Path> files = Files.walk(root, 10)) {
                List<Path> found = files.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".jsonl"))
                        .toList();
                paths.addAll(found);
                report.append(found.size()).append(" JSONL file(s)\n");
            } catch (IOException | SecurityException ex) {
                report.append("error: ").append(ex.getClass().getSimpleName()).append('\n');
            }
        }

        paths = paths.stream().distinct()
                .sorted(Comparator.comparingLong(CodexSessionScanner::modifiedMillis).reversed())
                .limit(MAX_CANDIDATES_TO_PARSE)
                .toList();

        List<Session> matching = new ArrayList<>();
        List<Session> other = new ArrayList<>();
        for (Path path : paths) {
            Session session = inspect(path);
            if (matchesProject(projectBasePath, session.cwd)) matching.add(session);
            else other.add(session);
        }

        report.append("\n  Parsed most recent: ").append(paths.size()).append(" JSONL files\n");
        report.append("  Project-directory matches: ").append(matching.size()).append('\n');

        report.append("\n  MATCHING CANDIDATES\n");
        appendCandidates(report, matching, MAX_REPORT_MATCHES);
        report.append("\n  OTHER RECENT CANDIDATES\n");
        appendCandidates(report, other, MAX_REPORT_OTHER);

        report.append("\nIMPORTANT\n");
        report.append("  A matching working directory is NOT proof that a session is the active IDE chat.\n");
        report.append("  JetBrains AI Assistant does not expose a supported public API for its active chat.\n");
        report.append("  This diagnostic reads local files only and never transmits their contents.\n");
        report.append("  Message previews may be sensitive; review the report before sharing it.\n");
        return report.toString();
    }

    private static List<Path> findRecentPaths() {
        Path systemRoot = Path.of(PathManager.getSystemPath());
        List<Path> roots = List.of(systemRoot.resolve("aia/codex"),
                systemRoot.resolve("aia/agents/codex"),
                Path.of(System.getProperty("user.home"), ".codex", "sessions"));
        List<Path> paths = new ArrayList<>();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) continue;
            try (Stream<Path> stream = Files.walk(root, 10)) {
                stream.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".jsonl"))
                        .forEach(paths::add);
            } catch (IOException | SecurityException ignored) { }
        }
        return paths.stream().distinct()
                .sorted(Comparator.comparingLong(CodexSessionScanner::modifiedMillis).reversed())
                .limit(120).toList();
    }

    private static void appendCandidates(StringBuilder report, List<Session> sessions, int limit) {
        if (sessions.isEmpty()) {
            report.append("    (none)\n");
            return;
        }
        int shown = 0;
        for (Session s : sessions) {
            if (++shown > limit) break;
            report.append("    [").append(shown).append("] ")
                    .append(TIME_FORMAT.format(Instant.ofEpochMilli(s.modifiedMillis))).append('\n');
            report.append("      id: ").append(s.id == null ? "not found" : s.id).append('\n');
            report.append("      cwd: ").append(s.cwd == null ? "not found" : s.cwd).append('\n');
            report.append("      file: ").append(s.path).append('\n');
            report.append("      last user message preview: ")
                    .append(s.lastUser == null ? "not found" : preview(s.lastUser, 140)).append('\n');
        }
    }

    private static Session inspect(Path path) {
        Session header = inspectHeader(path);
        return new Session(path, header.modifiedMillis, header.id, header.cwd, lastUserMessage(path));
    }

    private static Session inspectHeader(Path path) {
        String id = null;
        String cwd = null;
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            for (int i = 0; i < 200; i++) {
                String line = reader.readLine();
                if (line == null) break;
                JsonObject event = parseObject(line);
                if (event != null && "session_meta".equals(field(event, "type"))) {
                    JsonObject payload = childObject(event, "payload");
                    id = field(payload, "id");
                    cwd = field(payload, "cwd");
                    break;
                }
            }
        } catch (IOException | SecurityException ignored) {
            // A missing metadata record is reflected as 'not found' in the report.
        }
        return new Session(path, modifiedMillis(path), id, cwd, null);
    }

    private static String lastUserMessage(Path path) {
        List<JsonObject> events = lastEvents(path);
        for (int i = events.size() - 1; i >= 0; i--) {
            String message = userMessage(events.get(i));
            if (message != null && !message.isBlank()) return message;
        }
        return null;
    }

    private static List<JsonObject> lastEvents(Path path) {
        List<JsonObject> result = new ArrayList<>();
        try (SeekableByteChannel channel = Files.newByteChannel(path, StandardOpenOption.READ)) {
            long size = channel.size();
            int length = (int) Math.min(size, MAX_TAIL_BYTES);
            if (length == 0) return result;
            channel.position(size - length);
            ByteBuffer bytes = ByteBuffer.allocate(length);
            while (bytes.hasRemaining() && channel.read(bytes) > 0) { /* read tail */ }
            bytes.flip();
            String text = StandardCharsets.UTF_8.decode(bytes).toString();
            String[] lines = text.split("\\R");
            // The first line can be truncated when we read only the tail.
            for (int i = (size > length ? 1 : 0); i < lines.length; i++) {
                JsonObject event = parseObject(lines[i]);
                if (event != null) result.add(event);
            }
        } catch (IOException | SecurityException ignored) {
            // Session file may be locked or inaccessible.
        }
        return result;
    }

    private static String userMessage(JsonObject event) {
        if (event == null) return null;
        JsonObject payload = childObject(event, "payload");
        if (payload == null) return null;
        if ("event_msg".equals(field(event, "type"))
                && "user_message".equals(field(payload, "type"))) {
            return verifiedUserMessage(field(payload, "message"));
        }
        if ("response_item".equals(field(event, "type"))
                && "message".equals(field(payload, "type"))
                && "user".equals(field(payload, "role"))) {
            JsonElement content = payload.get("content");
            if (content instanceof JsonArray parts) {
                for (JsonElement part : parts) {
                    if (part.isJsonObject()) {
                        String input = field(part.getAsJsonObject(), "text");
                        if (input != null) return verifiedUserMessage(input);
                    }
                }
            }
        }
        return null;
    }

    private static String assistantMessage(JsonObject event) {
        if (event == null) return null;
        JsonObject payload = childObject(event, "payload");
        if (payload == null) return null;
        if ("event_msg".equals(field(event, "type"))
                && "agent_message".equals(field(payload, "type"))) {
            return field(payload, "message");
        }
        if ("response_item".equals(field(event, "type"))
                && "message".equals(field(payload, "type"))
                && "assistant".equals(field(payload, "role"))) {
            JsonElement content = payload.get("content");
            if (content instanceof JsonArray parts) {
                for (JsonElement part : parts) {
                    if (part.isJsonObject()) {
                        String text = field(part.getAsJsonObject(), "text");
                        if (text != null) return text;
                    }
                }
            }
        }
        return null;
    }

    private static String verifiedUserMessage(String text) {
        if (text == null || text.isBlank()) return null;
        String trimmed = text.stripLeading();
        if (trimmed.startsWith("The following is the Codex agent history added since")
                || trimmed.startsWith("<send_user_message_")
                || trimmed.startsWith("<request_user_input")
                || trimmed.startsWith("# AGENTS.md instructions")) return null;
        return text;
    }

    private static JsonObject parseObject(String text) {
        try {
            JsonElement json = JsonParser.parseString(text);
            return json.isJsonObject() ? json.getAsJsonObject() : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static JsonObject childObject(JsonObject parent, String key) {
        if (parent == null) return null;
        JsonElement child = parent.get(key);
        return child != null && child.isJsonObject() ? child.getAsJsonObject() : null;
    }

    private static String field(JsonObject parent, String key) {
        if (parent == null) return null;
        JsonElement child = parent.get(key);
        try {
            return child == null || child.isJsonNull() || !child.isJsonPrimitive()
                    ? null : child.getAsString();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static boolean matchesProject(String project, String cwd) {
        if (project == null || cwd == null) return false;
        String normalizedProject = normalize(project);
        String normalizedCwd = normalize(cwd);
        return normalizedCwd.equalsIgnoreCase(normalizedProject)
                || normalizedCwd.toLowerCase(java.util.Locale.ROOT)
                .startsWith(normalizedProject.toLowerCase(java.util.Locale.ROOT) + "/");
    }

    private static String normalize(String path) {
        return path.replace('\\', '/').replaceAll("/+$", "");
    }

    private static String preview(String text, int length) {
        String shortText = text.replaceAll("\\s+", " ").trim();
        return shortText.length() <= length ? shortText : shortText.substring(0, length) + "…";
    }

    private static long modifiedMillis(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException | SecurityException ignored) {
            return 0L;
        }
    }

    private record Session(Path path, long modifiedMillis, String id, String cwd, String lastUser) { }

    record Candidate(Path path, String id, String lastUser, long modifiedMillis) {
        String display() {
            return TIME_FORMAT.format(Instant.ofEpochMilli(modifiedMillis))
                    + " | " + (id == null ? I18n.text("session.unknown") : id)
                    + " | " + (lastUser == null ? I18n.text("session.noText") : preview(lastUser, 85));
        }
    }
}

