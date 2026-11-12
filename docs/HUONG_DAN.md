# Hướng dẫn chạy và tìm hiểu ứng dụng

Ứng dụng đối chiếu CV với mô tả công việc (JD), hiển thị điểm và kỹ năng khớp/chưa thấy, xuất Excel và lưu lịch sử vào PostgreSQL. Hai phương pháp so khớp là TF-IDF và embedding BGE-M3 qua Ollama.

## 1. Chuẩn bị

- JDK 25 LTS và Maven 3.9+; kiểm tra bằng `java -version` và `mvn -version`.
- PostgreSQL 18 cho ứng dụng chạy thực tế.
- Ollama và model `bge-m3` nếu dùng chế độ AI; TF-IDF không cần Ollama.
- Tùy chọn: Postman để thử API, Excel hoặc phần mềm đọc XLSX để mở báo cáo.

Mở terminal tại thư mục chứa [pom.xml](../pom.xml). Bộ dữ liệu trong [demo](../demo/) gồm một JD và ba CV **giả lập**.

## 2. Khởi động PostgreSQL

Trên Windows có PostgreSQL đã cài, có thể dùng helper tạo cụm riêng tại `127.0.0.1:5433`:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\Start-LocalPostgres.ps1
```

Helper tạo database, tài khoản và mật khẩu ngẫu nhiên, rồi ghi cấu hình vào `.env`. Để dùng PostgreSQL đang có hoặc hệ điều hành khác, sao chép [.env.example](../.env.example) thành `.env` và cấu hình thông tin kết nối của bạn theo [hướng dẫn PostgreSQL](POSTGRESQL.md). Không đưa `.env` hoặc thư mục dữ liệu database lên Git.

## 3. Chạy backend và TF-IDF

```powershell
mvn spring-boot:run
```

Mở <http://localhost:8080>. Để tái hiện cổng `8081` trong ảnh minh chứng, dùng:

```powershell
mvn '-Dspring-boot.run.arguments=--server.port=8081' spring-boot:run
```

1. Dán **toàn bộ** nội dung [JD mẫu](../demo/job_description.txt).
2. Chọn ba CV TXT trong [demo](../demo/).
3. Chọn **TF-IDF**, nhấn **Phân tích CV**.
4. Xem điểm, kỹ năng khớp/chưa thấy và tải Excel.

Với đúng JD/CV mẫu, điểm TF-IDF theo thứ tự giảm dần là Backend **65,2**, Data **28,2**, Frontend **25,3**. JD ngắn mặc định trên giao diện có thể cho điểm khác; xem [kết quả kiểm thử](TEST_RESULTS.md).

## 4. Chạy AI BGE-M3

Khi Ollama đang chạy, tải model một lần:

```powershell
ollama pull bge-m3
ollama list
```

Chọn **AI BGE-M3** và phân tích lại cùng JD/CV. Java gửi JD và CV tới `http://127.0.0.1:11434/api/embed`, nhận vector embedding rồi tính cosine similarity. Kết quả kiểm chứng trên ba CV mẫu là Backend **90,4**, Frontend **56,9**, Data **49,7**.

Khi Ollama/model không sẵn sàng, API trả HTTP `503` kèm hướng dẫn thử lại hoặc chọn TF-IDF. Ứng dụng không tự đổi phương pháp và gắn nhãn kết quả thành AI. Xem [kiểm chứng AI và số đo thời gian](AI_VALIDATION.md).

## 5. Dùng lịch sử và Excel

Mỗi lần phân tích thành công được lưu vào PostgreSQL. Trong **Lịch sử phân tích**, có thể làm mới danh sách, xem lại JD/kết quả, tải lại Excel và xóa một lần phân tích. Giới hạn danh sách không xóa lịch sử cũ.

Lịch sử giữ nguyên sau khi khởi động lại backend. Chỉ lưu metadata CV và kết quả; không lưu file CV gốc hoặc toàn văn trích xuất. Vì vậy, muốn phân tích mới phải chọn lại file. Xem [mô hình dữ liệu và API lịch sử](POSTGRESQL.md).

