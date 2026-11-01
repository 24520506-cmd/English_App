# Figma audit baseline — Star Journey

Captured: 2026-10-23 · File: https://www.figma.com/design/grgnOFjr54GkSROrTL0yCs/UI_app?node-id=5-8245
Source: live Figma page 5:8245. Detailed node, control, and edge inventory: [figma-baseline.json](figma-baseline.json).

## Scope and findings

- 113 top-level screens, 8119 nodes, 136 named ACTION/BUTTON controls, 310 prototype reactions.
- 37 state screens. No local component/instance, Figma variable collection, paint style, or text style was found on this page.
- All 310 recorded navigation destinations resolve to existing top-level screens.
- A prototype link demonstrates navigation only. Authentication, validation, audio playback, scoring, loading, role enforcement, and API-driven state transitions cannot be verified from Figma alone.
- The requested `product-spec(1).md` is absent. This audit uses [product-spec.md](product-spec.md), the 485-line local specification.
- The earlier request for a separate working copy was superseded by the user's later instruction to edit the original. No exact duplicate has been created.

## Issues to resolve without deleting screens

- Ranking's `Tuần • Tháng • Tất cả` filter is one unlinked frame; the month and all-time screens have no incoming prototype link.
- `report_name` is only reached from the success state's Back control; the base ranking screen offers no route to report a name.
- `admin_revoke` has no incoming prototype link, even though an admin revoke screen exists.
- 53 screens are unreachable from the learner welcome entry in the static prototype. Most are backend-selected response states or role-separated admin screens and should not receive artificial links. The remaining cases require targeted review.
- The home learning action sits below a large journey illustration in the first viewport. Listening gives its audio player the same card treatment as questions. Writing uses repeated equal-weight situation/input cards. Admin editor content is mostly same-weight cards; labels and validation need clearer hierarchy.
- The current dark palette is consistently applied but has no semantic tokens or shared components. Prominent yellow is repeated across unrelated headings and actions.

## Screen inventory

