// grow-timer 친구 서버 v1 (design.md 6절, D59·D63).
// - 기기는 스스로 만든 무작위 키로 자신을 밝힌다. 서버는 키의 해시만 둔다. 이름·이메일·전화 없음.
// - 친구에게 보이는 것은 숫자와 정해진 값뿐이다(날짜별 분, 정원 씨앗·누적, 자세 키, 아이콘 키·색 번호, 고르기식 후기).
//   스냅숏을 받을 때 모양을 검사해 자유 글이 들어갈 자리를 없앤다. 서버 로그에도 본문을 남기지 않는다.
// - 비용 상한: 기기당 하루 스냅숏 1회, 친구 20명, 읽기 하루 20회, 등록 IP당 하루 5회, 전역 하루 쓰기 예산, KILL 스위치.

export interface Env {
  DB: D1Database
  DAILY_WRITE_BUDGET: string
  KILL: string
  TEXT_REVIEWS: string
}

const LIMITS = { friends: 20, readsPerDay: 20, registersPerIpPerDay: 5, snapshotBytes: 16_384, days: 371 }
const ICONS = new Set(["reading", "instrument", "drawing", "knitting", "writing", "language", "study", "exercise", "cooking", "photo", "gardening", "coding", "custom", "default"])
const POSES = new Set(["rest", "book", "guitar", "sketch", "knit"])
const REPORT_REASONS = new Set(["spam", "inappropriate", "other"])
const CODE_ABC = "23456789ABCDEFGHJKMNPQRSTUVWXYZ" // 헷갈리는 0·O·1·I·L 없음

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" } })
const err = (status: number, code: string) => json(status, { error: code })

const day = (t = Date.now()) => new Date(t).toISOString().slice(0, 10)

async function sha256(s: string): Promise<string> {
  const d = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(s))
  return [...new Uint8Array(d)].map((b) => b.toString(16).padStart(2, "0")).join("")
}

function newCode(): string {
  const r = crypto.getRandomValues(new Uint8Array(8))
  return [...r].map((b) => CODE_ABC[b % CODE_ABC.length]).join("")
}

/** 쓰기 하나를 예산에서 뺀다. 넘었으면 false(→ 503, 앱은 "친구 화면은 잠시 쉬는 중이에요"). */
async function spend(env: Env): Promise<boolean> {
  const cap = Number(env.DAILY_WRITE_BUDGET || "0")
  const row = await env.DB.prepare(
    "INSERT INTO budget(day, writes) VALUES (?1, 1) ON CONFLICT(day) DO UPDATE SET writes = writes + 1 RETURNING writes",
  ).bind(day()).first<{ writes: number }>()
  return (row?.writes ?? 0) <= cap
}

async function deviceOf(req: Request, env: Env): Promise<{ id: string; code: string } | null> {
  const h = req.headers.get("authorization") ?? ""
  const key = h.startsWith("Bearer ") ? h.slice(7) : ""
  if (key.length < 32 || key.length > 128) return null
  return env.DB.prepare("SELECT id, code FROM device WHERE key_hash = ?1").bind(await sha256(key)).first()
}

async function body(req: Request, max: number): Promise<any | null> {
  const t = await req.text()
  if (t.length > max) return null
  try { return JSON.parse(t) } catch { return null }
}

const isInt = (v: unknown, lo: number, hi: number) => typeof v === "number" && Number.isInteger(v) && v >= lo && v <= hi

/**
 * 스냅숏 검사. 통과한 값만 다시 만들어 저장한다(모르는 칸은 버린다). 자유 글 후기(kind = text)는 스위치가 켜지기 전까지 거절(D59).
 * 돌려주는 값: 정리한 스냅숏 또는 거절 이유.
 */
export function cleanSnapshot(s: any, textReviews: boolean): { ok: any } | { bad: string } {
  if (!s || s.v !== 1) return { bad: "version" }
  if (!Array.isArray(s.days) || s.days.length > LIMITS.days || !s.days.every((m: unknown) => isInt(m, 0, 1440))) return { bad: "days" }
  if (typeof s.lastDay !== "string" || !/^\d{4}-\d{2}-\d{2}$/.test(s.lastDay)) return { bad: "lastDay" }
  if (!isInt(s.totalSec, 0, 1_000_000_000)) return { bad: "totalSec" }
  if (typeof s.gardenSeed !== "string" || !/^-?\d{1,20}$/.test(s.gardenSeed)) return { bad: "gardenSeed" }
  if (!POSES.has(s.pose)) return { bad: "pose" }
  const acts = s.acts ?? []
  if (!Array.isArray(acts) || acts.length > 12 || !acts.every((a: any) => ICONS.has(a?.icon) && isInt(a?.color, 0, 11) && isInt(a?.sec, 0, 1_000_000_000))) return { bad: "acts" }
  const reviews = s.reviews ?? []
  if (!Array.isArray(reviews) || reviews.length > 20) return { bad: "reviews" }
  for (const r of reviews) {
    if (r?.kind === "text" && !textReviews) return { bad: "text" }
    if (!["preset", "catalog"].includes(r?.kind) || !ICONS.has(r?.icon) || !isInt(r?.stars, 1, 5) || !isInt(r?.line, 0, 63)) return { bad: "review" }
    if (r.kind === "catalog" && (typeof r.catalog !== "string" || !/^OL\d{1,10}[WM]$/.test(r.catalog))) return { bad: "catalog" }
  }
  return {
    ok: {
      v: 1, days: s.days, lastDay: s.lastDay, totalSec: s.totalSec, gardenSeed: s.gardenSeed, pose: s.pose,
      acts: acts.map((a: any) => ({ icon: a.icon, color: a.color, sec: a.sec })),
      reviews: reviews.map((r: any) => ({ kind: r.kind, icon: r.icon, stars: r.stars, line: r.line, ...(r.kind === "catalog" ? { catalog: r.catalog } : {}) })),
    },
  }
}

