-- 친구 기능 서버 스키마 v1. 자유 글을 담는 칸은 없다(D59).

-- 기기: 무작위 키의 해시와 친구 코드. 이름·이메일·전화 없음.
CREATE TABLE device (
  id TEXT PRIMARY KEY,
  key_hash TEXT NOT NULL UNIQUE,
  code TEXT NOT NULL UNIQUE,
  created_at INTEGER NOT NULL,
  snapshot TEXT,            -- 친구에게 보일 숫자(JSON, 검사를 통과한 것만)
  snapshot_at INTEGER,
  snapshot_day TEXT,        -- 하루 1회(YYYY-MM-DD, UTC)
  reads_day TEXT,
  reads INTEGER NOT NULL DEFAULT 0
);

-- a가 b의 코드를 넣었다. 양쪽 다 넣으면(a→b, b→a) 친구다.
CREATE TABLE link (
  a TEXT NOT NULL,
  b TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  PRIMARY KEY (a, b)
);
CREATE INDEX link_b ON link(b);

-- a가 b를 막았다. 막으면 서로 친구 목록에서 사라지고 다시 맺을 수 없다.
CREATE TABLE block (
  a TEXT NOT NULL,
  b TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  PRIMARY KEY (a, b)
);

-- 신고: 이유는 정해진 값만.
CREATE TABLE report (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  reporter TEXT NOT NULL,
  target TEXT NOT NULL,
  reason TEXT NOT NULL,
  created_at INTEGER NOT NULL
);

-- 기기 등록 한도(IP당 하루 5회). IP는 그날의 소금과 섞은 해시만 남긴다.
CREATE TABLE ip_day (
  ip_hash TEXT NOT NULL,
  day TEXT NOT NULL,
  n INTEGER NOT NULL,
  PRIMARY KEY (ip_hash, day)
);

-- 전역 하루 쓰기 예산.
CREATE TABLE budget (
  day TEXT PRIMARY KEY,
  writes INTEGER NOT NULL
);

-- 익명 주간 신호(D63): 설치한 주 × 보낸 주 × 그 주에 시간을 보낸 날 수 → 몇 대. 기기 키 없음.
CREATE TABLE signal (
  install_week TEXT NOT NULL,
  week TEXT NOT NULL,
  days_active INTEGER NOT NULL,
  n INTEGER NOT NULL,
  PRIMARY KEY (install_week, week, days_active)
);
