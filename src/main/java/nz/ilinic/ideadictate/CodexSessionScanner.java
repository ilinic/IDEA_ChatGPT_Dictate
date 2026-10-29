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
        return new Session(path, modifiedMillis(path), id, cwd, lastUserMessage(path));
    }

    private static String lastUserMessage(Path path) {
        try (SeekableByteChannel channel = Files.newByteChannel(path, StandardOpenOption.READ)) {
            long size = channel.size();
            int length = (int) Math.min(size, MAX_TAIL_BYTES);
            if (length == 0) return null;
            channel.position(size - length);
            ByteBuffer bytes = ByteBuffer.allocate(length);
            while (bytes.hasRemaining() && channel.read(bytes) > 0) { /* read tail */ }
            bytes.flip();
            String text = StandardCharsets.UTF_8.decode(bytes).toString();
            String[] lines = text.split("\\R");
            // The first line can be truncated when we read only the tail.
            for (int i = lines.length - 1; i >= (size > length ? 1 : 0); i--) {
                JsonObject event = parseObject(lines[i]);
                String message = userMessage(event);
                if (message != null && !message.isBlank()) return message;
            }
        } catch (IOException | SecurityException ignored) {
            // Session file may be locked or inaccessible.
        }
        return null;
    }

    private static String userMessage(JsonObject event) {
        if (event == null) return null;
        JsonObject payload = childObject(event, "payload");
        if (payload == null) return null;
        if ("event_msg".equals(field(event, "type"))
                && "user_message".equals(field(payload, "type"))) {
            return field(payload, "message");
        }
        if ("response_item".equals(field(event, "type"))
                && "message".equals(field(payload, "type"))
                && "user".equals(field(payload, "role"))) {
            JsonElement content = payload.get("content");
            if (content instanceof JsonArray parts) {
                for (JsonElement part : parts) {
                    if (part.isJsonObject()) {
                        String input = field(part.getAsJsonObject(), "text");
                        if (input != null) return input;
                    }
                }
            }
        }
        return null;
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
}

