# Đặc tả ứng dụng học từ vựng theo chủ đề, định hướng IELTS

**Phiên bản:** 0.5 | **Ngày:** 2026-10-19 | **Chủ sở hữu:** Product Owner | **Trạng thái:** Bản nháp sau rà soát, chờ duyệt các quyết định còn mở

## 0. Ranh giới và nguồn quyết định

Tài liệu này đặc tả **các phần ngoài User API đã cung cấp**. Các quy tắc `USR`, `ACC`, `AUTH`, `SES`, `VER`, `REC` và tiêu chí nghiệm thu của User API được giữ nguyên, không chép lại. **Admin API cũ cho bài Listening/Reading sáu bước đã bỏ**; mục 2 dưới đây là yêu cầu quản trị nội dung mới cho sản phẩm này. Chưa có mã nguồn hoặc hợp đồng endpoint để đối chiếu; các mã dưới đây là yêu cầu nghiệp vụ và hành vi BE/FE, chưa phải danh sách URL HTTP.

| ID | Quyết định đã xác nhận với Product Owner |
|---|---|
| D-01 | Người học ở nhiều mức độ đều được phục vụ; bản đầu không có bài kiểm tra đầu vào bắt buộc. |
| D-02 | Trước khi vào app, người học **chỉ chọn band nội dung muốn học và các chủ đề gần gũi muốn học trước** cho việc học; có thể nhập thêm tên hiển thị công khai nhưng không chọn kỹ năng/mục tiêu khác. Band này không phải band hiện tại do app đánh giá. |
| D-03 | Bốn nhóm band nội dung: dưới 4.0; 4.0–5.0; 5.5–6.5; 7.0+. Nội dung thực tế sẽ được Product Owner bổ sung sau. |
| D-04 | Một lesson có 5–8 từ/cụm từ mục tiêu, theo thứ tự bắt buộc: học từ → luyện nghe → luyện viết. |
| D-05 | Kết quả bài nghe được chấm; AI nhận xét ngữ pháp, cách dùng từ và độ tự nhiên của bài viết. Bài viết bắt buộc nhưng nhận xét AI không dùng để tính điểm xếp hạng ở bản đầu. |
| D-06 | Nộp bài viết thành công là đủ điều kiện hoàn thành lesson, kể cả khi AI chậm/lỗi. Có thể yêu cầu nhận xét lại mà không mất tiến độ. |
| D-07 | Ôn tập kết hợp từ vựng và nghe, được nhắc ở mốc 1, 3, 7 ngày sau khi hoàn thành lesson; người học có thể mở ôn bất cứ lúc nào. |
| D-08 | Ranking **xếp theo điểm khối lượng học**: hoàn thành lesson được điểm theo số từ/cụm từ mục tiêu và band lesson, chịu trần 100 điểm lesson/ngày; ba mốc ôn hoàn thành lần đầu được thêm ít điểm. Tỷ lệ nghe lần đầu được hiện cạnh tên để tham khảo, không tính vào điểm hoặc thứ hạng. |
| D-09 | Admin tự biên soạn, kiểm tra và xuất bản nội dung lesson ở bản đầu; không dùng AI để tạo lesson. |
| D-10 | Điểm gốc của một lesson = số từ/cụm từ mục tiêu × điểm mỗi từ theo band: dưới 4.0 = 2; 4.0–5.0 = 3; 5.5–6.5 = 4; 7.0+ = 5. Điểm thực cấp chịu trần 100 điểm lesson/ngày theo PRG-R09. |
| D-11 | Mỗi mốc ôn 1, 3, 7 ngày hoàn thành lần đầu được 2 điểm; tổng tối đa 6 điểm ôn cho một lesson. |
| D-12 | Phần viết yêu cầu người học tự viết câu để vận dụng từ mục tiêu trong tình huống do đề mô tả; không đặt số từ tối thiểu/tối đa cho bài viết. |
| D-13 | Với mỗi đề viết, admin chọn 2–3 từ/cụm từ từ danh sách 5–8 từ mục tiêu của lesson để người học vận dụng. |
| D-14 | Nếu bài viết thiếu một hoặc nhiều từ được đề yêu cầu, hệ thống vẫn cho nộp và hoàn thành lesson; AI nêu rõ từ thiếu và gợi ý cách dùng. |
| D-15 | Mỗi từ trong 2–3 từ được chọn có một tình huống riêng và một ô để người học viết một câu; không ghép các từ vào một đề chung. |
| D-16 | Sau khi nhận góp ý AI, người học được sửa và gửi lại tối đa một lần cho mỗi câu; AI nhận xét lại câu đã sửa. Lưu cả hai bản, không cộng điểm ranking hay hoàn thành lesson lần nữa. |
| D-17 | Transcript bài nghe chỉ được hiện sau khi người học nộp bài nghe, cùng đáp án và giải thích. |
| D-18 | Lịch ôn đến hạn luôn hiện trong app; bản đầu có push notification nhắc ôn khi learner cho phép, nhưng từ chối quyền push không chặn ôn. |
| D-19 | Description/nghĩa theo ngữ cảnh hiển thị bằng tiếng Việt mặc định; admin có thể thêm giải thích tiếng Anh khi cần. |
| D-20 | Mỗi mốc ôn theo lịch có một câu viết bắt buộc theo tình huống, ưu tiên từ chưa từng xuất hiện trong câu learner đã nộp. Ôn từ vựng và nghe vẫn bao phủ toàn bộ từ mục tiêu; phần viết chỉ xoay vòng dần, không cam kết mỗi từ đều được viết sau ba mốc. |
| D-21 | Ranking hiển thị bảng tuần mặc định, cho chuyển sang bảng tháng và toàn thời gian; mọi bảng đều dùng điểm học tập, không dùng điểm nghe. |
| D-22 | Mốc ôn 1/3/7 ngày chỉ có hiệu lực trong đúng ngày đến hạn theo múi giờ learner. Mốc qua ngày thì hết hiệu lực; learner vẫn ôn ngoài mốc nhưng không nhận điểm mốc trễ. |
| D-23 | Bản đầu chặn câu viết lặp/rác rõ ràng, cảnh báo khi ít từ giống tiếng Anh; bài nghe phải phát đến cuối ít nhất một lượt có thể tạm dừng/tiếp tục trước khi nộp. Lượt hoàn thành nhanh bất thường chỉ gắn cờ rà soát, không tự trừ điểm. |
| D-24 | Thị trường chính bản đầu là Việt Nam: kỳ ranking và ngày áp trần điểm lesson theo `Asia/Ho_Chi_Minh`; mốc ôn và streak cá nhân vẫn theo múi giờ snapshot tương ứng. |

**Giả định đang dùng để viết đặc tả (chưa phải quyết định của Product Owner):**

| Mã | Giả định cần kiểm tra |
|---|---|
| ASM-01 | User API được triển khai theo phần đặc tả người dùng đã cung cấp; `user-api-contract.md` hiện là hợp đồng HTTP bản nháp, chưa có backend hoàn chỉnh để đối chiếu toàn bộ. |
| ASM-02 | Múi giờ dùng cho ngày học/streak và mốc ôn được đọc từ thiết bị, lưu trong hồ sơ lựa chọn; không thêm màn hình chọn múi giờ lúc vào app. |
| ASM-03 | Admin sẽ cung cấp nội dung chữ/đáp án và duyệt media của lesson; media có thể được thu âm/tải lên hoặc tạo bằng TTS từ chữ admin đã soạn. Danh sách lesson mẫu/dữ liệu thật sẽ được bổ sung sau. |

## 1. Bài toán, kết quả mong muốn và phạm vi

Người học cần nhớ từ theo ngữ cảnh rồi vận dụng lại trong bài nghe và bài viết. Sản phẩm tổ chức các hoạt động này trong một lesson ngắn theo chủ đề và mức band nội dung tự chọn; sau đó đưa lesson vào lịch ôn để hỗ trợ ghi nhớ.

| ID | Mục tiêu | Chỉ số cần đo |
|---|---|---|
| OBJ-01 | Người học đi hết mạch từ → nghe → viết | Tỷ lệ lesson hoàn thành / lesson đã bắt đầu, tách theo band |
| OBJ-02 | Người học quay lại ôn | Tỷ lệ mốc 1/3/7 hoàn thành trong ngày hiệu lực; tỷ lệ mốc `expired`; tỷ lệ lesson có lượt ôn ngoài mốc, đo riêng |
| OBJ-03 | Người học dùng lại từ | Tỷ lệ bài viết có nhận xét AI và tỷ lệ người học xem nhận xét |
| OBJ-04 | Người học duy trì thói quen | Số ngày hoạt động hợp lệ/tuần và streak theo ngày |
| OBJ-05 | Người học nghe hiểu tốt hơn | Tỷ lệ đúng của **lần nộp bài nghe đầu tiên** theo lesson/band; lần nộp sau là luyện thêm và không thay chỉ số này |
| OBJ-06 | Theo dõi độ bao phủ viết từ mục tiêu | Số từ/cụm từ mục tiêu chưa từng xuất hiện trong câu đã nộp sau hết ngày D+7, theo từng lesson; tổng số và trung bình/lesson |
| OBJ-07 | Theo dõi chi phí và độ trễ nhận xét AI | Số và tỷ lệ job nhận xét phải chờ vì trần lượt gọi theo ngày UTC, tách khỏi job lỗi nhà cung cấp |
| OBJ-08 | Theo dõi chính sách chống cày điểm | Số lesson bị giảm điểm do trần/ngày, số lượt hoàn thành bị gắn cờ nhanh bất thường; không tự kết luận gian lận |

`OBJ-02` tính trên từng mốc đã đến hết ngày hiệu lực, loại mốc bị admin tạm dừng theo `REV-R04` khỏi mẫu số; mốc hoàn thành và `expired` là hai trạng thái loại trừ nhau. Tỷ lệ ôn ngoài mốc tính số lesson có ít nhất một lượt ôn ngoài ngày mốc từ D+1 đến hết D+7 / số lesson đã hoàn thành đủ 7 ngày, không tính lượt ôn trong mốc. `OBJ-06` cũng lấy các lesson đã hoàn thành đủ 7 ngày; đối chiếu từ mục tiêu với headword/`accepted_forms[]` trong câu learner đã nộp đến hết D+7 theo múi giờ snapshot, chỉ đo sự xuất hiện chứ không khẳng định dùng đúng nghĩa. Chưa đặt ngưỡng để quyết định có tăng lên hai câu/mốc ở bản sau.

`OBJ-07` tính số job từng có `first_deferred_at` do trần / tổng job AI tạo trong cùng ngày UTC, theo `AiCallLedger/FeedbackJob`, báo cáo cả số tuyệt đối và tỷ lệ mỗi ngày; job chỉ lỗi provider không vào tử số, xử lý thành công sau đó không xóa lịch sử bị hoãn. `OBJ-08` đếm sự kiện `lesson_completed` có `capped_points > 0` và lượt có cờ `completion_too_fast` theo ngày `Asia/Ho_Chi_Minh`; hai số độc lập, không là số người gian lận.

Chưa đặt ngưỡng KPI khi chưa có dữ liệu nền. Sản phẩm **định hướng IELTS**, không cấp chứng chỉ, không tự gọi mức nội dung đã chọn hoặc kết quả bài nghe là IELTS band score chính thức.

**Trong phạm vi:** onboarding chọn band/chủ đề; danh mục lesson; quản trị nội dung mới; học từ có description ngữ cảnh, IPA, phát âm, câu mẫu, hội thoại ngắn, tình huống sử dụng; bài nghe có audio chứa từ mục tiêu, câu hỏi và điền khuyết; bài viết và nhận xét AI; ôn tập; tiến độ, streak, điểm học tập và ranking.

**Ngoài phạm vi bản đầu:** đăng nhập Google/Zalo, cấp chứng chỉ, quy đổi điểm nội bộ sang điểm IELTS chính thức, chấm phát âm, bài thi IELTS đầy đủ, tự sinh bài học bằng AI, cộng điểm ranking dựa trên nhận xét AI. User API vẫn điều chỉnh tài khoản/phiên như đã đặc tả riêng.

## 1.0. User API — Tài khoản và thông tin cá nhân

