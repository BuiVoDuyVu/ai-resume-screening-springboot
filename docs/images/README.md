# Hình minh chứng

Các hình được chuẩn bị từ lần kiểm chứng ngày 12/11/2026 với [JD](../../demo/job_description.txt) và ba CV giả lập. Ứng dụng trong ảnh sử dụng cổng `8081`; lệnh Maven mặc định sử dụng cổng `8080`.

| Hình | Nguồn và nội dung |
| --- | --- |
| [Nhập dữ liệu](input-form.png) | Ảnh chụp trực tiếp biểu mẫu JD và ba CV được chọn |
| [Kết quả TF-IDF](tfidf-results.png) | Ảnh chụp trực tiếp kết quả: 65,2 / 28,2 / 25,3 |
| [Kết quả AI](ai-results.png) | Ảnh chụp trực tiếp kết quả BGE-M3: 90,4 / 56,9 / 49,7 |
| [Lịch sử](history.png) | Ảnh chụp trực tiếp hai lần phân tích demo được lưu trong PostgreSQL |
| [Kiến trúc](architecture.png) | Sơ đồ mô tả luồng xử lý và persistence theo mã nguồn |
| [API và kiểm thử](api-tests.png) | Trang trình bày dữ liệu từ JSON và log Maven đã ghi nhận; không phải giao diện Postman |
| [Excel](excel-preview.png) | Bản xem trước dữ liệu đọc từ hai workbook XLSX đã xuất; không phải ảnh giao diện Microsoft Excel |
| [PostgreSQL](postgresql.png) | Trang trình bày đầu ra psql đã ghi nhận, migration V1 và năm bảng nghiệp vụ |

Số liệu và phạm vi kiểm chứng được giải thích trong [kết quả kiểm thử](../TEST_RESULTS.md), [kiểm chứng AI](../AI_VALIDATION.md) và [báo cáo](../report/Bao_cao_Option2_Java_AI_CV_Matching.pdf).
