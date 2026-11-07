package com.englishapp.server.content;

import com.englishapp.server.auth.TokenCodec;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
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
        String band = datasetBand(root, file, payload);
        normalizeBandFields(payload, band);
        int version = payload.path("version").asInt(1);
        if (version < 1) throw new IOException("Invalid version in " + file);
        String status = text(payload, "status");
        if (status == null) status = "draft";
        String topic = text(payload, "topic");
        String topicFolder = datasetTopic(root, file);
        String topicId = topicFolder == null ? null : topicId(topicFolder);
        String topicLabel = topicFolder == null ? null : topicLabel(topicFolder);
        String bandId = text(payload, "band_id");
        if (bandId == null || bandId.isBlank()) bandId = toBandId(band);
        String sourceKey = root.relativize(file).toString().replace('\\', '/');
        String audioPath = findAudio(root, file.getParent());
        UUID id = UUID.nameUUIDFromBytes(sourceKey.getBytes(StandardCharsets.UTF_8));
        db.update("INSERT INTO lesson_content(content_id,source_key,lesson_id,version,title,band,band_id,topic,topic_id,status,"
                        + "payload,audio_relative_path,payload_checksum_sha256) VALUES (?,?,?,?,?,?,?,?,?,?,?::jsonb,?,?) "
                        + "ON CONFLICT (source_key) DO UPDATE SET lesson_id=EXCLUDED.lesson_id,version=EXCLUDED.version,"
                        + "title=EXCLUDED.title,band=EXCLUDED.band,band_id=EXCLUDED.band_id,"
                        + "topic=EXCLUDED.topic,topic_id=EXCLUDED.topic_id,status=EXCLUDED.status,"
                        + "payload=EXCLUDED.payload,audio_relative_path=EXCLUDED.audio_relative_path,"
                        + "payload_checksum_sha256=EXCLUDED.payload_checksum_sha256,updated_at=now()",
                id, sourceKey, lessonId, version, title, band, bandId, topic, topicId, status, payload.toString(), audioPath,
                TokenCodec.sha256(new String(raw, StandardCharsets.UTF_8)));
        syncTopic(topicId, topicLabel);
        syncBand(bandId, band);
    }

    private static String datasetTopic(Path root, Path file) {
        Path relative = root.relativize(file);
        if (relative.getNameCount() >= 3) return relative.getName(1).toString();
        if (relative.getNameCount() == 2) return relative.getName(0).toString();
        return null;
    }

    private static String topicLabel(String folder) {
        String label = folder.trim().replace('_', ' ').replace('-', ' ').replaceAll("\\s+", " ");
        if (label.isEmpty()) return null;
        String[] words = label.toLowerCase(java.util.Locale.ROOT).split(" ");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    private void syncTopic(String topicId, String topicLabel) {
        if (topicId == null || topicId.isBlank() || topicLabel == null || topicLabel.isBlank()) return;
        String label = topicLabel.trim().replaceAll("\\s+", " ");
        if (label.length() > 80) {
            log.warn("Skipping onboarding topic longer than 80 characters: {}", label);
            return;
        }
        db.update("INSERT INTO onboarding_topic(topic_id,label,sort_order,active) "
                        + "SELECT ?, ?, COALESCE(MAX(sort_order),0)+1, TRUE FROM onboarding_topic "
                        + "ON CONFLICT (topic_id) DO UPDATE SET label=EXCLUDED.label, active=TRUE",
                topicId, label);
    }

    private static String topicId(String label) {
        String normalized = label.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (normalized.isEmpty()) throw new IllegalArgumentException("Lesson topic has no usable characters");
        if (normalized.length() > 80) throw new IllegalArgumentException("Lesson topic ID exceeds 80 characters");
        return normalized;
    }

    private void syncBand(String bandId, String label) {
        if (bandId == null || bandId.isBlank() || label == null || label.isBlank()) return;
        String normalizedLabel = label.trim().replaceAll("\\s+", " ");
        if (bandId.length() > 80 || normalizedLabel.length() > 80) {
            log.warn("Skipping invalid lesson band: {} ({})", bandId, normalizedLabel);
            return;
        }
        db.update("INSERT INTO onboarding_band(band_id,label,sort_order,active) "
                        + "SELECT ?, ?, COALESCE(MAX(sort_order),0)+1, TRUE FROM onboarding_band "
                        + "ON CONFLICT (band_id) DO UPDATE SET label=EXCLUDED.label, active=TRUE",
                bandId, normalizedLabel);
    }

    private static String findAudio(Path root, Path directory) throws IOException {
        try (var files = Files.list(directory)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".wav"))
                    .sorted().map(root::relativize).map(Path::toString).map(path -> path.replace('\\', '/'))
                    .findFirst().orElse(null);
        }
    }

    private static String datasetBand(Path root, Path file, JsonNode payload) throws IOException {
        Path relative = root.relativize(file);
        if (relative.getNameCount() > 1) return relative.getName(0).toString();
        String band = firstText(payload, "band", "band_id", "level");
        if (band == null) throw new IOException("Missing dataset band in " + file);
        return band;
    }

    private static void normalizeBandFields(JsonNode payload, String band) {
        if (!(payload instanceof ObjectNode object)) return;
        String cefr = text(object, "cefr_reference");
        if (cefr == null) {
            String level = text(object, "level");
            if (level != null && !level.matches("\\d+(?:[._-].*)?")) object.put("cefr_reference", level);
        }
        object.put("band", band);
        object.put("band_id", toBandId(band));
        object.remove("level");
    }

    private static String toBandId(String band) {
        if (band.endsWith("+")) {
            String score = band.substring(0, band.length() - 1);
            if (!score.contains(".")) score += ".0";
            return score.replace('.', '_') + "_PLUS";
        }
        return band.replace('.', '_').replace('-', '_');
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
