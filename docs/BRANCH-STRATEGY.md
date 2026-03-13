# 브랜치 전략 (Branch Strategy)

> 프로젝트: GamePaper
> 전략: 스프린트 기반 단순 전략

---

## 브랜치 구조

```
master  ──────────────────────────────────────────── (항상 안정)
   │
   ├── sprint1  ─── 작업 ─── PR → master 병합 → 브랜치 삭제
   ├── sprint2  ─── 작업 ─── PR → master 병합 → 브랜치 삭제
   │   ...
   └── hotfix/{name}  ─── 긴급 수정 ─── PR → master 병합 → 브랜치 삭제
```

---

## 브랜치 종류

### `master`
- 항상 배포 가능한 안정 상태 유지
- **직접 커밋 금지** — PR을 통해서만 병합
- 각 스프린트 완료 시 sprint 브랜치가 병합됨

### `sprint{n}`
- 각 스프린트 작업 브랜치 (예: `sprint1`, `sprint2`)
- `master`에서 분기
- 스프린트 완료 후 PR → `master` 병합
- 병합 완료 후 브랜치 삭제

### `hotfix/{name}`
- `master`의 긴급 버그 수정
- `master`에서 분기
- 수정 완료 후 PR → `master` 병합
- 명명 예시: `hotfix/crawler-null-pointer`, `hotfix/storage-path`

---

## 워크플로우

### 스프린트 시작
```bash
# master 최신화
git checkout master
git pull origin master

# 스프린트 브랜치 생성
git checkout -b sprint{n}
git push -u origin sprint{n}
```

### 스프린트 진행 중 커밋
```bash
git add {파일}
git commit -m "feat: 기능 설명"
git push origin sprint{n}
```

### 스프린트 완료 (sprint-close 에이전트가 수행)
```bash
# PR 생성
gh pr create --base master --head sprint{n} --title "Sprint {n} 완료"

# 병합 후 브랜치 삭제
git checkout master
git pull origin master
git branch -d sprint{n}
git push origin --delete sprint{n}
```

### 핫픽스
```bash
git checkout master
git pull origin master
git checkout -b hotfix/{name}

# 수정 작업
git commit -m "fix: 수정 내용"
git push -u origin hotfix/{name}

# PR 생성 후 병합
gh pr create --base master --head hotfix/{name}
```

---

## 커밋 메시지 규칙

```
{type}: {설명}

예시:
feat: GenericCrawlerExecutor 구현
fix: Selenium 세션 누수 수정
docs: ROADMAP 업데이트
refactor: StorageService 인터페이스 분리
test: WallpaperRepository 통합 테스트 추가
chore: Docker Compose 환경변수 추가
```

| 타입 | 용도 |
|------|------|
| `feat` | 새로운 기능 |
| `fix` | 버그 수정 |
| `docs` | 문서 변경 |
| `refactor` | 코드 리팩토링 (기능 변경 없음) |
| `test` | 테스트 추가/수정 |
| `chore` | 빌드, 설정, 의존성 등 기타 |

---

## PR 규칙

- PR 제목: `[Sprint {n}] 스프린트 목표 요약` 또는 `[Hotfix] 수정 내용`
- PR 본문: 변경 사항, 완료 기준 달성 여부, 테스트 결과 포함
- `sprint-close` 에이전트가 PR 생성 및 검증 수행
- Self-merge 허용 (1인 개발 기준)

---

## GitHub 브랜치 보호 규칙 (권장)

`master` 브랜치에 아래 보호 규칙 적용을 권장합니다.

- PR 없이 직접 push 불가
- PR 병합 전 CI(GitHub Actions) 통과 필수
- 병합 후 소스 브랜치 자동 삭제
