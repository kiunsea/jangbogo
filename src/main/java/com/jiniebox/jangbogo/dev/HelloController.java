package com.jiniebox.jangbogo.dev;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import java.util.Map;

@Controller
public class HelloController {

    // 화면 렌더링용 hello.html
    @GetMapping("/hello")
    public String helloPage() {
        return "hello"; // → src/main/resources/templates/hello.html
    }

    // AJAX용 JSON 응답
    @GetMapping("/api/message")
    @ResponseBody
    public Map<String, String> getMessage() {
        return Map.of("message", "장보고 프로젝트 AJAX 응답입니다!");
    }
}