## 6. Hiểu cách tính điểm

- TXT đọc UTF-8; PDF dùng PDFBox; DOCX dùng Apache POI. PDF scan chưa có OCR sẽ bị từ chối với thông báo rõ ràng.
- Từ điển kỹ năng và alias xác định kỹ năng được yêu cầu trong JD, sau đó tìm chúng trong CV.
- TF-IDF tính cosine trên vector từ; BGE-M3 tính cosine trên embedding.
- Cả hai chế độ dùng `score = 100 × (0,6 × S + 0,4 × K)`, với `S` giới hạn trong `[0,1]` và `K` là tỷ lệ kỹ năng khớp.

Điểm giúp đối chiếu tài liệu, không phải xác suất ứng viên phù hợp hoặc quyết định tuyển dụng.

## 7. Thử API

Nhập [Postman collection](postman/AI-CV-Matching.postman_collection.json) theo [hướng dẫn](postman/README.md). Cổng của `baseUrl` phải trùng cổng backend đang chạy.

| Phương thức và đường dẫn | Chức năng |
| --- | --- |
| `POST /api/analyze` | Multipart: `jobDescription`, `mode=tfidf` hoặc `ai`, một hay nhiều trường `files`. |
| `GET /api/export/{id}` | Tải báo cáo XLSX. |
| `GET /api/analyses?limit=50` | Xem lịch sử gần nhất. |
| `GET /api/analyses/{id}` | Lấy JD và kết quả đã lưu. |
| `DELETE /api/analyses/{id}` | Xóa lần phân tích và dữ liệu liên quan. |

## 8. Chạy kiểm thử

```powershell
mvn --batch-mode --no-transfer-progress verify
```

Bộ kiểm thử có **27 test** và dùng H2 cùng HTTP stub cục bộ, không cần PostgreSQL hoặc model Ollama. Các nhóm kiểm tra gồm parser TXT/PDF/DOCX, kỹ năng, TF-IDF, multipart HTTP, Excel, lỗi đầu vào/AI và lưu trữ. Kết quả ghi nhận: **27 đạt, không lỗi hoặc bỏ qua**; coverage dòng **95,70%** khi đo riêng bằng JaCoCo. Xem [cách đo lại](TEST_RESULTS.md).

## 9. Khắc phục nhanh

| Hiện tượng | Cách xử lý |
| --- | --- |
| `UnsupportedClassVersionError` | Kiểm tra `java -version` và `mvn -version` đều dùng Java 25; dừng backend cũ bằng `Ctrl+C`, rồi chạy lại. |
| Cổng HTTP đang được dùng | Dừng tiến trình của ứng dụng cũ hoặc chạy với cổng `8081` bằng lệnh ở trên. |
| Không truy cập PostgreSQL / HTTP `503` | Kiểm tra database đang chạy, địa chỉ/cổng/tài khoản trong `.env`; xem [POSTGRESQL.md](POSTGRESQL.md). |
| AI không phân tích được | Kiểm tra Ollama đang chạy và `ollama list` có `bge-m3`; thử TF-IDF để kiểm tra luồng ứng dụng. |
| Điểm khác bảng minh chứng | Dùng toàn bộ JD và đúng ba CV mẫu, cùng phương pháp; không thay nội dung giữa các lần đối chiếu. |

## 10. Tài liệu và minh chứng

- [Kịch bản demo](KICH_BAN_DEMO.md)
- [Đề cương và phạm vi báo cáo](DE_CUONG_BAO_CAO.md)
- [Báo cáo PDF](report/Bao_cao_Option2_Java_AI_CV_Matching.pdf)
- [Kết quả TF-IDF](images/tfidf-results.png), [AI](images/ai-results.png), [lịch sử](images/history.png), [kiểm thử/API](images/api-tests.png)
