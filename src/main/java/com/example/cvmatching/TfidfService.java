package com.example.cvmatching;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.regex.*;

@Service
public class TfidfService {
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}+#.]{2,}");
    private Map<String, Integer> counts(String text) {
        var m = new HashMap<String,Integer>();
        Matcher matcher = WORD.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) m.merge(matcher.group(), 1, (left,right)->left+right);
        return m;
    }
    public List<Double> compare(String jd, List<String> cvs) {
        List<Map<String,Integer>> docs = new ArrayList<>();
        docs.add(counts(jd));
        for(String cv:cvs) docs.add(counts(cv));
        Map<String,Integer> df = new HashMap<>();
        for(var doc:docs) for(String token:doc.keySet()) df.merge(token,1,(left,right)->left+right);
        List<Map<String,Double>> vectors=new ArrayList<>();
        for(var doc:docs) {
            var v=new HashMap<String,Double>();
            double total=doc.values().stream().mapToInt(value->value.intValue()).sum();
            if(total==0) total=1;
            for(var e:doc.entrySet()) {
                double idf = Math.log((docs.size()+1.0)/(df.get(e.getKey())+1.0)) + 1.0;
                v.put(e.getKey(),e.getValue()/total*idf);
            }
            vectors.add(v);
        }
        List<Double> result = new ArrayList<>();
        for(int i=1;i<vectors.size();i++) result.add(cos(vectors.get(0),vectors.get(i)));
        return result;
    }
    private double cos(Map<String,Double> a, Map<String,Double> b) {
        double dot=0, aa=0, bb=0;
        for(var e:a.entrySet()) {aa+=e.getValue()*e.getValue();dot+=e.getValue()*b.getOrDefault(e.getKey(),0d);}
        for(double x:b.values()) bb+=x*x;
        return aa==0||bb==0?0:Math.max(0,Math.min(1,dot/Math.sqrt(aa*bb)));
    }
}
