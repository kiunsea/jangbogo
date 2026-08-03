package com.jiniebox.jangbogo.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jiniebox.jangbogo.dev.DevTestController;
import com.jiniebox.jangbogo.dev.HelloController;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;

/**
 * 보안 하드닝 회귀 감시 (B-2 · B-3 · B-4).
 *
 * <p>계획서가 "별도 과제"로 분류해 둔 항목들이다. 이 저장소는 PUBLIC 이라 <b>소스가 이미 공개돼 있고</b>, 그래서 각 항목이 이론적 위험이 아니다.
 *
 * <p>키 관련(B-1)은 접근자를 public 으로 열지 않으려고 {@code util} 패키지의 별도 테스트에 둔다.
 *
 * <p>브라우저·네트워크·DB 를 쓰지 않는다.
 *
 * @author KIUNSEA
 */
class SecurityHardeningTest {

  // ---------------------------------------------------------------
  // B-2 — 개발용 엔드포인트가 운영 JAR 에 등록되지 않는다
  // ---------------------------------------------------------------

  @Test
  @DisplayName("dev 컨트롤러는 dev 프로파일에서만 등록된다")
  void devControllersAreProfileGated() {
    // /dev/reset-database 는 주문·아이템을 통째로 지운다. 인증이 앞을 막고 있었지만
    // 파괴적 기능이 운영 산출물에 실려 있는 것 자체가 문제였다.
    for (Class<?> type : new Class<?>[] {DevTestController.class, HelloController.class}) {
      Profile profile = type.getAnnotation(Profile.class);
      assertNotNull(profile, type.getSimpleName() + " 에 @Profile 이 없다 — 운영 JAR 에 등록된다.");
      assertTrue(
          java.util.Arrays.asList(profile.value()).contains("dev"),
          type.getSimpleName() + " 의 @Profile 이 dev 가 아니다: " + String.join(",", profile.value()));
    }
  }

  // ---------------------------------------------------------------
  // B-3 — 외부에서 온 값을 SQL 에 이어 붙이지 않는다
  // ---------------------------------------------------------------

  @Test
  @DisplayName("주문·아이템 INSERT 는 바인딩 파라미터를 쓴다")
  void orderAndItemInsertsUseBoundParameters() throws Exception {
    // serial_num·mall_name·name 은 수집한 웹페이지에서 온 값이다. 따옴표 하나로 INSERT 가
    // 깨지고, 그 자리가 곧 주입 지점이 된다.
    //
    // 실제로 이 프로젝트는 같은 INSERT 를 두 벌 갖고 있었고 한쪽만 고쳐져 있었다 —
    // 수집기가 쓰는 addWithConnection 은 진작 PreparedStatement 였고, add(...) 오버로드만
    // 문자열 조립으로 남아 있었다. 두 벌을 두면 반드시 갈라진다.
    for (String dao : new String[] {"JbgOrderDataAccessObject", "JbgItemDataAccessObject"}) {
      String source =
          Files.readString(
              Path.of("src/main/java/com/jiniebox/jangbogo/dao/" + dao + ".java"),
              StandardCharsets.UTF_8);
      String code = source.replaceAll("(?m)^\\s*//.*$", "");

      assertFalse(
          code.contains("\"'\" + "), dao + " 에 SQL 문자열 조립(\"'\" + 값)이 남아 있다. 바인딩 파라미터를 쓸 것.");
      assertTrue(code.contains("txPstmtExecuteUpdate"), dao + " 가 PreparedStatement 를 쓰지 않는다.");
    }
  }

  // ---------------------------------------------------------------
  // B-4 — 인증 화이트리스트에 actuator 를 남겨 두지 않는다
  // ---------------------------------------------------------------

  @Test
  @DisplayName("actuator 는 인증 예외 목록에 없다")
  void actuatorIsNotWhitelisted() throws Exception {
    // 지금은 의존성이 없어 404 지만, 나중에 넣으면 health·env·beans 가 무인증으로 열린 채
    // 시작하게 된다. 쓰지도 않는 예외를 미리 깔아 두지 않는다.
    for (String file : new String[] {"sys/AuthInterceptor.java", "sys/WebMvcConfig.java"}) {
      String source =
          Files.readString(
              Path.of("src/main/java/com/jiniebox/jangbogo/" + file), StandardCharsets.UTF_8);
      String code = source.replaceAll("(?m)^\\s*//.*$", "");

      assertFalse(code.contains("\"/actuator"), file + " 에 actuator 인증 예외가 남아 있다.");
    }
  }
}
