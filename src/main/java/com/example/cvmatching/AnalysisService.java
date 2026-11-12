package com.example.cvmatching;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

record CandidateResult(String fileName, double technicalScore, double similarity,
                       int matchedCount,int requiredCount,List<String> matchedSkills,List<String> missingSkills) { }
record AnalysisResponse(UUID analysisId, String mode, List<String> requiredSkills,
                        List<CandidateResult> candidates) { }

@Service
public class AnalysisService {
    private final CvTextService parser;
    private final SkillService skillService;
    private final TfidfService tfidf;
    private final OllamaService ollama;
    private final AnalysisPersistence persistence;
    public AnalysisService(CvTextService parser,SkillService skillService,TfidfService tfidf,OllamaService ollama,
                           AnalysisPersistence persistence){
        this.parser=parser; this.skillService=skillService;this.tfidf=tfidf;this.ollama=ollama;
        this.persistence=persistence;
    }
    public AnalysisResponse analyze(String jd, MultipartFile[] files,String mode){
        if(jd==null || jd.isBlank()) throw new IllegalArgumentException("Cần nhập mô tả tuyển dụng (JD).");
        if(jd.length()>10000) throw new IllegalArgumentException("JD quá dài, tối đa 10.000 ký tự.");
        if(files==null||files.length==0||files.length>10)throw new IllegalArgumentException("Chọn từ 1 đến 10 CV.");
        if(!"tfidf".equals(mode)&&!"ai".equals(mode))throw new IllegalArgumentException("Chế độ phải là tfidf hoặc ai.");
        List<String> required=skillService.detect(jd);
        if(required.isEmpty())throw new IllegalArgumentException("Chưa nhận diện được kỹ năng nào trong JD. Hãy bổ sung danh sách công nghệ rõ ràng.");
        List<String> texts=new ArrayList<>();
        for(var file:files)texts.add(parser.read(file));
        List<Double> similarities="ai".equals(mode)?ollama.compare(jd,texts):tfidf.compare(jd,texts);
        List<CandidateWithDocument> out=new ArrayList<>();
        for(int i=0;i<files.length;i++){
            List<String> detected=skillService.detect(texts.get(i));
            List<String> matched=required.stream().filter(detected::contains).toList();
            List<String> missing=required.stream().filter(s->!detected.contains(s)).toList();
            double similarity=similarities.get(i);
            double rate=(double)matched.size()/required.size();
            double score=100*(.6*similarity+.4*rate);
            CandidateResult candidate=new CandidateResult(files[i].getOriginalFilename(),round(score),round(100*similarity),matched.size(),required.size(),matched,missing);
            CvDocumentMetadata document=new CvDocumentMetadata(files[i].getOriginalFilename(),files[i].getContentType(),files[i].getSize(),sha256(files[i]));
            out.add(new CandidateWithDocument(candidate,document));
        }
        out.sort(Comparator.comparingDouble((CandidateWithDocument item)->item.candidate().technicalScore()).reversed());
        AnalysisResponse result=new AnalysisResponse(UUID.randomUUID(),mode,required,out.stream().map(item->item.candidate()).toList());
        persistence.save(jd,result,out.stream().map(item->item.document()).toList());
        return result;
    }
    public AnalysisResponse get(UUID id){
        return persistence.get(id);
    }
    private String sha256(MultipartFile file){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(file.getBytes()));}
        catch(IOException ex){throw new IllegalArgumentException("Không đọc được nội dung CV để lưu thông tin hồ sơ.",ex);}
        catch(NoSuchAlgorithmException ex){throw new IllegalStateException("SHA-256 không khả dụng.",ex);}
    }
    private record CandidateWithDocument(CandidateResult candidate,CvDocumentMetadata document) { }
    private double round(double d){return Math.round(d*10.0)/10.0;}
}