| Mã | Backend | Frontend |
|---|---|---|
| USR-01 | Đăng ký tài khoản người học, kiểm tra hợp lệ/trùng và lưu hồ sơ theo ACC-01–ACC-04. | Đăng ký, báo lỗi, cho bỏ qua xác thực liên hệ. |
| USR-02 | Đăng nhập bằng tài khoản/mật khẩu, kiểm tra trạng thái, tạo phiên theo thiết bị; áp dụng AUTH-01–AUTH-04. | Nhập thông tin, gửi nhãn thiết bị; báo lỗi chung hoặc thời điểm thử lại. |
| USR-03 | Thu hồi phiên hiện tại và token. | Đăng xuất; xóa token và về đăng nhập khi BE xác nhận. |
| USR-04 | Khôi phục mật khẩu bằng OTP Gmail hoặc Zalo theo REC-01–REC-06. | Chọn kênh, nhận/nhập OTP, nhập và xác nhận mật khẩu mới rồi đăng nhập lại. |
| USR-05 | Kiểm tra mật khẩu hiện tại, đổi mật khẩu và thu hồi tất cả phiên. | Đổi mật khẩu, báo thành công và về đăng nhập. |
| USR-06 | Đọc hồ sơ tài khoản hiện tại. | Màn hình hồ sơ. |
| USR-07 | Cập nhật trường hồ sơ được phép; thêm/thay liên hệ sau đăng ký phải qua USR-16. | Sửa hồ sơ; phân biệt liên hệ đang dùng, đang chờ và trạng thái xác thực. |
| USR-08 | Trả vai trò và kiểm tra quyền ở BE. | Hiển thị chức năng theo quyền; ẩn menu không thay thế kiểm tra BE. |
| USR-09 | Liệt kê phiên còn hiệu lực theo SES-01–SES-03. | Thiết bị/phiên, nền tảng, thời điểm đăng nhập/hoạt động cuối; đánh dấu phiên hiện tại. |
| USR-10 | Kiểm tra chủ sở hữu và thu hồi phiên được chọn cùng token. | Xác nhận đăng xuất một phiên rồi cập nhật danh sách. |
| USR-11 | Thu hồi mọi phiên, gồm phiên hiện tại, theo SES-05. | Xác nhận đăng xuất tất cả và về đăng nhập. |
| USR-12 | Kiểm tra/làm mới phiên, luân chuyển token và phát hiện dùng lại theo SES-08–SES-16. | Khôi phục đăng nhập qua process kill; phân biệt hết phiên, bảo mật và lỗi mạng. |
| USR-13 | Liên kết Google để xác thực Gmail theo VER-01–VER-03/VER-10. | Đồng ý tại Google; hiện Gmail đã xác thực hoặc lỗi liên kết trùng. |
| USR-14 | Tạo yêu cầu xác thực SĐT, gửi OTP Zalo theo VER-04–VER-05/VER-07. | Nhập SĐT; hiện số đã che, trạng thái gửi và thời gian chờ. |
| USR-15 | Kiểm tra/tiêu thụ OTP và xác thực SĐT theo VER-06. | Nhập mã; báo đúng, sai, hết hạn hoặc hết lượt thử. |
| USR-16 | Thêm/thay liên hệ: xác thực lại chủ tài khoản và kiểm chứng liên hệ mới theo VER-08. | Hiện liên hệ đang dùng/đang chờ; chỉ báo hoàn tất khi BE xác nhận. |

### 1.1. Tài khoản và quyền sử dụng

Đăng nhập bằng tài khoản/mật khẩu. Google chỉ xác thực Gmail, Zalo gửi OTP; đăng nhập bằng Google/Zalo và lấy hồ sơ Zalo chưa thuộc phạm vi.

| Mã | Quy tắc |
|---|---|
| ACC-01 | Đăng ký chỉ tạo vai trò người học; lưu mã người dùng ổn định và hồ sơ ngay. Chưa xác thực Gmail/SĐT vẫn học, kiểm tra kỹ năng IELTS, lưu từ và xem lịch sử; không chuyển thành khách hoặc khóa chức năng. |
| ACC-02 | Liên hệ tự khai có trạng thái chưa xác thực và thời điểm xác thực trống. Có thể nhận OTP khôi phục nhưng chỉ OTP đúng mới cấp quyền đặt lại; việc lưu liên hệ không chứng minh quyền sở hữu, không cho gộp tài khoản hoặc chiếm liên hệ đã xác thực. |
| ACC-03 | Hồ sơ/dữ liệu học gắn mã người dùng, không gắn email/SĐT hay mã phiên. Xác thực/đổi liên hệ, kill app, đăng xuất và hết phiên không đổi chủ hoặc xóa dữ liệu BE; bài chưa gửi thành công chưa được coi là đã lưu. |
| ACC-04 | FE cho bỏ qua xác thực liên hệ. Phân biệt chưa lưu liên hệ với đã lưu nhưng chưa xác thực: trường hợp đầu chưa thể nhận OTP khôi phục, cả hai vẫn được học. |

#### 1.1.1. Chống dò mật khẩu

| Mã | Quy tắc |
|---|---|
| AUTH-01 | Sau 5 lần sai liên tiếp/tài khoản trong 15 phút, tạm ngưng thử 15 phút; tối đa 20 yêu cầu/IP/10 phút. Hết thời gian chờ đặt lại bộ đếm tài khoản; giới hạn IP vẫn theo cửa sổ thời gian. Không tiết lộ tài khoản tồn tại hay đang bị giới hạn. |
| AUTH-02 | Sai tài khoản/mật khẩu, bị khóa hoặc bị giới hạn đều trả “Thông tin đăng nhập không hợp lệ”; không phân biệt bằng mã lỗi FE hoặc độ dài phản hồi. Có thể trả thời điểm thử lại chung, không trả bộ đếm hay trạng thái tài khoản. |
| AUTH-03 | Thành công: đặt lại bộ đếm sai, tạo phiên và ghi sự kiện. Không khóa vĩnh viễn vì sai mật khẩu; khóa quản trị theo ADM-02. OTP có giới hạn riêng tại VER-05/REC-03. |
| AUTH-04 | Ghi thời điểm, định danh đã chuẩn hóa, IP và kết quả giới hạn; không ghi mật khẩu/OTP. CAPTCHA nếu dùng chỉ bổ sung khi có lạm dụng, không thay giới hạn tài khoản/IP. |

### 1.2. Phiên và thu hồi

| Mã | Quy tắc |
|---|---|
| SES-01 | Một phiên tương ứng một lần đăng nhập thành công. Cùng thiết bị có thể có nhiều phiên; phân biệt bằng thời điểm đăng nhập. Nhãn thiết bị không phải bằng chứng xác thực. |
| SES-02 | BE lưu mã phiên, chủ tài khoản, nhãn thiết bị/nền tảng, thời điểm tạo/hoạt động cuối, hạn không hoạt động/tuyệt đối và thời điểm/lý do thu hồi. IP nếu lưu lấy từ kết nối/proxy tin cậy, không suy ra vị trí chính xác. |
| SES-03 | Chỉ xem/thu hồi phiên của mình; chủ tài khoản lấy từ xác thực BE, không từ mã FE tự gửi. Danh sách thiết bị không trả token/bí mật. |
| SES-04 | Access token gắn mã phiên. Mọi API cần đăng nhập, kể cả refresh, kiểm tra hiệu lực phiên và tài khoản. Thu hồi có hiệu lực ở yêu cầu kế tiếp dù JWT chưa hết hạn. |
| SES-05 | Thu hồi tất cả phiên phải nguyên tử với refresh đồng thời: token phát từ phiên cũ không khôi phục được quyền sau thu hồi. Đăng nhập mới sau thời điểm thu hồi tạo phiên mới. |
| SES-06 | Đổi/đặt lại mật khẩu hoặc khóa tài khoản thu hồi toàn bộ phiên; mở khóa không phục hồi phiên cũ. Không xóa dữ liệu học hoặc đăng xuất phiên Google/Zalo bên ngoài. |
| SES-07 | Thiết bị offline nhận biết thu hồi khi gọi API lại. FE chỉ báo thao tác thu hồi thành công sau xác nhận của BE. |

### 1.3. Hết hạn và khôi phục phiên mobile

| Tham số | Người học | Quản trị viên |
|---|---|---|
| Access token | 15 phút | 15 phút |
| Không hoạt động | 7 ngày | 30 phút |
| Hạn tuyệt đối từ lúc tạo phiên | 30 ngày | 8 giờ |
| Refresh token | Không vượt hạn tuyệt đối; chỉ dùng khi phiên còn hiệu lực | Như người học |

| Mã | Quy tắc |
|---|---|
| SES-08 | Theo UTC máy chủ, phiên hết hạn khi `now >= min(last_activity_at + idle_timeout, created_at + absolute_timeout)` hoặc bị thu hồi. Kiểm tra trước khi ghi hoạt động; không hồi sinh phiên hết hạn. Hết hạn không đồng nghĩa xóa bản ghi. |
| SES-09 | Chỉ tương tác chủ động đã xác thực cập nhật hoạt động của chính phiên. Refresh, polling, đồng bộ nền, push hoặc chỉ mở app không tính; thiết bị A không gia hạn B. |
| SES-10 | Khi đọc/nhập chưa gọi API, FE chỉ gửi tín hiệu hoạt động lúc foreground và có tương tác mới, tối đa 1 lần/phút. BE dùng giờ nhận; đọc thụ động, audio tự phát và offline không tự gia hạn. |
| SES-11 | Refresh luân chuyển mỗi lần; BE lưu hash và quan hệ token cũ/mới. Dùng lại token cũ thu hồi cả chuỗi token và phiên, ghi `refresh_token_reuse`, yêu cầu đăng nhập lại. FE gộp refresh đồng thời, lưu token mới trước khi báo khôi phục xong; mất phản hồi/crash không được lặp dùng token cũ vô hạn. Hiện thông báo bảo mật riêng, khác đăng xuất/hết hạn/lỗi mạng. |
| SES-12 | Access token hết hạn: refresh một lần rồi thử lại yêu cầu phù hợp, không lặp vô hạn/nộp trùng. Hết phiên: đăng nhập lại; bản nháp chưa gửi không tự nộp bằng tài khoản khác. |
| SES-13 | Trong ứng dụng Android native Java, refresh token lưu bằng khóa Android Keystore không xuất được, dữ liệu mã hóa loại khỏi backup; access token ở bộ nhớ. Không lưu mật khẩu tự đăng nhập/token dạng rõ trong SharedPreferences, log, DB thường hoặc saved state. Khởi động tiến trình phải kiểm tra/refresh với BE trước màn hình bảo vệ hoặc deep link; cờ đăng nhập cục bộ không cấp quyền. |
| SES-14 | Kill app, force-stop, crash, khởi động điện thoại, xuống nền/tạo lại Activity không tự thu hồi phiên hoặc xóa token. Khôi phục thành công giữ mã phiên, chủ và thời điểm tạo/hạn tuyệt đối; không thêm phiên hoặc gọi đăng xuất từ callback hủy. |
| SES-15 | Hết hạn/thu hồi/khóa tài khoản/token không hợp lệ: xóa token bộ nhớ và SecureStore, yêu cầu đăng nhập lại. Thiếu/mất token cũng yêu cầu đăng nhập. Lỗi mạng/BE tạm thời giữ token và cho thử lại, chưa coi phiên hợp lệ. Đăng xuất được BE xác nhận thì xóa cả token bền vững; dữ liệu BE vẫn giữ. |
| SES-16 | Không mở lại app thì BE vẫn áp dụng hết hạn/thu hồi. Danh sách chỉ ghi phiên còn hiệu lực và hoạt động cuối, không khẳng định online. Mở offline không cấp quyền mới hay cam kết học offline. |

### 1.4. Xác thực và thay liên hệ

