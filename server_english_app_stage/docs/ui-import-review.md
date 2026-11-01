# Rà soát bản import màn hình Star Journey

## Cập nhật 21/10/2026

`product-spec.md` đã có User API. Trang import trong Figma hiện có 113 màn hình, gồm luồng OTP/đặt lại mật khẩu, đổi mật khẩu và quản lý phiên. Toàn bộ trang đã được rà UI: chữ trong khối nội dung co chiều cao theo số dòng; bỏ dòng rỗng; thẻ dùng nền trầm và viền nhẹ hơn; nút phụ hòa nền; nút quay lại có vùng chạm 104 × 48 px và thanh điều hướng dễ đọc hơn. Kiểm tra cấu trúc trên 113 màn cho kết quả không có khối nội dung chồng lấn hoặc nút hành động thấp dưới 48 px. Các liên kết prototype vẫn chỉ mô tả UI; trạng thái do User API quyết định trong ứng dụng.

Phần đánh giá dưới đây ghi lại bản 107 màn hình trước khi có User API.

**Phiên bản:** 1.1 · **Ngày:** 2026-10-21 · **Chủ sở hữu:** Product/UX · **Trạng thái:** Đã sửa gói import tại máy; chờ kiểm tra trong Figma Desktop

## Trạng thái sau chỉnh sửa

- Đã bỏ 44 nút demo hoặc nút không hợp lệ ở trạng thái mặc định. Các frame response vẫn còn và 37 frame được đánh dấu `STATE · ...` khi import.
- Welcome chỉ còn đăng nhập, tạo tài khoản và quên mật khẩu. Lựa chọn cho khách học không đăng nhập vẫn là `DEC-01` cần quyết định, nên nút “Bắt đầu học” đã được gỡ khỏi welcome.
- Home: Part 1 mở màn chi tiết bài học mẫu; Part 2 mở trang chủ đề với nhãn “Xem chủ đề” vì gói chưa có màn chi tiết riêng cho Part 2. Ô tìm kiếm mở màn tìm kiếm; thẻ streak/heatmap mở phần chi tiết tương ứng.
- Hồ sơ người học không hiện nút Quản trị; màn hoàn thành lesson đang chờ AI không hiện nút Xem nhận xét. App chỉ cho những hành động này khi có quyền hoặc dữ liệu tương ứng.
- Đã đồng bộ `screenCount` thành 107 và sửa ghi chú về prototype link. Bốn kiểm tra hợp đồng import và kiểm tra cú pháp `code.js` đã qua; chưa chạy import trong Figma Desktop.
- Bảng phát hiện dưới đây ghi lại **hiện trạng trước khi sửa** và lý do thay đổi. `GAP-06` vẫn phụ thuộc User API; `DEC-01`–`DEC-03` cần xác nhận trước khi triển khai app thật.

## 1. Mục tiêu và nguồn đối chiếu

Mục tiêu là để người dùng chỉ thấy hành động có thể thực hiện ở trạng thái hiện tại. Màn hình lỗi, chờ, rỗng và thành công vẫn cần trong Figma để bàn giao, nhưng app phải hiển thị chúng theo dữ liệu và kết quả thao tác, không bằng các nút “xem trạng thái mẫu”. Tiêu chí thành công: ở mỗi trạng thái, mọi nút hiển thị đều có hành động hợp lệ; một lần nộp không thể bị gửi lặp do bấm nhiều lần.