async function codeTarget(env: Env, code: unknown): Promise<string | null> {
  if (typeof code !== "string" || !/^[2-9A-HJKMNP-Z]{8}$/.test(code)) return null
  const row = await env.DB.prepare("SELECT id FROM device WHERE code = ?1").bind(code).first<{ id: string }>()
  return row?.id ?? null
}

export default {
  async fetch(req: Request, env: Env): Promise<Response> {
    const url = new URL(req.url)
    const path = url.pathname
    const m = req.method
    if (env.KILL === "1") return err(503, "paused") // 서버 쪽 차단 스위치: 전부 멈춘다
    const write = m !== "GET"
    if (write && !(await spend(env))) return err(503, "budget") // 예산을 넘으면 읽기만 된다

    // 익명 주간 신호(D63): 기기 키를 받지 않는다.
    if (m === "POST" && path === "/v1/signal") {
      const b = await body(req, 256)
      if (!b || typeof b.installWeek !== "string" || !/^\d{4}-W\d{2}$/.test(b.installWeek) || !isInt(b.daysActive, 0, 7)) return err(400, "bad")
      const now = new Date()
      const week = isoWeek(now)
      await env.DB.prepare("INSERT INTO signal VALUES (?1, ?2, ?3, 1) ON CONFLICT DO UPDATE SET n = n + 1")
        .bind(b.installWeek, week, b.daysActive).run()
      return json(200, { ok: true })
    }

    if (m === "POST" && path === "/v1/devices") {
      const b = await body(req, 512)
      if (!b || typeof b.key !== "string" || b.key.length < 32 || b.key.length > 128) return err(400, "bad")
      const ip = req.headers.get("cf-connecting-ip") ?? "local"
      const ipHash = await sha256(ip + "|" + day()) // 하루가 지나면 이어 볼 수 없다
      const n = await env.DB.prepare("INSERT INTO ip_day VALUES (?1, ?2, 1) ON CONFLICT DO UPDATE SET n = n + 1 RETURNING n")
        .bind(ipHash, day()).first<{ n: number }>()
      if ((n?.n ?? 0) > LIMITS.registersPerIpPerDay) return err(429, "limit")
      const keyHash = await sha256(b.key)
      const old = await env.DB.prepare("SELECT code FROM device WHERE key_hash = ?1").bind(keyHash).first<{ code: string }>()
      if (old) return json(200, { code: old.code }) // 같은 키로 다시 와도 같은 코드
      for (let i = 0; i < 5; i++) {
        const code = newCode()
        const r = await env.DB.prepare("INSERT OR IGNORE INTO device(id, key_hash, code, created_at) VALUES (?1, ?2, ?3, ?4)")
          .bind(crypto.randomUUID(), keyHash, code, Date.now()).run()
        if (r.meta.changes === 1) return json(200, { code })
      }
      return err(500, "code")
    }

    const me = await deviceOf(req, env)
    if (!me) return err(401, "auth")

    if (m === "PUT" && path === "/v1/snapshot") {
      const b = await body(req, LIMITS.snapshotBytes)
      if (!b) return err(400, "bad")
      const c = cleanSnapshot(b, env.TEXT_REVIEWS === "1")
      if ("bad" in c) return err(400, c.bad)
      const r = await env.DB.prepare(
        "UPDATE device SET snapshot = ?1, snapshot_at = ?2, snapshot_day = ?3 WHERE id = ?4 AND (snapshot_day IS NULL OR snapshot_day <> ?3)",
      ).bind(JSON.stringify(c.ok), Date.now(), day(), me.id).run()
      if (r.meta.changes === 0) return err(429, "daily") // 하루 1회(D59)
      return json(200, { ok: true })
    }

    if (m === "GET" && path === "/v1/friends") {
      const r = await env.DB.prepare(
        "UPDATE device SET reads = CASE WHEN reads_day = ?1 THEN reads + 1 ELSE 1 END, reads_day = ?1 WHERE id = ?2 RETURNING reads",
      ).bind(day(), me.id).first<{ reads: number }>()
      if ((r?.reads ?? 0) > LIMITS.readsPerDay) return err(429, "limit")
      const friends = await env.DB.prepare(`
        SELECT d.code, d.snapshot, d.snapshot_at FROM link l1
        JOIN link l2 ON l2.a = l1.b AND l2.b = l1.a
        JOIN device d ON d.id = l1.b
        WHERE l1.a = ?1
          AND NOT EXISTS (SELECT 1 FROM block k WHERE (k.a = ?1 AND k.b = l1.b) OR (k.a = l1.b AND k.b = ?1))
        ORDER BY l1.created_at`).bind(me.id).all<{ code: string; snapshot: string | null; snapshot_at: number | null }>()
      const waiting = await env.DB.prepare(`
        SELECT d.code FROM link l1 JOIN device d ON d.id = l1.b
        WHERE l1.a = ?1 AND NOT EXISTS (SELECT 1 FROM link l2 WHERE l2.a = l1.b AND l2.b = l1.a)`).bind(me.id).all<{ code: string }>()
      return json(200, {
        me: me.code,
        friends: friends.results.map((f) => ({ code: f.code, snapshot: f.snapshot ? JSON.parse(f.snapshot) : null, at: f.snapshot_at })),
        waiting: waiting.results.map((w) => w.code),
      })
    }

    if (m === "POST" && path === "/v1/friends") {
      const b = await body(req, 256)
      const target = await codeTarget(env, b?.code)
      if (!target) return err(404, "code")
      if (target === me.id) return err(400, "self")
      const blocked = await env.DB.prepare("SELECT 1 FROM block WHERE (a = ?1 AND b = ?2) OR (a = ?2 AND b = ?1)").bind(me.id, target).first()
      if (blocked) return err(404, "code") // 막힌 사이는 "없는 코드"와 구분하지 않는다
      const count = await env.DB.prepare("SELECT COUNT(*) AS n FROM link WHERE a = ?1").bind(me.id).first<{ n: number }>()
      if ((count?.n ?? 0) >= LIMITS.friends) return err(409, "full")
      await env.DB.prepare("INSERT OR IGNORE INTO link VALUES (?1, ?2, ?3)").bind(me.id, target, Date.now()).run()
      const back = await env.DB.prepare("SELECT 1 FROM link WHERE a = ?1 AND b = ?2").bind(target, me.id).first()
      return json(200, { status: back ? "friends" : "waiting" })
    }

    const del = path.match(/^\/v1\/friends\/([2-9A-HJKMNP-Z]{8})$/)
    if (m === "DELETE" && del) {
      const target = await codeTarget(env, del[1])
      if (target) await env.DB.prepare("DELETE FROM link WHERE (a = ?1 AND b = ?2) OR (a = ?2 AND b = ?1)").bind(me.id, target).run()
      return json(200, { ok: true })
    }

    if (m === "POST" && path === "/v1/blocks") {
      const b = await body(req, 256)
      const target = await codeTarget(env, b?.code)
      if (!target || target === me.id) return err(404, "code")
      await env.DB.batch([
        env.DB.prepare("INSERT OR IGNORE INTO block VALUES (?1, ?2, ?3)").bind(me.id, target, Date.now()),
        env.DB.prepare("DELETE FROM link WHERE (a = ?1 AND b = ?2) OR (a = ?2 AND b = ?1)").bind(me.id, target),
      ])
      return json(200, { ok: true })
    }

    if (m === "POST" && path === "/v1/reports") {
      const b = await body(req, 256)
      const target = await codeTarget(env, b?.code)
      if (!target || !REPORT_REASONS.has(b?.reason)) return err(400, "bad")
      await env.DB.prepare("INSERT INTO report(reporter, target, reason, created_at) VALUES (?1, ?2, ?3, ?4)").bind(me.id, target, b.reason, Date.now()).run()
      return json(200, { ok: true })
    }

    // 기기와 그 흔적을 지운다(앱의 "친구 기능 끄기").
    if (m === "DELETE" && path === "/v1/devices/me") {
      await env.DB.batch([
        env.DB.prepare("DELETE FROM link WHERE a = ?1 OR b = ?1").bind(me.id),
        env.DB.prepare("DELETE FROM device WHERE id = ?1").bind(me.id),
      ])
      return json(200, { ok: true })
    }

    return err(404, "path")
  },
}

/** ISO 주(예: 2026-W48). */
export function isoWeek(d: Date): string {
  const t = new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), d.getUTCDate()))
  const dow = t.getUTCDay() || 7
  t.setUTCDate(t.getUTCDate() + 4 - dow)
  const y = t.getUTCFullYear()
  const w = Math.ceil(((t.getTime() - Date.UTC(y, 0, 1)) / 86400000 + 1) / 7)
  return `${y}-W${String(w).padStart(2, "0")}`
}