| Mã | Quy tắc |
|---|---|
| VER-01 | Liên kết Google/thay liên hệ yêu cầu mật khẩu hiện tại; bằng chứng có hạn 5 phút, gắn phiên/thao tác. Google dùng yêu cầu do BE khởi tạo, kiểm tra state/nonce, dùng PKCE với authorization code. Không gộp tài khoản theo email. |
| VER-02 | BE kiểm tra chữ ký, issuer, audience, hạn và nonce của ID token; chỉ xác thực Gmail khi `email_verified = true`, định danh Google bằng `sub`. Gmail khác liên hệ hiện hành qua USR-16; email ngoài Gmail cần cách xác minh riêng. Không tin cờ xác thực FE. |
| VER-03 | Google `sub`, email/SĐT đã xác thực không được thuộc hai tài khoản. Chuẩn hóa email nhất quán, SĐT theo mã quốc gia; kiểm tra trùng cả lúc bắt đầu và hoàn tất để xử lý cạnh tranh. |
| VER-04 | OTP SĐT: ngẫu nhiên 6 chữ số, hạn 5 phút, gắn yêu cầu/tài khoản/SĐT/mục đích. Giá trị kiểm chứng được bảo vệ bằng bí mật máy chủ; không trả OTP cho FE hoặc ghi log. |
| VER-05 | Sai tối đa 5 lần/yêu cầu; lần thứ 5 vô hiệu yêu cầu. Gửi lại cách ≥ 60 giây, tối đa 5 lần/giờ theo cả tài khoản và SĐT, thêm giới hạn IP. Gửi lại không xóa bộ đếm giờ; mã mới thay mã cũ. |
| VER-06 | Chỉ OTP `phone_verification` đúng, còn hạn, chưa dùng mới đặt `phone_verified_at`; kiểm tra/tiêu thụ nguyên tử, một lần thành công. OTP khôi phục và trạng thái gửi/giao tin không xác thực hồ sơ. |
| VER-07 | Gửi Zalo lỗi/hết hạn mức/không đủ điều kiện: giữ chưa xác thực, báo lỗi và cho thử theo giới hạn. Không thay bằng email OTP; SMS dự phòng chưa thuộc bản đầu. |
| VER-08 | Thêm/thay liên hệ sau đăng ký qua USR-16: xác thực lại theo VER-01, kiểm chứng liên hệ mới rồi mới thay. Liên hệ cũ vẫn dùng trong lúc chờ; ứng viên không nhận OTP khôi phục. Hoàn tất: vô hiệu yêu cầu xác thực/khôi phục liên hệ cũ, thu hồi mọi phiên và yêu cầu đăng nhập lại. |
| VER-09 | Lưu trạng thái/thời điểm xác thực riêng từng liên hệ; USR-07 không tự đặt cờ hoặc thay liên hệ. Xác thực hồ sơ tùy chọn, độc lập OTP khôi phục tại REC-01–REC-06. |
| VER-10 | Google/Gmail đã xác thực thuộc tài khoản khác: từ chối nguyên tử, mã lỗi FE ổn định; không liên kết một phần, chuyển/gộp tài khoản hoặc lộ dữ liệu bên kia. FE báo “Tài khoản Google này đã được liên kết với tài khoản khác”. |

#### 1.4.1. Quên mật khẩu

Chưa đăng nhập → chọn **Gmail (email OTP)** hoặc **SĐT (OTP qua Zalo, không phải SMS)** → nhập định danh/liên hệ → xác minh OTP → đặt mật khẩu mới → đăng nhập lại. Chỉ cần một kênh; không cần mật khẩu cũ hoặc xác thực hồ sơ trước.

| Mã | Quy tắc |
|---|---|
| REC-01 | Dùng một liên hệ đã lưu hiện hành, kể cả chưa xác thực. Chưa lưu hoặc mất quyền truy cập mọi liên hệ thì chưa có cách tự khôi phục; không tự mở hỗ trợ thủ công/câu hỏi bảo mật/mã dự phòng. |
| REC-02 | Không thay nơi nhận mã trong luồng khôi phục, không gửi cho liên hệ cũ/ứng viên. Yêu cầu phải xác định một tài khoản; liên hệ trùng cần định danh đăng nhập bổ sung. Phản hồi trước xác minh: “Nếu thông tin phù hợp, mã khôi phục sẽ được gửi qua kênh đã chọn”; không lộ tài khoản/trạng thái/liên hệ khác. |
| REC-03 | OTP `password_reset` gắn tài khoản/liên hệ/kênh/yêu cầu: 6 chữ số, hạn 5 phút, dùng một lần, sai tối đa 5 lần; gửi lại ≥ 60 giây, tối đa 5 lần/giờ theo liên hệ và tài khoản cộng gộp hai kênh, thêm giới hạn IP. Mã mới thay mã cũ. Lần sai thứ 5 vô hiệu yêu cầu; gửi mới vẫn tính hạn mức, đổi kênh không đặt lại bộ đếm. Không dùng OTP xác thực hồ sơ; bảo vệ giá trị kiểm chứng bằng bí mật máy chủ, không log/trả mã cho FE. |
| REC-04 | Tiêu thụ OTP hợp lệ nguyên tử rồi cấp quyền đặt lại đúng tài khoản, hạn 5 phút, dùng một lần. Quyền này chỉ đổi mật khẩu theo chính sách, không đăng nhập/truy cập dữ liệu/đổi liên hệ/liên kết Google/xác thực hồ sơ; ghi sự kiện khôi phục riêng. |
| REC-05 | Đổi mật khẩu, tiêu thụ quyền đặt lại, vô hiệu mọi OTP/quyền khôi phục của cả hai kênh và thu hồi mọi phiên phải nguyên tử. Hai yêu cầu đồng thời chỉ một thành công; không tự đăng nhập, giữ trạng thái xác thực hồ sơ. |
| REC-06 | Không nhận/gửi được OTP: thử lại hoặc đổi kênh theo hạn mức, vẫn phải xác minh. Quyền đặt lại chỉ ở bộ nhớ; kill app sau OTP thì bắt đầu lại, không ảnh hưởng quy tắc lưu refresh token. |

### 1.5. Tiêu chí nghiệm thu User

| Mã | Tham chiếu | Kiểm tra |
|---|---|---|
| AC-USR-01 | SES-01–SES-03 | Hai thiết bị thấy đúng phiên của mình; không xem/thu hồi phiên tài khoản khác. |
| AC-USR-02 | SES-04 | Thu hồi B từ A: access/refresh B bị từ chối ở yêu cầu kế tiếp, A vẫn dùng được. |
| AC-USR-03 | SES-05 | Đăng xuất tất cả cạnh tranh với refresh: mọi token phiên cũ đều mất hiệu lực. |
| AC-USR-04 | SES-08–SES-10 | Kiểm tra ngay trước/đúng hạn idle; background và hoạt động phiên khác không gia hạn. |
| AC-USR-05 | SES-08 | Access hết 15 phút được refresh nếu phiên còn hạn; hạn tuyệt đối vẫn chặn dù đang hoạt động. |
| AC-USR-06 | SES-06 | Đổi/đặt lại mật khẩu, khóa tài khoản thu hồi mọi phiên; mở khóa không khôi phục. |
| AC-USR-07 | VER-01–VER-03 | Từ chối Google token sai chữ ký/audience/nonce, hết hạn, email chưa xác thực hoặc đã bị liên kết. |
| AC-USR-08 | VER-04–VER-06 | Từ chối OTP hết hạn/sai mục đích/sai SĐT/đã dùng/bị thay; hai xác nhận đồng thời chỉ một thành công. |
| AC-USR-09 | VER-05/VER-07 | Kiểm tra lần sai thứ 5, gửi trước 60 giây/quá 5 lần mỗi giờ; lỗi/giao tin Zalo không tự xác thực. |
| AC-USR-10 | VER-08–VER-09 | Đổi liên hệ chưa xong giữ liên hệ cũ; xong thì thu hồi phiên và vô hiệu yêu cầu liên quan liên hệ cũ. |
| AC-USR-11 | SES-07/SES-11 | Phiên offline bị thu hồi không dùng lại khi online; FE gộp refresh, reuse bị thu hồi. |
| AC-USR-12 | SES-13–SES-14 | Kill/force-stop/crash/reboot rồi mở qua icon/deep link: khôi phục cùng phiên còn hạn, không tạo phiên mới. |
| AC-USR-13 | SES-13–SES-14, VER-01 | Xoay màn hình/xuống nền không mất phiên; quay lại Google sau kill chỉ tiếp tục khi kiểm chứng được state/nonce. |
| AC-USR-14 | SES-15–SES-16 | Mở offline/lỗi BE giữ token; hết hạn/thu hồi khi app đóng yêu cầu đăng nhập, không mất dữ liệu BE. |
| AC-USR-15 | SES-11/SES-13/SES-15 | Token chỉ lưu đúng nơi, refresh cập nhật token mới; đăng xuất xóa token; mất/lỗi lưu không gây vòng lặp refresh. |
| AC-USR-16 | REC-01–REC-02 | Chỉ Gmail/chỉ SĐT/cả hai, đã/chưa xác thực đều khôi phục qua một kênh hợp lệ; chặn liên hệ lạ/ứng viên, không tự chọn tài khoản trùng. |
| AC-USR-17 | REC-03–REC-04 | Chặn OTP sai/hết hạn/dùng lại/sai kênh/tài khoản/mục đích; đổi kênh vẫn chịu hạn mức; quyền reset không gọi được API nghiệp vụ. |
| AC-USR-18 | REC-05–REC-06 | Reset một kênh vô hiệu cả hai và mọi phiên; cạnh tranh chỉ một thành công, không tự đăng nhập; kill sau OTP phải bắt đầu lại. |
| AC-USR-19 | ACC-01/ACC-04 | Bỏ qua xác thực liên hệ vẫn dùng mọi chức năng người học, gồm kiểm tra kỹ năng IELTS. |
| AC-USR-20 | ACC-02–ACC-03 | Lưu hồ sơ/bài làm rồi kill/đăng xuất/đổi liên hệ: dữ liệu và mã chủ giữ nguyên. |
| AC-USR-21 | ACC-04, REC-01/REC-04, VER-06 | Reset không đổi cờ xác thực/Google liên kết; OTP hai mục đích không dùng chéo; thiếu liên hệ không chặn học. |
| AC-USR-22 | AUTH-01–AUTH-04 | Đủ ngưỡng sai tài khoản/IP bị giới hạn; đúng sau thời gian chờ tạo phiên và đặt lại bộ đếm, không khóa vĩnh viễn. |
| AC-USR-23 | SES-11 | Refresh vô hiệu token cũ; reuse thu hồi chuỗi/phiên và ghi sự kiện. |
| AC-USR-24 | VER-03/VER-10 | Google trùng bị từ chối nguyên tử, không lộ/sửa tài khoản kia hoặc tạo liên kết một phần. |
| AC-USR-25 | AUTH-02 | Các nguyên nhân đăng nhập thất bại có cùng thông báo/mã lỗi FE/độ dài; thời điểm thử lại không tiết lộ trạng thái. |
| AC-USR-26 | REC-03 | OTP sai lần 1–4 còn được thử trong hạn; lần 5 vô hiệu, FE quay về gửi mới và giữ hạn mức giờ. |
| AC-USR-27 | SES-11 | Reuse xóa token cục bộ, hiện lý do bảo mật riêng, không nhầm lỗi mạng/đăng xuất/hết hạn. |

### 1.6. Phụ thuộc và rủi ro

| Mã | Rủi ro — Khả năng × Tác động | Yêu cầu |
|---|---|---|
| RISK-USR-01 | Thiếu Zalo OA/quyền gửi/mẫu duyệt/ngân sách — M × H | Chuẩn bị trước tích hợp; nghiệm thu gửi/nhận OTP thực, mô phỏng phải ghi rõ. |
| RISK-USR-02 | Chỉ kiểm tra JWT, bỏ qua thu hồi phiên — M × H | Thực hiện SES-04 và kiểm thử AC-USR-03. |
| RISK-USR-03 | Google sai cấu hình/liên kết nhầm — M × H | Client/redirect đúng nền tảng, bí mật ở BE; kiểm chứng VER-01–VER-03. |

