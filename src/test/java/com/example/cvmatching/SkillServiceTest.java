package com.example.cvmatching;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
class SkillServiceTest {
  private final SkillService service = new SkillService();
  @Test void detectsEightRequiredSkills() {
    String jd = "JavaScript, Node.js, Express, PostgreSQL, MongoDB, Docker, Git, REST API";
    assertEquals(8,service.detect(jd).size());
  }
  @Test void dataProfileHasThreeSkills() {
    String cv = "Python, PostgreSQL, Docker, Git";
    var all = service.detect(cv);
    assertTrue(all.containsAll(List.of("PostgreSQL","Docker","Git")));
  }
  @Test void javaIsNotJavascript() {
    assertFalse(service.detect("JavaScript").contains("Java"));
  }
}
