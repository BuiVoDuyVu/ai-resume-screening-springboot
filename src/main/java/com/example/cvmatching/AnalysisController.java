package com.example.cvmatching;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.dao.DataAccessException;
import java.util.*;

@RestController
@RequestMapping("/api")
public class AnalysisController {
    private final AnalysisService service;
    private final ExcelReportService excel;
    private final AnalysisPersistence persistence;
    public AnalysisController(AnalysisService service,ExcelReportService excel,AnalysisPersistence persistence){
        this.service=service;this.excel=excel;this.persistence=persistence;
    }
    @PostMapping(value="/analyze",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public AnalysisResponse analyze(@RequestParam("jobDescription") String jd,
                                    @RequestParam("files") MultipartFile[] files,
                                    @RequestParam(defaultValue="tfidf") String mode){
        return service.analyze(jd,files,mode);
    }
    @GetMapping("/export/{id}")
    public ResponseEntity<byte[]> export(@PathVariable UUID id){
        byte[] b=excel.export(service.get(id));
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=cv_matching_result.xlsx")
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(b);
    }
    @GetMapping("/analyses")
    public List<AnalysisHistoryItem> history(@RequestParam(defaultValue="50") int limit){
        if(limit<1||limit>100)throw new IllegalArgumentException("Số kết quả lịch sử phải từ 1 đến 100.");
        return persistence.history(limit);
    }
    @GetMapping("/analyses/{id}")
    public AnalysisDetails details(@PathVariable UUID id){
        return persistence.details(id);
    }
    @DeleteMapping("/analyses/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id){
        persistence.delete(id);
        return ResponseEntity.noContent().build();
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String,String>> badInput(IllegalArgumentException e){
        return ResponseEntity.badRequest().body(Map.of("message",e.getMessage()));
    }
    @ExceptionHandler(OllamaUnavailableException.class)
    public ResponseEntity<Map<String,String>> aiUnavailable(OllamaUnavailableException e){
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("message",e.getMessage()));
    }
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String,String>> databaseUnavailable(DataAccessException e){
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of("message","Không truy cập được cơ sở dữ liệu. Hãy kiểm tra PostgreSQL rồi thử lại."));
    }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String,String>> fileTooLarge(MaxUploadSizeExceededException e){
        return ResponseEntity.status(413).body(Map.of("message","Tổng dung lượng upload vượt giới hạn."));
    }
}
