package com.example.cvmatching;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import java.io.*;

@Service
public class ExcelReportService {
    public byte[] export(AnalysisResponse r){
        try(XSSFWorkbook wb=new XSSFWorkbook(); ByteArrayOutputStream out=new ByteArrayOutputStream()){
            Sheet sheet=wb.createSheet("CV Matching");
            Row header=sheet.createRow(0);
            String[] cols={"Tên file CV","Điểm kỹ thuật","Độ tương đồng (%)","Số kỹ năng khớp","Tổng kỹ năng JD","Kỹ năng khớp","Kỹ năng chưa thấy"};
            CellStyle style=wb.createCellStyle();
            Font bold=wb.createFont();bold.setBold(true);style.setFont(bold);
            for(int c=0;c<cols.length;c++){Cell cell=header.createCell(c);cell.setCellValue(cols[c]);cell.setCellStyle(style);}
            int row=1;
            for(CandidateResult candidate:r.candidates()){
                Row rr=sheet.createRow(row++);
                rr.createCell(0).setCellValue(candidate.fileName());
                rr.createCell(1).setCellValue(candidate.technicalScore());
                rr.createCell(2).setCellValue(candidate.similarity());
                rr.createCell(3).setCellValue(candidate.matchedCount());
                rr.createCell(4).setCellValue(candidate.requiredCount());
                rr.createCell(5).setCellValue(String.join(", ", candidate.matchedSkills()));
                rr.createCell(6).setCellValue(String.join(", ", candidate.missingSkills()));
            }
            sheet.createFreezePane(0,1);
            for(int c=0;c<cols.length;c++){sheet.autoSizeColumn(c);sheet.setColumnWidth(c,Math.min(sheet.getColumnWidth(c)+600,18000));}
            Sheet notes=wb.createSheet("Ghi chu");
            notes.createRow(0).createCell(0).setCellValue("Kết quả chỉ mô tả sự trùng khớp văn bản / kỹ năng; không tự động quyết định tuyển dụng.");
            notes.createRow(1).createCell(0).setCellValue("Phương pháp: "+r.mode()+"; điểm = 60% tương đồng + 40% tỷ lệ kỹ năng khớp.");
            notes.setColumnWidth(0,23000);
            wb.write(out);return out.toByteArray();
        }catch(IOException ex){throw new IllegalStateException("Không xuất được Excel",ex);}
    }
}