Nguồn tích hợp: [Google ID token](https://developers.google.com/identity/gsi/web/guides/verify-google-id-token), [quyền Zalo ZBS](https://docs.zaloplatforms.com/docs/ZBS/bat-dau/gioi-thieu-ve-cac-nhom-quyen-thuoc-zbs-template-message), [SecureStore và backup](https://docs.expo.dev/versions/latest/sdk/securestore/). Cần OAuth client, email SMTP, Zalo OA/ứng dụng và mẫu OTP được duyệt; khôi phục dự phòng khi mất mọi liên hệ vẫn là quyết định mở.



## 2. Quản trị nội dung mới — Content/Admin API

### 2.1. Chức năng

| Mã | Backend | Frontend |
|---|---|---|
| CNT-01 | Tạo/sửa/ẩn chủ đề; một chủ đề có lesson ở nhiều nhóm band. | Danh sách chủ đề, trạng thái, thứ tự và bộ lọc. |
| CNT-02 | Tạo lesson nháp với chủ đề, nhóm band, tiêu đề, mục tiêu và 5–8 từ/cụm từ mục tiêu. | Trình soạn lesson hiển thị số từ và kiểm tra trước xuất bản. |
| CNT-03 | Lưu mỗi từ với headword, description/nghĩa tiếng Việt theo ngữ cảnh, giải thích tiếng Anh tùy chọn, IPA, các dạng biến đổi chấp nhận, audio phát âm, ít nhất một câu mẫu, một hội thoại ngắn và một tình huống dùng từ. | Soạn và nghe thử từng từ; nhập dạng biến đổi hợp lệ, xem trước màn hình learner với tiếng Việt mặc định. |
| CNT-04 | Lưu bài nghe gồm audio, transcript, câu hỏi hiểu nội dung, câu điền khuyết, đáp án/biến thể và giải thích; chỉ trả transcript cho learner sau khi nộp. | Nghe thử, xem trước bài nghe và đáp án; kiểm tra từ mục tiêu xuất hiện trong nội dung. |
| CNT-05 | Lưu 2–3 lời nhắc viết, mỗi lời nhắc gắn đúng một từ/cụm từ mục tiêu, các dạng chấp nhận của từ đó và tình huống riêng gồm bối cảnh, vai trò/người nhận hoặc mục đích giao tiếp, hành động cần diễn đạt. | Soạn tình huống riêng cho từng từ, xem trước từng ô viết một câu như learner sẽ thấy. |
| CNT-06 | Kiểm tra toàn bộ lesson rồi xuất bản/ẩn; lưu người thao tác, thời điểm và phiên bản nội dung. | Hiển thị lỗi theo trường; xác nhận trước khi xuất bản hoặc ẩn. |
| CNT-07 | Trả danh mục lesson đã xuất bản theo band/chủ đề; chỉ admin xem nháp và đáp án trước khi learner nộp. | Learner xem lesson phù hợp; admin xem trước mọi trạng thái. |
| CNT-08 | Cho tạo audio phát âm/bài nghe bằng TTS từ văn bản admin đã soạn; lưu văn bản nguồn, giọng, tệp và trạng thái admin duyệt. | Admin chọn giọng, nghe thử, sửa văn bản nếu cần và xác nhận tệp trước xuất bản. |
| CNT-09 | Cho admin thu hồi một phiên bản lesson sai nghiêm trọng, ghi lý do/người/thời điểm và tạm dừng ôn/push trên phiên bản đó; giữ hồ sơ học/điểm. | Màn admin xác nhận thu hồi phiên bản, hiển thị số learner bị ảnh hưởng và trạng thái sau thao tác. |

### 2.2. Quy tắc nội dung

| Mã | Quy tắc |
|---|---|
| CNT-R01 | Chỉ admin được tạo/sửa/xuất bản/ẩn. Learner không truy cập được dữ liệu nháp, đáp án bài nghe hay transcript trước khi nộp bài nghe. |
| CNT-R02 | Lesson chỉ xuất bản khi có 5–8 từ với đầy đủ trường ở CNT-03, audio phát âm tải/phát được, bài nghe có audio và câu hỏi/điền khuyết với đáp án, và 2–3 lời nhắc viết riêng gắn 2–3 từ khác nhau thuộc lesson; mỗi lời nhắc có bối cảnh và mục đích diễn đạt. |
| CNT-R03 | Audio bài nghe phải chứa các từ/cụm từ mục tiêu theo kiểm duyệt của admin; hệ thống đối chiếu transcript với headword và `accepted_forms[]` để cảnh báo từ thiếu, admin nghe xác nhận trước khi xuất bản. Không lấy việc khớp chuỗi làm bằng chứng duy nhất vì biến thể phát âm/ngữ pháp. |
| CNT-R04 | Admin kiểm tra audio giải mã được, thời lượng lớn hơn 0, câu mẫu/hội thoại/tình huống dùng từ đúng nghĩa đã mô tả, đáp án và biến thể có giải thích. |
| CNT-R05 | Khi sửa lesson đã xuất bản, lưu phiên bản nội dung mới. Lượt học đang diễn ra tiếp tục trên snapshot/phiên bản đã bắt đầu; không xóa media còn được tham chiếu. Lesson bị ẩn chặn lượt học mới nhưng không xóa tiến độ, điểm hay bài viết cũ. |
| CNT-R06 | Mỗi lesson thuộc đúng một nhóm band trong D-03. Band là nhãn độ khó biên tập của nội dung, không phải band đạt được của learner. |
| CNT-R07 | Đề viết phải tạo lý do dùng từ trong đúng nghĩa đã dạy: nêu ai đang ở tình huống nào và cần nói/viết điều gì. Không chỉ yêu cầu “đặt câu với từ X” hoặc chép lại câu mẫu. |
| CNT-R08 | Headword luôn là dạng chấp nhận; admin có thể khai báo thêm các biến đổi hình thái hợp lệ theo từng từ/tình huống, ví dụ `cut`, `cuts`, `cutting`. Khi so khớp dạng từ, bỏ khác biệt chữ hoa/thường và khoảng trắng ngoài; AI dùng danh sách để nhận biết từ đã xuất hiện rồi vẫn đánh giá nghĩa/cách dùng. Không coi khớp chuỗi là bằng chứng dùng từ đúng. |
| CNT-R09 | TTS chỉ chuyển văn bản do admin soạn thành tệp audio; không tạo nội dung lesson, nên phù hợp D-09. Tệp TTS phải qua cùng kiểm tra chất lượng, thời lượng và bước admin nghe duyệt ở CNT-R04 trước xuất bản. |
| CNT-R10 | Lesson ẩn hoặc có phiên bản mới vẫn cho learner đã hoàn thành ôn bằng snapshot phiên bản đã học. Admin có thao tác riêng để thu hồi phiên bản sai nghiêm trọng; thao tác này chặn phát lại nội dung lỗi, giữ lịch sử/điểm và ghi lý do, thời điểm. |
| CNT-R11 | Description tiếng Việt theo ngữ cảnh là trường bắt buộc để xuất bản; giải thích tiếng Anh là trường tùy chọn, không dùng bản tiếng Anh để thay trường tiếng Việt. |

**Ví dụ định dạng đề viết:** từ *apologize*; tình huống “Bạn đến muộn buổi hẹn và muốn nhận lỗi với bạn mình. Hãy viết một câu bạn sẽ nói với người bạn đó, dùng *apologize*.” Mỗi từ được chọn có tình huống khác phù hợp nghĩa đã học.

### 2.3. Nghiệm thu

| Mã | Tham chiếu | Kiểm tra |
|---|---|---|
| AC-CNT-01 | CNT-02–CNT-06, CNT-R02–CNT-R04/CNT-R07 | Lesson thiếu từ, IPA, audio, câu mẫu, hội thoại, tình huống, đáp án hoặc thiếu 2–3 tình huống viết riêng gắn từ hợp lệ bị chặn xuất bản và hiện đúng lỗi. |
| AC-CNT-02 | CNT-01, CNT-07, CNT-R01 | Learner không thấy chủ đề/lesson ẩn, bản nháp hay đáp án trước nộp; admin xem trước được. |
| AC-CNT-03 | CNT-R05 | Admin sửa lesson đang có người học: lượt cũ dùng nội dung/media cũ; lượt mới dùng phiên bản đã xuất bản mới. |
| AC-CNT-04 | CNT-03/CNT-05/CNT-R08 | `cutting` được nhận là dạng đã dùng khi admin khai báo cho `cut`; một dạng chưa khai báo được chuyển sang AI xem xét ngữ cảnh, không tự kết luận sai nghĩa chỉ vì khác chuỗi. |
| AC-CNT-05 | CNT-08/CNT-R09 | Admin tạo TTS từ nội dung đã soạn; chưa nghe duyệt hoặc tệp hỏng thì không xuất bản, duyệt xong thì phát đúng tệp. |
| AC-CNT-06 | CNT-09/CNT-R10 | Lesson ẩn/được sửa vẫn ôn bằng phiên bản learner đã học; phiên bản bị thu hồi vì lỗi chặn phát nội dung lỗi nhưng không xóa lịch sử và điểm, thao tác có lý do/người/thời điểm. |
| AC-CNT-07 | CNT-03/CNT-R11 | Thiếu giải nghĩa tiếng Việt bị chặn xuất bản; thiếu tiếng Anh vẫn được xuất bản; learner thấy tiếng Việt mặc định và tiếng Anh nếu admin đã thêm. |

## 3. Khởi đầu và khám phá bài học — Onboarding/Catalog API

| Mã | Backend | Frontend |
|---|---|---|
| ONB-01 | Lưu nhóm band nội dung và danh sách chủ đề ưu tiên người học chọn, gắn `user_id` từ User API. | Trước khi vào app, chỉ cho chọn band và một hoặc nhiều chủ đề gần gũi; giải thích band là mức bài muốn học. |
| ONB-02 | Cho đọc/sửa band và chủ đề đã chọn sau onboarding; sửa không xóa bài, điểm hoặc lịch sử đã học. | Trang đổi band và chủ đề ưu tiên; xác nhận lưu. |
| ONB-03 | Tạo tên hiển thị công khai tùy chọn cho `user_id`; bỏ qua thì cấp biệt danh mặc định không suy ra `user_id`. Cho đọc/sửa tên trong Hồ sơ theo `PRG-R07` và `ONB-R03`. | Bước tên hiển thị có nút bỏ qua; Hồ sơ hiện tên hiện hành và thời điểm được đổi tiếp theo. |
| CAT-01 | Trả lesson xuất bản với trạng thái riêng của learner và ưu tiên chủ đề đã chọn. | Trang chủ hiển thị chủ đề ưu tiên trước, cho tìm/xem tất cả chủ đề. |
| CAT-02 | Lọc lesson theo nhóm band đã chọn, nhưng cho learner đổi band để xem và học mức khác. | Hiển thị rõ band của từng lesson và bộ lọc đang dùng. |

**Quy tắc:** `ONB-R01` Chọn band/chủ đề không là bài kiểm tra đầu vào và không khóa nội dung mức khác. `ONB-R02` Hồ sơ lựa chọn gắn user ID, không gắn phiên hay liên hệ; cập nhật lựa chọn không đổi chủ bài học theo `ACC-03`. `ONB-R03` BE áp dụng kiểm duyệt `PRG-R07` khi tạo/sửa tên; tên nhập không đạt thì báo lỗi để sửa hoặc bỏ qua, không âm thầm lưu tên đó. Lần đầu đặt tên tự nhập, tại onboarding hoặc Hồ sơ sau khi đã bỏ qua, không tính là đổi tên; tiếp theo tối đa một lần đổi tên thành công trong mỗi khoảng 30 ngày liên tục (720 giờ) theo UTC máy chủ, từ chối sớm kèm `next_change_at`. Đổi tên không tự bỏ trạng thái admin đã ẩn tên khỏi ranking. `CAT-R01` Thứ tự ưu tiên chủ đề không thay quyền truy cập; chủ đề chưa chọn vẫn tìm được.

**Nghiệm thu:** `AC-ONB-01` người học chọn một band và ít nhất một chủ đề rồi vào danh mục; đổi lựa chọn không mất lịch sử. `AC-ONB-02` bỏ qua tên vẫn vào app và nhận biệt danh mặc định; tên hợp lệ lưu và hiện trong Hồ sơ/ranking; tên không đạt bị chặn, không mất lựa chọn band/chủ đề. `AC-ONB-03` lần đặt tên đầu không tính vào hạn mức; đổi tên hợp lệ lần đầu được lưu, yêu cầu tiếp theo trước 720 giờ bị từ chối với `next_change_at`, sau hạn được đổi; đổi tên không hiện lại tên admin đã ẩn. `AC-CAT-01` chủ đề ưu tiên lên đầu, band của lesson hiển thị đúng; các chủ đề khác vẫn truy cập được.

## 4. Học lesson — Learning API

| Mã | Backend | Frontend |
|---|---|---|
| LES-01 | Tạo lượt học với `user_id`, `lesson_id`, phiên bản nội dung; lưu bước hiện tại và thời điểm. | Mở lesson tại bước đang dở. |
| LES-02 | Ghi tiến độ xem các từ/cụm từ và mở bước nghe sau khi hoàn tất phần từ. | Thẻ từ có description ngữ cảnh, IPA, audio, câu mẫu, hội thoại, tình huống; đánh dấu đã xem. |
| LES-03 | Phục vụ audio nghe và câu hỏi không kèm đáp án; lưu nháp câu trả lời, cho phát lại và ghi sự kiện player hoàn thành audio cho lượt học. | Màn nghe có điều khiển phát/tạm dừng/nghe lại, câu hỏi và điền khuyết; tiến độ nghe chưa xong không được coi là đã phát hết. |
| LES-04 | Chỉ nhận bài nghe khi player đã báo phát đến cuối đủ một lượt theo LES-R12 và tất cả câu hỏi/ô khuyết có đáp án; chấm, lưu từng lần nộp và kết quả; sau nộp trả transcript. | Báo audio/câu hỏi còn thiếu trước khi nộp; sau nộp hiển thị số đúng/tổng số, câu sai, giải thích và transcript; mở bước viết. |
| LES-05 | Nhận/lưu nháp từng câu theo `writing_prompt_id` gắn lượt học; kiểm tra tối thiểu không dùng AI trước nộp, lưu nguyên văn và đánh dấu lesson hoàn thành. | Hiển thị 2–3 tình huống/ô viết; báo câu quá ngắn/lặp rõ ràng, cảnh báo từ điển riêng; nộp và xem lại từng câu. |
| LES-06 | Trả lịch sử lesson, phiên bản đã học, kết quả nghe, bài viết và trạng thái nhận xét AI. | Màn tiến độ/chi tiết lesson đã học; có thể quay lại xem nội dung. |

### 4.1. Quy tắc học

| Mã | Quy tắc |
|---|---|
| LES-R01 | Thứ tự bắt buộc là từ → nghe → viết. Một lesson hoàn thành khi phần từ đã xem hết, bài nghe đủ đáp án đã nộp và mọi câu viết qua kiểm tra tối thiểu LES-R11 đã nộp thành công. |
| LES-R02 | Kết quả nghe là số câu đúng/tổng câu và tỷ lệ đúng; không gọi là IELTS band. Điền khuyết bỏ qua chữ hoa/thường và khoảng trắng đầu/cuối; biến thể khác do admin khai báo. |
| LES-R03 | Người học có thể nộp lại bài nghe sau khi transcript đã mở. **Kết quả lần nộp hợp lệ đầu tiên** là kết quả đo nghe cho tiến độ và OBJ-05; kết quả tốt nhất ở các lần sau chỉ hiển thị với nhãn “luyện thêm”, không thay kết quả gốc hoặc tăng điểm ranking. |
| LES-R04 | Bài chưa được BE xác nhận lưu/nộp không được tuyên bố đã lưu. Kill app, hết phiên hoặc đăng xuất không xóa bản đã lưu ở BE; nháp cục bộ chưa gửi phải gắn tài khoản và không tự nộp dưới tài khoản khác. Tuân theo `ACC-03`, `SES-12` và `SES-15`. |
| LES-R05 | Nộp lặp cùng idempotency key không tạo lượt hoàn thành, bài nộp hay điểm thứ hai. Một `user_id` nhận điểm hoàn thành tối đa một lần cho cùng `lesson_id`; học lại được nhưng không cộng lại. |
| LES-R06 | Nếu audio lỗi/mất mạng, FE báo lỗi và cho thử lại; bài viết/nháp đã xác nhận lưu ở BE vẫn giữ. |
| LES-R07 | Việc câu viết thiếu từ được tình huống đó yêu cầu không chặn nộp hoặc hoàn thành lesson; mỗi ô vẫn phải qua kiểm tra tối thiểu LES-R11 và thỏa điều kiện lưu/nộp hợp lệ. |
| LES-R08 | Bản sửa sau góp ý AI là một phiên bản câu viết trong cùng bài nộp/lesson; không thay bản gốc, không tạo lượt hoàn thành mới và không đổi kết quả nghe hoặc điểm học tập. |
| LES-R09 | Transcript bài nghe chỉ được trả cho learner có lượt học đã nộp bài nghe tương ứng; mở lại lesson/ôn tập sau đó có thể xem lại. |
| LES-R10 | Bài nghe phải có đáp án cho toàn bộ câu hỏi/ô khuyết trước khi nộp; đáp án sai vẫn hợp lệ. Không đặt ngưỡng đúng để hoàn thành lesson vì điểm ranking đo lượng học, không đo năng lực nghe. |
| LES-R11 | Mỗi ô viết phải có ít nhất 3 từ chứa chữ cái Latin, tính theo chuỗi chữ cái tách bởi ký tự không phải chữ cái; câu viết có ít nhất 2 từ khác nhau. Không cho nộp cùng một câu y hệt ở nhiều ô trong một lesson. Sau chuẩn hóa chữ hoa/thường và khoảng trắng, chặn câu từ 4 token trở lên nếu một token chiếm hơn 50% số token, hoặc câu chứa token chỉ gồm cùng một chữ cái lặp từ 4 lần trở lên. FE nêu rõ ô và lý do để learner sửa; quy tắc này không đánh giá ngữ pháp/nghĩa, không chặn câu chỉ vì thiếu từ mục tiêu. |
| LES-R12 | Trước lần nộp bài nghe đầu, player phải phát đến cuối audio ít nhất một lượt; tạm dừng/tiếp tục và nghe lại được, tua qua đoạn chưa phát không tính đoạn đó. FE gửi sự kiện hoàn thành gắn `attempt_id`/`audio_asset_id`, BE xác nhận lưu trước khi cho nộp; sự kiện mất do mạng cho thử gửi lại, không bắt nghe lại sau khi BE đã xác nhận. Đây là chốt trình tự học bằng tín hiệu từ thiết bị, không là bằng chứng người học thật sự nghe hoặc biện pháp bảo mật chống giả mạo client. |
| LES-R13 | BE dùng từ điển tiếng Anh phổ biến có phiên bản cùng headword/`accepted_forms[]` của lesson để cảnh báo khi dưới 60% token chữ cái trong câu được nhận diện; cảnh báo “Câu có vẻ chưa phải tiếng Anh, bạn muốn sửa không?” cho phép vẫn nộp. Không dùng tỷ lệ từ điển để chặn bài, trừ điểm hoặc kết luận năng lực vì tên riêng, từ IELTS và từ lóng có thể ngoài từ điển. |
| LES-R14 | Gắn cờ `completion_too_fast` nếu thời gian từ mở lượt học tới BE xác nhận hoàn thành nhỏ hơn thời lượng audio bài nghe cộng 30 giây. Lưu thời gian và ngưỡng áp dụng để admin rà soát; không tự khóa tài khoản, thu hồi điểm hoặc chặn lesson. Cờ có thể sai với learner đã biết bài hoặc do đồng hồ/telemetry client; tính bằng thời gian máy chủ. |

### 4.2. Nghiệm thu

| Mã | Tham chiếu | Kiểm tra |
|---|---|---|
| AC-LES-01 | LES-01–LES-03 | Vào lại lesson dở mở đúng bước/phiên bản; audio và nội dung từ tải được; không xem đáp án trước nộp. |
| AC-LES-02 | LES-04, LES-R02–LES-R03 | Nộp bài nghe đúng/sai/biến thể, nộp lại; phản hồi đúng từng câu, lịch sử giữ đủ, điểm ranking không cộng thêm. |
| AC-LES-03 | LES-05, LES-R01, LES-R04–LES-R05/LES-R11 | Một trong 2–3 ô viết trống, quá ngắn hoặc lặp y hệt câu ô khác thì chặn nộp và chỉ đúng ô; khi các ô hợp lệ, nộp hoàn thành một lần; kill app/đổi phiên không mất bản đã lưu. |
| AC-LES-04 | LES-R07/LES-R11 | Mọi ô qua kiểm tra tối thiểu nhưng một câu thiếu từ được tình huống đó yêu cầu vẫn nộp và hoàn thành lesson; không cộng thêm điểm ranking vì nộp lại để bổ sung từ. |
| AC-LES-05 | LES-R08 | Gửi bản sửa của một câu giữ bản gốc trong lịch sử, lesson vẫn chỉ có một sự kiện hoàn thành và không cộng điểm lần nữa. |
| AC-LES-06 | LES-04, LES-R09 | Trước nộp, API learner không trả transcript kể cả gọi trực tiếp; sau nộp, đúng learner xem được transcript của phiên bản bài đã làm. |
| AC-LES-07 | LES-04/LES-R10 | Một ô nghe chưa trả lời bị chặn và chỉ rõ ô; trả lời hết nhưng sai toàn bộ vẫn được nộp, chấm 0 đúng và mở bước viết. |
| AC-LES-08 | LES-05/LES-R11–LES-R13 | Câu `a`, `asdf asdf asdf`, `I I I I like` hoặc ba ô có cùng một câu bị chặn và chỉ rõ lý do; `I like music.` qua kiểm tra tối thiểu; câu ít từ điển nhưng không lặp chỉ cảnh báo và vẫn nộp được. Không gọi AI trong bước kiểm tra này. |
| AC-LES-09 | LES-R03/OBJ-05 | Lần nộp đầu sai hết, lần hai đúng hết sau khi xem transcript: tiến độ đo nghe vẫn ghi lần đầu 0%; kết quả luyện thêm 100% hiển thị riêng. |
| AC-LES-10 | LES-03–LES-04/LES-R12 | Phát audio đến cuối một lần, kể cả tạm dừng rồi tiếp tục, mới được nộp; chỉ tua đến cuối hoặc còn đoạn chưa phát thì bị chặn; mạng lỗi khi ghi sự kiện cho thử lại, sự kiện đã được BE xác nhận không mất sau kill app. |
| AC-LES-11 | LES-R14 | Lượt hoàn thành dưới thời lượng audio + 30 giây được gắn cờ với ngưỡng và thời gian máy chủ; điểm/trạng thái lesson không đổi, admin thấy cờ để rà soát. |

## 5. Nhận xét bài viết — Writing Feedback API

| Mã | Backend | Frontend |
|---|---|---|
| WRF-01 | Sau khi bài viết được nộp, tạo yêu cầu nhận xét AI gắn đúng bài nộp/phiên bản đề; gom 2–3 câu bản đầu vào một job, xử lý bất đồng bộ. | Sau nộp hiện `đang nhận xét` nhưng lesson đã hoàn thành; nếu job chờ trần, hiện trạng thái và thời điểm xử lý lại theo WRF-R06. |
| WRF-02 | Với từng câu/tình huống, lưu nhận xét thành ba mục: ngữ pháp/cấu trúc câu, cách dùng từ mục tiêu (kể cả từ được yêu cầu nhưng chưa dùng), độ tự nhiên; kèm ví dụ sửa khi có căn cứ. | Hiển thị ba mục theo từng câu, từ được yêu cầu còn thiếu, câu gốc và gợi ý sửa; không thay bài gốc. |
| WRF-03 | Nếu xử lý lỗi hoặc AI trả dữ liệu sai schema, ghi trạng thái lỗi có thể thử lại; cùng phiên bản câu chỉ có một job nhận xét đang xử lý. | Hiện nút yêu cầu nhận xét lại; không mất bài hoặc trạng thái hoàn thành. |
| WRF-04 | Sau khi nhận xét bản đầu đã sẵn sàng, nhận tối đa một bản sửa qua LES-R11 cho mỗi `writing_prompt_id`, lưu như phiên bản 2 và tạo nhận xét AI riêng. | Cho sửa từng câu sau khi xem góp ý, hiển thị bản đầu/bản sửa và phản hồi tương ứng; khóa gửi lần thứ ba. |

**Quy tắc:**

| Mã | Quy tắc |
|---|---|
| WRF-R01 | AI chỉ cung cấp nhận xét luyện tập, không tạo điểm ranking hoặc band IELTS ở bản đầu. |
| WRF-R02 | Đầu vào AI gồm đề, dạng từ chấp nhận và câu người học viết. Không ghép token, email/SĐT hồ sơ hoặc dữ liệu phiên. BE che email tự nhập, SĐT Việt Nam đầu `0` hoặc `+84`, và số quốc tế dạng `+` rồi 7–15 chữ số, cho phép khoảng trắng/dấu gạch ngang giữa các nhóm, trước khi gửi AI; lưu bản gốc riêng để learner xem lại. Đây là che theo mẫu đã định nghĩa/kiểm thử, không cam kết phát hiện mọi dữ liệu cá nhân trong văn bản tự do. |
| WRF-R03 | Hiển thị rõ nhận xét do AI tạo; giữ và hiển thị bản gốc. Văn bản đầu ra phải được render như dữ liệu đã escape, không thực thi HTML/script. |
| WRF-R04 | Mỗi job AI được thử tối đa 3 lần gọi nhà cung cấp (lần đầu + 2 lần thử lại do lỗi). Thử lại chỉ áp dụng cùng phiên bản câu, không tiêu lượt sửa; một câu có tối đa hai phiên bản learner gửi. |
| WRF-R05 | Câu người học và đề/từ admin soạn được truyền như dữ liệu không đáng tin; chỉ chỉ dẫn hệ thống do BE kiểm soát có quyền điều khiển AI. JSON schema cố định cho mỗi câu gồm `prompt_id` (ID đã gửi), `version` (1/2), `grammar_feedback`, `usage_feedback`, `naturalness_feedback` (chuỗi tối đa 500 ký tự/mục), `missing_target` (mảng chỉ chứa từ mục tiêu của prompt, không lặp), `suggested_sentence` (chuỗi tối đa 300 ký tự). BE yêu cầu đúng một kết quả cho mỗi `prompt_id`, kiểm tra schema/ID/kiểu/độ dài và giá trị `missing_target` trước khi lưu. Bản không hợp lệ là lỗi job, không đưa lên FE. |
| WRF-R06 | Gộp 2–3 câu bản đầu trong một job; mỗi câu bản sửa là một job riêng. Tối đa **30 lượt gọi thực tế tới AI/người học/ngày UTC**, kể cả lần lỗi/thử lại; vượt trần thì job chuyển `deferred_quota`, lưu `retry_after_at` là 00:00 UTC kế tiếp, không gọi provider trước thời điểm đó. FE hiện “Đã đạt giới hạn nhận xét; dự kiến xử lý lại lúc [giờ địa phương từ retry_after_at]”, khác `đang nhận xét`/lỗi. Learner vẫn giữ lesson hoàn thành; thống kê job bị hoãn riêng theo OBJ-07. Trần do vận hành cấu hình nhưng thay đổi phải ghi phiên bản và không hồi tố. |

**Nghiệm thu:**

| Mã | Kiểm tra |
|---|---|
| AC-WRF-01 | Nộp viết làm lesson hoàn thành trước khi AI trả kết quả; nhận xét về đủ ba tiêu chí hiển thị sau. |
| AC-WRF-02 | AI lỗi/timeout hoặc sai schema: bài và điểm không đổi, thử lại cùng phiên bản không tạo bài nộp mới. |
| AC-WRF-03 | Câu chứa email, `0912345678`, `+84912345678`, `+1 202-555-0123` và chuỗi `ignore previous instructions`: yêu cầu gửi AI không chứa token/thông tin phiên, che đúng các mẫu trên; chỉ dẫn trong câu không đổi schema/kết luận mặc định. Không coi ca này là bảo đảm che mọi PII tự do. |
| AC-WRF-04 | Câu thiếu từ mục tiêu vẫn nhận nhận xét chỉ rõ từ thiếu và gợi ý phù hợp tình huống; dạng chấp nhận hợp lệ không bị báo thiếu chỉ vì khác headword. |
| AC-WRF-05 | Sau nhận xét bản đầu, learner sửa một câu và nhận phản hồi bản 2; gửi lần ba bị từ chối, lần thử lại do lỗi không tiêu lượt sửa. |
| AC-WRF-06 | AI trả JSON thiếu trường, sai `prompt_id`, trường quá dài hoặc HTML/script: không lưu/hiển thị bản thô; job chuyển lỗi hoặc thử lại theo WRF-R04. |
| AC-WRF-07 | Job ban đầu nhận xét 3 câu bằng một lần gọi thành công; sau 30 lượt gọi AI của cùng user trong ngày UTC, job mới vào `deferred_quota`, trả `retry_after_at` chính xác và không gọi provider sớm; FE hiện giờ địa phương tương ứng, lesson vẫn hoàn thành; báo cáo OBJ-07 tăng một job hoãn, không tính job lỗi. |

## 6. Ôn tập — Review API

| Mã | Backend | Frontend |
|---|---|---|
| REV-01 | Tạo ba mốc ôn cho lesson hoàn thành: ngày 1, 3, 7 tính từ ngày hoàn thành theo múi giờ learner tại thời điểm đó; lưu snapshot múi giờ cho cả ba mốc, trả trạng thái sắp tới/hôm nay/hết hiệu lực. | Màn ôn tập hiển thị mốc hôm nay kèm nhãn “hết hạn hôm nay”, sắp tới, đã xong/hết hiệu lực và nút “ôn ngoài mốc” dù quyền push bị từ chối. |
| REV-02 | Tạo lượt ôn gồm từ/description/ngữ cảnh, phát âm, nghe lại audio với câu hỏi ngắn và một lời nhắc viết câu cho từ được chọn theo REV-R06. | Ôn từ vựng, nghe và viết một câu theo tình huống; xem đáp án/gợi ý sau khi nộp. |
| REV-03 | Lưu lượt ôn theo mốc còn hiệu lực hoặc ôn ngoài mốc, kết quả, `lesson_version_id` và thời điểm; mốc đã qua ngày không thể hoàn thành bù. | Xem lịch sử, mốc còn hiệu lực và ôn ngoài mốc bất cứ lúc nào. |
| REV-04 | Lưu lựa chọn nhận nhắc ôn và push token khi có quyền; nhắc mọi mốc D+1/D+3/D+7 ở 19:00 và nhắc mốc chưa xong sắp hết hạn ở 21:00 theo `timezone_snapshot`, gộp các mốc cùng giờ/thiết bị và chống gửi lặp theo `REV-R07`. | Giải thích lợi ích trước khi xin quyền thông báo; cho bật/tắt nhắc push trong app. |

**Quy tắc:**

| Mã | Quy tắc |
|---|---|
| REV-R01 | Mỗi mốc D+1, D+3, D+7 chỉ có hiệu lực từ 00:00 đến trước 00:00 ngày kế tiếp theo snapshot múi giờ lưu lúc lesson hoàn thành; đổi múi giờ thiết bị sau đó không dời hạn. **Lượt ôn hợp lệ đầu tiên của lesson trong ngày hiệu lực của một mốc sẽ hoàn thành mốc đó và cấp 2 điểm, bất kể mở từ đâu. Lượt ôn ngoài cửa sổ hiệu lực hoặc sau khi mốc đã hoàn thành là ôn ngoài mốc và không cấp điểm.** Hết cửa sổ mà chưa hoàn thành thì mốc chuyển `expired`, không thể hoàn thành bù. Nếu quay lại D+10, cả ba mốc đã hết hiệu lực; learner chỉ ôn ngoài mốc. |
| REV-R02 | Lượt ôn không sửa kết quả nghe lần đầu hoặc trạng thái hoàn thành lesson. |
| REV-R03 | Đáp án ôn không lộ trước nộp; phải trả lời hết câu hỏi/ô khuyết và nộp một câu qua kiểm tra tối thiểu LES-R11 để hoàn thành mốc. Không yêu cầu trả lời đúng hoặc chờ AI để cấp điểm mốc. |
| REV-R04 | Thay đổi band/chủ đề lựa chọn không xóa lịch ôn cũ. Ôn dùng snapshot/phiên bản lesson gắn với lần hoàn thành của learner, kể cả lesson hiện đã ẩn hoặc có bản mới. Nếu admin thu hồi phiên bản theo CNT-R10, tạm dừng mốc ôn chưa làm và push của phiên bản đó, không phát nội dung lỗi; mốc qua ngày vẫn hết hiệu lực theo REV-R01, lịch sử và điểm đã cấp vẫn giữ. |
| REV-R05 | Từ chối/tắt quyền push hoặc push gửi lỗi không làm mất mốc ôn hay ngăn truy cập danh sách trong app; push không tự gia hạn phiên theo `SES-09`. |
| REV-R06 | Ôn từ vựng và nghe bao phủ toàn bộ 5–8 từ/cụm từ mục tiêu của phiên bản lesson; một câu viết/mốc không cam kết bao phủ hết từ. Ở mỗi mốc, chọn một từ chưa từng xuất hiện trong câu learner đã nộp theo headword/`accepted_forms[]`, dùng tình huống đã biên soạn của VocabularyItem để yêu cầu một câu. Nếu tất cả từ đã từng xuất hiện, chọn từ từng làm sai/thiếu trước đó; nếu không có, xoay vòng theo thứ tự nội dung. Ôn ngoài mốc cũng ưu tiên các từ chưa từng được viết. Không gọi AI cho câu ôn ở bản đầu; sau nộp hiển thị từ cần dùng, dạng chấp nhận và câu mẫu để tự đối chiếu. |
| REV-R07 | Với quyền push và tùy chọn nhắc đang bật, gửi hai loại thông báo cho mỗi ngày đến hạn theo `timezone_snapshot`: lúc 19:00 nhắc các mốc chưa hoàn thành, lúc 21:00 nhắc các mốc vẫn chưa hoàn thành với nhãn “hết hạn hôm nay”. Gộp mốc của nhiều lesson có cùng thời điểm gửi và thiết bị thành một thông báo kèm số mốc; mỗi thiết bị/ngày/khung giờ chỉ nhận tối đa một thông báo cho cùng nhóm mốc. Kiểm tra trạng thái trước khi gửi; không gửi nếu đã hoàn thành, hết hiệu lực, phiên bản bị thu hồi hoặc người học tắt nhắc. Gửi lỗi không thay trạng thái mốc. |

**Nghiệm thu:** `AC-REV-01` hoàn thành lesson ngày D tạo mốc D+1, D+3, D+7; quay lại D+10 thấy cả ba mốc `expired`, ôn ngoài mốc không cấp điểm mốc. `AC-REV-02` lượt ôn hợp lệ đầu tiên trong đúng ngày đến hạn, kể cả mở từ nút ôn ngoài mốc, cấp 2 điểm cho mốc; lượt sau không tăng điểm. `AC-REV-03` có quyền push thì cả ba mốc được nhắc lúc 19:00; mốc còn thiếu được nhắc lúc 21:00, mốc đã xong không nhắc; nhiều mốc cùng giờ/thiết bị được gộp và không gửi lặp. Từ chối quyền hoặc gửi lỗi vẫn thấy mốc trong app và không gia hạn phiên. `AC-REV-04` lesson đã ẩn/sửa: learner ôn bằng nội dung phiên bản đã học; phiên bản bị thu hồi tạm dừng mốc chưa làm/push, không phát nội dung lỗi và không xóa điểm cũ. `AC-REV-05` mốc ôn ưu tiên từ chưa từng xuất hiện trong câu đã nộp, yêu cầu một câu theo tình huống; câu rỗng/quá ngắn không hoàn thành mốc; ôn ngoài mốc xoay đến các từ còn lại, trong khi phần từ/nghe vẫn bao phủ toàn bộ từ. `AC-REV-06` nộp lượt ôn ngay trước 00:00 được tính mốc, tại/qua 00:00 theo múi giờ learner không tính mốc cũ và vẫn ôn ngoài mốc được. `AC-REV-07` nhãn “hết hạn hôm nay” chỉ hiện cho mốc chưa xong trong ngày hiệu lực; sau ngày đó hiện `expired`.

## 7. Tiến độ, streak, điểm và ranking — Progress API

| Mã | Backend | Frontend |
|---|---|---|
| PRG-01 | Tổng hợp số lesson đã bắt đầu/hoàn thành, kết quả nghe lần nộp đầu, các lần luyện nghe sau, bài viết và lịch ôn theo `user_id`. | Màn tiến độ cá nhân phân biệt kết quả nghe lần đầu, lần luyện thêm và điểm học tập. |
| PRG-02 | Ghi ngày hoạt động hợp lệ và tính streak từ các ngày liên tiếp. | Hiện streak hiện tại và ngày học cuối cùng. |
| PRG-03 | Khi lesson hoàn thành, tính điểm gốc theo số từ/cụm từ và band, cấp điểm thực không vượt 100 điểm lesson/ngày theo PRG-R09. | Hiện điểm gốc, điểm thực nhận, điểm lesson đã nhận trong ngày và giờ đặt lại trần nếu bị giới hạn. |
| PRG-04 | Cấp 2 điểm cho lượt ôn hợp lệ đầu tiên trong ngày hiệu lực của từng mốc 1/3/7, bất kể điểm vào màn ôn; lượt ngoài mốc không cộng điểm. | Hiện điểm mốc riêng; lượt ôn ngoài mốc có nhãn “không cộng điểm”. |
| PRG-05 | Trả bảng ranking toàn thời gian theo điểm học tập tích lũy và thứ hạng của learner, kèm tỷ lệ nghe lần đầu cùng kỳ theo PRG-R10. | Cho xem bảng toàn thời gian từ bộ lọc; hiện tỷ lệ nghe cạnh tên với nhãn tham khảo, ghi rõ điểm đo hoạt động học, không đo trình độ IELTS. |
| PRG-06 | Nhận báo cáo tên hiển thị không phù hợp và cho admin ẩn tên khỏi ranking, thay bằng biệt danh mặc định. | Learner có nút báo cáo tên; admin xem và xử lý báo cáo; tên bị ẩn không làm mất điểm. |
| PRG-07 | Trả thêm ranking tuần/tháng theo `Asia/Ho_Chi_Minh` dựa trên thời điểm cộng điểm, kèm tỷ lệ nghe lần đầu cùng kỳ. | Mặc định xem tuần, cho đổi sang tháng/toàn thời gian; hiển thị giờ kết thúc kỳ theo múi giờ thiết bị của learner. |
| PRG-08 | Chỉ admin xem danh sách/cấu trúc cờ `completion_too_fast` và đổi múi giờ quá 2 lần/7 ngày kèm mốc thời gian, ngưỡng và lesson liên quan; không tự xử phạt. | Bộ lọc cờ bất thường cho admin rà soát, phân biệt tín hiệu với kết luận gian lận. |

### 7.1. Quy tắc điểm

| Mã | Quy tắc |
|---|---|
| PRG-R01 | Điểm gốc hoàn thành lesson = số từ/cụm từ mục tiêu × điểm mỗi từ của band lesson: dưới 4.0 = 2; 4.0–5.0 = 3; 5.5–6.5 = 4; 7.0+ = 5. Với 5–8 từ, điểm gốc là 10–40; điểm thực cấp chịu PRG-R09. |
| PRG-R02 | **Lượt ôn hợp lệ đầu tiên của lesson trong ngày hiệu lực của một mốc sẽ hoàn thành mốc đó và cấp 2 điểm, bất kể mở từ đâu. Lượt ôn ngoài cửa sổ hiệu lực hoặc sau khi mốc đã hoàn thành là ôn ngoài mốc và không cấp điểm.** Xác định ngày bằng thời điểm BE chấp nhận bài nộp theo `timezone_snapshot`, không theo lúc bắt đầu lượt. Tối đa 6 điểm ôn/lesson. |
| PRG-R03 | Điểm được ghi trong sổ điểm theo sự kiện duy nhất `lesson_completed` hoặc `review_milestone_completed`; cùng sự kiện không ghi hai lần kể cả yêu cầu đồng thời. Sửa band/nội dung lesson sau đó không tính lại điểm đã cấp. |
| PRG-R04 | Kết quả đúng/sai bài nghe và nhận xét viết không làm tăng/giảm điểm học tập. Ranking là tổng điểm hoạt động; không tuyên bố đánh giá năng lực. |
| PRG-R05 | Hoạt động hợp lệ cho streak là hoàn thành lesson hoặc hoàn thành lượt ôn theo REV-R03, kể cả lượt không nhận điểm do trần; một ngày địa phương chỉ tính một lần. Khi BE ghi sự kiện, chốt `local_date`, IANA timezone ID và UTC offset của learner tại thời điểm đó, không tính lại ngày cũ khi đổi múi giờ. BE tự tính offset của IANA zone tại thời điểm sự kiện, chỉ chấp nhận nếu nằm từ UTC−12 đến UTC+14 và khớp offset FE gửi; nếu FE gửi sai/thiếu, dùng múi giờ hợp lệ đã lưu gần nhất, nếu chưa có thì dùng `Asia/Ho_Chi_Minh`, đồng thời ghi cờ kiểm tra. Hơn 2 lần đổi IANA timezone ID trong 7 ngày liên tiếp gắn cờ admin rà soát, không tự xóa streak/điểm. |
| PRG-R06 | Bảng ranking sắp điểm học tập thực cấp giảm dần; bằng điểm thì cùng hạng. Kỳ tuần tính từ 00:00 thứ Hai đến trước 00:00 thứ Hai kế tiếp, kỳ tháng theo tháng dương lịch, cố định `Asia/Ho_Chi_Minh`. Gán điểm vào kỳ theo `awarded_at` UTC quy đổi sang múi giờ này, không tính lại khi learner đổi band/múi giờ. FE đổi thời điểm kết thúc kỳ sang giờ thiết bị để hiển thị, nhưng không đổi ranh giới kỳ. |
| PRG-R07 | Tên công khai 2–30 ký tự sau chuẩn hóa khoảng trắng; BE từ chối markup/ký tự điều khiển, chuỗi có dạng email/SĐT và từ trong danh sách kiểm duyệt do admin quản lý ngay khi lưu. Không nhập hoặc tên bị admin ẩn thì ranking dùng biệt danh mặc định theo mã công khai ngẫu nhiên, không suy ra `user_id`; không lộ liên hệ hoặc dữ liệu phiên. Admin có thể ẩn tên sau báo cáo; sửa tên theo ONB-R03 không tự gỡ quyết định ẩn, chỉ admin gỡ sau kiểm duyệt. |
| PRG-R08 | Điểm học tập chỉ xét sau khi bài nghe đủ điều kiện LES-R10/LES-R12, các câu viết qua LES-R11 và BE ghi lesson hoàn thành. Quy tắc tối thiểu chỉ làm khó bài rác rõ ràng, không chứng minh người học đã hiểu; cờ LES-R14 chỉ hỗ trợ rà soát, không tự trừ điểm. |
| PRG-R09 | Trần **100 điểm lesson/người học/ngày `Asia/Ho_Chi_Minh`** chỉ áp dụng sự kiện `lesson_completed`, không áp dụng 2 điểm mỗi mốc ôn. BE ghi điểm thực = `min(điểm gốc, max(0, 100 − tổng điểm lesson đã cấp trong ngày))` nguyên tử với hoàn thành và sổ điểm; phần vượt trần không chuyển sang ngày sau. Lesson sau trần vẫn hoàn thành, giữ lịch sử/nhận xét AI/tiến độ/streak; sự kiện điểm 0 vẫn ghi một lần chống cấp bù khi học lại. FE báo điểm gốc, thực cấp và thời điểm đặt lại 00:00 ngày sau giờ Việt Nam. |
| PRG-R10 | Trên từng hàng ranking hiện tỷ lệ nghe lần đầu cạnh tên: tổng câu đúng / tổng câu của **lần nộp hợp lệ đầu tiên** ở các lesson learner hoàn thành trong kỳ đang xem, không lấy lần luyện thêm, không lấy điểm nghe để sắp hạng hay cấp điểm. Nếu kỳ chỉ có điểm ôn mà không có lesson hoàn thành, hiện “—”. Tỷ lệ hiển thị là chỉ số bài tập, không phải band IELTS; không hiện bài làm/câu trả lời của người khác. |

### 7.2. Nghiệm thu

| Mã | Tham chiếu | Kiểm tra |
|---|---|---|
| AC-PRG-01 | PRG-03, PRG-R01/R03/R09 | Khi còn đủ trần ngày, hoàn thành lesson 5 từ ở mức dưới 4.0 cấp 10 điểm; nộp lại/học lại không cấp thêm. |
| AC-PRG-02 | PRG-04, PRG-R02/R03 | Ba mốc đúng hạn cấp tối đa 6 điểm; mốc đã hết hạn, ôn ngoài mốc hoặc nộp lặp không tăng. Mở lượt trước nửa đêm nhưng nộp sau nửa đêm không hoàn thành mốc cũ. |
| AC-PRG-03 | PRG-02, PRG-R05 | Hai hoạt động cùng ngày địa phương đã chốt chỉ tính một ngày; đổi múi giờ sau đó không tính lại ngày cũ. Offset ngoài UTC−12…UTC+14 dùng fallback và ghi cờ; đổi IANA zone 3 lần/7 ngày gắn cờ nhưng không tự xóa streak/điểm. |
| AC-PRG-04 | PRG-05, PRG-R04/R06/R10 | Hai learner bằng điểm đồng hạng; tỷ lệ nghe lần đầu hiện cạnh tên nhưng thay đổi tỷ lệ không đổi hạng/điểm, không lộ liên hệ hoặc bài làm. |
| AC-PRG-05 | PRG-07/PRG-R06 | Điểm lúc 23:59 Chủ nhật và 00:00 thứ Hai giờ Việt Nam thuộc hai tuần khác nhau; tháng đổi lúc 00:00 ngày 1 giờ Việt Nam. FE hiển thị giờ kết thúc kỳ theo múi giờ thiết bị; người học cũ không học tuần này không đứng trên người mới có điểm tuần này trong bảng tuần mặc định. |
| AC-PRG-06 | ONB-03/PRG-06/PRG-R07 | Tên chứa email, SĐT, markup hoặc từ bị cấm bị chặn khi lưu; bỏ qua tên hoặc admin ẩn tên thì hiển thị biệt danh mặc định mà không xóa điểm. Đổi tên sau khi bị ẩn vẫn không công khai cho đến khi admin gỡ ẩn. |
| AC-PRG-07 | PRG-R08/R09 | Chọn đáp án thiếu câu, chưa phát hết audio hoặc viết `a` không hoàn thành/cấp điểm; trả lời hết, đã phát audio hợp lệ, viết hợp lệ và hoàn thành lesson thì được xét điểm theo trần dù nghe sai hết. |
| AC-PRG-08 | PRG-03/PRG-R01/R03/R09 | Đã nhận 80 điểm lesson trong ngày giờ Việt Nam, hoàn thành lesson điểm gốc 40 thì nhận 20; lesson kế tiếp nhận 0 nhưng vẫn hoàn thành/streak, ôn đúng hạn vẫn được 2 điểm. Nộp lại/cạnh tranh không nhận bù; sau 00:00 giờ Việt Nam trần reset, phần bị cắt hôm trước không chuyển sang. |
| AC-PRG-09 | PRG-05/PRG-07/PRG-R10 | Trong tuần có hai lesson với lần nghe đầu 2/4 và 3/6 thì ranking hiện 50%; nghe lại đúng hết không đổi 50%. Sang tuần chỉ có điểm ôn hiện “—”; kỳ toàn thời gian gom đúng các lần đầu. |
| AC-PRG-10 | PRG-08/PRG-R05/LES-R14 | Learner không gọi được API cờ bất thường của người khác; admin lọc được cờ hoàn thành nhanh và đổi múi giờ, xem dữ kiện/phiên bản ngưỡng nhưng không có điểm tự động bị thu hồi. |

## 8. Dữ liệu và tích hợp

| Thực thể | Dữ liệu tối thiểu / quan hệ |
|---|---|
| LearnerPreferences | `user_id`, nhóm band chọn, danh sách chủ đề ưu tiên, IANA timezone ID/UTC offset hợp lệ gần nhất và lịch sử đổi múi giờ để tính ngày học/ôn |
| PublicRankingName | `user_id`, `public_alias` ngẫu nhiên ổn định, tên tự nhập tùy chọn, `last_name_change_at`, trạng thái admin ẩn tên và người/thời điểm xử lý |
| Topic | `topic_id`, tên, mô tả, trạng thái, thứ tự |
| Lesson/Version | `lesson_id`, `version_id`, `topic_id`, band, tiêu đề, mục tiêu, trạng thái, số từ mục tiêu, người/thời điểm xuất bản |
| VocabularyItem | `item_id`, `version_id`, headword, description tiếng Việt bắt buộc, giải thích tiếng Anh tùy chọn, IPA, `accepted_forms[]`, audio, câu mẫu, hội thoại, tình huống |
| ListeningTask | `version_id`, audio, transcript nội bộ, câu hỏi, đáp án/biến thể, giải thích |
| AudioAsset | `asset_id`, văn bản nguồn do admin soạn, nguồn upload/TTS, giọng nếu dùng TTS, tệp, thời lượng, người/thời điểm nghe duyệt, phiên bản lesson tham chiếu |
| WritingPrompt | `prompt_id`, `version_id`, `target_item_id`, snapshot `accepted_forms[]`, bối cảnh, vai trò/người nhận hoặc mục đích giao tiếp, hành động cần diễn đạt; 2–3 bản ghi/lesson |
| LessonAttempt | `user_id`, `lesson_id`, `version_id`, bước, trạng thái, thời điểm tạo/hoàn thành theo máy chủ, cờ `completion_too_fast` và ngưỡng áp dụng |
| ListeningPlayback | `attempt_id`, `audio_asset_id`, các đoạn đã phát theo tín hiệu player, sự kiện đến cuối và thời điểm BE xác nhận; không xem là bằng chứng nghe thật |
| Submission | `attempt_id`, loại nghe/viết, nội dung, thời điểm nộp, thứ tự lần nộp, cờ lần nghe đầu tiên, kết quả nghe nếu có |
| WritingSentence/Feedback | `submission_id`, `prompt_id`, số phiên bản câu 1/2, câu nguyên văn, trạng thái nhận xét, ba mục nhận xét, thời điểm, phiên bản bộ hướng dẫn AI |
| ReviewMilestone/Attempt | `user_id`, `lesson_id`, `lesson_version_id`, mốc 1/3/7, `timezone_snapshot`, hạn UTC và ngày địa phương, trạng thái, `target_item_id` của câu viết, lịch sử lượt ôn |
| ReviewNotificationPreference/Delivery | `user_id`, thiết bị/push token, quyền và lựa chọn nhận nhắc; khóa gửi theo thiết bị + thời điểm gửi 19:00/21:00 + ngày địa phương + `timezone_snapshot`, danh sách mốc gộp, trạng thái gửi |
| Activity/PointLedger | `user_id`, `local_date` của streak tại sự kiện, IANA timezone ID/UTC offset lúc chốt, `awarded_at` UTC, ngày trần điểm theo `Asia/Ho_Chi_Minh`, loại sự kiện, khóa chống trùng, `base_points`/`awarded_points`/`capped_points`, phiên bản quy tắc |
| AiCallLedger/FeedbackJob | `user_id`, `job_id`, ngày UTC, số lần gọi thực tế, trạng thái gồm `deferred_quota`, `first_deferred_at` không xóa khi xử lý xong, `retry_after_at`, chi phí/usage nếu nhà cung cấp trả về |
| PublicRankingNameReport | `user_id` người bị báo cáo, `reporter_user_id`, lý do, thời điểm, quyết định admin; liên kết `PublicRankingName` |

**Tích hợp:** User API cung cấp xác thực, `user_id`, quyền admin và kiểm tra phiên theo `SES-04`; kho lưu audio; dịch vụ AI nhận xét bài viết; cơ chế gửi nhắc ôn nếu dùng push. Nội dung và bài làm gắn `user_id` ổn định theo `ACC-03`, không gắn email/SĐT hay mã phiên.

## 9. Phi chức năng và rủi ro

| Mã | Ưu tiên | Yêu cầu kiểm chứng |
|---|---|---|
| NFR-01 | Must | Mọi API học/điểm/nhận xét kiểm tra chủ tài khoản ở BE; learner A không đọc/sửa bài learner B. |
| NFR-02 | Must | Ghi bài nộp, hoàn thành lesson và cộng điểm chống trùng ngay cả khi hai yêu cầu đồng thời hoặc app gửi lại do mạng. |
| NFR-03 | Must | Audio lỗi và AI lỗi có trạng thái/đường thử lại; lỗi AI không đảo trạng thái lesson hoàn thành. |
| NFR-04 | Must | Admin và learner có quyền khác nhau; đáp án, transcript nội bộ và bản nháp không trả cho learner trước thời điểm cho phép. |
| NFR-05 | Should | Ghi thời gian xử lý phía BE cho danh mục, mở lesson, lưu nháp và bảng ranking; báo cáo P95 theo từng API trong cửa sổ 24 giờ để phát hiện chậm. |
| NFR-06 | Must | API nộp câu viết giới hạn 4 KiB UTF-8 mỗi ô để chặn payload bất thường; FE báo rõ khi vượt giới hạn kỹ thuật. Đây không là quy định số từ của bài học. |

| Mã | Xác suất × tác động | Rủi ro | Giảm thiểu |
|---|---|---|---|
| RISK-01 | Cao × Cao | Nội dung thủ công sai nghĩa, IPA, phát âm hoặc đáp án. | Xem trước, nghe thử, kiểm tra trường và duyệt bởi admin trước xuất bản. |
| RISK-02 | Trung bình × Cao | AI nhận xét sai, chậm hoặc bị câu người học điều khiển bằng prompt injection. | Dữ liệu đầu vào không có quyền điều khiển, schema cố định, kiểm tra đầu ra, nhãn AI và xử lý bất đồng bộ. |
| RISK-03 | Cao × Trung bình | Người học nộp bài rác hoặc chọn band 7.0+ để lấy điểm cao hơn dù chưa nghe hiểu; công thức D-10 không dùng độ chính xác. | Trần 100 điểm lesson/ngày, hiện tỷ lệ nghe lần đầu trên ranking, chặn câu lặp rõ ràng, yêu cầu player phát đến cuối, cảnh báo từ điển và cờ hoàn thành quá nhanh. Các tín hiệu từ client có thể bị giả mạo, không chứng minh nghe thật; nếu lạm dụng lớn, Product Owner xem lại chính sách điểm. |
| RISK-04 | Trung bình × Cao | Nội dung sửa làm sai lượt học/bài nộp cũ. | Lưu phiên bản và snapshot tệp đang tham chiếu. |
| RISK-05 | Trung bình × Trung bình | Số lượt gọi AI tăng theo số câu, bản sửa và lần thử lại. | Gộp câu bản đầu trong một job, giới hạn lần thử lại/job và 30 lượt gọi/user/ngày UTC; đo chi phí thực tế để điều chỉnh trần. |
| RISK-06 | Trung bình × Cao | Tên ranking chứa thông tin riêng/nội dung không phù hợp hoặc người bị ẩn tên né kiểm duyệt bằng đổi tên. | Kiểm tra ngay khi lưu, biệt danh dự phòng, giới hạn đổi tên 1 lần/720 giờ, báo cáo và giữ trạng thái admin ẩn cho tới khi admin gỡ. |
| RISK-07 | Trung bình × Trung bình | Nhiều mốc cùng ngày hoặc job push chạy lại gây thông báo trùng/ồn. | Gộp theo thiết bị/giờ/ngày/múi giờ snapshot; khóa gửi chống trùng và kiểm tra trạng thái mốc trước mỗi lần gửi. |
| RISK-08 | Trung bình × Trung bình | Từ điển hoặc kiểm tra lặp đánh dấu nhầm câu hợp lệ; yêu cầu player phát hết có thể bị lách bằng client chỉnh sửa. | Từ điển chỉ cảnh báo; chặn lặp theo điều kiện cụ thể và báo lý do; kiểm thử câu hợp lệ biên, theo dõi phản hồi người học; không dùng tín hiệu player để khẳng định học thật. |

## 10. Sổ quyết định và việc cần làm tiếp

| Mã | Cần xác nhận | Đề xuất hiện tại / ảnh hưởng |
|---|---|---|
| DEC-01 | Bảng điểm mỗi từ theo band | **Đã duyệt:** `2/3/4/5` như PRG-R01. |
| DEC-02 | Điểm mỗi mốc ôn | **Đã duyệt:** `2 điểm/mốc` như PRG-R02. |
| DEC-03 | Độ dài bài viết và chi phí nhận xét AI | **Đã duyệt:** viết câu theo tình huống, không có giới hạn số từ học tập. **Đề xuất từ review:** trần kỹ thuật 4 KiB/câu, tối đa 2 lần thử lại/job và 30 lượt gọi AI thực tế/user/ngày UTC; chờ đo chi phí để điều chỉnh trần. |
| DEC-04 | Mục tiêu kỹ năng trên onboarding | **Đã loại bỏ:** onboarding chỉ chọn band và chủ đề cho việc học theo D-02; bước tên công khai là tùy chọn, không phải chọn kỹ năng/mục tiêu. |
| DEC-05 | Chi tiết câu hỏi ôn và bài nghe | Cần một lesson mẫu/nguồn dữ liệu để chốt số câu, độ dài audio và câu hỏi ôn. Transcript đã chốt chỉ hiện sau khi nộp. |
| DEC-06 | Cách gửi nhắc ôn | **Đã duyệt:** danh sách trong app và push khi được cấp quyền. **Chi tiết áp dụng từ vòng review:** nhắc cả ba mốc lúc 19:00 và nhắc mốc chưa xong lúc 21:00 theo snapshot múi giờ; gộp và chống trùng theo REV-R07. |
| DEC-07 | Kỳ bảng xếp hạng | **Đã duyệt:** bảng tuần mặc định, xem thêm tháng và toàn thời gian theo PRG-07/PRG-R06. |
| DEC-08 | Mốc ôn bị trễ | **Đã duyệt:** mốc chỉ có hiệu lực đúng ngày D+1/D+3/D+7; quá hạn chỉ ôn ngoài mốc, không cộng điểm, theo REV-R01. |
| DEC-09 | Chọn band cao/cày điểm | **Đã duyệt:** trần 100 điểm hoàn thành lesson/ngày giờ Việt Nam và tỷ lệ nghe lần đầu cạnh tên trên ranking; điểm ôn không chịu trần, tỷ lệ nghe không xếp hạng; theo PRG-R09–PRG-R10. |
| DEC-10 | Chống bài viết rác và bỏ nghe | **Đã duyệt:** chặn lặp rõ ràng, từ điển chỉ cảnh báo, player phát đến cuối ít nhất một lượt có thể tạm dừng/tiếp tục; gắn cờ hoàn thành nhanh để admin rà soát, không tự trừ điểm. Không áp dụng từ điển 60% làm điều kiện nộp hay yêu cầu nghe liên tục. |
| DEC-11 | Ranh giới kỳ ranking | **Đã duyệt:** thị trường chính Việt Nam, dùng `Asia/Ho_Chi_Minh` cho tuần/tháng và ngày trần điểm lesson; mốc ôn/streak cá nhân không đổi sang giờ Việt Nam. Mở rộng đa vùng và cấu hình ranh giới ranking là việc bản sau. |

**Ưu tiên tiếp theo:** Xác nhận trần AI đề xuất ở DEC-03; cung cấp một lesson mẫu cho một nhóm band/chủ đề để chốt DEC-05 và kiểm thử cảnh báo từ điển/ngưỡng thời gian; nhóm kỹ thuật đối chiếu User API hiện có và chọn dịch vụ AI/kho audio/TTS; sau đó mới tách endpoint HTTP và backlog triển khai.

## Thuật ngữ

**Band nội dung:** mức khó của lesson do admin biên tập, người học tự chọn; không phải band IELTS đã đạt. **Từ mục tiêu:** từ hoặc cụm từ lesson yêu cầu hiểu và dùng lại. **Kết quả nghe:** số câu đúng/tổng câu của bài nghe. **Điểm học tập:** điểm ghi nhận khối lượng học và mốc ôn, dùng cho ranking. **Streak:** số ngày liên tiếp có hoàn thành lesson hoặc lượt ôn hợp lệ. **Mốc ôn:** lần ôn theo lịch 1, 3 hoặc 7 ngày sau khi hoàn thành lesson. **Ôn ngoài mốc:** lượt ôn không hoàn thành mốc đang hiệu lực, không cộng điểm, vẫn lưu lịch sử. **Tên công khai:** tên người học tự nhập hoặc biệt danh mặc định hiển thị trên ranking, tách khỏi liên hệ/tên tài khoản User API. **Snapshot lesson:** phiên bản nội dung gắn với một lượt học đang diễn ra.
