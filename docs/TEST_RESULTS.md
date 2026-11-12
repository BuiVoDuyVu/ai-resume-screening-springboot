# Kết quả kiểm thử và đối chiếu TF-IDF

Ghi nhận ngày 12/11/2026 sau khi tích hợp PostgreSQL. Môi trường: Temurin Java 25.0.5+8, Maven 3.9.16, JUnit Jupiter 6.0.3 và Maven Surefire 3.5.6.

## Bộ kiểm thử

**BUILD SUCCESS — 27 test, 0 thất bại, 0 lỗi, 0 bị bỏ qua.**

| Nhóm kiểm thử | Số test | Kết quả |
| --- | --- | --- |
| AnalysisValidationTest | 8 | Đạt |
| JavaRuntimeCompatibilityTest | 5 | Đạt |
| OllamaServiceTest | 6 | Đạt |
| PersistenceIntegrationTest | 4 | Đạt |
| SkillServiceTest | 3 | Đạt |
| TfidfServiceTest | 1 | Đạt |
| **Tổng** | **27** | **100% đạt** |

Mã kiểm thử nằm trong [src/test](../src/test/java/com/example/cvmatching/). [AnalysisValidationTest](../src/test/java/com/example/cvmatching/AnalysisValidationTest.java) kiểm tra đầu vào và HTTP; [OllamaServiceTest](../src/test/java/com/example/cvmatching/OllamaServiceTest.java) dùng HTTP stub; [PersistenceIntegrationTest](../src/test/java/com/example/cvmatching/PersistenceIntegrationTest.java) kiểm tra lưu trữ. Xem [ảnh API/kiểm thử](images/api-tests.png).

## Lưu trữ và lịch sử

