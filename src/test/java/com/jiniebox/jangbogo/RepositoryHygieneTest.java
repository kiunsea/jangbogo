package com.jiniebox.jangbogo;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 자격증명·세션이 PUBLIC 저장소로 새지 않게 하는 {@code .gitignore} 규칙 감시.
 *
 * <h2>왜 테스트로 묶는가</h2>
 *
 * <p>이 저장소는 PUBLIC 이다. 아래 규칙들은 <b>한 줄이 사라지면 그 순간부터 조용히 새기 시작하고</b>, 커밋되고 나면 이력에 남아 되돌릴 수 없다. 실제로 이
 * 프로젝트는 죽은 빌드 선언 하나 때문에 특정 PC 절대경로와 비공개 저장소명이 공개 이력에 남은 전례가 있다.
 *
 * <p>특히 {@code profiles/} 는 <b>디렉터리가 아직 생기지도 않은 시점에 미리 막아 둔 것</b>이라, 눈에 보이는 파일이 없어서 "쓰지 않는 규칙"으로
 * 오해되고 지워지기 쉽다. Phase 5(프로필 재사용)가 그 폴더를 만들면 사람이 한 번 로그인한 세션 — 쿠키와 토큰 — 이 그대로 들어간다.
 *
 * <p>파일만 읽는다. 브라우저·네트워크·DB 를 쓰지 않는다.
 *
 * @author KIUNSEA
 */
class RepositoryHygieneTest {

  private static final Path GITIGNORE = Path.of(".gitignore");

  /** 지워지면 자격증명·세션·구매내역이 새는 규칙들. 값은 {@code .gitignore} 의 패턴 그대로다. */
  private static final List<String> MUST_IGNORE =
      List.of(
          "profiles/", // 실로그인 Chrome 세션 (Phase 5)
          ".locks/", // 프로필 단위 락 — 런타임 상태이고 지우지 않는 규칙이라 쌓인다
          "config/mall_account.yml", // 쇼핑몰 계정
          "config/admin.properties", // 관리자 자격증명
          "db/*.db", // 구매내역 + jbg_mall 의 암호화 키
          "db/backup/", // 위 DB 의 백업 (확장자가 제각각이라 위 패턴에 안 걸린다)
          "logs/"); // 수집 로그 (DEBUG 에 구매 상세가 남는다)

  @Test
  @DisplayName("자격증명·세션이 담기는 경로는 전부 무시 대상이다")
  void keepsEveryCredentialBearingPathIgnored() throws Exception {
    assertTrue(Files.isRegularFile(GITIGNORE), ".gitignore 가 없다. 테스트 작업 디렉터리는 프로젝트 루트여야 한다.");
    List<String> lines =
        Files.readAllLines(GITIGNORE, StandardCharsets.UTF_8).stream()
            .map(String::trim)
            .filter(line -> !line.isEmpty() && !line.startsWith("#"))
            .toList();

    for (String pattern : MUST_IGNORE) {
      assertTrue(
          lines.contains(pattern),
          ".gitignore 에서 '" + pattern + "' 규칙이 사라졌다. 이 저장소는 PUBLIC 이고, 한 번 커밋되면 이력에 남아 되돌릴 수 없다.");
    }
  }

  @Test
  @DisplayName("세션 인계 계획서는 커밋하지 않는다")
  void keepsInternalPlanDocsOutOfTheRepository() throws Exception {
    String gitignore = Files.readString(GITIGNORE, StandardCharsets.UTF_8);

    // 정본은 비공개 저장소에 있다. 여기서는 패턴으로 막아 앞으로 만들 계획서도 기본적으로 걸리게 한다.
    assertTrue(
        gitignore.contains("doc/PLAN-*.md"),
        "doc/PLAN-*.md 규칙이 사라졌다. 세션 인계 문서에는 운영 환경 세부와 미완 작업이 섞인다.");
  }
}
