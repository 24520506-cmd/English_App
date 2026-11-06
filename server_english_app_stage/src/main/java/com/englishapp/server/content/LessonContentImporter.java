package com.englishapp.server.content;

import com.englishapp.server.auth.TokenCodec;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class LessonContentImporter implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(LessonContentImporter.class);
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final String configuredDataDir;

    public LessonContentImporter(JdbcTemplate db, ObjectMapper json,
            @Value("${app.content.data-dir:}") String configuredDataDir) {
        this.db = db;
        this.json = json;
        this.configuredDataDir = configuredDataDir;
    }

    @Override public void run(ApplicationArguments args) throws Exception {
        if (configuredDataDir == null || configuredDataDir.isBlank()) {
            log.info("Lesson import disabled because CONTENT_DATA_DIR is empty");
            return;
        }
        Path root = Path.of(configuredDataDir).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            log.warn("Lesson data directory does not exist: {}", root);
            return;
        }
        List<Path> files;
        try (var paths = Files.walk(root)) {
            files = paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".json"))
                    .sorted(Comparator.naturalOrder()).toList();
        }
        int imported = 0;
        for (Path file : files) {
            importFile(root, file);
            imported++;
        }
        log.info("Imported or refreshed {} lesson files from {}", imported, root);
    }

    void importFile(Path root, Path file) throws IOException {
        byte[] raw = Files.readAllBytes(file);
        JsonNode payload = json.readTree(raw);
        String lessonId = requiredText(payload, "lesson_id", file);
        String title = requiredText(payload, "title", file);
        int version = payload.path("version").asInt(1);
        if (version < 1) throw new IOException("Invalid version in " + file);
        String status = text(payload, "status");
        if (status == null) status = "draft";
        String band = firstText(payload, "band_id", "band", "level");
        String topic = text(payload, "topic");
        String sourceKey = root.relativize(file).toString().replace('\\', '/');
        String audioPath = findAudio(root, file.getParent());
        UUID id = UUID.nameUUIDFromBytes(sourceKey.getBytes(StandardCharsets.UTF_8));
        db.update("INSERT INTO lesson_content(content_id,source_key,lesson_id,version,title,band,topic,status,"
                        + "payload,audio_relative_path,payload_checksum_sha256) VALUES (?,?,?,?,?,?,?,?,?::jsonb,?,?) "
                        + "ON CONFLICT (source_key) DO UPDATE SET lesson_id=EXCLUDED.lesson_id,version=EXCLUDED.version,"
                        + "title=EXCLUDED.title,band=EXCLUDED.band,topic=EXCLUDED.topic,status=EXCLUDED.status,"
                        + "payload=EXCLUDED.payload,audio_relative_path=EXCLUDED.audio_relative_path,"
                        + "payload_checksum_sha256=EXCLUDED.payload_checksum_sha256,updated_at=now()",
                id, sourceKey, lessonId, version, title, band, topic, status, payload.toString(), audioPath,
                TokenCodec.sha256(new String(raw, StandardCharsets.UTF_8)));
    }

    private static String findAudio(Path root, Path directory) throws IOException {
        try (var files = Files.list(directory)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".wav"))
                    .sorted().map(root::relativize).map(Path::toString).map(path -> path.replace('\\', '/'))
                    .findFirst().orElse(null);
        }
    }

    private static String requiredText(JsonNode node, String field, Path file) throws IOException {
        String value = text(node, field);
        if (value == null) throw new IOException("Missing " + field + " in " + file);
        return value;
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (value != null) return value;
        }
        return null;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || !value.isValueNode() ? null : value.asText();
    }
}
