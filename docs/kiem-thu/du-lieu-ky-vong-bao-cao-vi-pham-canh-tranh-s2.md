# Dữ liệu và kỳ vọng — Báo cáo, vi phạm, cạnh tranh — Sprint 2

**Ngày:** 27/11/2026. **Trạng thái:** thiết kế; mọi kỳ vọng ở trạng thái Not Run, chưa có kết quả chạy. **Phạm vi:** AC-06 (gửi báo cáo vi phạm), AC-08 (xử lý báo cáo và đề xuất khóa) và các tình huống cạnh tranh trên cùng tin, báo cáo hoặc người gửi.

**Nguồn nghiệp vụ:** SRS 3.1 (BR-04, BR-05, BR-07, BR-08, BR-02, BR-01), tiêu chí nghiệm thu AC-05.4, AC-06, AC-08, đặc tả UC-08, UC-09, UC-14, quyết định làm rõ Q02, Q03, Q07, Q26, Q37, mô hình dữ liệu (bảng trạng thái chuẩn), sequence seq-bao-cao-vi-pham-dang-tin và seq-xu-ly-vi-pham, TSP-S1 mục 2 (dữ liệu chung).

## 1. Phạm vi và giới hạn

- Tài liệu chỉ chuẩn hóa dữ liệu vào và kết quả mong đợi (oracle) ở mức logic. Đây là đầu vào cho TSP-S2 và cho việc chạy ca báo cáo, vi phạm, cạnh tranh của Sprint 2.
- Tài liệu không gán mã TS2. TSP-S2 sẽ gán mã TS2 ổn định và tham chiếu các mã KV-BC, KV-VP, KV-CT của tài liệu này.
- Tài liệu chưa bind vào API, DTO hoặc schema. OpenAPI Sprint 1 chưa có endpoint báo cáo. Migration V1 chỉ có bảng `bao_cao_vi_pham` với trạng thái `cho_xu_ly` và `da_dong`. Các trạng thái `da_bac_bo`, `da_xac_nhan`, sự kiện vi phạm và yêu cầu khóa được viết bằng tên logic theo mô hình dữ liệu; bind khi hợp đồng và migration Sprint 2 được chốt. Mã lỗi và mã HTTP không được quy định ở đây.
- Ngoài phạm vi: ma trận quyền, thông báo cho người gửi, giao diện, gợi ý, tiền và ví. Ví chỉ xuất hiện ở dòng "số dư không đổi" và chỉ kiểm khi ví đã tồn tại.

## 2. Định nghĩa "cạnh tranh"

Cạnh tranh là hai hay nhiều thao tác cùng chạm một tin, một báo cáo hoặc một người gửi mà lịch commit chồng lấn nhau. Tài liệu chia ba nhóm:

- Đua hạn mức và ý định: nhiều lần gửi báo cáo đồng thời của cùng một người trên cùng một tin.
- Đua giữa chủ tin và luồng báo cáo: chủ sửa hoặc xóa tin đồng thời với gửi báo cáo hoặc xử lý báo cáo; gồm tình huống chủ xóa trước.
- Đua giữa hai KDV: hai KDV xử lý cùng một báo cáo, hai báo cáo cùng tin, hoặc cùng gửi yêu cầu khóa.

Mỗi ca cạnh tranh chạy đủ các lịch commit nêu trong ca, dùng barrier và kết nối riêng cho từng thao tác, rồi đọc lại bằng kết nối mới.

## 3. Cách đọc

- Mỗi mã KV là một ca logic, có thể gồm nhiều biến thể. Kết quả của từng biến thể ghi riêng theo Run-ID.
- Mọi kỳ vọng được kiểm bằng cách đọc lại sau commit qua kết nối mới, gồm cả việc không có tác động ngoài ý muốn. Snapshot trước và sau gồm: tin, phiên bản, gói và thời lượng, báo cáo, sự kiện vi phạm, yêu cầu khóa, nhật ký kiểm toán, số dư ví nếu ví đã có.
- Mức kiểm thử của mọi ca: DB/API trên PostgreSQL 17 test thật. Mock hoặc fake repository không thay kết quả này.
- Trạng thái kết quả theo TSP-S1 mục 1: Pass, Fail, Blocked, Not Run. Trạng thái ban đầu của mọi ca là Not Run.

## 4. Dữ liệu chuẩn

Dữ liệu là giả, có mã định danh cố định trong mỗi lần chạy. Ưu tiên dựng tin bằng luồng nghiệp vụ thật (đăng, duyệt, từ chối, sửa, xóa) như bộ hỗ trợ kiểm thử hiện có. Gói, báo cáo, sự kiện vi phạm và yêu cầu khóa được seed trực tiếp bằng fixture khi chưa có luồng thật. Email và credential thử được cấp ngoài tài liệu, không ghi vào fixture hay report.

