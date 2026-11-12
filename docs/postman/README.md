# Kiểm tra API bằng Postman

1. Khởi động backend bằng task `Spring Boot: run AI CV Matching` trong VS Code. Collection dùng `http://localhost:8081`, tương ứng với task này. Nếu chạy `mvn spring-boot:run` trực tiếp với cấu hình mặc định, đổi biến collection `baseUrl` thành `http://localhost:8080` như trong hình hướng dẫn.
2. Trong Postman, chọn **Import** rồi mở [AI-CV-Matching.postman_collection.json](./AI-CV-Matching.postman_collection.json).
3. Đặt **Working directory** của Postman là thư mục project `AI_CV_Matching_Java_SpringBoot` (thư mục chứa [pom.xml](../../pom.xml) và [demo/](../../demo/)). Nếu Postman không đọc được đường dẫn tương đối, mở request đầu tiên, vào **Body → form-data** và chọn lại ba file [CV_01_Backend.txt](../../demo/CV_01_Backend.txt), [CV_02_Frontend.txt](../../demo/CV_02_Frontend.txt), [CV_03_Data.txt](../../demo/CV_03_Data.txt).
4. Gửi request **1. Phân tích JD và 3 CV mẫu**. `jobDescription` đã chứa toàn bộ JD mẫu từ [job_description.txt](../../demo/job_description.txt); trường này và `mode=tfidf` phải có kiểu **Text**. Ba dòng `files` đều có kiểu **File** và giữ nguyên cùng tên `files`. Không tự thêm header `Content-Type`: Postman tạo multipart boundary khi gửi file.
5. Kiểm tra HTTP `200`, ba kết quả CV và các kiểm tra trong **Test Results**. Script tự lưu `analysisId` vào biến collection để request tiếp theo sử dụng.
6. Gửi request **2. Tải báo cáo Excel**. Khi nhận HTTP `200` và kiểu nội dung XLSX, dùng **Send and Download** hoặc **Save Response → Save to a file** để lưu thành `cv_matching_result.xlsx`, rồi mở bằng Excel hoặc LibreOffice.

Backend lưu lịch sử trong PostgreSQL. Sau khi khởi động lại backend, `analysisId` đã lưu vẫn dùng được để tải Excel; ID chỉ mất hiệu lực khi lần phân tích bị xóa. Có thể lấy lại ID qua `GET {{baseUrl}}/api/analyses?limit=50` hoặc mục Lịch sử phân tích trên giao diện. Nếu cổng `8081` không kết nối được, kiểm tra backend đang chạy, PostgreSQL sẵn sàng và biến `baseUrl` khớp cổng trong terminal. Xem [hướng dẫn PostgreSQL](../POSTGRESQL.md).

## Kiểm tra AI BGE-M3 (tùy chọn)

1. Cài Ollama, giữ Ollama đang chạy và tải model bằng `ollama pull bge-m3` (khoảng 1,2 GB). Dùng `ollama list` để kiểm tra model đã có. Nếu chạy Ollama bằng CLI riêng, mở một terminal chạy `ollama serve`; nếu ứng dụng Ollama đã phục vụ API thì giữ phiên đó hoạt động.
2. Biến collection `ollamaUrl` mặc định là `http://127.0.0.1:11434`. Gửi **3. Kiểm tra embedding Ollama BGE-M3**: request JSON chứa model `bge-m3` và hai chuỗi tiếng Việt. Kết quả cần HTTP `200`, hai embedding không rỗng, cùng số chiều; xem **Test Results**. Lần gọi đầu tiên có thể mất thêm thời gian để nạp model.
3. Giữ backend đang chạy rồi gửi **4. Phân tích JD và 3 CV bằng AI BGE-M3**. Nếu cần, chọn lại ba file CV như với request 1. Request này giữ nguyên JD và CV mẫu, đổi trường **Text** `mode` thành `ai`; các trường `files` vẫn là **File**. Không thêm header multipart `Content-Type` thủ công.
4. Kiểm tra HTTP `200`, `mode=ai`, ba kết quả CV và `analysisId` hợp lệ. Script thay biến collection `analysisId` bằng ID của lần phân tích AI. Gửi tiếp **2. Tải báo cáo Excel** và lưu file để lấy báo cáo AI mới nhất. Thứ tự thao tác cho AI là **3 → 4 → 2**.

Request 3 gọi trực tiếp Ollama; request 4 gọi backend, backend dùng cấu hình Ollama riêng của ứng dụng. Nếu đổi địa chỉ dịch vụ Ollama, cần điều chỉnh cả `ollamaUrl` trong collection và cấu hình backend tương ứng. Nếu Ollama chưa chạy hoặc thiếu model, cần xử lý dịch vụ/model trước khi gửi lại request AI.
