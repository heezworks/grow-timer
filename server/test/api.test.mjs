// 서버 시험: 시험마다 새 로컬 D1로 `wrangler dev --local`을 띄우고 실제 HTTP로 묻는다(계정·인터넷 필요 없음).
// 실행: npm test
import { test } from "node:test"
import assert from "node:assert/strict"
import { spawn, execFileSync } from "node:child_process"
import { mkdtempSync, rmSync } from "node:fs"
import { tmpdir } from "node:os"
import { join } from "node:path"
import { randomBytes } from "node:crypto"

let nextPort = 8790
async function server(vars = {}) {
  const dir = mkdtempSync(join(tmpdir(), "gt-"))
  const port = nextPort++
  execFileSync("npx", ["wrangler", "d1", "migrations", "apply", "grow-timer", "--local", "--persist-to", dir], { stdio: "ignore", env: { ...process.env, CI: "1" } })
  const args = ["wrangler", "dev", "--local", "--port", String(port), "--persist-to", dir, "--ip", "127.0.0.1"]
  for (const [k, v] of Object.entries(vars)) args.push("--var", `${k}:${v}`)
  const p = spawn("npx", args, { stdio: "ignore", env: { ...process.env, CI: "1" }, detached: true })
  const base = `http://127.0.0.1:${port}`
  for (let i = 0; i < 120; i++) {
    try { await fetch(base + "/v1/x"); break } catch { await new Promise((r) => setTimeout(r, 250)) }
  }
  return {
    base,
    stop() { try { process.kill(-p.pid) } catch {} ; rmSync(dir, { recursive: true, force: true }) },
  }
}

const key = () => randomBytes(32).toString("base64url")
async function call(base, method, path, k, b) {
  const r = await fetch(base + path, { method, headers: { "content-type": "application/json", ...(k ? { authorization: `Bearer ${k}` } : {}) }, body: b === undefined ? undefined : JSON.stringify(b) })
  return { status: r.status, body: await r.json() }
}
const snap = (over = {}) => ({ v: 1, days: Array(371).fill(0).map((_, i) => i % 3 ? 30 : 0), lastDay: "2026-11-23", totalSec: 123456, gardenSeed: "-8812345678901234567", pose: "guitar", acts: [{ icon: "instrument", color: 1, sec: 3600 }], reviews: [{ kind: "catalog", icon: "reading", stars: 5, line: 2, catalog: "OL45883W" }], ...over })

test("친구 맺기는 양쪽이 코드를 넣어야 되고, 스냅숏은 정해진 값만 보인다", async () => {
  const s = await server()
  try {
    const ka = key(), kb = key()
    const a = (await call(s.base, "POST", "/v1/devices", null, { key: ka })).body.code
    const b = (await call(s.base, "POST", "/v1/devices", null, { key: kb })).body.code
    assert.match(a, /^[2-9A-HJKMNP-Z]{8}$/)
    assert.equal((await call(s.base, "POST", "/v1/devices", null, { key: ka })).body.code, a, "같은 키는 같은 코드")
    assert.equal((await call(s.base, "POST", "/v1/friends", ka, { code: b })).body.status, "waiting")
    let fa = (await call(s.base, "GET", "/v1/friends", ka)).body
    assert.deepEqual(fa.friends, []); assert.deepEqual(fa.waiting, [b])
    assert.equal((await call(s.base, "POST", "/v1/friends", kb, { code: a })).body.status, "friends")
    assert.equal((await call(s.base, "PUT", "/v1/snapshot", kb, { ...snap(), name: "몰래 넣은 글" })).status, 200)
    fa = (await call(s.base, "GET", "/v1/friends", ka)).body
    assert.equal(fa.friends.length, 1)
    assert.equal(fa.friends[0].snapshot.pose, "guitar")
    assert.equal(fa.friends[0].snapshot.name, undefined, "모르는 칸은 버린다")
    assert.equal((await call(s.base, "PUT", "/v1/snapshot", kb, snap())).status, 429, "하루 1회")
    assert.equal((await call(s.base, "POST", "/v1/friends", ka, { code: a })).status, 400, "자기 자신")
    assert.equal((await call(s.base, "POST", "/v1/friends", ka, { code: "ZZZZZZZZ" })).status, 404)
    assert.equal((await call(s.base, "GET", "/v1/friends", null)).status, 401)
  } finally { s.stop() }
})

