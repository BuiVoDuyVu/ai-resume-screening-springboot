# Kiểm chứng BGE-M3 và so sánh với TF-IDF

Kết quả dưới đây được đo ngày 12/11/2026 bằng Ollama, backend và PostgreSQL đang chạy thật, với một JD và ba CV giả lập. Đây là phép kiểm chứng chức năng và quan sát thời gian trên một máy, không phải đánh giá chất lượng tuyển dụng.

## Môi trường

| Thành phần | Cấu hình được ghi nhận |
| --- | --- |
| Hệ điều hành | Windows 11 Home Single Language, 64-bit. |
| CPU / RAM | Intel Core i7-1185G7 @ 3,00 GHz, 4 nhân / 8 luồng; 15,73 GB RAM. |
| Java / Maven | Temurin 25.0.5+8 / Maven 3.9.16. |
| Backend / database | Spring Boot 4.1.2 / PostgreSQL 18.0. |
| Ollama | 0.40.16, API tại `http://127.0.0.1:11434`. |
| Model | `bge-m3:latest`, GGUF F16, khoảng 566,70 triệu tham số. |
| Dung lượng model | 1.157.672.605 byte, khoảng 1,16 GB. |
| Vector embedding | 1.024 chiều. |

Backend trong ảnh minh chứng dùng `http://localhost:8081`, chạy bằng `mvn '-Dspring-boot.run.arguments=--server.port=8081' spring-boot:run`. Lệnh Maven không có tham số đổi cổng dùng `http://localhost:8080`.

## Kiểm tra API embedding

Khi Ollama đang chạy và `ollama list` có `bge-m3`, gửi `POST http://127.0.0.1:11434/api/embed` với `Content-Type: application/json`:

```json
{
  "model": "bge-m3",
  "input": [
    "Mô tả công việc Backend Developer",
    "CV ứng viên Backend Developer"
  ]
}
```

Lần kiểm tra thực tế trả đủ hai vector 1.024 chiều với các phần tử hữu hạn; cosine giữa hai câu là **0,785745**. Backend gửi JD trước, các CV theo sau, rồi tính cosine giữa vector JD và từng CV.

## Kết quả trên cùng JD và ba CV

Sử dụng toàn bộ [job_description.txt](../demo/job_description.txt) và ba file [CV_01_Backend.txt](../demo/CV_01_Backend.txt), [CV_02_Frontend.txt](../demo/CV_02_Frontend.txt), [CV_03_Data.txt](../demo/CV_03_Data.txt). Cả hai phương pháp gọi `POST /api/analyze` với cùng dữ liệu multipart; chỉ đổi `mode` giữa `tfidf` và `ai`.

| CV | Kỹ năng khớp | TF-IDF: tương đồng | TF-IDF: điểm /100 | BGE-M3: tương đồng | BGE-M3: điểm /100 |
| --- | --- | --- | --- | --- | --- |
| Backend | 8/8 | 42,0% | 65,2 | 84,1% | 90,4 |
| Frontend | 3/8 | 17,2% | 25,3 | 69,8% | 56,9 |
| Data | 3/8 | 21,9% | 28,2 | 57,8% | 49,7 |

Cả hai chế độ dùng:

`score = 100 × (0,6 × S + 0,4 × K)`

`S` là cosine similarity đã chặn trong `[0,1]`; `K` là số kỹ năng khớp chia cho số kỹ năng yêu cầu. Backend tính từ cosine gốc rồi làm tròn một chữ số thập phân để hiển thị; tính lại từ cột tương đồng đã làm tròn có thể lệch nhẹ.

Các lần gọi trả HTTP `200`, đủ ba kết quả và tám kỹ năng. Giao diện xác nhận phân tích thành công; XLSX tải được và lịch sử được lưu trong PostgreSQL. Xem [ảnh TF-IDF](images/tfidf-results.png), [ảnh AI](images/ai-results.png), [Excel](images/excel-preview.png) và [lịch sử](images/history.png).

## Thời gian thực đo trên backend có PostgreSQL

Mỗi chế độ chạy ba lần với cùng JD và ba CV. Cột **phân tích** tính từ gửi multipart đến nhận JSON; cột **đến Excel** tính từ bắt đầu gửi multipart đến nhận đủ nội dung XLSX của lần phân tích đó. Phép đo gồm xử lý HTTP, phân tích, lưu database và tạo/tải Excel ở cột cuối; không gồm thao tác chọn file của người dùng hoặc tải model.

| Lần | Chế độ | Phân tích (giây) | Đến Excel (giây) |
| --- | --- | --- | --- |
| 1 | TF-IDF | 1,5085 | 4,1797 |
| 2 | TF-IDF | 0,3838 | 0,9155 |
| 3 | TF-IDF | 0,3009 | 0,6958 |
| 1 | AI BGE-M3 | 14,6866 | 15,4055 |
| 2 | AI BGE-M3 | 7,0289 | 7,3594 |
| 3 | AI BGE-M3 | 7,1636 | 7,5248 |

| Chế độ | Trung bình đến Excel | Nhỏ nhất | Lớn nhất |
| --- | --- | --- | --- |
| TF-IDF | **1,9303 giây** | 0,6958 giây | 4,1797 giây |
| AI BGE-M3 | **10,0966 giây** | 7,3594 giây | 15,4055 giây |

Các lần đầu có thể gồm chi phí nạp model, JIT hoặc bộ nhớ đệm; phép đo không tách riêng chi phí này. Ba lần chạy trên một máy chưa đủ để suy ra độ trễ điển hình, khả năng chịu tải hoặc so sánh trên phần cứng khác. **Chưa đo quy trình thủ công**, vì vậy không tính hệ số tăng tốc so với con người.

Hai phương pháp có thang tương đồng khác nhau. Điểm AI cao hơn không tự chứng minh chất lượng tốt hơn; điểm chưa được hiệu chuẩn và không phải xác suất phù hợp hoặc quyết định tuyển dụng.

## Tái hiện

1. Cấu hình PostgreSQL theo [hướng dẫn](POSTGRESQL.md) và chạy backend.
2. Giữ Ollama đang chạy; dùng `ollama pull bge-m3` nếu chưa có model.
3. Dán đầy đủ JD mẫu, chọn đúng ba CV và chạy lần lượt TF-IDF, AI BGE-M3 trên giao diện.
4. Tải XLSX của mỗi kết quả; mở lại trong lịch sử để kiểm tra lưu trữ.
5. Nếu dùng Postman, nhập [collection](postman/AI-CV-Matching.postman_collection.json); đặt `baseUrl` trùng cổng backend. Request 3 kiểm tra embedding, request 4 phân tích AI, request 2 tải Excel. Xem [hướng dẫn Postman](postman/README.md).

Lỗi kết nối Ollama, HTTP lỗi và embedding không hợp lệ được kiểm tra riêng bằng HTTP stub trong [bộ test](TEST_RESULTS.md). Khi AI không sẵn sàng, backend trả HTTP `503` và hướng dẫn chọn TF-IDF; ứng dụng không tự gắn nhãn AI cho kết quả TF-IDF.
