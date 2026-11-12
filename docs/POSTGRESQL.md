# Lưu lịch sử phân tích bằng PostgreSQL

Mỗi lần phân tích TF-IDF hoặc AI thành công được lưu vào PostgreSQL. Có thể xem lại JD, điểm, kỹ năng khớp/chưa thấy và tải lại Excel sau khi khởi động lại backend. Danh sách giao diện hiển thị 50 lần gần nhất; giới hạn hiển thị không xóa lịch sử cũ.

## Cách 1: cụm riêng trên Windows

Cài PostgreSQL 18, rồi từ thư mục chứa [pom.xml](../pom.xml), chạy:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\Start-LocalPostgres.ps1
```

[Start-LocalPostgres.ps1](../scripts/Start-LocalPostgres.ps1) sử dụng bộ công cụ PostgreSQL đã cài. Nếu vị trí cài khác mặc định, truyền `-PostgresBin` với thư mục chứa `initdb.exe`, `pg_ctl.exe`, `pg_isready.exe` và `psql.exe`.

Helper tạo cụm dữ liệu riêng trong `.local/postgres`, lắng nghe tại `127.0.0.1:5433`, tạo database/tài khoản `cv_matching` và sinh mật khẩu ngẫu nhiên. Có thể đổi cổng bằng `-Port`; cổng được kiểm tra trước khi khởi động. Helper không dừng hoặc sửa cụm PostgreSQL khác.

Thông tin `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` được ghi vào `.env` theo định dạng Java properties. Backend đọc file này khi chạy từ thư mục project. Không cần sao chép `.env.example` nếu dùng helper.

Kiểm tra trạng thái và dừng cụm riêng:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\Start-LocalPostgres.ps1 -Status
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\Stop-LocalPostgres.ps1
```

[Stop-LocalPostgres.ps1](../scripts/Stop-LocalPostgres.ps1) giữ nguyên dữ liệu và cấu hình để lần sau dùng tiếp. Không xóa `.local/postgres` hoặc `.env` nếu muốn giữ lịch sử.

## Cách 2: PostgreSQL tự quản lý trên Windows, Linux hoặc macOS

Dùng một PostgreSQL 18 đang chạy và tài khoản quản trị của **bạn** để tạo role/database dành riêng cho ứng dụng. Ví dụ kết nối bằng `psql` với cổng của hệ thống đó:

```text
psql -h 127.0.0.1 -p 5432 -U postgres -d postgres
```

Trong phiên `psql`, tạo role có quyền đăng nhập, đặt mật khẩu tương tác rồi tạo database:

```sql
CREATE ROLE cv_matching LOGIN;
\password cv_matching
CREATE DATABASE cv_matching OWNER cv_matching;
```

Lệnh `\password` yêu cầu bạn nhập mật khẩu riêng, không đưa mật khẩu vào câu SQL hoặc lịch sử lệnh. Tài khoản ứng dụng không cần quyền superuser. Nếu role/database đã tồn tại, dùng cấu hình phù hợp thay vì chạy lại lệnh tạo.

Tại thư mục project, sao chép [.env.example](../.env.example) thành `.env`:

```powershell
# PowerShell
Copy-Item .env.example .env
```

```bash
# Linux / macOS
cp .env.example .env
```

Sửa `DB_URL` theo host/cổng/database của bạn; ví dụ với cổng `5432` là `jdbc:postgresql://127.0.0.1:5432/cv_matching`. Đặt `DB_USERNAME` thành role vừa tạo và điền `DB_PASSWORD` bằng mật khẩu riêng. File này được đọc như Java properties, không đặt dấu nháy kiểu shell quanh giá trị.

`.env` và `.local/` đã được bỏ qua trong [.gitignore](../.gitignore). Không đưa thông tin đăng nhập hoặc dữ liệu database vào commit/gói source.

## Chạy ứng dụng

Sau khi database sẵn sàng:

```powershell
mvn spring-boot:run
```

Mở <http://localhost:8080>. Ảnh minh chứng dùng cổng `8081`, được chọn bằng:

```powershell
mvn '-Dspring-boot.run.arguments=--server.port=8081' spring-boot:run
```

