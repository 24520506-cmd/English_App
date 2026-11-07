package com.englishapp.server.content;

import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/topics")
public class TopicController {
    private final JdbcTemplate db;

    public TopicController(JdbcTemplate db) {
        this.db = db;
    }

    @Operation(security = {})
    @GetMapping
    public Map<String, Object> topics() {
        List<Map<String, Object>> topics = db.query(
                "SELECT topic_id, label, sort_order FROM onboarding_topic WHERE active = TRUE ORDER BY sort_order",
                (rs, rowNum) -> Map.<String, Object>of(
                        "id", rs.getString("topic_id"),
                        "label", rs.getString("label"),
                        "sort_order", rs.getInt("sort_order")));
        return Map.of("topics", topics);
    }
}