test("자유 글과 이상한 값은 거절한다", async () => {
  const s = await server()
  try {
    const k = key(); await call(s.base, "POST", "/v1/devices", null, { key: k })
    const bad = [
      snap({ reviews: [{ kind: "text", icon: "reading", stars: 3, line: 0, text: "hello" }] }),
      snap({ pose: "<script>" }),
      snap({ acts: [{ icon: "아무 글", color: 1, sec: 1 }] }),
      snap({ days: [99999] }),
      snap({ gardenSeed: "abc" }),
      snap({ reviews: [{ kind: "catalog", icon: "reading", stars: 4, line: 1, catalog: "not-an-id" }] }),
    ]
    for (const b of bad) assert.equal((await call(s.base, "PUT", "/v1/snapshot", k, b)).status, 400, JSON.stringify(b).slice(0, 80))
  } finally { s.stop() }
})

test("막으면 서로 사라지고 다시 맺을 수 없다. 끊기·신고·기기 지우기", async () => {
  const s = await server()
  try {
    const ka = key(), kb = key()
    const a = (await call(s.base, "POST", "/v1/devices", null, { key: ka })).body.code
    const b = (await call(s.base, "POST", "/v1/devices", null, { key: kb })).body.code
    await call(s.base, "POST", "/v1/friends", ka, { code: b }); await call(s.base, "POST", "/v1/friends", kb, { code: a })
    assert.equal((await call(s.base, "POST", "/v1/reports", ka, { code: b, reason: "spam" })).status, 200)
    assert.equal((await call(s.base, "POST", "/v1/reports", ka, { code: b, reason: "자유 글" })).status, 400)
    await call(s.base, "POST", "/v1/blocks", ka, { code: b })
    assert.deepEqual((await call(s.base, "GET", "/v1/friends", kb)).body.friends, [])
    assert.equal((await call(s.base, "POST", "/v1/friends", kb, { code: a })).status, 404, "막힌 사이")
    assert.equal((await call(s.base, "DELETE", "/v1/devices/me", ka)).status, 200)
    assert.equal((await call(s.base, "GET", "/v1/friends", ka)).status, 401)
  } finally { s.stop() }
})

test("한도: 친구 20명, 읽기 하루 20회, 등록 IP당 하루 5회", async () => {
  const s = await server()
  try {
    const keys = Array.from({ length: 5 }, key)
    for (const k of keys) assert.equal((await call(s.base, "POST", "/v1/devices", null, { key: k })).status, 200)
    assert.equal((await call(s.base, "POST", "/v1/devices", null, { key: key() })).status, 429, "여섯째 등록")
    const k = keys[0]
    for (let i = 0; i < 20; i++) assert.equal((await call(s.base, "GET", "/v1/friends", k)).status, 200)
    assert.equal((await call(s.base, "GET", "/v1/friends", k)).status, 429, "스물한째 읽기")
  } finally { s.stop() }
})

test("전역 예산을 넘으면 쓰기만 503, KILL이면 전부 503", async () => {
  const s = await server({ DAILY_WRITE_BUDGET: "2" })
  try {
    const k = key()
    assert.equal((await call(s.base, "POST", "/v1/devices", null, { key: k })).status, 200) // 쓰기 1
    assert.equal((await call(s.base, "POST", "/v1/signal", null, { installWeek: "2026-W48", daysActive: 3 })).status, 200) // 쓰기 2
    assert.equal((await call(s.base, "PUT", "/v1/snapshot", k, snap())).status, 503, "예산 넘음")
    assert.equal((await call(s.base, "GET", "/v1/friends", k)).status, 200, "읽기는 된다")
  } finally { s.stop() }
  const s2 = await server({ KILL: "1" })
  try { assert.equal((await call(s2.base, "GET", "/v1/friends", key())).status, 503) } finally { s2.stop() }
})

test("익명 신호는 기기 키 없이, 모양만 맞으면", async () => {
  const s = await server()
  try {
    assert.equal((await call(s.base, "POST", "/v1/signal", null, { installWeek: "2026-W48", daysActive: 5 })).status, 200)
    assert.equal((await call(s.base, "POST", "/v1/signal", null, { installWeek: "last week", daysActive: 5 })).status, 400)
    assert.equal((await call(s.base, "POST", "/v1/signal", null, { installWeek: "2026-W48", daysActive: 9 })).status, 400)
  } finally { s.stop() }
})