| Figma ID | Screen | Nodes | Named buttons | Links | Group |
|---|---|---:|---:|---:|---|
| 5:8246 | welcome · CHÀO MỪNG + ĐĂNG NHẬP (UNIFIED) | 89 | 4 | 3 | USER |
| 5:8332 | register · Tạo tài khoản | 71 | 1 | 2 | USER |
| 5:8396 | forgot_password · Khôi phục mật khẩu | 67 | 1 | 2 | USER |
| 5:8460 | onboarding_band · Chọn mức nội dung | 74 | 1 | 2 | USER |
| 5:8534 | onboarding_topics · Chọn chủ đề ưu tiên | 71 | 1 | 2 | USER |
| 5:8605 | onboarding_name · Tên hiển thị | 67 | 2 | 3 | USER |
| 5:8672 | home · Học | 97 | 1 | 8 | USER |
| 5:8772 | catalog · Khám phá chủ đề | 92 | 0 | 9 | USER |
| 5:8864 | topic_detail · Chủ đề Finance | 83 | 0 | 7 | USER |
| 5:8947 | lesson_detail · Chi tiết bài học | 82 | 1 | 6 | USER |
| 5:9030 | vocab_1 · Học từ vựng | 89 | 2 | 3 | USER |
| 5:9115 | vocab_2 · Học từ vựng | 85 | 1 | 2 | USER |
| 5:9195 | vocab_3 · Học từ vựng | 85 | 1 | 2 | USER |
| 5:9275 | vocab_4 · Học từ vựng | 85 | 1 | 2 | USER |
| 5:9355 | vocab_5 · Học từ vựng | 85 | 1 | 2 | USER |
| 5:9435 | vocab_6 · Học từ vựng | 85 | 1 | 2 | USER |
| 5:9515 | vocab_7 · Học từ vựng | 85 | 1 | 2 | USER |
| 5:9595 | listening · Luyện nghe | 82 | 1 | 2 | USER |
| 5:9678 | listening_result · Kết quả nghe | 75 | 3 | 4 | USER |
| 5:9753 | writing · Luyện viết | 87 | 1 | 2 | USER |
| 5:9841 | lesson_complete · Hoàn thành bài học | 84 | 1 | 2 | USER |
| 5:9926 | writing_feedback · Nhận xét bài viết | 78 | 3 | 4 | USER |
| 5:10004 | writing_revision · Sửa bài viết | 68 | 1 | 2 | USER |
| 5:10072 | review · Ôn tập | 87 | 1 | 7 | USER |
| 5:10159 | review_detail · Chi tiết ôn tập | 69 | 1 | 2 | USER |
| 5:10228 | review_vocab · Ôn từ vựng | 71 | 1 | 2 | USER |
| 5:10299 | review_listening · Nghe ôn tập | 68 | 1 | 2 | USER |
| 5:10367 | review_writing · Viết khi ôn | 68 | 1 | 2 | USER |
| 5:10435 | review_result · Kết quả ôn | 70 | 2 | 3 | USER |
| 5:10505 | progress · Tiến độ | 128 | 3 | 10 | USER |
| 5:10637 | activity · Hoạt động học tập | 81 | 1 | 6 | USER |
| 5:10718 | lesson_history · Lịch sử bài học | 80 | 0 | 7 | USER |
| 5:10798 | lesson_history_detail · Chi tiết lịch sử | 74 | 1 | 2 | USER |
| 5:10872 | ranking · Xếp hạng | 86 | 0 | 4 | USER |
| 5:10960 | report_name · Báo cáo tên | 65 | 1 | 2 | USER |
| 5:11025 | profile · Hồ sơ | 92 | 2 | 10 | USER |
| 5:11118 | profile_edit · Sửa tên hiển thị | 65 | 1 | 2 | USER |
| 5:11183 | preferences · Tùy chọn học tập | 72 | 1 | 2 | USER |
| 5:11255 | notification_settings · Nhắc ôn tập | 71 | 1 | 2 | USER |
| 5:11326 | account_settings · Tài khoản & bảo mật | 69 | 0 | 3 | USER |
| 5:11395 | admin_topics · Admin · Chủ đề | 72 | 3 | 4 | ADMIN |
| 5:11467 | admin_editor · Admin · Soạn lesson | 94 | 7 | 8 | ADMIN |
| 5:11561 | STATE · auth_invalid · Không thể đăng nhập | 73 | 2 | 3 | STATE |
| 5:11634 | STATE · register_validation · Kiểm tra đăng ký | 68 | 1 | 2 | STATE |
| 5:11702 | STATE · reset_sent · Đã gửi yêu cầu | 68 | 1 | 2 | STATE |
| 5:11767 | STATE · session_expired · Phiên đăng nhập hết hạn | 62 | 1 | 2 | STATE |
| 5:11829 | STATE · onboarding_topics_error · Chưa chọn chủ đề | 65 | 1 | 2 | STATE |
| 5:11894 | STATE · onboarding_name_error · Tên chưa phù hợp | 67 | 2 | 3 | STATE |
| 5:11961 | STATE · catalog_empty · Chưa có bài phù hợp | 62 | 1 | 2 | STATE |
| 5:12023 | catalog_search · Tìm bài học | 68 | 1 | 2 | USER |
| 5:12091 | STATE · lesson_locked · Chưa thể sang bước sau | 65 | 1 | 2 | STATE |
| 5:12156 | STATE · lesson_continue · Tiếp tục bài dang dở | 67 | 2 | 3 | STATE |
| 5:12223 | STATE · vocab_audio_error · Không phát được phát âm | 65 | 1 | 2 | STATE |
| 5:12288 | vocab_situation · Tình huống sử dụng từ | 74 | 1 | 2 | USER |
| 5:12362 | STATE · listening_loading · Đang tải audio | 65 | 1 | 2 | STATE |
| 5:12427 | STATE · listening_audio_error · Lỗi tải bài nghe | 65 | 1 | 2 | STATE |
| 5:12492 | STATE · listening_not_finished · Chưa nghe hết audio | 65 | 1 | 2 | STATE |
| 5:12557 | STATE · listening_missing · Còn câu chưa trả lời | 65 | 1 | 2 | STATE |
| 5:12622 | STATE · listening_submitting · Đang nộp bài nghe | 65 | 1 | 2 | STATE |
| 5:12687 | listening_transcript · Transcript và giải thích | 68 | 1 | 2 | USER |
| 5:12755 | listening_practice_again · Nghe luyện thêm | 68 | 1 | 2 | USER |
| 5:12823 | STATE · writing_missing_target · Thiếu từ mục tiêu | 70 | 2 | 3 | STATE |
| 5:12893 | STATE · writing_validation · Câu viết cần sửa | 68 | 1 | 2 | STATE |
| 5:12961 | STATE · writing_dictionary_warning · Cảnh báo ngôn ngữ | 67 | 2 | 3 | STATE |
| 5:13028 | STATE · writing_save_failed · Chưa lưu được bài | 65 | 1 | 2 | STATE |
| 5:13093 | STATE · writing_feedback_pending · AI đang nhận xét | 65 | 1 | 2 | STATE |
| 5:13158 | STATE · writing_feedback_deferred · AI tạm hoãn | 68 | 1 | 2 | STATE |
| 5:13226 | STATE · writing_feedback_error · Không tạo được nhận xét | 65 | 1 | 2 | STATE |
| 5:13291 | writing_feedback_detail · Phân tích câu viết | 71 | 1 | 2 | USER |
| 5:13362 | STATE · writing_revision_success · Đã gửi bản sửa | 65 | 1 | 2 | STATE |
| 5:13427 | STATE · writing_revision_blocked · Đã hết lượt sửa | 62 | 1 | 2 | STATE |
| 5:13489 | STATE · lesson_points_capped · Điểm trong ngày | 68 | 1 | 2 | STATE |
| 5:13557 | STATE · review_today · Ôn tập đến hạn hôm nay | 65 | 1 | 2 | STATE |
| 5:13622 | STATE · review_upcoming · Ôn sắp tới | 65 | 1 | 2 | STATE |
| 5:13687 | STATE · review_expired · Mốc ôn đã hết hạn | 65 | 1 | 2 | STATE |
| 5:13752 | review_outside · Ôn ngoài mốc | 65 | 1 | 2 | USER |
| 5:13817 | review_answer_reveal · Đáp án ôn tập | 65 | 1 | 2 | USER |
| 5:13882 | STATE · review_revoked · Nội dung tạm dừng | 62 | 1 | 2 | STATE |
| 5:13944 | review_notify_permission · Cho phép nhắc ôn | 65 | 1 | 2 | USER |
| 5:14009 | progress_listening · Kết quả nghe lần đầu | 65 | 1 | 2 | USER |
| 5:14074 | progress_streak · Chuỗi ngày học | 66 | 1 | 2 | USER |
| 5:14140 | progress_heatmap · Biểu đồ hoạt động | 105 | 1 | 2 | USER |
| 5:14245 | STATE · progress_empty · Chưa có hoạt động | 62 | 1 | 2 | STATE |
| 5:14307 | progress_points · Sổ điểm học tập | 68 | 1 | 2 | USER |
| 5:14375 | ranking_month · Bảng xếp hạng tháng | 67 | 1 | 2 | USER |
| 5:14442 | ranking_alltime · Bảng xếp hạng toàn thời gian | 67 | 1 | 2 | USER |
| 5:14509 | STATE · ranking_no_listening · Chưa có tỷ lệ nghe | 62 | 1 | 2 | STATE |
| 5:14571 | STATE · ranking_report_success · Đã nhận báo cáo | 62 | 1 | 2 | STATE |
| 5:14633 | STATE · profile_name_cooldown · Chưa đến hạn đổi tên | 65 | 1 | 2 | STATE |
| 5:14698 | STATE · preferences_saved · Đã cập nhật lựa chọn | 62 | 1 | 2 | STATE |
| 5:14760 | STATE · notification_denied · Quyền thông báo đã tắt | 62 | 1 | 2 | STATE |
| 5:14822 | profile_logout_confirm · Đăng xuất? | 64 | 2 | 3 | USER |
| 5:14886 | admin_dashboard · Quản trị nội dung | 72 | 0 | 4 | ADMIN |
| 5:14958 | admin_lesson_list · Danh sách lesson | 70 | 1 | 4 | ADMIN |
| 5:15028 | admin_topic_edit · Tạo / sửa chủ đề | 68 | 1 | 2 | ADMIN |
| 5:15096 | admin_vocab_edit · Soạn từ vựng | 80 | 1 | 2 | ADMIN |
| 5:15176 | admin_audio_tts · Tạo và duyệt audio | 71 | 1 | 2 | ADMIN |
| 5:15247 | admin_listening_edit · Soạn bài nghe | 77 | 1 | 2 | ADMIN |
| 5:15324 | admin_writing_edit · Soạn đề viết | 74 | 1 | 2 | ADMIN |
| 5:15398 | admin_preview · Xem trước lesson | 65 | 1 | 2 | ADMIN |
| 5:15463 | admin_publish_check · Kiểm tra xuất bản | 71 | 1 | 2 | ADMIN |
| 5:15534 | STATE · admin_publish_error · Lesson chưa hợp lệ | 65 | 1 | 2 | ADMIN |
| 5:15599 | STATE · admin_published · Phiên bản đã xuất bản | 67 | 2 | 3 | ADMIN |
| 5:15666 | admin_hide_confirm · Ẩn lesson? | 64 | 2 | 3 | ADMIN |
| 5:15730 | admin_revoke · Thu hồi phiên bản | 68 | 1 | 2 | ADMIN |
| 5:15798 | admin_audit · Rà soát hoạt động | 71 | 1 | 2 | ADMIN |
| 5:15869 | admin_name_reports · Báo cáo tên hiển thị | 65 | 1 | 2 | ADMIN |
| 17:3 | reset_password · Đặt mật khẩu mới | 67 | 1 | 2 | USER |
| 17:70 | reset_complete · Đặt lại thành công | 62 | 1 | 2 | USER |
| 19:2 | change_password · Đổi mật khẩu | 71 | 1 | 2 | USER |
| 19:73 | password_changed · Đổi mật khẩu thành công | 62 | 1 | 2 | USER |
| 19:137 | active_sessions · Quản lý phiên | 69 | 0 | 2 | USER |
| 20:2 | sessions_revoke_confirm · Đăng xuất tất cả? | 64 | 2 | 3 | USER |

