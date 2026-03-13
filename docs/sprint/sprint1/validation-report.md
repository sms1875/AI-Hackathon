# Sprint 1 검증 보고서

**작성 일자**: 2026-03-13
**스프린트**: Sprint 1 — 백엔드 인프라 추상화
**브랜치**: sprint1 → master (PR #1)

---

## 자동 검증 결과

### Gradle 빌드 및 단위/통합 테스트

**명령어**: `./gradlew test --no-daemon` (서버 디렉토리 기준)
**결과**: BUILD SUCCESSFUL

| 테스트 클래스 | 테스트명 | 결과 |
|--------------|----------|------|
| `GameRepositoryTest` | 게임 저장 및 조회 | 통과 |
| `GameRepositoryTest` | 상태별 게임 목록 조회 | 통과 |
| `LocalStorageServiceTest` | 파일 업로드 후 URL 반환 | 통과 |
| `LocalStorageServiceTest` | 업로드한 파일이 실제로 저장됨 | 통과 |
| `LocalStorageServiceTest` | 파일 삭제 | 통과 |
| `LocalStorageServiceTest` | getUrl은 파일 존재 여부와 무관하게 URL 반환 | 통과 |
| `GameApiControllerTest` | GET /api/games - 게임 없을 때 빈 목록 반환 | 통과 |
| `GameApiControllerTest` | GET /api/games - 게임 등록 후 목록 반환 | 통과 |

**총계**: 8개 통과 / 0개 실패

---

## 수동 검증 필요 항목

Docker 환경이 필요한 항목은 사용자가 직접 수행해야 합니다. 자세한 검증 명령어는 `server/deploy.md` 참고.

| 항목 | 상태 | 비고 |
|------|------|------|
| `docker compose up --build` 서버 정상 기동 | ⬜ 수동 필요 | `Started GamepaperApplication` 로그 확인 |
| `curl http://localhost:8080/api/games` → `[]` | ⬜ 수동 필요 | Docker 실행 후 |
| SQLite DB 파일 생성 및 데이터 영속성 | ⬜ 수동 필요 | 재시작 후 데이터 유지 확인 |
| WAL 모드 활성화 (`PRAGMA journal_mode` → `wal`) | ⬜ 수동 필요 | `sqlite3 gamepaper.db` 명령 필요 |
| 이미지 URL 접근 (HTTP 200) | ⬜ 수동 필요 | `/storage/images/` 서빙 확인 |

---

## 코드 리뷰 요약

상세 내용: [code-review.md](code-review.md)

- Critical 이슈: 0개
- Important 이슈: 3개 (Sprint 2~3에서 해소 예정)
  - I-1: `GameApiController` wallpaperCount N+1 쿼리 위험
  - I-2: `WebMvcConfigurer` 이중 구현 (StorageConfig + WebConfig)
  - I-3: `CrawlingLog.status` 필드 타입 불일치 (String vs enum)
- Suggestion: 3개 (낮은 우선순위)

---

## DoD (완료 기준) 달성 현황

| 기준 | 자동 검증 | 수동 검증 |
|------|-----------|-----------|
| `docker compose up`으로 서버 정상 기동 | - | ⬜ |
| `GET /api/games` 빈 목록 반환 | ✅ (MockMvc 테스트) | ⬜ Docker 실행 후 |
| SQLite DB 파일 생성 및 데이터 영속성 | - | ⬜ |
| 로컬 스토리지 이미지 영구 URL 접근 | ✅ (단위 테스트) | ⬜ Docker 실행 후 |
| SQLite WAL 모드 활성화 | ✅ (DatabaseConfig 코드 확인) | ⬜ 실제 DB 확인 |