Flyway chạy [migration V1](../src/main/resources/db/migration/V1__create_analysis_history.sql) khi khởi động, tạo năm bảng nghiệp vụ và bảng kỹ thuật `flyway_schema_history`. Hibernate dùng chế độ `validate` để kiểm tra cấu trúc, không tự sửa schema.

## Dữ liệu được lưu

| Bảng | Nội dung |
| --- | --- |
| `job_descriptions` | Toàn bộ JD và danh sách kỹ năng yêu cầu theo thứ tự. |
| `analyses` | ID phân tích, thời gian UTC, chế độ `tfidf`/`ai` và liên kết JD. |
| `cv_documents` | ID, tên file, loại nội dung, kích thước và SHA-256 của CV. |
| `matching_results` | Điểm, tương đồng, số kỹ năng và thứ tự kết quả của từng CV. |
| `matching_skills` | Kỹ năng khớp/chưa thấy cùng thứ tự hiển thị. |

Không lưu file CV gốc hoặc toàn văn trích xuất từ CV. Khi mở lịch sử, giao diện khôi phục JD, phương pháp và kết quả; muốn phân tích mới phải chọn lại file. JD và tên file vẫn có thể chứa thông tin cá nhân. Bộ dữ liệu công khai chỉ dùng CV giả lập.

Ứng dụng mặc định chỉ lắng nghe loopback, chưa có đăng nhập/phân quyền. Cấu hình local này cần được bổ sung kiểm soát truy cập và quản trị dữ liệu trước khi dùng trong môi trường nhiều người.

## Xem, tải và xóa lịch sử

Trong **Lịch sử phân tích**, chọn **Làm mới lịch sử**, **Xem kết quả** hoặc **Xóa**. Thao tác xóa có xác nhận; backend xóa lần phân tích và dữ liệu liên quan trong cùng giao dịch. Nếu bản ghi đó đang hiển thị, giao diện dọn phần kết quả và liên kết tải tương ứng.

| API | Chức năng |
| --- | --- |
| `GET /api/analyses?limit=50` | Liệt kê gần nhất; `limit` từ 1 đến 100. |
| `GET /api/analyses/{id}` | Lấy JD và đầy đủ kết quả đã lưu. |
| `DELETE /api/analyses/{id}` | Xóa lần phân tích và dữ liệu liên quan; thành công trả `204`. |
| `GET /api/export/{id}` | Tạo lại XLSX từ kết quả đã lưu. |

Khi database không truy cập được, yêu cầu liên quan trả HTTP `503` và thông báo tiếng Việt, không trả mật khẩu hoặc chi tiết kết nối. Kiểm tra PostgreSQL và `.env` rồi thử lại.

## Kết quả kiểm chứng

Đã kiểm tra ngày 12/11/2026 trên PostgreSQL 18 thật, cổng `5433`, với role ứng dụng không có quyền superuser:

- Flyway V1 thành công và đủ năm bảng nghiệp vụ; xem [ảnh PostgreSQL](images/postgresql.png).
- TF-IDF và BGE-M3 phân tích cùng JD/ba CV mẫu đều trả HTTP `200`.
- Sau restart backend, ID, JD tiếng Việt, thời gian, thứ tự CV/kỹ năng và kết quả vẫn được giữ; tải lại Excel không cần chọn lại CV hoặc gọi model.
- Giao diện xem lịch sử, tự làm mới sau phân tích, xác nhận xóa và dọn kết quả đã xóa hoạt động đúng; xem [ảnh lịch sử](images/history.png).
- Helper đã được kiểm tra khởi động, trạng thái, dừng và khởi động lại, giữ dữ liệu/thông tin kết nối.
- Bộ Java test đạt 27/27, gồm khôi phục sau restart, lưu hơn 50 lần phân tích, xóa liên quan và rollback. Xem [báo cáo kiểm thử](TEST_RESULTS.md).

Số bản ghi thay đổi theo mỗi lần phân tích/xóa; repository không phát hành database hoặc lịch sử của máy phát triển. H2 chỉ dùng cho kiểm thử tự động, backend chạy bình thường dùng PostgreSQL.
