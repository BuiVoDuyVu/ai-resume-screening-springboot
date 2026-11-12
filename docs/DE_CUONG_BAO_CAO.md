# Đề cương và phạm vi báo cáo đồ án

**Đề tài:** Xây dựng hệ thống tự động hóa so khớp CV và mô tả công việc bằng Java Spring Boot và mô hình embedding AI.

**Người thực hiện:** Bùi Võ Duy Vũ. **Giảng viên hướng dẫn:** Thầy Đức Đạt. **Học phần/lớp:** CS_DATA_17.

Bản hoàn chỉnh: [PDF](report/Bao_cao_Option2_Java_AI_CV_Matching.pdf) và [Word](report/Bao_cao_Option2_Java_AI_CV_Matching.docx). Tài liệu này tóm tắt bố cục, kết quả có minh chứng và các giới hạn của đồ án.

## Bố cục

1. **Giới thiệu:** vấn đề đọc và đối chiếu từng CV, mục tiêu và phạm vi tự động hóa.
2. **Cơ sở công nghệ:** Java 25, Spring Boot, PDFBox, Apache POI, TF-IDF, cosine similarity, Ollama BGE-M3, PostgreSQL, JPA và Flyway.
3. **Yêu cầu và kiến trúc:** luồng nhập JD/CV, API, xử lý lỗi, xuất Excel và lưu lịch sử; [sơ đồ kiến trúc](images/architecture.png).
4. **Thiết kế và cài đặt:** parser, nhận diện kỹ năng, hai phương pháp tính tương đồng, công thức điểm và năm bảng nghiệp vụ.
5. **Vận hành:** cấu hình PostgreSQL, chạy Maven, tải model và dùng API.
6. **Kiểm thử:** 27 test tự động, đối chiếu dữ liệu mẫu, lỗi đầu vào, lỗi AI và tính toàn vẹn khi lưu/xóa/khởi động lại.
7. **Thực nghiệm:** điểm của ba CV giả lập và thời gian thực đo trên cùng dữ liệu.
8. **Giới hạn và hướng phát triển:** chưa OCR, chưa đăng nhập/phân quyền, cần đánh giá chất lượng trên dữ liệu được cho phép sử dụng và cần người duyệt.
9. **Tài liệu tham khảo và phụ lục:** API, hướng dẫn demo và ảnh minh chứng.

## Kết quả đã kiểm chứng

| Hạng mục | Kết quả |
| --- | --- |
| TF-IDF với JD đầy đủ và ba CV mẫu | Backend 65,2; Data 28,2; Frontend 25,3 điểm. |
| BGE-M3 với cùng dữ liệu | Backend 90,4; Frontend 56,9; Data 49,7 điểm. |
| Kiểm thử Java | 27/27 đạt; coverage dòng đo bằng JaCoCo: 95,70%. |
| PostgreSQL | Đã tích hợp năm bảng nghiệp vụ, migration Flyway, xem/xóa lịch sử và tải lại Excel sau restart. |
| Thời gian từ gửi multipart đến nhận đủ XLSX, trung bình ba lần | TF-IDF 1,9303 giây; AI 10,0966 giây. Không gồm chọn file thủ công hoặc tải model. |
| Quy trình thủ công | Chưa đo; không suy ra hệ số tăng tốc so với con người. |

Chi tiết và điều kiện đo: [TEST_RESULTS.md](TEST_RESULTS.md), [AI_VALIDATION.md](AI_VALIDATION.md) và [POSTGRESQL.md](POSTGRESQL.md). Đây là kết quả trên dữ liệu giả lập, không phải đánh giá độ chính xác tuyển dụng trên dữ liệu thực.

## Minh chứng

- [Kết quả TF-IDF](images/tfidf-results.png) và [AI BGE-M3](images/ai-results.png).
- [Xem lại lịch sử](images/history.png) và [schema PostgreSQL](images/postgresql.png).
- [Báo cáo Excel](images/excel-preview.png).
- [API và bộ kiểm thử](images/api-tests.png).

Ảnh API/kiểm thử ghi lại kết quả HTTP và log kiểm chứng; không được mô tả thành ảnh giao diện Postman nếu không chụp từ Postman. Tên file, JD và CV trong demo đều là dữ liệu giả lập.
