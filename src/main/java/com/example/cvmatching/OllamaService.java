package com.example.cvmatching;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

@Service
public class OllamaService {
    private final JsonMapper mapper;
    private final String url, model;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    public OllamaService(JsonMapper mapper,
            @Value("${ollama.url}") String url, @Value("${ollama.model}") String model) {
        this.mapper=mapper;this.url=url;this.model=model;
    }
    public List<Double> compare(String jd, List<String> cvs) {
        try {
            List<String> inputs=new ArrayList<>(); inputs.add(jd);inputs.addAll(cvs);
            String json=mapper.writeValueAsString(Map.of("model",model,"input",inputs,"truncate",true));
            HttpRequest req=HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(180))
                .header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)).build();
            HttpResponse<String> res=client.send(req,HttpResponse.BodyHandlers.ofString());
            if(res.statusCode()!=200) throw new IllegalStateException("Ollama trả HTTP "+res.statusCode());
            JsonNode embeddings=mapper.readTree(res.body()).path("embeddings");
            if(!embeddings.isArray()||embeddings.size()!=inputs.size())throw new IllegalStateException("Đầu ra embedding không hợp lệ.");
            List<Double> result=new ArrayList<>();
            for(int i=1;i<embeddings.size();i++)result.add(cos(embeddings.get(0),embeddings.get(i)));
            return result;
        } catch(Exception ex){
            if(ex instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new OllamaUnavailableException("Không gọi được Ollama. Hãy chạy Ollama, tải model bge-m3 hoặc chọn TF-IDF.");
        }
    }
    private double cos(JsonNode a,JsonNode b){
        if(a.size()!=b.size()||a.size()==0)throw new IllegalStateException("Sai số chiều embedding");
        double dot=0,aa=0,bb=0;
        for(int j=0;j<a.size();j++){double x=a.get(j).asDouble(),y=b.get(j).asDouble();dot+=x*y;aa+=x*x;bb+=y*y;}
        return aa==0||bb==0?0:Math.max(0,Math.min(1,dot/Math.sqrt(aa*bb)));
    }
}
class OllamaUnavailableException extends RuntimeException {
    OllamaUnavailableException(String msg){super(msg);}
}