### DS-S2-ACCOUNT — tài khoản

| Ký hiệu | Fixture |
|---|---|
| U1 | Người dùng hoat_dong, email đã xác minh, chủ của các tin trong DS-S2-LISTING (trừ L-PUB-K) |
| UK | Người dùng bi_khoa, email đã xác minh, chủ của L-PUB-K |
| R1, R2, R3 | Người dùng hoat_dong, email đã xác minh, phiên riêng; người gửi báo cáo, không phải chủ tin |
| U3a, U3b, U3c | Người dùng hoat_dong, chủ tin, có lịch sử vi phạm theo DS-S2-VIOLATION |
| K1, K2 | KDV hoat_dong, email đã xác minh, phiên riêng |
| Admin | Quản trị viên; chỉ dùng để tạo tiền đề yêu cầu khóa đã bị từ chối trong LS-C |

### DS-S2-LISTING — tin

Nội dung hợp lệ theo DS-LISTING của TSP-S1. Mọi tin thuộc U1 trừ khi ghi khác. V1 và V2 là hai phiên bản có tiêu đề khác nhau để phân biệt.

| Mã | Trạng thái tin | Con trỏ phiên bản |
|---|---|---|
| L-PUB | da_duyet | hiện tại = đã duyệt = V1 |
| L-PUB-2 | da_duyet | hiện tại = đã duyệt = V1; tin thứ hai của U1 |
| L-SUA-CHO | cho_kiem_duyet | hiện tại = V2; đã duyệt = V1 |
| L-SUA-DUYET | da_duyet | hiện tại = đã duyệt = V2; V1 là bản cũ |
| L-SUA-TUCHOI | bi_tu_choi | hiện tại = V2 (bị từ chối); đã duyệt = V1 |
| L-XOA | da_xoa | không áp dụng |
| L-GO | bi_go_vi_pham | không áp dụng |
| L-PUB-K | da_duyet, chủ UK bi_khoa | hiện tại = đã duyệt = V1 |

Kết luận của KDV là đầu vào của ca; hệ thống không tự chấm nội dung. Khi một ca ghi "V1 vi phạm" hoặc "V2 sạch", KDV chọn kết luận tương ứng với nhãn đó.

### DS-S2-PACKAGE — gói đẩy tin

Gói được tạo trực tiếp bằng fixture hợp lệ, không dùng luồng mua.

| Mã | Trạng thái | Thời lượng |
|---|---|---|
| GOI-CHAY | dang_hoat_dong | còn 5 ngày |
| GOI-TAMDUNG | tam_dung_cho_duyet | còn 5 ngày |

### DS-S2-TIME — thời gian

Clock test cố định t0 = 09/11/2026 10:00 Asia/Ho_Chi_Minh (03:00 UTC), theo DS-SIDE-EFFECT của TSP-S1; không dùng giờ thực và không sleep. Báo cáo seed có thời điểm gửi lần lượt t0 − 1 giờ, t0 − 2 giờ, … trừ ca ghi khác.

### DS-S2-REPORT — báo cáo

- Lý do chuẩn: "Nội dung không đúng sự thật". Mô tả chuẩn: "Mô tả báo cáo thử".
- Mã ý định: I1, I2, … là các mã khác nhau trong cùng một ca, dạng BC-<mã KV>-<số thứ tự>.
- B(n): n báo cáo cho_xu_ly của cùng một người gửi trên cùng một tin, trong cửa sổ 24 giờ trượt tính tới t0.
- Phiên bản tham chiếu của báo cáo là bản đã duyệt tại lúc gửi (V1 với L-PUB).

### DS-S2-EVIDENCE — ảnh bằng chứng

Tệp được tạo trong khu test cô lập; ghi kích thước và checksum vào run.

| Mã | Nội dung |
|---|---|
| E-JPG-1 … E-JPG-6 | Sáu ảnh JPG thật, hợp lệ, khác nhau |
| E-PNG, E-WEBP | Ảnh PNG và WebP thật, hợp lệ |
| E-PDF | Tệp PDF |
| E-GIA | Tệp có đuôi .jpg nhưng nội dung không phải ảnh |
| E-10M-OK | Ảnh thật đọc được, đúng 10.000.000 byte |
| E-10M-OVER | Ảnh thật đọc được, 10.000.001 byte |

### DS-S2-VIOLATION — lịch sử vi phạm

Mỗi tin vi phạm đã xác nhận có trạng thái bi_go_vi_pham và đúng một sự kiện vi phạm. Mỗi tin mục tiêu (M1, M2) là tin da_duyet của chủ tương ứng, có một báo cáo cho_xu_ly.

