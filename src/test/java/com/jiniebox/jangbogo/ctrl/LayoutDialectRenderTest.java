package com.jiniebox.jangbogo.ctrl;

import static org.junit.jupiter.api.Assertions.assertTrue;

import nz.net.ultraq.thymeleaf.layoutdialect.LayoutDialect;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * 레이아웃 다이얼렉트가 실제로 화면을 조립하는지 본다.
 *
 * <p><b>왜 이 테스트가 있는가.</b> 이 저장소의 화면 5장({@code index}, {@code orders}, {@code profile}, {@code
 * collect-logs}, {@code dev/welcome})은 {@code layout:decorate} 로 {@code layout.html} 을 입는다. 그런데 그
 * 조립을 실제로 거치는 테스트가 하나도 없었다 — {@code ScreenAssetVersionTest} 는 템플릿을 <b>텍스트로 읽을</b> 뿐이고 {@code
 * JangbogoApplicationTests} 는 컨텍스트만 띄운다.
 *
 * <p>그래서 2026-08-24 에 thymeleaf-layout-dialect 를 3.2.1 에서 4.0.1 로 올리는 dependabot PR 을 두고 <b>판단할 근거가
 * 없었다.</b> 전체 테스트가 통과해도 그것이 이 다이얼렉트에 대해 말해 주는 바가 없었다 — 깨지면 테스트가 아니라 화면에서 깨진다.
 *
 * <p>실제 템플릿 대신 픽스처를 쓰는 것은 화면이 요구하는 모델 변수에 얽히지 않기 위해서다. 여기서 보는 것은 다이얼렉트의 계약이지 각 화면의 내용이 아니다. 픽스처는 실제
 * {@code layout.html}·{@code index.html} 이 쓰는 세 속성을 그대로 쓴다 — {@code layout:decorate}, {@code
 * layout:fragment}, {@code layout:title-pattern}.
 */
class LayoutDialectRenderTest {

  private static String render(String template) {
    ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
    resolver.setPrefix("layout-fixtures/");
    resolver.setSuffix(".html");
    resolver.setTemplateMode(TemplateMode.HTML);
    resolver.setCharacterEncoding("UTF-8");

    TemplateEngine engine = new TemplateEngine();
    engine.setTemplateResolver(resolver);
    engine.addDialect(new LayoutDialect());

    return engine.process(template, new Context());
  }

  @Test
  @DisplayName("layout:decorate 가 레이아웃의 껍데기를 입힌다")
  void 레이아웃_껍데기가_입혀진다() {
    String html = render("page");
    assertTrue(html.contains("SHELL_HEADER"), "레이아웃의 머리가 없다:\n" + html);
    assertTrue(html.contains("SHELL_FOOTER"), "레이아웃의 꼬리가 없다:\n" + html);
  }

  @Test
  @DisplayName("layout:fragment 자리에 페이지 내용이 들어간다")
  void 조각_자리에_내용이_들어간다() {
    String html = render("page");
    assertTrue(html.contains("PAGE_CONTENT"), "페이지 내용이 없다:\n" + html);

    // 껍데기 안쪽에 놓였는가 — 단순 포함이 아니라 조립됐는지를 본다.
    int header = html.indexOf("SHELL_HEADER");
    int body = html.indexOf("PAGE_CONTENT");
    int footer = html.indexOf("SHELL_FOOTER");
    assertTrue(
        header < body && body < footer,
        "내용이 머리와 꼬리 사이에 있지 않다 (header="
            + header
            + ", body="
            + body
            + ", footer="
            + footer
            + "):\n"
            + html);
  }

  @Test
  @DisplayName("layout:title-pattern 이 레이아웃 제목과 페이지 제목을 합친다")
  void 제목이_규칙대로_합쳐진다() {
    String html = render("page");
    assertTrue(
        html.contains("장보고 - 수집 로그"), "title-pattern 이 적용되지 않았다 (기대: \"장보고 - 수집 로그\"):\n" + html);
  }

  @Test
  @DisplayName("빈 레이아웃 자리는 페이지가 채우지 않으면 비어 있다")
  void 채우지_않은_자리는_비어_있다() {
    // layout 자체를 직접 그리면 fragment 자리가 비어 있어야 한다.
    String html = render("layout");
    assertTrue(html.contains("SHELL_HEADER"), "레이아웃 단독 렌더가 안 된다:\n" + html);
    assertTrue(!html.contains("PAGE_CONTENT"), "페이지 내용이 새어 들어왔다:\n" + html);
  }

  @Test
  @DisplayName("실제 화면이 죽은 title-pattern 토큰을 쓰지 않는다")
  void 실제_화면은_죽은_토큰을_쓰지_않는다() throws java.io.IOException {
    // $DECORATOR_TITLE 은 layout-dialect 2.x 의 이름이고 3.x 에서 사라졌다. 3.2.1 과 4.0.1 모두
    // $LAYOUT_TITLE 만 인식한다. 죽은 토큰은 오류를 내지 않고 <title> 에 글자 그대로 남는다 —
    // 화면을 열어 보지 않으면 드러나지 않아서 다섯 장 전부가 그대로 나가 있었다 (2026-08-24 발견).
    java.nio.file.Path templates = java.nio.file.Path.of("src/main/resources/templates");
    java.util.List<String> offenders = new java.util.ArrayList<>();
    try (var paths = java.nio.file.Files.walk(templates)) {
      for (java.nio.file.Path p : paths.filter(java.nio.file.Files::isRegularFile).toList()) {
        if (!p.toString().endsWith(".html")) continue;
        String body = java.nio.file.Files.readString(p, java.nio.charset.StandardCharsets.UTF_8);
        if (body.contains("$DECORATOR_TITLE")) offenders.add(templates.relativize(p).toString());
      }
    }
    assertTrue(
        offenders.isEmpty(), "죽은 토큰 $DECORATOR_TITLE 을 쓰는 화면이 있다 (=> $LAYOUT_TITLE): " + offenders);
  }
}
