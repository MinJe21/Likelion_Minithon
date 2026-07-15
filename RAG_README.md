# 다시, 학교 — 폐교 활용 진단 서비스 (dasi_backend)

Spring Boot 4.1 + Spring AI 2.0 + Gemini + pgvector 기반.
자유 챗봇이 아니라, **시스템이 보유한 폐교 데이터·유사사례에 정해진 판단 기준을 적용해
구조화된 진단 리포트(JSON)를 생성**하는 LLM 기반 AI 진단 서비스다.
(MD 스펙 `다시학교_MVP_개발자_공유문서_AI구축상세_최종.md` 기반, **수요 관련 기능 제외**)

## 흐름
```
폐교 선택(CSV 전국 1,194개) → 아이디어 유무 선택
  ├ 아이디어 없음  → 활용모델 TOP 3 추천
  └ 아이디어 있음  → 아이디어 적합성 진단
→ 판단 4축 등급 + 근거 + 유사사례(RAG) + 리스크 + 초기 실행 체크리스트
```

## 판단 기준 (수요 축 제외 → 4축)
공간 적합성 · 접근성 · 유사사례 적합성 · 실행 리스크 (각 높음/중간/낮음)

## 데이터
- `src/main/resources/data/closed_schools.csv` — 전국 폐교 1,194건 (폐교 DB)
- `src/main/resources/data/use_models.json` — 활용모델 후보 6종
- `src/main/resources/data/case_library.json` — 유사사례 6건 (pgvector로 RAG 검색)

## API (프론트 계약 = message.txt)
| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/api/v1/ai/recommendations` | 활용모델 추천 (아이디어 없음) — 정확히 3개 |
| POST | `/api/v1/ai/idea-evaluations` | 아이디어 적합성 진단 (아이디어 있음) |
| GET | `/api/schools?region=&level=&status=&keyword=&page=&size=` | (로컬 테스트용) CSV 폐교 목록 |
| GET | `/api/schools/{schoolId}` | (로컬 테스트용) 폐교 상세 |

**폐교 정보는 프론트가 request 로 전송한다** (백엔드 CSV 조회 아님).
```json
// POST /api/v1/ai/recommendations
{ "school": { "id","name","address","closedDate","landAreaSqm","buildingAreaSqm","plan","nearbyResources":[] } }
// POST /api/v1/ai/idea-evaluations
{ "school": {...}, "idea": "이 폐교를 유아 체험센터로 활용하고 싶어요." }
```
- 등급 코드: `VERY_LOW / LOW / MEDIUM / HIGH / VERY_HIGH` (영어). `executionFeasibility`는 HIGH=실행 쉬움.
- 오류 응답: `{ "code", "message", "requestId" }` (INVALID_REQUEST, SCHOOL_DATA_MISSING, IDEA_REQUIRED, IDEA_TOO_SHORT/LONG, AI_RESPONSE_FAILED/INVALID, ...).
- CORS: `/api/**` 모든 오리진 허용(MVP, `common/WebConfig`).

> 참고: idea-evaluations 는 계약상 `metrics.regionalDemand`(지역 수요)를 포함한다(이전 '수요 제외'와 상충하나 프론트 계약 우선).
> 참고: AI 제공자는 Gemini(gemini-3.5-flash) 사용 중. 스펙의 "OpenRouter" 요구는 키 서버측 관리 취지이며, 필요 시 OpenAI 호환 스타터로 OpenRouter 스왑 가능.

## 실행
```bash
docker compose up -d                 # 1. pgvector Postgres
export GEMINI_API_KEY=...             # 2. https://aistudio.google.com/apikey (또는 .env.local)
./gradlew bootRun                     # 3. 최초 기동 시 유사사례 임베딩 적재
```
브라우저에서 **http://localhost:8080** → 폐교 검색·선택 → 진단.

> 로컬 실행 편의: `.env.local`(gitignore됨)에 `GEMINI_API_KEY=...` 를 넣고
> `set -a; . ./.env.local; set +a; ./gradlew bootRun` 로 주입 가능.

## 주요 구성요소
- `school/ClosedSchoolRepository` — CSV 인메모리 적재/검색
- `diagnose/CatalogService` — 활용모델·유사사례 로드
- `diagnose/CaseIngestionRunner` — 유사사례 pgvector 적재(RAG)
- `diagnose/DiagnoseService` — 프롬프트 구성 + RAG 검색 + 구조화 출력(`.entity()`)
- `diagnose/dto/*` — 출력 스키마(수요 축·항목 제외)
- `common/GlobalExceptionHandler` — 404/400 정상 처리 + 500 원인 노출

## 모델 설정 주의
- 채팅 모델은 `gemini-3.5-flash` (application.yml). `gemini-2.5-flash`는 신규 사용자 차단(404).
- 임베딩 `gemini-embedding-001` 768차원 = `spring.ai.vectorstore.pgvector.dimensions` 와 일치해야 함.

## 안전장치
단정 금지("성공합니다"/"법적으로 가능합니다"), "확인 필요" 형태 표현 — `DiagnoseService.SYSTEM_PROMPT`.
결과 하단에 "초기 검토용 진단" 고지 표시.