| Mã | Chủ | Tin vi phạm đã xác nhận | Tin khác | Yêu cầu khóa | Tin mục tiêu |
|---|---|---|---|---|---|
| LS-A | U3a | 1 | 1 tin bi_tu_choi do KDV từ chối kiểm duyệt (không phải vi phạm) | không | M1 |
| LS-B | U3b | 2 | không | không | M1, M2 |
| LS-C | U3c | 3 | không | 1 yêu cầu bi_tu_choi do Admin, căn cứ là ba tin vi phạm đã có | M1 |

## 5. Ca gửi báo cáo (AC-06)

### KV-BC-01 — Biên năm báo cáo

- **Truy vết:** AC-06.1; BR-04.
- **Dữ liệu:** L-PUB; R1 có B(4) trên L-PUB.
- **Thao tác:** (a) R1 gửi báo cáo thứ năm bằng ý định mới. (b) Với R1 có B(5), R1 gửi báo cáo thứ sáu bằng ý định mới.
- **Kỳ vọng:** (a) Được nhận; báo cáo mới ở cho_xu_ly; R1 có đúng 5 báo cáo trên L-PUB. (b) Bị chặn do hạn mức; R1 vẫn có đúng 5 báo cáo; không có bản ghi báo cáo mới; nếu có ảnh tạm thì được dọn; dữ liệu không đổi.

### KV-BC-02 — Cửa sổ 24 giờ trượt

- **Truy vết:** AC-06.1; BR-04.
- **Dữ liệu:** L-PUB; R1 có 5 báo cáo cho_xu_ly: bốn báo cáo tại t0 − 1 giờ đến t0 − 4 giờ, và báo cáo cũ nhất tại (a) t0 − 23 giờ 59 phút 59 giây; (b) t0 − 24 giờ 00 phút 01 giây; (c) t0 − 24 giờ 00 phút 00 giây.
- **Thao tác:** Tại t0, R1 gửi ý định mới.
- **Kỳ vọng:** (a) Bị chặn do đủ 5 báo cáo trong cửa sổ. (b) Được nhận do chỉ có 4 báo cáo trong cửa sổ. (c) Blocked chờ quyết định, xem mục 9.

### KV-BC-03 — Phạm vi đếm theo người gửi và theo tin

- **Truy vết:** BR-04.
- **Dữ liệu:** L-PUB và L-PUB-2; R1 có B(5) trên L-PUB; R2 không có báo cáo nào.
- **Thao tác:** (a) R1 gửi báo cáo cho L-PUB-2. (b) R2 gửi báo cáo cho L-PUB.
- **Kỳ vọng:** Cả hai được nhận. Số báo cáo của R1 trên L-PUB vẫn là 5.

### KV-BC-04 — Lý do bắt buộc

- **Truy vết:** FR-06; UC-09 bước 1.
- **Dữ liệu:** L-PUB; R1 chưa có báo cáo.
- **Thao tác:** R1 gửi báo cáo với lý do (a) thiếu; (b) rỗng; (c) toàn khoảng trắng.
- **Kỳ vọng:** Mỗi biến thể bị chặn; không lưu báo cáo; số báo cáo của R1 không tăng; nếu có ảnh tạm thì được dọn.

### KV-BC-05 — Số lượng ảnh bằng chứng

- **Truy vết:** AC-06.2; Q26.
- **Dữ liệu:** L-PUB; R1 chưa có báo cáo trong mỗi biến thể; ảnh E-JPG-1 … E-JPG-6.
- **Thao tác:** R1 gửi báo cáo với (a) 0 ảnh; (b) 1 ảnh; (c) 5 ảnh; (d) 6 ảnh.
- **Kỳ vọng:** (a), (b), (c) được nhận, báo cáo gắn đúng số ảnh đã gửi. (d) Bị chặn; không lưu báo cáo; không ảnh nào được gắn; ảnh tạm được dọn.

### KV-BC-06 — Định dạng và dung lượng ảnh bằng chứng

- **Truy vết:** AC-06.2; Q26; mô hình dữ liệu (10 MB = 10.000.000 byte, kiểm nội dung ảnh thật).
- **Dữ liệu:** L-PUB; R1 chưa có báo cáo trong mỗi biến thể.
- **Thao tác:** R1 gửi báo cáo kèm một ảnh: (a) E-JPG-1; (b) E-PNG; (c) E-WEBP; (d) E-PDF; (e) E-GIA; (f) E-10M-OK; (g) E-10M-OVER.
- **Kỳ vọng:** (a), (b), (c), (f) được nhận. (d), (e), (g) bị chặn; không lưu báo cáo; số báo cáo của R1 không tăng; ảnh tạm được dọn.

### KV-BC-07 — Trạng thái tin mục tiêu

