package com.englishapp.server.content;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.englishapp.server.auth.ApiFailure;

@RestController
@RequestMapping("/api/v1/me/lessons/{contentId}/progress")
public class LessonProgressController {
    private static final List<String> STAGES = List.of("stage_1", "stage_2");
    private static final Set<String> ALLOWED_STAGES = Set.copyOf(STAGES);
    private final JdbcTemplate db;

    public LessonProgressController(JdbcTemplate db) {
        this.db = db;
    }

    public record ProgressRequest(Boolean completed) { }

    @GetMapping
    public Map<String, Object> getProgress(Authentication principal, @PathVariable UUID contentId) {
        UUID userId = learnerId(principal);
        ensureLessonExists(contentId);

        Map<String, Boolean> stages = new LinkedHashMap<>();
        STAGES.forEach(stage -> stages.put(stage, false));
        db.query("SELECT stage_id, completed FROM lesson_stage_progress WHERE user_id=? AND content_id=?",
                (rs, rowNum) -> {
                    stages.put(rs.getString("stage_id"), rs.getBoolean("completed"));
                    return null;
                }, userId, contentId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content_id", contentId.toString());
        result.put("stages", stages);
        return result;
    }

    @PutMapping("/{stageId}")
    public Map<String, Object> setProgress(Authentication principal, @PathVariable UUID contentId,
            @PathVariable String stageId, @RequestBody ProgressRequest request) {
        UUID userId = learnerId(principal);
        if (!ALLOWED_STAGES.contains(stageId) || request == null || request.completed() == null)
            throw validationFailure();
        ensureLessonExists(contentId);

        db.update("INSERT INTO lesson_stage_progress(user_id,content_id,stage_id,completed,updated_at) "
                        + "VALUES (?,?,?,?,now()) ON CONFLICT (user_id,content_id,stage_id) "
                        + "DO UPDATE SET completed=EXCLUDED.completed,updated_at=now()",
                userId, contentId, stageId, request.completed());
        return db.query("SELECT content_id,stage_id,completed,updated_at FROM lesson_stage_progress "
                        + "WHERE user_id=? AND content_id=? AND stage_id=?",
                (rs, rowNum) -> Map.<String, Object>of(
                        "content_id", rs.getObject("content_id").toString(),
                        "stage_id", rs.getString("stage_id"),
                        "completed", rs.getBoolean("completed"),
                        "updated_at", rs.getTimestamp("updated_at").toInstant().toString()),
                userId, contentId, stageId).getFirst();
    }

    private void ensureLessonExists(UUID contentId) {
        Boolean exists = db.queryForObject("SELECT EXISTS(SELECT 1 FROM lesson_content WHERE content_id=?)",
                Boolean.class, contentId);
        if (!Boolean.TRUE.equals(exists))
            throw new ApiFailure("LESSON_NOT_FOUND", HttpStatus.NOT_FOUND, "Không tìm thấy bài học.");
    }

    private static UUID learnerId(Authentication principal) {
        boolean learner = principal != null && principal.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_LEARNER".equals(authority.getAuthority()));
        if (!learner) throw new ApiFailure("FORBIDDEN", HttpStatus.FORBIDDEN,
                "Không có quyền thực hiện thao tác này.");
        return UUID.fromString(principal.getName());
    }

    private static ApiFailure validationFailure() {
        return new ApiFailure("VALIDATION_ERROR", HttpStatus.BAD_REQUEST,
                "Vui lòng kiểm tra thông tin đã nhập.");
    }
}
