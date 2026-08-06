package com.jiniebox.jangbogo.dao;

/**
 * 다른 패키지의 경계 테스트가 스키마 마이그레이션을 다시 돌릴 수 있게 하는 테스트 헬퍼.
 *
 * <p>{@link SchemaMigrator#resetForTest()} 는 package-private 이라 {@code dao} 패키지 밖에서는 부를 수 없다. 프로덕션
 * API 를 넓히지 않으려고, 같은 패키지의 <b>테스트 소스</b>에 이 접근점을 둔다 — 여기서만 열고 필요한 테스트가 이걸 통해 쓴다.
 *
 * <p>{@code SchemaMigrator} 는 JVM 당 1회만 실제 작업을 하므로, 임시 DB 를 쓰는 테스트는 매번 다시 돌려야 그 DB 에 테이블이 생긴다.
 */
public final class SchemaTestSupport {

  private SchemaTestSupport() {}

  /** 마이그레이터를 되돌리고 현재 {@code jangbogo.localdb.url} 대상에 스키마를 만든다. */
  public static void remigrate() {
    SchemaMigrator.resetForTest();
    SchemaMigrator.ensureMigrated();
  }

  /** 다음 테스트가 자기 DB 에 다시 마이그레이션하도록 플래그만 되돌린다. */
  public static void reset() {
    SchemaMigrator.resetForTest();
  }
}