**Đã xác nhận từ file:** Gói import ở [`STAR_JOURNEY_UNIFIED_AUTH`](../star_journey_unified_auth_full_import/STAR_JOURNEY_UNIFIED_AUTH/README.md) tạo trang Figma mới với 107 màn hình. `design.json` có 173 block loại `button`; `code.js` tạo prototype link từ `route` và các nút ở welcome. Gói chưa được chạy thử trong Figma Desktop theo README. [Bản Figma cũ](https://www.figma.com/design/GLDVgds4ZyBIC9OF88NODN/Untitled?node-id=2-2) có 109 màn hình và vẫn hữu ích để đối chiếu bố cục.

**Giới hạn đối chiếu:** [`product-spec.md`](product-spec.md) không chứa hợp đồng User API hoặc mã nguồn app. Vì vậy luồng xác thực, mã response cụ thể và điều kiện mở onboarding cần chốt với Product Owner/Backend trước khi triển khai.

## 2. Kết luận về màn hình chào mừng mới

Việc gộp welcome/login đã bỏ hai màn hình trùng chức năng và loại nút Google/Apple phù hợp phạm vi V1. Tuy nhiên welcome mới vẫn dồn **form đăng nhập, “Bắt đầu học”, “Tạo tài khoản”, “Quên mật khẩu”** lên cùng một màn hình. Nút “Đăng nhập” trong prototype đi thẳng đến Home, còn “Bắt đầu học” đi đến onboarding; đây là mô phỏng, chưa thể dùng làm quy tắc xác thực.

**Đề xuất UX:** giữ `Đăng nhập` là CTA chính của form; đặt `Tạo tài khoản` và `Quên mật khẩu?` dưới dạng liên kết phụ. Chỉ giữ `Bắt đầu học` nếu có luồng khách được duyệt; nếu không, thay bằng lời dẫn đến đăng ký. Khi bàn phím mở hoặc màn hình thấp hơn 844 px, form và liên kết phải cuộn được, không bị cắt bởi khung welcome hiện đang có chiều cao cố định và `clipsContent`.

## 3. Danh sách thay đổi ưu tiên

| ID | Ưu tiên | Phát hiện có căn cứ | Đề xuất có thể kiểm tra |
|---|---|---|---|
| GAP-01 | Must | `register`, `onboarding_topics`, `listening`, `writing`, `progress` và nhiều màn hình khác có nút dẫn tới trạng thái lỗi/rỗng/chờ mẫu. | Bỏ các nút này khỏi trạng thái mặc định. Điều hướng hoặc hiển thị biến thể chỉ khi kiểm tra nhập liệu, kết quả request hoặc dữ liệu trả về khớp điều kiện. |
| GAP-02 | Must | `lesson_detail` hiện đồng thời “Bắt đầu / Tiếp tục”, “Khôi phục bài dang dở”, “Chưa mở bước tiếp”. | Ánh xạ từ trạng thái lesson sang tối đa một CTA chính: chưa bắt đầu → Bắt đầu; đang dở → Tiếp tục đúng bước; bước khóa → nhãn giải thích, không có nút mở trạng thái khóa. Theo `LES-01`, `LES-R01`. |
| GAP-03 | Must | `listening` có cả “Nộp bài nghe”, “Đang tải”, “Chưa nghe hết”, “Thiếu câu trả lời”, “Đang nộp”. | Giữ một nút Nộp. Khi gửi, khóa nút và hiện tiến trình; lỗi điều kiện hiển thị cạnh player/câu chưa trả lời; lỗi tải audio mới hiện Tải lại. Theo `LES-03`–`LES-04`, `LES-R12`. |
| GAP-04 | Must | `writing` có nút “Thiếu từ mục tiêu” bên cạnh nút nộp. | Thiếu từ mục tiêu chỉ là cảnh báo có quyền tiếp tục nộp, không được chặn hoàn thành. Lỗi câu quá ngắn/lặp chỉ rõ từng ô; lỗi lưu mới hiện Thử gửi lại. Theo `D-14`, `LES-R07`, `LES-R11`–`LES-R13`. |
| GAP-05 | Must | Trên Home, hai block `journey` có chữ “TIẾP TỤC HỌC →” nhưng không có `route` trong `design.json`; `code.js` chỉ tạo link khi block có `route`. | Gắn đích cho card bài học hoặc bỏ dấu hiệu có thể bấm. Kiểm tra cả Part 1 và Part 2 đi đúng lesson tương ứng. |
| GAP-06 | Must | Welcome có `BẮT ĐẦU HỌC → onboarding_band` và `ĐĂNG NHẬP → home`, không qua phản hồi User API. | Quyết định liệu onboarding có cho khách chưa xác thực hay không; sau đăng nhập, điều hướng theo trạng thái onboarding của tài khoản, không đi Home cố định. |
| GAP-07 | Should | `review` dùng các nút “Mốc hôm nay”, “Sắp đến hạn”, “Mốc hết hạn”, “Phiên bản thu hồi” để mở trạng thái mẫu; card sắp tới vẫn có route vào chi tiết ôn. | Render nhóm mốc theo response `upcoming/today/completed/expired/revoked`; chỉ hiện Bắt đầu ôn khi có lượt hợp lệ. “Ôn ngoài mốc” vẫn khả dụng theo `REV-R01`/`REV-03`, trừ nội dung bị thu hồi. |
| GAP-08 | Should | `progress` có “Chưa có dữ liệu”; `ranking` có “Chưa có tỷ lệ nghe”; `lesson_complete` có “Đang chờ AI”, “Giới hạn điểm ngày”. | Biến các trường hợp này thành empty state, chỉ số rỗng hoặc nhãn trạng thái. Giữ CTA khi nó dẫn tới việc làm thực tế, ví dụ Bắt đầu học, Xem tiến độ, Thử nhận xét lại khi job lỗi. |
| GAP-09 | Should | `admin_publish_check` hiện đồng thời “Xem lỗi thiếu dữ liệu” và “Xác nhận xuất bản”. | Trả danh sách lỗi theo trường và chỉ cho xuất bản khi kiểm tra hợp lệ; lỗi sau request hiển thị tại màn đang thao tác. Theo `CNT-06`. |
| GAP-10 | Should | Trên welcome, nút `BẮT ĐẦU HỌC` và `TẠO TÀI KHOẢN` cao 39 px; `Quên mật khẩu?` cao 32 px. | Tăng vùng chạm lên ít nhất 44 pt iOS / 48 dp Android, kể cả khi phần nhìn nhỏ hơn. |
| GAP-11 | Could | `design.json.meta.screenCount` ghi 109 nhưng mảng `screens` có 107. `interactionNotes` ghi không tự tạo prototype link, trong khi `code.js` gọi `setReactionsAsync`. | Đồng bộ metadata/README với kết quả thực tế; sau khi import kiểm tra số frame và các link. |

## 4. Quy tắc dựng UI từ trạng thái

| Nguồn trạng thái | Ví dụ | Cách thể hiện | Nút được phép |
|---|---|---|---|
| Nhập liệu tại máy | Chủ đề chưa chọn, câu chưa đủ điều kiện | Lỗi ngay cạnh trường liên quan; giữ dữ liệu đã nhập | Sửa/Tiếp tục sau khi hợp lệ |
| Request đang chạy | Đăng nhập, nộp nghe/viết | Trạng thái chờ tại CTA, ngăn gửi lặp | Không tạo nút “Đang nộp” riêng |
| Response thành công | Đã lưu lựa chọn, đã gửi báo cáo | Chuyển bước hoặc hiện xác nhận gọn | CTA tiếp theo nếu có |
| Response lỗi có thể khôi phục | Không tải audio, không lưu bài, AI lỗi | Thông báo giữ dữ liệu và ngữ cảnh hiện tại | Thử lại đúng thao tác lỗi |
| Dữ liệu nghiệp vụ | Không có bài, mốc ôn đã hết hạn, thiếu tỷ lệ nghe | Empty state/nhãn/giá trị rỗng theo dữ liệu | Chỉ hành động có ích trong trạng thái đó |
| Quyền tài khoản | Quản trị viên, người học | Chỉ trả và cho truy cập chức năng đúng quyền từ Backend | Nút admin chỉ với quyền admin |

Màn hình riêng trong Figma nên được đặt tên `Base`, `Loading`, `Validation`, `Error`, `Empty`, `Success` và gắn điều kiện xuất hiện. Một biến thể chỉ cần frame riêng nếu bố cục hoặc nội dung thay đổi đáng kể; cảnh báo ngắn nên là trạng thái của component ngay trong màn gốc.

## 5. Các quyết định còn mở

| ID | Quyết định cần chốt | Tác động |
|---|---|
| DEC-01 | `Bắt đầu học` cho khách chưa đăng nhập hay chuyển sang đăng ký? | Luồng welcome, onboarding và lưu lựa chọn theo `user_id` (`ONB-R02`). |
| DEC-02 | User API trả những trường/mã nào cho đăng nhập, đăng ký, hết phiên và khôi phục mật khẩu? | Không thể ánh xạ chính xác response vào biến thể UI nếu thiếu hợp đồng. |
| DEC-03 | Card bài học Home mở trang chi tiết hay tiếp tục đúng bước đang dở? | Route và nhãn CTA của hai card `journey`. |

## 6. Tiêu chí nghiệm thu cho lần sửa import

1. **AC-UI-01:** Trên màn đăng nhập mặc định không có nút mô phỏng lỗi/hết phiên; đăng nhập thất bại mới hiện lỗi, vẫn giữ tên tài khoản đã nhập.
2. **AC-UI-02:** Khi request đang chạy, CTA nộp/đăng nhập chỉ gửi một lần và thể hiện tiến trình trên chính CTA.
3. **AC-UI-03:** Tại `lesson_detail`, một trạng thái lesson chỉ có một CTA chính và CTA mở đúng bước Backend trả về.
4. **AC-UI-04:** Thiếu từ mục tiêu trong câu viết vẫn nộp được nếu câu qua kiểm tra tối thiểu; lỗi lưu không làm mất câu đã nhập.
5. **AC-UI-05:** Card Part 1 trên Home mở chi tiết lesson mẫu; card Part 2 mở danh sách chủ đề với nhãn “Xem chủ đề”. Khi có màn chi tiết Part 2 thật, route phải được thay bằng đúng `lesson_id`; card không có đích thì không hiện chữ/mũi tên ám chỉ có thể bấm.
6. **AC-UI-06:** Sau import, số frame bằng số phần tử `screens`, không có route tới ID không tồn tại, và các liên kết prototype quan trọng được chạy thử trong Figma Desktop.

**Thứ tự xử lý khuyến nghị:** chốt `DEC-01`–`DEC-03`; sửa các mục Must trong `design.json` và `code.js`; sau đó kiểm tra trên thiết bị nhỏ và chạy thử import/prototype. Chỉ đổi tên hoặc ẩn các frame trạng thái trong bản Figma sau khi quy tắc hiển thị đã được gắn với dữ liệu và hành động thực tế.
