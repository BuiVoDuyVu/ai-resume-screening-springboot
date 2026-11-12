package com.example.cvmatching;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.Locale;

@Service
public class CvTextService {
    public String read(MultipartFile file) {
        if (file.isEmpty() || file.getSize() > 5L*1024*1024) throw new IllegalArgumentException("Mỗi CV phải từ 1 byte đến tối đa 5 MB.");
        String name = file.getOriginalFilename()==null ? "" : file.getOriginalFilename();
        String lower = name.toLowerCase(Locale.ROOT);
        try {
            byte[] bytes = file.getBytes();
            String text;
            if (lower.endsWith(".txt")) {
                text = new String(bytes,StandardCharsets.UTF_8).replace("\uFEFF","");
            } else if (lower.endsWith(".pdf")) {
                try (PDDocument document = Loader.loadPDF(bytes)) {
                    text = new PDFTextStripper().getText(document);
                }
            } else if (lower.endsWith(".docx")) {
                try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes));
                     XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
                    text = extractor.getText();
                }
            } else throw new IllegalArgumentException("Chỉ nhận .txt, .pdf và .docx");
            if (text == null || text.isBlank()) throw new IllegalArgumentException("Không trích xuất được chữ từ " + name + ". PDF dạng ảnh cần OCR.");
            return text.length() > 10000 ? text.substring(0,10000) : text;
        } catch (IOException | RuntimeException e) {
            if (e instanceof IllegalArgumentException) throw (IllegalArgumentException)e;
            throw new IllegalArgumentException("Lỗi đọc file " + name + ": định dạng không hợp lệ hoặc bị khóa.");
        }
    }
}
