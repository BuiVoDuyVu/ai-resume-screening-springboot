package com.example.cvmatching;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class TfidfServiceTest {
  @Test void identicalBeatsDifferent() {
    var scores=new TfidfService().compare("Java Spring Boot REST API", List.of("Java Spring Boot REST API","Piano football tea"));
    assertTrue(scores.get(0)>scores.get(1));
  }
}
