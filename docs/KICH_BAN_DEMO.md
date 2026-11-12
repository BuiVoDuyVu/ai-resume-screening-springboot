# Kịch bản demo 4–6 phút

## Chuẩn bị trước buổi demo

- Dùng JDK 25 và Maven 3.9+; cấu hình PostgreSQL theo [POSTGRESQL.md](POSTGRESQL.md).
- Tải `bge-m3` trước buổi demo bằng `ollama pull bge-m3`; kiểm tra `ollama list` và giữ Ollama đang chạy.
- Chạy `mvn spring-boot:run`, mở <http://localhost:8080>.
- Nếu cần dùng cổng `8081` như ảnh minh chứng, chạy `mvn '-Dspring-boot.run.arguments=--server.port=8081' spring-boot:run` và mở <http://localhost:8081>.
- Chuẩn bị toàn bộ [JD mẫu](../demo/job_description.txt) cùng [ba CV giả lập](../demo/); không dùng CV thật có thông tin cá nhân.
- Chạy trước `mvn --batch-mode --no-transfer-progress verify` để có kết quả 27 test. Bộ test dùng H2 và HTTP stub, không gọi model thật.

## Trình bày

| Thời gian | Thao tác và nội dung |
| --- | --- |
| 30 giây | Giới thiệu bài toán đối chiếu CV/JD và hai phương pháp TF-IDF, embedding. |
| 45 giây | Dán đầy đủ JD, chọn ba CV TXT mẫu. Chỉ ra giới hạn 1–10 file, tối đa 5 MB/file và các định dạng TXT/PDF/DOCX. |
| 60 giây | Chọn TF-IDF và phân tích. Bảng dự kiến: Backend 65,2; Data 28,2; Frontend 25,3. Giải thích kỹ năng khớp/chưa thấy. |
| 60 giây | Chọn AI BGE-M3, giữ nguyên dữ liệu và chạy lại. Kết quả mẫu: Backend 90,4; Frontend 56,9; Data 49,7. Điểm tăng không tự chứng minh mô hình chính xác hơn. |
| 30 giây | Tải XLSX và mở báo cáo. Chỉ ra cột điểm, tương đồng và kỹ năng. |
| 45 giây | Mở lịch sử, xem lại một lần phân tích và tải lại Excel. Giải thích dữ liệu được lưu trong PostgreSQL và vẫn có sau restart. |
| 30 giây | Trình bày API `POST /api/analyze` và kết quả kiểm thử; dùng [collection Postman](postman/AI-CV-Matching.postman_collection.json) nếu cần demo request. |
| 30 giây | Nêu giới hạn: PDF scan cần OCR, chưa xác thực, kết quả chỉ hỗ trợ người đánh giá và chưa đo quy trình thủ công. |

## Câu hỏi thường gặp

- **Vì sao hai CV cùng khớp 3/8 kỹ năng nhưng điểm khác?** Điểm còn có 60% đóng góp từ độ tương đồng văn bản.
- **TF-IDF và embedding khác nhau thế nào?** TF-IDF dựa trên phân bố từ; BGE-M3 biểu diễn văn bản bằng vector do model tạo. Java tính cosine và kết hợp tỷ lệ kỹ năng cho cả hai.
- **Có cần Python không?** Backend Java gọi Ollama qua HTTP, không cần microservice Python.
- **Ollama tắt thì sao?** Chế độ AI trả HTTP `503` và hướng dẫn thử lại/chọn TF-IDF; không trả kết quả AI giả.
- **Dữ liệu được lưu thế nào?** Lưu JD, metadata CV, kết quả và kỹ năng trong năm bảng PostgreSQL; không lưu file CV gốc hoặc toàn văn CV.
- **Có thể dùng trong tuyển dụng thực tế ngay không?** Cần bổ sung xác thực, quản trị dữ liệu và đánh giá chất lượng/thiên lệch; điểm demo không thay quyết định của con người.

Xem [ảnh minh chứng và kết quả thực nghiệm](AI_VALIDATION.md), [bộ kiểm thử](TEST_RESULTS.md) và [báo cáo PDF](report/Bao_cao_Option2_Java_AI_CV_Matching.pdf).
