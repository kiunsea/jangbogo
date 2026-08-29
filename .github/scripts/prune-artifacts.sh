#!/usr/bin/env bash
# 빌드 임시 아티팩트 정리 — build.yml 의 업로드 "앞" 과 "뒤" 두 스텝이 공유하는 단일 구현.
#
# 왜 한 파일인가: 앞/뒤 두 스텝이 각자 셸 블록을 갖고 같은 판정(이름별 나열·최신 판별·삭제)을
#   복제하면, 한쪽만 고쳐졌을 때 "앞에서는 지워지는데 뒤에서는 안 지워진다" 가 조용히 생긴다.
#   이 저장소가 반복해서 겪은 '판정 2구현' 사고와 같은 형태라 처음부터 한 구현으로 둔다.
#
# 사용법:  prune-artifacts.sh <owner/repo> <all|keep-newest> <이름>...
#   all         — 그 이름의 아티팩트를 전부 지운다 (업로드 직전용).
#   keep-newest — 이름별로 가장 최근 1벌만 남기고 지운다 (업로드 직후 수렴 가드용).
#
# 필요 권한: GH_TOKEN + 워크플로 permissions 의 `actions: write`.
# 실패해도 빌드 결과를 깨뜨리지 않는다 — 항상 exit 0 (호출 스텝도 continue-on-error).
#
# 이 저장소는 runs-on: windows-latest 라 기본 셸이 PowerShell 이다. 호출 스텝에 `shell: bash`
#   를 반드시 적어야 한다(러너의 Git Bash 로 돈다). 빠뜨리면 PowerShell 이 이 파일을 해석하려
#   들어 스텝이 죽는데, continue-on-error 때문에 초록으로 넘어가 정리가 통째로 멈춘 것을
#   아무도 모르게 된다. WorkflowArtifactHygieneTest 가 그 형태를 감시한다.

set +e # 기본 셸이 `bash -e` 라 gh api 한 번 실패(rate limit·일시 5xx·권한)에 즉사한다. 뒤 이름까지 정리하려면 꺼야 한다.

REPO="$1"
MODE="$2"
shift 2

if [ -z "$REPO" ] || [ -z "$MODE" ] || [ "$#" -eq 0 ]; then
  echo "::warning::prune-artifacts.sh 인자 부족 (repo='$REPO' mode='$MODE' names=$#) — 정리 건너뜀"
  exit 0
fi

if [ "$MODE" != "all" ] && [ "$MODE" != "keep-newest" ]; then
  echo "::warning::prune-artifacts.sh 알 수 없는 모드 '$MODE' — 정리 건너뜀"
  exit 0
fi

# 그 이름의 아티팩트를 "생성시각<TAB>id" 로 전부 나열한다 (페이지 경계를 넘어서).
#
# `--paginate` 는 --jq 를 **페이지마다** 적용한다. 그래서 '최신 1벌만 남긴다' 같은 전역 판정을
#   jq 안에서 하면 페이지마다 1벌씩 남아 조용히 어긋난다. 정렬·자르기는 반드시 셸에서 한다.
#   이 저장소는 정리 직전 228벌이었다 — per_page=100 의 페이지 경계를 실제로 넘는 규모다.
# expired 로 거르지 않는다. retention-days 로 만료된 벌은 GitHub GC 전까지 목록에 남아 저장
#   quota 를 계속 잡는데, 거르면 그 누적분이 영영 사정권 밖이 된다. 2026-08-29 실측: 이 저장소의
#   7,637MB 중 6,046MB(184벌)가 retention-days: 7 로 이미 만료된 것이었고, 그중 가장 오래된 것은
#   2025-11 자였다. 만료분만 놔뒀어도 계정 한도(500MB)의 12배다.
list_artifacts() {
  export NAME="$1"
  gh api --paginate "repos/$REPO/actions/artifacts?per_page=100" \
    --jq '.artifacts[] | select(.name == $ENV.NAME) | "\(.created_at)\t\(.id)"'
}

delete_artifact() {
  if gh api -X DELETE "repos/$REPO/actions/artifacts/$1" >/dev/null; then
    echo "pruned($MODE) $2 artifact $1"
  else
    echo "::warning::$2 artifact $1 삭제 실패 — 다음 회차에 재시도된다"
  fi
}

for NAME in "$@"; do
  LIST=$(list_artifacts "$NAME")
  if [ $? -ne 0 ]; then
    echo "::warning::$NAME 아티팩트 목록 조회 실패 — 이번 회차 정리 건너뜀"
    continue
  fi

  if [ "$MODE" = "all" ]; then
    TARGETS=$(printf '%s\n' "$LIST" | cut -f2)
  else
    # created_at 은 ISO8601 UTC(Z) 라 문자열 역순 정렬이 곧 최신순이다. 첫 줄(=최신)만 남기고 나머지.
    TARGETS=$(printf '%s\n' "$LIST" | sort -r | tail -n +2 | cut -f2)
  fi

  COUNT=0
  for ID in $TARGETS; do
    delete_artifact "$ID" "$NAME"
    COUNT=$((COUNT + 1))
  done
  echo "$NAME: mode=$MODE 대상 $COUNT 벌"
done

exit 0
