# grow-timer 친구 서버

Cloudflare Workers + D1. 친구 코드·친구 스냅숏·익명 주간 신호만 다룬다. 자유 글은 받지 않는다.

```
npm ci
npm test                 # 로컬 D1로 wrangler dev를 띄워 API를 시험한다(계정 필요 없음)
npm run dev              # http://127.0.0.1:8787 (먼저: npx wrangler d1 migrations apply grow-timer --local)
```

배포(계정이 있을 때): `npx wrangler login` → `npx wrangler d1 create grow-timer` → 나온 id를 `wrangler.jsonc`에 → `npx wrangler d1 migrations apply grow-timer --remote` → `npm run deploy`.

비용 상한: 기기당 하루 스냅숏 1회, 친구 20명, 읽기 하루 20회, 등록 IP당 하루 5회, `DAILY_WRITE_BUDGET`(전역 하루 쓰기), `KILL=1`이면 전부 멈춤.
