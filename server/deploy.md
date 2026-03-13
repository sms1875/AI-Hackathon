# GamePaper 배포 가이드

## 최초 실행 전 사전 준비 (C-3)

`docker compose up` 실행 전에 반드시 아래 명령을 먼저 실행해야 합니다.
Docker는 마운트 대상 파일이 없으면 **디렉토리**로 마운트하기 때문에 SQLite가 정상 동작하지 않습니다.

```bash
cd server/

# SQLite DB 파일 초기화 (없으면 Docker가 디렉토리로 마운트)
touch gamepaper.db

# 이미지 스토리지 디렉토리 생성
mkdir -p storage/images

# 환경변수 파일 복사
cp .env.example .env
```

## 실행

```bash
docker compose up --build
```

서버 기동 후 약 60초 내에 `Started GamepaperApplication` 로그가 출력됩니다.

## DoD 검증

```bash
# 1. API 응답 확인
curl http://localhost:8080/api/games
# 예상: []

# 2. 테스트 데이터 삽입 후 재확인
sqlite3 gamepaper.db "INSERT INTO games (name, url, status, created_at) VALUES ('원신', 'https://hoyolab.com', 'ACTIVE', datetime('now'));"
curl http://localhost:8080/api/games
# 예상: [{"id":1,"name":"원신","wallpaperCount":0,"status":"ACTIVE","lastCrawledAt":null}]

# 3. 서버 재시작 후 데이터 유지 확인
docker compose restart backend
curl http://localhost:8080/api/games
# 예상: 동일 데이터 유지

# 4. WAL 모드 확인
sqlite3 gamepaper.db "PRAGMA journal_mode;"
# 예상: wal

# 5. 이미지 서빙 확인
mkdir -p storage/images/1
echo "fake-image" > storage/images/1/test.jpg
curl -I http://localhost:8080/storage/images/1/test.jpg
# 예상: HTTP/1.1 200
```

## 수동 검증 필요 항목

- ⬜ `docker compose up --build` 실행 후 서버 정상 기동 확인
- ⬜ `curl http://localhost:8080/api/games` → `[]` 반환 확인
- ⬜ SQLite DB 데이터 영속성 확인 (재시작 후 데이터 유지)
- ⬜ WAL 모드 활성화 확인 (`PRAGMA journal_mode` → `wal`)
- ⬜ 이미지 URL 접근 확인 (HTTP 200)