- **Truy vết:** AC-06.3; BR-01; BR-04.
- **Dữ liệu:** R1 chưa có báo cáo; mỗi biến thể dùng một tin mục tiêu trong bảng.
- **Thao tác:** R1 gửi báo cáo hợp lệ bằng ý định mới cho từng tin.

| Tin mục tiêu | Kỳ vọng |
|---|---|
| L-PUB | Được nhận |
| L-SUA-CHO | Bị chặn |
| L-SUA-TUCHOI | Bị chặn |
| L-XOA | Bị chặn |
| L-GO | Bị chặn |
| L-PUB-K | Bị chặn |

- **Kỳ vọng chung cho các biến thể bị chặn:** không có bản ghi báo cáo mới; số báo cáo của R1 không đổi; phản hồi và log không chứa tiêu đề, mô tả, ảnh hoặc nội dung riêng tư của tin; ảnh tạm được dọn.

### KV-BC-08 — Tin chuyển chờ duyệt trước lúc gửi

- **Truy vết:** AC-06.3; UC-09 bước 2.
- **Dữ liệu:** L-PUB; R1 đã mở trang tin lúc công khai và chưa có báo cáo; U1 sửa L-PUB thành L-SUA-CHO và commit.
- **Thao tác:** (a) R1 gửi báo cáo dựa trên màn hình cũ. (b) K1 duyệt V2 (tin về da_duyet, chủ hoạt động), rồi R1 gửi lại bằng ý định mới.
- **Kỳ vọng:** (a) Bị chặn; số báo cáo của R1 trên tin vẫn là 0; không có bản ghi mới; thông báo cho biết có thể gửi lại khi tin được duyệt và đủ điều kiện công khai; không lộ nội dung V2. (b) Được nhận; R1 có đúng 1 báo cáo cho_xu_ly.

### KV-BC-09 — Báo cáo đã nhận trước khi sửa

- **Truy vết:** AC-06.4; Q37.
- **Dữ liệu:** L-PUB; R1 có 1 báo cáo B1 cho_xu_ly, phiên bản tham chiếu V1; U1 sửa L-PUB thành L-SUA-CHO.
- **Thao tác:** Đọc lại B1 và bản được dùng để xử lý B1.
- **Kỳ vọng:** B1 vẫn cho_xu_ly, không bị đóng hay bác bỏ chỉ vì chủ sửa; phiên bản tham chiếu vẫn là V1; bản dùng để xử lý là bản đã duyệt gần nhất (V1), không phải V2 đang chờ duyệt; V2 không công khai.

### KV-BC-10 — Retry cùng ý định đã nhận

- **Truy vết:** AC-06.4; BR-04; UC-09 bước 2.
- **Dữ liệu:** L-PUB; R1 gửi thành công báo cáo B1 bằng ý định I1; R2 không có báo cáo.
- **Thao tác:** (a) R1 gửi lại I1 khi tin vẫn công khai. (b) U1 sửa tin thành L-SUA-CHO, rồi R1 gửi lại I1. (c) U1 xóa tin thành L-XOA, rồi R1 gửi lại I1. (d) R1 có đủ 5 báo cáo, gồm B1, rồi gửi lại I1. (e) R2 gửi một yêu cầu mang mã ý định I1.
- **Kỳ vọng:** (a), (b), (c), (d) trả đúng B1 (cùng mã báo cáo); không có bản ghi mới; số báo cáo của R1 không đổi. (e) R2 không nhận B1 và không thấy dữ liệu của R1; B1 không đổi.

## 6. Ca xử lý báo cáo và đề xuất khóa (AC-08)

### KV-VP-01 — Xác nhận vi phạm gỡ tin ngay

- **Truy vết:** AC-08.1; BR-05; UC-14 bước 4.
- **Dữ liệu:** L-PUB không có gói; R1 có báo cáo B1 cho_xu_ly; U1 chưa có tin vi phạm; K1.
- **Thao tác:** K1 xem B1 cùng bản V1, rồi kết luận xác nhận vi phạm kèm lý do.
- **Kỳ vọng:** B1 chuyển da_xac_nhan; tin chuyển bi_go_vi_pham trong cùng giao dịch; đúng 1 sự kiện vi phạm cho tin; số tin vi phạm của U1 là 1; tin không còn xuất hiện ở truy vấn công khai mới; có 1 bản ghi nhật ký kiểm toán gồm người thực hiện K1, trạng thái trước và sau, lý do; không có nhắc đề xuất khóa.

### KV-VP-02 — Gói khi gỡ tin vi phạm