- Đóng/mở lại Spring context vẫn khôi phục cùng ID, JD tiếng Việt, thời gian, thứ tự CV/kỹ năng và báo cáo Excel; kiểm tra cả hai CV trùng tên.
- Lưu 51 lần phân tích không làm mất bản ghi cũ; giới hạn danh sách chỉ áp dụng khi hiển thị.
- Xóa một lần phân tích dọn dữ liệu liên quan ở năm bảng và giữ nguyên lần phân tích khác.
- Cố ý tạo metadata không hợp lệ xác nhận rollback toàn bộ giao dịch. Cảnh báo SQL `22001` trong test này là tình huống có chủ đích.
- HTTP test kiểm tra danh sách, chi tiết, giới hạn không hợp lệ, xóa và không thể tải Excel của bản ghi đã xóa.
- Test tự động dùng H2 riêng. Lưu trữ thực tế đã được kiểm chứng trên PostgreSQL 18 và backend khởi động lại thật; xem [POSTGRESQL.md](POSTGRESQL.md#kết-quả-kiểm-chứng).

## Đối chiếu ba CV mẫu

Gọi `POST /api/analyze` với toàn bộ [JD mẫu](../demo/job_description.txt), ba CV giả lập và `mode=tfidf`. Kết quả khớp [bảng tham chiếu](../demo/java_tfidf_reference.csv):

| Hồ sơ | Kỹ năng khớp | Tương đồng TF-IDF | Điểm kỹ thuật /100 |
| --- | --- | --- | --- |
| Backend | 8/8 | 42,0% | 65,2 |
| Data | 3/8 | 21,9% | 28,2 |
| Frontend | 3/8 | 17,2% | 25,3 |

Ảnh [kết quả TF-IDF](images/tfidf-results.png) dùng backend tại `http://localhost:8081`; cổng này được chọn bằng `mvn '-Dspring-boot.run.arguments=--server.port=8081' spring-boot:run`. Lệnh `mvn spring-boot:run` mặc định dùng `8080`.

Không điều chỉnh thuật toán hoặc trọng số để ép điểm trùng chương trình Python khác. Thay JD đầy đủ bằng đoạn mặc định ngắn hơn trên giao diện có thể cho điểm khác.

## Các tình huống lỗi và biên

| Tình huống | Kết quả đã xác minh |
| --- | --- |
| JD rỗng | HTTP `400`, yêu cầu nhập JD. |
| CV định dạng JPG | HTTP `400`, chỉ nhận TXT/PDF/DOCX. |
| PDF chỉ chứa ảnh chữ, không có lớp văn bản | HTTP `400`, giải thích cần OCR. |
| Không kết nối được Ollama khi chọn AI | HTTP `503`, hướng dẫn chạy Ollama/tải BGE-M3 hoặc chọn TF-IDF. |
| CV thiếu toàn bộ kỹ năng JD | HTTP `200`, danh sách khớp rỗng và liệt kê đủ kỹ năng chưa thấy. |
| File CV rỗng | HTTP `400`, thông báo dung lượng không hợp lệ. |
| Gửi 11 CV | HTTP `400`, giới hạn 1–10 CV. |
| JD dài hơn 10.000 ký tự | HTTP `400`, thông báo giới hạn JD. |

CV thiếu kỹ năng vẫn là tài liệu hợp lệ. Điểm còn phụ thuộc độ tương đồng: test có từ chung ngoài danh sách kỹ năng xác nhận phần cosine vẫn đóng góp đúng tỷ trọng 60%.

PDF scan kiểm thử được tạo bằng PDFBox với ảnh raster chứa chữ “Java Docker”. Parser không đọc được chữ trong ảnh khi chưa có OCR.

Lỗi Ollama được tái hiện bằng endpoint loopback không có dịch vụ lắng nghe, trong Spring context riêng. HTTP stub kiểm tra payload tiếng Việt, thứ tự JD/CV, cosine với vector đã biết, vector ngược/zero, HTTP lỗi, JSON sai, số lượng/số chiều embedding sai và kết nối bị từ chối. Bộ test không cần model hoặc dịch vụ Ollama thật.

Các test khác xác nhận đọc TXT tiếng Việt/BOM, PDF có chữ, DOCX, XLSX có autosize, khởi động Spring Boot, multipart HTTP, nhận diện Java không nhầm JavaScript và TF-IDF.

## Độ bao phủ đã ghi nhận

Đo riêng bằng JaCoCo 0.8.14 với cùng bộ kiểm thử:

| Chỉ số | Đã phủ / tổng | Tỷ lệ |
| --- | --- | --- |
| Dòng mã | 289/302 | 95,70% |
| Lệnh bytecode | 2.070/2.178 | 95,04% |
| Nhánh | 117/142 | 82,39% |

Đây là kết quả của lần đo đã ghi nhận, không phải ngưỡng bắt buộc trong Maven hoặc CI. Số liệu có thể thay đổi khi mã nguồn/bộ test thay đổi.

## Chạy lại

Từ thư mục chứa [pom.xml](../pom.xml), xác nhận `java -version` và `mvn -version` đều dùng Java 25:

```powershell
mvn --batch-mode --no-transfer-progress verify
```

Lệnh trên biên dịch, chạy test và đóng gói ứng dụng. Test dùng H2 và HTTP stub cục bộ, không cần cấu hình PostgreSQL hoặc tải BGE-M3. GitHub Actions dùng cùng lệnh trên Temurin 25.

Để tạo coverage mà không cần cài JaCoCo vào cấu hình Maven của project:

```powershell
mvn --batch-mode --no-transfer-progress org.jacoco:jacoco-maven-plugin:0.8.14:prepare-agent test org.jacoco:jacoco-maven-plugin:0.8.14:report
```

Sau khi chạy thành công, mở `target/site/jacoco/index.html`; báo cáo test nằm trong `target/surefire-reports/`. Đây là file build được tạo tại máy chạy, không được commit vào repository.

Nếu Java agent trên Windows không khởi động được do đường dẫn chứa dấu hoặc khoảng trắng, thử clone/copy project vào một thư mục chỉ gồm ký tự ASCII rồi chạy lại lệnh coverage. Không cần hard-code vị trí JDK, Maven cache hoặc đường dẫn Java agent của máy khác.

Kiểm chứng AI với model thật, số đo thời gian và giới hạn của phép đo được ghi riêng trong [AI_VALIDATION.md](AI_VALIDATION.md).