## Traceability matrix

Status means screen presence and static prototype evidence only; no backend behavior has been marked implemented.

| Requirement | Existing screen(s) | Existing prototype flow | Improvement needed | Preservation status |
|---|---|---|---|---|
| USR-01 | `register` (5:8332), `STATE` (5:11634) | 4 outgoing reaction(s) across mapped screens; API/state behavior unverified | Visual hierarchy, clear controls, accessible feedback | Screens preserved in baseline; behavior unverified |
| USR-02 | `welcome` (5:8246), `STATE` (5:11561) | 6 outgoing reaction(s) across mapped screens; API/state behavior unverified | Visual hierarchy, clear controls, accessible feedback | Screens preserved in baseline; behavior unverified |
| USR-03 | `profile_logout_confirm` (5:14822) | 3 outgoing reaction(s) across mapped screens; API/state behavior unverified | Visual hierarchy, clear controls, accessible feedback | Screens preserved in baseline; behavior unverified |
| USR-04 | `forgot_password` (5:8396), `reset_password` (17:3), `reset_complete` (17:70) | 6 outgoing reaction(s) across mapped screens; API/state behavior unverified | Visual hierarchy, clear controls, accessible feedback | Screens preserved in baseline; behavior unverified |
| USR-05 | `change_password` (19:2), `password_changed` (19:73) | 4 outgoing reaction(s) across mapped screens; API/state behavior unverified | Visual hierarchy, clear controls, accessible feedback | Screens preserved in baseline; behavior unverified |
| USR-06 | `profile` (5:11025) | 10 outgoing reaction(s) across mapped screens; API/state behavior unverified | Visual hierarchy, clear controls, accessible feedback | Screens preserved in baseline; behavior unverified |
| USR-07 | `profile_edit` (5:11118), `account_settings` (5:11326) | 5 outgoing reaction(s) across mapped screens; API/state behavior unverified | Visual hierarchy, clear controls, accessible feedback | Screens preserved in baseline; behavior unverified |
| USR-08 | `admin_dashboard` (5:14886) | 4 outgoing reaction(s) across mapped screens; API/state behavior unverified | Visual hierarchy, clear controls, accessible feedback | Screens preserved in baseline; behavior unverified |
| USR-09 | `active_sessions` (19:137) | 2 outgoing reaction(s) across mapped screens; API/state behavior unverified | Visual hierarchy, clear controls, accessible feedback | Screens preserved in baseline; behavior unverified |
| USR-10 | `active_sessions` (19:137) | 2 outgoing reaction(s) across mapped screens; API/state behavior unverified | Visual hierarchy, clear controls, accessible feedback | Screens preserved in baseline; behavior unverified |
| USR-11 | `sessions_revoke_confirm` (20:2) | 3 outgoing reaction(s) across mapped screens; API/state behavior unverified | Visual hierarchy, clear controls, accessible feedback | Screens preserved in baseline; behavior unverified |
| USR-12 | `STATE` (5:11767) | 2 outgoing reaction(s) across mapped screens; API/state behavior unverified | Visual hierarchy, clear controls, accessible feedback | Screens preserved in baseline; behavior unverified |
| USR-13 | No dedicated screen verified | No static flow verified | Dedicated contact verification flow missing or unverified | Gap: no dedicated screen verified |
| USR-14 | No dedicated screen verified | No static flow verified | Dedicated contact verification flow missing or unverified | Gap: no dedicated screen verified |
| USR-15 | No dedicated screen verified | No static flow verified | Dedicated contact verification flow missing or unverified | Gap: no dedicated screen verified |
| USR-16 | `account_settings` (5:11326) | 3 outgoing reaction(s) across mapped screens; API/state behavior unverified | Dedicated contact verification flow missing or unverified | Screens preserved in baseline; behavior unverified |
| CNT-01 | `admin_topics` (5:11395), `admin_topic_edit` (5:15028) | 6 outgoing reaction(s) across mapped screens; API/state behavior unverified | Visual hierarchy, clear controls, accessible feedback | Screens preserved in baseline; behavior unverified |
| CNT-02 | `admin_editor` (5:11467), `admin_lesson_list` (5:14958) | 12 outgoing reaction(s) across mapped screens; API/state behavior unverified | Visual hierarchy, clear controls, accessible feedback | Screens preserved in baseline; behavior unverified |
| CNT-03 | `admin_vocab_edit` (5:15096) | 2 outgoing reaction(s) across mapped screens; API/state behavior unverified | Visual hierarchy, clear controls, accessible feedback | Screens preserved in baseline; behavior unverified |
| CNT-04 | `admin_listening_edit` (5:15247) | 2 outgoing reaction(s) across mapped screens; API/state behavior unverified | Visual hierarchy, clear controls, accessible feedback | Screens preserved in baseline; behavior unverified |
| CNT-05 | `admin_writing_edit` (5:15324) | 2 outgoing reaction(s) across mapped screens; API/state behavior unverified | Visual hierarchy, clear controls, accessible feedback | Screens preserved in baseline; behavior unverified |
| CNT-06 | `admin_publish_check` (5:15463), `STATE` (5:15599), `admin_hide_confirm` (5:15666) | 8 outgoing reaction(s) across mapped screens; API/state behavior unverified | Visual hierarchy, clear controls, accessible feedback | Screens preserved in baseline; behavior unverified |
| CNT-07 | `catalog` (5:8772), `admin_preview` (5:15398) | 11 outgoing reaction(s) across mapped screens; API/state behavior unverified | Visual hierarchy, clear controls, accessible feedback | Screens preserved in baseline; behavior unverified |
| CNT-08 | `admin_audio_tts` (5:15176) | 2 outgoing reaction(s) across mapped screens; API/state behavior unverified | Visual hierarchy, clear controls, accessible feedback | Screens preserved in baseline; behavior unverified |
| CNT-09 | `admin_revoke` (5:15730) | 2 outgoing reaction(s) across mapped screens; API/state behavior unverified | Expose revoke flow from admin context | Screens preserved in baseline; behavior unverified |
| ONB-01 | `onboarding_band` (5:8460), `onboarding_topics` (5:8534) | 4 outgoing reaction(s) across mapped screens; API/state behavior unverified | Visual hierarchy, clear controls, accessible feedback | Screens preserved in baseline; behavior unverified |
| ONB-02 | `preferences` (5:11183) | 2 outgoing reaction(s) across mapped screens; API/state behavior unverified | Visual hierarchy, clear controls, accessible feedback | Screens preserved in baseline; behavior unverified |
| ONB-03 | `onboarding_name` (5:8605), `profile_edit` (5:11118) | 5 outgoing reaction(s) across mapped screens; API/state behavior unverified | Visual hierarchy, clear controls, accessible feedback | Screens preserved in baseline; behavior unverified |
| CAT-01 | `home` (5:8672), `catalog` (5:8772), `catalog_search` (5:12023) | 19 outgoing reaction(s) across mapped screens; API/state behavior unverified | Visual hierarchy, clear controls, accessible feedback | Screens preserved in baseline; behavior unverified |
| CAT-02 | `catalog` (5:8772), `preferences` (5:11183) | 11 outgoing reaction(s) across mapped screens; API/state behavior unverified | Visual hierarchy, clear controls, accessible feedback | Screens preserved in baseline; behavior unverified |
| LES-01 | `lesson_detail` (5:8947), `STATE` (5:12156) | 9 outgoing reaction(s) across mapped screens; API/state behavior unverified | Visual hierarchy, clear controls, accessible feedback | Screens preserved in baseline; behavior unverified |
| LES-02 | `vocab_1` (5:9030), `vocab_2` (5:9115), `vocab_3` (5:9195), `vocab_4` (5:9275), `vocab_5` (5:9355), `vocab_6` (5:9435), `vocab_7` (5:9515) | 15 outgoing reaction(s) across mapped screens; API/state behavior unverified | Visual hierarchy, clear controls, accessible feedback | Screens preserved in baseline; behavior unverified |
| LES-03 | `listening` (5:9595) | 2 outgoing reaction(s) across mapped screens; API/state behavior unverified | Visual hierarchy, clear controls, accessible feedback | Screens preserved in baseline; behavior unverified |
| LES-04 | `listening_result` (5:9678), `listening_transcript` (5:12687) | 6 outgoing reaction(s) across mapped screens; API/state behavior unverified | Visual hierarchy, clear controls, accessible feedback | Screens preserved in baseline; behavior unverified |
| LES-05 | `writing` (5:9753), `lesson_complete` (5:9841) | 4 outgoing reaction(s) across mapped screens; API/state behavior unverified | Visual hierarchy, clear controls, accessible feedback | Screens preserved in baseline; behavior unverified |
| LES-06 | `lesson_history` (5:10718), `lesson_history_detail` (5:10798) | 9 outgoing reaction(s) across mapped screens; API/state behavior unverified | Visual hierarchy, clear controls, accessible feedback | Screens preserved in baseline; behavior unverified |
| WRF-01 | `STATE` (5:13093), `STATE` (5:13158) | 4 outgoing reaction(s) across mapped screens; API/state behavior unverified | Visual hierarchy, clear controls, accessible feedback | Screens preserved in baseline; behavior unverified |
| WRF-02 | `writing_feedback` (5:9926), `writing_feedback_detail` (5:13291) | 6 outgoing reaction(s) across mapped screens; API/state behavior unverified | Visual hierarchy, clear controls, accessible feedback | Screens preserved in baseline; behavior unverified |
| WRF-03 | `STATE` (5:13226) | 2 outgoing reaction(s) across mapped screens; API/state behavior unverified | Visual hierarchy, clear controls, accessible feedback | Screens preserved in baseline; behavior unverified |
| WRF-04 | `writing_revision` (5:10004), `STATE` (5:13362), `STATE` (5:13427) | 6 outgoing reaction(s) across mapped screens; API/state behavior unverified | Visual hierarchy, clear controls, accessible feedback | Screens preserved in baseline; behavior unverified |
| REV-01 | `review` (5:10072), `STATE` (5:13557), `STATE` (5:13622), `STATE` (5:13687) | 13 outgoing reaction(s) across mapped screens; API/state behavior unverified | Visual hierarchy, clear controls, accessible feedback | Screens preserved in baseline; behavior unverified |
| REV-02 | `review_vocab` (5:10228), `review_listening` (5:10299), `review_writing` (5:10367) | 6 outgoing reaction(s) across mapped screens; API/state behavior unverified | Visual hierarchy, clear controls, accessible feedback | Screens preserved in baseline; behavior unverified |
| REV-03 | `review_detail` (5:10159), `review_outside` (5:13752), `review_result` (5:10435) | 7 outgoing reaction(s) across mapped screens; API/state behavior unverified | Visual hierarchy, clear controls, accessible feedback | Screens preserved in baseline; behavior unverified |
| REV-04 | `review_notify_permission` (5:13944), `notification_settings` (5:11255) | 4 outgoing reaction(s) across mapped screens; API/state behavior unverified | Visual hierarchy, clear controls, accessible feedback | Screens preserved in baseline; behavior unverified |
| PRG-01 | `progress` (5:10505), `progress_listening` (5:14009) | 12 outgoing reaction(s) across mapped screens; API/state behavior unverified | Visual hierarchy, clear controls, accessible feedback | Screens preserved in baseline; behavior unverified |
| PRG-02 | `progress_streak` (5:14074), `progress_heatmap` (5:14140) | 4 outgoing reaction(s) across mapped screens; API/state behavior unverified | Visual hierarchy, clear controls, accessible feedback | Screens preserved in baseline; behavior unverified |
| PRG-03 | `progress_points` (5:14307), `STATE` (5:13489) | 4 outgoing reaction(s) across mapped screens; API/state behavior unverified | Visual hierarchy, clear controls, accessible feedback | Screens preserved in baseline; behavior unverified |
| PRG-04 | `review_result` (5:10435), `progress_points` (5:14307) | 5 outgoing reaction(s) across mapped screens; API/state behavior unverified | Visual hierarchy, clear controls, accessible feedback | Screens preserved in baseline; behavior unverified |
| PRG-05 | `ranking_alltime` (5:14442) | 2 outgoing reaction(s) across mapped screens; API/state behavior unverified | Visual hierarchy, clear controls, accessible feedback | Screens preserved in baseline; behavior unverified |
| PRG-06 | `report_name` (5:10960), `admin_name_reports` (5:15869) | 4 outgoing reaction(s) across mapped screens; API/state behavior unverified | Expose report action from ranking | Screens preserved in baseline; behavior unverified |
| PRG-07 | `ranking` (5:10872), `ranking_month` (5:14375), `ranking_alltime` (5:14442) | 8 outgoing reaction(s) across mapped screens; API/state behavior unverified | Link week/month/all-time tabs | Screens preserved in baseline; behavior unverified |
| PRG-08 | `admin_audit` (5:15798) | 2 outgoing reaction(s) across mapped screens; API/state behavior unverified | Visual hierarchy, clear controls, accessible feedback | Screens preserved in baseline; behavior unverified |

## Audit limits

- Form fields in this Figma file are editable design layers, not functional inputs. Static reactions cannot prove validation behavior.
- Full visual QA across every scroll position and device size, dynamic text sizing, reduced motion, actual audio, and ADMIN authorization require an application or working prototype.
- The local import package provides a supported `manifest.json` and `design.json` for its own plugin, but that package is not a verified duplicate of the current live Figma file.