- **Truy vết:** UC-14 bước 4; BR-08.
- **Dữ liệu:** (a) L-PUB có GOI-CHAY. (b) L-SUA-CHO có GOI-TAMDUNG. Mỗi biến thể có báo cáo B1 gửi trước khi sửa, phiên bản tham chiếu V1. Snapshot số dư ví nếu ví đã có.
- **Thao tác:** K1 kết luận xác nhận vi phạm.
- **Kỳ vọng:** Tin chuyển bi_go_vi_pham (ở (b) dù đang cho_kiem_duyet); gói chuyển ket_thuc_do_go_tin ở cả hai biến thể; đúng 1 sự kiện vi phạm; không tự hoàn tiền, số dư ví và sổ giao dịch không đổi.

### KV-VP-03 — Nhiều báo cáo cùng tin và xử lý lặp

- **Truy vết:** AC-08.1; Q02.
- **Dữ liệu:** L-PUB; B1 của R1, B2 của R2, B3 của R3, đều cho_xu_ly; K1.
- **Thao tác:** (a) K1 xác nhận B1. (b) K1 xác nhận B2 rồi B3. (c) K1 gọi lại xử lý B1 với cùng kết luận. (d) K1 gọi xử lý B1 với kết luận bác bỏ.
- **Kỳ vọng:** Sau (a): tin bi_go_vi_pham, 1 sự kiện vi phạm, số tin vi phạm của U1 là 1. Sau (b): B2 và B3 da_xac_nhan, vẫn 1 sự kiện, số tin vi phạm của U1 vẫn là 1. Sau (c) và (d): trả kết quả đã xử lý của B1 (da_xac_nhan); không thay đổi dữ liệu; không thêm nhật ký kiểm toán; số tin vi phạm không tăng.

### KV-VP-04 — Bác bỏ khi không thấy vi phạm

- **Truy vết:** UC-14 bước 3; BR-05.
- **Dữ liệu:** L-PUB có GOI-CHAY; B1 cho_xu_ly; K1.
- **Thao tác:** K1 kết luận không thấy vi phạm kèm lý do.
- **Kỳ vọng:** B1 chuyển da_bac_bo kèm lý do; tin giữ da_duyet và vẫn đủ điều kiện công khai theo BR-01; gói giữ dang_hoat_dong, thời lượng không đổi; không có sự kiện vi phạm; số tin vi phạm của U1 không tăng.

### KV-VP-05 — Bản được xét khi tin đã sửa

- **Truy vết:** AC-08.2; Q37; UC-14 bước 1 và bước 4.
- **Dữ liệu:** Mỗi biến thể có B1 gửi trước khi sửa, phiên bản tham chiếu V1; K1; K2 khi cần duyệt V2.
  - (a) L-SUA-CHO có GOI-TAMDUNG; V1 vi phạm.
  - (b) L-SUA-CHO có GOI-TAMDUNG; V1 sạch.
  - (c) L-SUA-DUYET (K2 đã duyệt V2 trước khi xử lý); V2 sạch, V1 vi phạm.
  - (d) L-SUA-DUYET; V2 vi phạm.
  - (e) L-SUA-TUCHOI có GOI-TAMDUNG; V1 vi phạm.
- **Thao tác:** K1 xem B1 và kết luận theo nhãn của bản được xét.
- **Kỳ vọng:**
  - Bản K1 nhận để xét: V1 ở (a), (b), (e); V2 ở (c), (d).
  - (a) B1 da_xac_nhan; tin bi_go_vi_pham dù đang cho_kiem_duyet; gói ket_thuc_do_go_tin; 1 sự kiện vi phạm.
  - (b) B1 da_bac_bo; tin giữ cho_kiem_duyet; gói giữ tam_dung_cho_duyet còn 5 ngày; không có sự kiện vi phạm.
  - (c) B1 da_bac_bo; tin giữ da_duyet; không có sự kiện vi phạm; số tin vi phạm không tăng.
  - (d) B1 da_xac_nhan; tin bi_go_vi_pham; 1 sự kiện vi phạm.
  - (e) B1 da_xac_nhan; tin bi_go_vi_pham dù trước đó bị từ chối bản sửa; gói ket_thuc_do_go_tin; 1 sự kiện vi phạm; việc từ chối kiểm duyệt trước đó không được tính là vi phạm.

### KV-VP-06 — Chủ xóa trước khi KDV xử lý

- **Truy vết:** AC-08.3; BR-05; Q07; UC-08; UC-14 bước 2.
- **Dữ liệu:** L-PUB có GOI-CHAY; B1 của R1 và B2 của R2 cho_xu_ly; K1. Snapshot số dư ví nếu ví đã có và số lượt đăng tin tháng của U1.
- **Thao tác:** U1 xóa tin và commit. Sau đó K1 mở B1 và cố kết luận xác nhận vi phạm.
- **Kỳ vọng:** Sau khi xóa: tin da_xoa; B1 và B2 chuyển da_dong với lý do tin_da_xoa; gói ket_thuc_do_go_tin. Sau thao tác của K1: không kết luận vi phạm; không có sự kiện vi phạm; số tin vi phạm của U1 không tăng; tin giữ da_xoa, không thành bi_go_vi_pham; số dư ví và số lượt đăng tin không đổi.

