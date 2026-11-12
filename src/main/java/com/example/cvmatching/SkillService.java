package com.example.cvmatching;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class SkillService {
    private final Map<String,List<String>> aliases = new LinkedHashMap<>();
    public SkillService() {
        aliases.put("JavaScript", List.of("javascript", "js", "ecmascript"));
        aliases.put("Node.js", List.of("node.js", "nodejs"));
        aliases.put("Express", List.of("express.js", "expressjs", "express"));
        aliases.put("PostgreSQL", List.of("postgresql", "postgres"));
        aliases.put("MongoDB", List.of("mongodb", "mongo db"));
        aliases.put("Docker", List.of("docker"));
        aliases.put("Git", List.of("git"));
        aliases.put("REST API", List.of("rest api", "restful api", "restful"));
        aliases.put("Java", List.of("java", "java se"));
        aliases.put("Spring Boot", List.of("spring boot", "springboot"));
        aliases.put("Python", List.of("python"));
        aliases.put("React", List.of("react", "reactjs", "react.js"));
        aliases.put("SQL", List.of("sql"));
        aliases.put("AWS", List.of("aws", "amazon web services"));
        aliases.put("Kubernetes", List.of("kubernetes", "k8s"));
    }
    public List<String> detect(String text) {
        String t = text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        List<String> found = new ArrayList<>();
        for (var e : aliases.entrySet()) {
            boolean matched = e.getValue().stream().anyMatch(alias -> {
                var regex = "(?<![\\p{L}\\p{N}])"+ Pattern.quote(alias) +"(?![\\p{L}\\p{N}])";
                return Pattern.compile(regex, Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE).matcher(t).find();
            });
            if (matched) found.add(e.getKey());
        }
        return found;
    }
}
