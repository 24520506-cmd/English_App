package com.englishapp.server.content;

import com.englishapp.server.auth.ApiFailure;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/lessons")
public class LessonContentController {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final Path dataRoot;

    public LessonContentController(JdbcTemplate db, ObjectMapper json,
            @Value("${app.content.data-dir:}") String dataDir) {
        this.db = db;
        this.json = json;
        this.dataRoot = dataDir == null || dataDir.isBlank() ? null : Path.of(dataDir).toAbsolutePath().normalize();
    }

    @GetMapping
    Map<String, Object> list(@RequestParam(required = false) String band,
            @RequestParam(required = false) String topic) {
        List<Map<String, Object>> lessons = db.query("SELECT content_id,lesson_id,version,title,band,band_id,topic,topic_id,status,"
                        + "audio_relative_path IS NOT NULL AS has_audio FROM lesson_content "
                        + "WHERE (?::varchar IS NULL OR band=?::varchar OR band_id=?::varchar) "
                        + "AND (?::varchar IS NULL OR topic_id=?::varchar OR lower(topic)=lower(?::varchar)) "
                        + "ORDER BY band,title",
                (rs, ignored) -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("content_id", rs.getObject("content_id").toString());
                    item.put("lesson_id", rs.getString("lesson_id"));
                    item.put("version", rs.getInt("version"));
                    item.put("title", rs.getString("title"));
                    item.put("band", rs.getString("band"));
                    item.put("band_id", rs.getString("band_id"));
                    item.put("topic", rs.getString("topic"));
                    item.put("topic_id", rs.getString("topic_id"));
                    item.put("status", rs.getString("status"));
                    item.put("has_audio", rs.getBoolean("has_audio"));
                    return item;
                }, band, band, band, topic, topic, topic);
        return Map.of("lessons", lessons);
    }

    @GetMapping("/{contentId}")
    JsonNode detail(@PathVariable UUID contentId) {
        return db.query("SELECT payload::text FROM lesson_content WHERE content_id=?",
                (rs, ignored) -> parse(rs.getString(1)), contentId).stream().findFirst().orElseThrow(this::notFound);
    }

    @GetMapping("/{contentId}/audio")
    ResponseEntity<Resource> audio(@PathVariable UUID contentId) {
        String relative = db.query("SELECT audio_relative_path FROM lesson_content WHERE content_id=?",
                (rs, ignored) -> rs.getString(1), contentId).stream().findFirst().orElseThrow(this::notFound);
        if (relative == null || dataRoot == null) throw notFound();
        Path file = dataRoot.resolve(relative).normalize();
        if (!file.startsWith(dataRoot) || !Files.isRegularFile(file)) throw notFound();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("audio/wav"))
                .header("Accept-Ranges", "bytes").body(new FileSystemResource(file));
    }

    private JsonNode parse(String value) {
        try { return json.readTree(value); }
        catch (Exception error) { throw new IllegalStateException("Invalid lesson JSON in database", error); }
    }

    private ApiFailure notFound() {
        return new ApiFailure("LESSON_NOT_FOUND", HttpStatus.NOT_FOUND, "Không tìm thấy bài học.");
    }
}