### KV-VP-07 — Ngưỡng ba tin vi phạm và yêu cầu khóa

- **Truy vết:** AC-08.4; BR-05; UC-14 bước 5.
- **Dữ liệu:** LS-A (biến thể a) và LS-B (biến thể b, c, d, thực hiện liên tiếp trên cùng fixture); K1.
- **Thao tác:**
  - (a) Với LS-A, K1 xác nhận vi phạm trên M1.
  - (b) Với LS-B, K1 xác nhận vi phạm trên M1.
  - (c) K1 xác nhận gửi yêu cầu khóa sau lời nhắc ở (b).
  - (d) K1 xác nhận vi phạm trên M2 của LS-B.
- **Kỳ vọng:**
  - (a) Số tin vi phạm của U3a là 2, không tính tin bi_tu_choi do kiểm duyệt; không nhắc KDV; không có yêu cầu khóa.
  - (b) Số tin vi phạm của U3b là 3; hệ thống nhắc K1 gửi yêu cầu khóa; chưa có yêu cầu khóa nào được tạo.
  - (c) Tạo đúng 1 yêu cầu khóa ở cho_admin, căn cứ gồm ba tin vi phạm.
  - (d) Số tin vi phạm của U3b là 4; không nhắc; vẫn đúng 1 yêu cầu cho_admin.

### KV-VP-08 — Đề xuất lại sau khi Admin từ chối

- **Truy vết:** AC-08.4; BR-05; Q03.
- **Dữ liệu:** LS-C; K1.
- **Thao tác:** (a) K1 cố gửi yêu cầu khóa mới với căn cứ cũ. (b) K1 xác nhận vi phạm trên M1 (tin vi phạm thứ tư), rồi xác nhận gửi yêu cầu khóa.
- **Kỳ vọng:** (a) Bị chặn; không nhắc; không tạo yêu cầu mới; yêu cầu cũ vẫn bi_tu_choi. (b) Hệ thống nhắc K1; sau khi K1 gửi, có đúng 1 yêu cầu cho_admin mới với căn cứ gồm tin thứ tư; yêu cầu cũ giữ nguyên bi_tu_choi.

### KV-VP-09 — Lỗi giữa giao dịch xử lý và retry

- **Truy vết:** UC-14 (luồng lỗi); NFR-04; AC-08.
- **Dữ liệu:** L-PUB có GOI-CHAY; B1 cho_xu_ly; K1; điểm chèn lỗi.
- **Thao tác:** K1 kết luận xác nhận vi phạm với lỗi chèn (a) sau khi gỡ tin, trước khi kết thúc gói; (b) sau khi kết thúc gói, trước khi ghi sự kiện vi phạm; (c) sau commit, làm mất phản hồi. Sau phục hồi, K1 gọi lại.
- **Kỳ vọng:** (a), (b) Rollback toàn bộ: tin giữ da_duyet, gói giữ dang_hoat_dong, B1 giữ cho_xu_ly, không có sự kiện vi phạm, không có nhật ký kiểm toán; sau khi gọi lại thành công thì kết quả giống KV-VP-01 và KV-VP-02, chỉ xảy ra một lần. (c) Gọi lại trả kết quả đã ghi nhận; số tin vi phạm không tăng; không có sự kiện hay nhật ký thứ hai.

## 7. Ca cạnh tranh

### KV-CT-01 — Gửi đồng thời ở biên năm

- **Truy vết:** AC-06.1; BR-04.
- **Dữ liệu:** L-PUB; R1 có B(4); hai yêu cầu mang hai ý định mới khác nhau.
- **Thao tác:** R1 gửi hai yêu cầu đồng thời bằng barrier; chạy đủ các lịch để mỗi yêu cầu có lần được commit trước.
- **Kỳ vọng:** Đúng 1 yêu cầu được nhận và 1 yêu cầu bị chặn; R1 có đúng 5 báo cáo trên L-PUB; kết quả không phụ thuộc yêu cầu nào thắng.

### KV-CT-02 — Gửi đồng thời khi chưa có báo cáo nào

- **Truy vết:** BR-04 (khóa có hiệu lực cả khi chưa có báo cáo).
- **Dữ liệu:** L-PUB; R1 chưa có báo cáo; sáu ý định khác nhau.
- **Thao tác:** R1 gửi sáu yêu cầu đồng thời bằng barrier.
- **Kỳ vọng:** Đúng 5 yêu cầu được nhận và 1 yêu cầu bị chặn; R1 có đúng 5 báo cáo trên L-PUB.

### KV-CT-03 — Cùng ý định gửi đồng thời

- **Truy vết:** AC-06.4; BR-04; NFR-04.
- **Dữ liệu:** L-PUB; R1 chưa có báo cáo; một mã ý định I1.
- **Thao tác:** R1 gửi hai yêu cầu cùng mã I1 đồng thời bằng barrier.
- **Kỳ vọng:** Chỉ có đúng 1 báo cáo được tạo; cả hai phản hồi trả cùng một mã báo cáo; R1 có đúng 1 báo cáo trên L-PUB.

### KV-CT-04 — Gửi báo cáo đồng thời với chủ sửa tin

- **Truy vết:** AC-06.3; AC-06.4.
- **Dữ liệu:** L-PUB; R1 chưa có báo cáo; U1 chuẩn bị sửa tin.
- **Thao tác:** Lịch A: sửa commit trước, rồi gửi báo cáo. Lịch B: gửi báo cáo commit trước, rồi sửa commit.
- **Kỳ vọng:** Lịch A: gửi bị chặn; không có báo cáo; tin là L-SUA-CHO. Lịch B: có đúng 1 báo cáo cho_xu_ly với phiên bản tham chiếu V1; sau khi sửa báo cáo vẫn cho_xu_ly, không bị đóng. Cả hai lịch: không có báo cáo mới nào lưu trên tin đã chuyển chờ duyệt.

### KV-CT-05 — Gửi báo cáo đồng thời với chủ xóa tin

- **Truy vết:** AC-05.4; AC-06.3.
- **Dữ liệu:** L-PUB; R1 chưa có báo cáo; U1 chuẩn bị xóa tin.
- **Thao tác:** Lịch A: xóa commit trước, rồi gửi báo cáo. Lịch B: gửi báo cáo commit trước, rồi xóa commit.
- **Kỳ vọng:** Lịch A: gửi bị chặn; không có báo cáo; tin da_xoa. Lịch B: tin da_xoa; báo cáo vừa gửi chuyển da_dong với lý do tin_da_xoa; không có sự kiện vi phạm.

### KV-CT-06 — Hai KDV cùng xử lý một báo cáo

- **Truy vết:** AC-08.1; UC-14 (luồng lỗi); tương tự AC-07.2 của Sprint 1.
- **Dữ liệu:** L-PUB; B1 cho_xu_ly; K1 và K2 cùng xem B1 với bản V1.
- **Thao tác:** Hai cặp kết luận đồng thời bằng barrier: xác nhận/xác nhận và bác bỏ/xác nhận; chạy đủ các lịch để mỗi bên có lần được commit trước.
- **Kỳ vọng:** Chỉ một kết luận hợp lệ được commit; bên sau nhận kết quả đã xử lý của B1, không ghi đè. Nếu kết luận thắng là xác nhận: tin bi_go_vi_pham, 1 sự kiện vi phạm, 1 nhật ký kiểm toán. Nếu kết luận thắng là bác bỏ: tin giữ da_duyet, không có sự kiện vi phạm, 1 nhật ký kiểm toán. Không bao giờ có hai nhật ký kiểm toán hay hai sự kiện cho cùng một quyết định.

### KV-CT-07 — Hai KDV, hai báo cáo cùng tin

- **Truy vết:** AC-08.1; UC-14 bước 4 (sự kiện vi phạm duy nhất theo tin).
- **Dữ liệu:** L-PUB có GOI-CHAY; B1 của R1 và B2 của R2 cho_xu_ly; K1 và K2.
- **Thao tác:** K1 xác nhận vi phạm B1 và K2 xác nhận vi phạm B2 đồng thời bằng barrier.
- **Kỳ vọng:** Đúng 1 sự kiện vi phạm cho tin; số tin vi phạm của U1 là 1; tin bi_go_vi_pham; B1 và B2 đều da_xac_nhan; gói ket_thuc_do_go_tin.

### KV-CT-08 — Chủ xóa đồng thời với xác nhận vi phạm

- **Truy vết:** AC-08.3; BR-02; UC-08.
- **Dữ liệu:** L-PUB có GOI-CHAY; B1 cho_xu_ly; K1; U1 chuẩn bị xóa tin.
- **Thao tác:** Lịch A: xóa commit trước, rồi K1 xác nhận. Lịch B: K1 xác nhận commit trước, rồi U1 xóa.
- **Kỳ vọng:** Lịch A: kết quả như KV-VP-06 (B1 da_dong lý do tin_da_xoa, không sự kiện vi phạm, tin da_xoa). Lịch B: tin bi_go_vi_pham; B1 da_xac_nhan; 1 sự kiện vi phạm; gói ket_thuc_do_go_tin; thao tác xóa của U1 bị chặn vì tin bi_go_vi_pham chỉ đọc; tin không thành da_xoa.

### KV-CT-09 — Bản đã duyệt đổi giữa lúc xem và lúc quyết định

- **Truy vết:** AC-08.2; UC-14 bước 1.
- **Dữ liệu:** L-SUA-CHO có B1 gửi trước khi sửa (phiên bản tham chiếu V1); K1 đã xem B1 với bản đã duyệt V1.
- **Thao tác:** K2 duyệt V2 và commit (tin thành da_duyet, bản đã duyệt = V2). Sau đó K1 gửi kết luận kèm mã bản đã xem là V1.
- **Kỳ vọng:** K1 được yêu cầu xem lại bản đã duyệt mới nhất (V2); B1 vẫn cho_xu_ly; không có thay đổi dữ liệu: tin giữ da_duyet, không có sự kiện vi phạm, không có nhật ký kiểm toán cho kết luận này. Sau khi K1 xem lại V2 và kết luận, kết quả theo KV-VP-05 (c) hoặc (d).

### KV-CT-10 — Hai KDV cùng gửi yêu cầu khóa

- **Truy vết:** AC-08.4; BR-05.
- **Dữ liệu:** LS-B sau bước (b) của KV-VP-07: số tin vi phạm là 3, đã nhắc, chưa có yêu cầu khóa; K1 và K2.
- **Thao tác:** K1 và K2 cùng xác nhận gửi yêu cầu khóa đồng thời bằng barrier.
- **Kỳ vọng:** Có đúng 1 yêu cầu khóa ở cho_admin; không tạo yêu cầu thứ hai.

## 8. Ma trận truy vết

| Tiêu chí | Ca |
|---|---|
| AC-06.1 | KV-BC-01, KV-BC-02, KV-BC-03, KV-CT-01, KV-CT-02 |
| AC-06.2 | KV-BC-05, KV-BC-06 |
| AC-06.3 | KV-BC-07, KV-BC-08, KV-CT-04, KV-CT-05 |
| AC-06.4 | KV-BC-09, KV-BC-10, KV-CT-03, KV-CT-04 |
| AC-08.1 | KV-VP-01, KV-VP-03, KV-CT-06, KV-CT-07 |
| AC-08.2 | KV-VP-05, KV-CT-09 |
| AC-08.3 | KV-VP-06, KV-CT-05, KV-CT-08 |
| AC-08.4 | KV-VP-07, KV-VP-08, KV-CT-10 |
| UC-14 (nhánh bác bỏ, gói, lỗi giao dịch) | KV-VP-02, KV-VP-04, KV-VP-09 |
| UC-09 bước 1 (lý do bắt buộc) | KV-BC-04 |

Mọi biến thể của mỗi ca phải đạt ở mức DB/API thì phần tiêu chí tương ứng mới được coi là đạt. Ma trận này chỉ phủ phần báo cáo, vi phạm và cạnh tranh; các phần khác của AC-08 như quyền và thông báo do tài liệu khác phụ trách.

## 9. Điểm chưa có oracle

Các điểm dưới đây không được tự quyết. Ca hoặc biến thể liên quan ghi Blocked chờ quyết định, hoặc chỉ kiểm phần đã nêu rõ.

1. Ranh giới đúng 24 giờ 00 phút 00 giây của cửa sổ trượt (KV-BC-02 biến thể c): BR-04 chỉ ghi "24 giờ trượt", chưa nói báo cáo đúng 24 giờ trước có còn được đếm.
2. Báo cáo đã xử lý hoặc đã đóng có còn được đếm vào giới hạn năm báo cáo hay không: BR-04 không phân biệt trạng thái. Mọi báo cáo seed trong tài liệu này đều cho_xu_ly nên không bị ảnh hưởng.
3. Trạng thái trung gian của các báo cáo cho_xu_ly khác trên cùng tin ngay sau khi một báo cáo được xác nhận vi phạm: KV-VP-03 chỉ kiểm sau khi từng báo cáo đã được xử lý.
4. Kết quả cụ thể của bên bị chặn trong KV-CT-10 và của người gửi dùng trùng mã ý định trong KV-BC-10 biến thể (e): chỉ kiểm các điều kiện đã nêu trong ca.
5. Mã lỗi, mã HTTP, tên trường và tên bảng: chờ hợp đồng API, DTO và migration Sprint 2.

## 10. Lịch sử thay đổi

| Ngày | Phiên bản | Nội dung |
|---|---|---|
| 27/11/2026 | 1.0 | Tạo bộ dữ liệu chuẩn và kỳ vọng cho ca gửi báo cáo, xử lý vi phạm và cạnh tranh của Sprint 2; toàn bộ Not Run. |

