# 다시, 학교 — AI 진단·최종 체크포인트 API 명세서 (v2)

폐교 활용 진단 백엔드(`dasi_backend`). 프론트가 폐교 정보를 요청으로 전달하면
백엔드가 Gemini + DB(유사사례 RAG)로 분석·검증한 결과를 **구조화된 JSON**으로 반환한다.

- Base URL(예): `http://172.18.156.26:8080` (LAN) / 로컬 `http://localhost:8080`
- Content-Type: `application/json; charset=UTF-8`
- CORS: `/api/**` 모든 오리진 허용 (MVP)
- AI 제공자: Google Gemini `gemini-3.5-flash` (키·프롬프트는 백엔드에서만 관리)

포함 API
1. `POST /api/v1/ai/idea-evaluations` — 아이디어 진단
2. `POST /api/v1/ai/final-checkpoints` — 학교별 최종 체크포인트
3. `POST /api/v1/ai/recommendations` — 활용모델 추천(아이디어 없음)
4. `GET /api/schools`, `GET /api/schools/{id}` — (로컬 테스트용) CSV 폐교 목록/상세

---

## 공통

### Grade 코드
`VERY_LOW`(매우 낮음) · `LOW`(낮음) · `MEDIUM`(보통) · `HIGH`(높음) · `VERY_HIGH`(매우 높음)

### 공통 `school` 객체 (모든 AI API 공통)
| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `schoolId` | String | O | 학교 고유 ID |
| `schoolName` | String | O | 학교명 |
| `address` | String | O | 소재지 |
| `closedDate` | String(`YYYY-MM-DD`) | X | 폐교일 (없으면 null) |
| `buildingArea` | Number(㎡) | X | 건물 연면적 (소수 허용) |
| `siteArea` | Number(㎡) | X | 전체 부지 면적 (소수 허용) |
| `utilizationPlan` | String | X | 현재 활용 상태/계획 (예: 미활용) |
| `promotionPlan` | String | X | 교육청/지자체 추진 계획 (예: 대부 또는 매각 검토) |
| `nearbyResources` | String[] | X | 주변 자원. 없으면 `[]` |

---

## 1. 아이디어 진단 — `POST /api/v1/ai/idea-evaluations`

### 요청
```json
{
  "school": { "schoolId": "school-001", "schoolName": "온혜초등학교", "address": "경상북도 안동시 도산면 온혜리",
    "closedDate": "2024-03-01", "buildingArea": 2450.5, "siteArea": 12600.0,
    "utilizationPlan": "미활용", "promotionPlan": "대부 또는 매각 검토",
    "nearbyResources": ["도산서원", "지역 농촌마을", "관광 이동 경로"] },
  "idea": "이 폐교를 유아 체험센터로 활용하고 싶어요."
}
```
- `idea`: 필수, 최소 10자 · 최대 1000자, 공백만 입력 불가.

### 성공 응답 (200)
```json
{
  "evaluationId": "eval-20260715-001",
  "schoolId": "school-001",
  "idea": "이 폐교를 유아 체험센터로 활용하고 싶어요.",
  "overallGrade": "HIGH",
  "overallScore": 78,
  "coverage": 80,
  "statusCode": "CONDITIONAL_REVIEW",
  "statusLabel": "일부 조건 확인 후 검토 가능",
  "summary": "활용 가능성이 있지만 운영 방식과 접근성 보완이 필요합니다.",
  "metrics": {
    "spaceSuitability":       { "grade": "HIGH",   "score": 4, "weight": 30, "weightedScore": 24.0, "reasons": ["...", "..."] },
    "accessibility":          { "grade": "MEDIUM", "score": 3, "weight": 20, "weightedScore": 12.0, "reasons": ["...", "..."] },
    "regionalDemand":         { "grade": "HIGH",   "score": 4, "weight": 20, "weightedScore": 16.0, "reasons": ["...", "..."] },
    "similarCaseSuitability": { "grade": "VERY_HIGH","score": 5,"weight": 10, "weightedScore": 10.0, "reasons": ["...", "..."] },
    "executionFeasibility":   { "grade": null,     "score": null, "weight": 20, "weightedScore": 0.0, "reasons": ["...미확인, 확인 필요..."] }
  },
  "strengths": ["...", "..."],
  "weaknesses": ["...", "..."],
  "alternativeModels": ["어린이 문화센터", "가족 체험형 주말 프로그램"],
  "recommendation": "지역 유아교육기관과 연계한 프로그램형 운영을 추천합니다.",
  "createdAt": "2026-07-15T14:30:00+09:00"
}
```

### 응답 필드
| 필드 | 타입 | 설명 |
|---|---|---|
| `evaluationId` | String | 진단 결과 고유 ID |
| `schoolId` | String | 요청 `school.schoolId`와 동일 |
| `idea` | String | 입력 아이디어 |
| `overallGrade` | Grade\|null | 종합 적합도(종합점수 구간에서 파생). 판단 제한이면 null |
| `overallScore` | Integer\|null | 종합점수 0~100 (규칙 계산). 판단 제한이면 null |
| `coverage` | Integer | 확인된 가중치 합(%) |
| `statusCode` | String | 결과 구간 코드(아래) |
| `statusLabel` | String | 결과 구간 사용자 표시 문구 |
| `summary` | String | 종합 평가 문장 |
| `metrics` | Object | 5개 세부 지표 (아래) |
| `strengths` / `weaknesses` / `alternativeModels` | String[] | 장점 / 단점·보완점 / 대안 모델 |
| `recommendation` | String | 최종 추천 문장 |
| `createdAt` | String | ISO 8601 |

### metrics 5개 지표 (각각 `{grade, score, weight, weightedScore, reasons}`)
| 키 | 가중치 | 의미 |
|---|:-:|---|
| `spaceSuitability` | 30 | 공간 적합성 (부지·건물 면적) |
| `accessibility` | 20 | 접근성 (도로·대중교통·생활권 거리) |
| `regionalDemand` | 20 | 지역 수요 (인구·관광·주변자원) |
| `similarCaseSuitability` | 10 | 유사사례 적합성 (유사 활용사례) |
| `executionFeasibility` | 20 | 실행 가능성 (**HIGH일수록 실행 쉬움**, 리스크와 반대) |

- `score`(1~5)/`grade`는 **규칙 엔진이 계산**(LLM 아님). 미확인이면 `score=null`,`grade=null`,`weightedScore=0`.
- `reasons`(String[], 2~3개)는 LLM이 계산된 점수를 **설명**한 한국어 문장. 미확인 항목은 무엇을 확인해야 하는지 설명.

### Core 진단 점수 계산 (규칙 기반)
- 각 기준 1~5점 → `weightedScore = (score/5)×weight`. `overallScore = round(Σ확인 가중점수 ÷ Σ확인 가중치 × 100)`.
- **미확인(null) 기준은 0점이 아니라 계산에서 제외**하고 `coverage`(확인된 가중치 합)를 함께 반환.
- `coverage < 60%` 이면 점수와 무관하게 `INSUFFICIENT_DATA`(판단 제한).
- 점수는 **결정적**(같은 입력→같은 점수). LLM은 점수를 정하지 않고 설명만 한다.

### statusCode 구간
| 종합점수 | statusCode | statusLabel |
|---|---|---|
| 80~100 | `HIGH_BASIC_FIT` | 기초 활용 가능성이 높음 |
| 60~79 | `CONDITIONAL_REVIEW` | 일부 조건 확인 후 검토 가능 |
| 40~59 | `MAJOR_CHECKS_REQUIRED` | 주요 조건 보완 필요 |
| 0~39 | `LIMITED_FIT` | 현재 확인된 조건상 검토 부담이 큼 |
| coverage<60% | `INSUFFICIENT_DATA` | 현재 자료만으로 판단 제한 |

> MVP 참고: 접근성·지역수요·실행가능성 3개 기준은 데모 6개 폐교만 사전 조사/큐레이션 값(`demo_school_signals.json`)을 사용해 커버리지 100%가 됩니다. 그 외 학교는 이 세 기준이 미확인이라 커버리지 40% → `INSUFFICIENT_DATA`(판단 제한)가 됩니다. 본 가중치는 초기 가설이며 "60점=성공확률 60%"로 해석하지 않습니다.

---

## 2. 학교별 최종 체크포인트 — `POST /api/v1/ai/final-checkpoints`

추천 선택 또는 아이디어 진단 결과를 바탕으로, 사업 검토 시 확인할 6개 카테고리 체크포인트를 생성한다.

### 요청 — 경로 A (추천 선택)
```json
{
  "sourceType": "RECOMMENDATION",
  "analysisId": "analysis-...",
  "school": { "...": "위 공통 school 객체" },
  "selectedRecommendation": {
    "recommendationId": "recommendation_01",
    "rank": 1,
    "title": "전통문화 체험형 교육공간",
    "description": "...",
    "advantages": ["...", "..."],
    "limitations": ["...", "..."]
  }
}
```
> `selectedRecommendation`은 추천 API 응답 항목의 `id/strengths/risks` 명칭으로 보내도 허용된다(서버에서 `recommendationId/advantages/limitations`로 매핑).

### 요청 — 경로 B (아이디어 진단)
```json
{
  "sourceType": "IDEA_EVALUATION",
  "analysisId": "eval-...",
  "school": { "...": "공통 school 객체" },
  "ideaEvaluation": { "...": "1번 idea-evaluations 응답 객체 전체" }
}
```

### 요청 필드
| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `sourceType` | String | O | `RECOMMENDATION` \| `IDEA_EVALUATION` |
| `analysisId` | String | O | 추천/진단 결과 ID |
| `school` | Object | O | 공통 school 객체 |
| `selectedRecommendation` | Object | 조건부 | `RECOMMENDATION`일 때 필수 |
| `ideaEvaluation` | Object | 조건부 | `IDEA_EVALUATION`일 때 필수 |

### 성공 응답 (200)
```json
{
  "checkpointId": "checkpoint-20260715-001",
  "analysisId": "eval-20260715-001",
  "schoolId": "school-001",
  "items": [
    { "category": "ADMINISTRATION", "priority": "HIGH", "note": "...", "tip": "..." },
    { "category": "FACILITY", "priority": "HIGH", "note": "...", "tip": "..." },
    { "category": "BUDGET", "priority": "MEDIUM", "note": "...", "tip": "..." },
    { "category": "DEMAND", "priority": "MEDIUM", "note": "...", "tip": "..." },
    { "category": "OPERATING_SUSTAINABILITY", "priority": "MEDIUM", "note": "...", "tip": "..." },
    { "category": "COMMUNITY_ACCEPTANCE", "priority": "LOW", "note": "...", "tip": "..." }
  ],
  "createdAt": "2026-07-15T14:35:00+09:00"
}
```
- `items`는 **정확히 6개**, 아래 **고정 순서**로 반환된다.

### 카테고리 (고정 6개·순서 유지)
| 순서 | category | 화면 제목 |
|---:|---|---|
| 1 | `ADMINISTRATION` | 행정 |
| 2 | `FACILITY` | 시설 |
| 3 | `BUDGET` | 예산 |
| 4 | `DEMAND` | 수요 |
| 5 | `OPERATING_SUSTAINABILITY` | 운영 지속성 |
| 6 | `COMMUNITY_ACCEPTANCE` | 지역수용성 |

### 항목 구조 / 우선순위
| 필드 | 타입 | 설명 |
|---|---|---|
| `category` | CheckpointCategory | 고정 카테고리 |
| `priority` | Priority | `HIGH`(우선 확인) / `MEDIUM`(확인 필요) / `LOW`(참고) — 카테고리별 고정 아님, 학교·모델에 따라 달라짐 |
| `note` | String | 학교·활용모델 맞춤 확인 문장 |
| `tip` | String | 실무 참고 팁 |

---

## 3. 활용모델 추천 — `POST /api/v1/ai/recommendations`

아이디어가 없는 경우. `{ "school": {공통 school 객체} }`를 받아 **정확히 3개** 추천.

```json
{
  "analysisId": "analysis-...",
  "schoolId": "school-001",
  "recommendations": [
    { "id": "recommendation_01", "rank": 1, "title": "...", "description": "...",
      "strengths": ["...","..."], "risks": ["...","..."] }
  ],
  "createdAt": "2026-07-15T..."
}
```

---

## 공통 오류 응답

```json
{ "code": "IDEA_TOO_SHORT", "message": "아이디어는 10자 이상 입력해 주세요.", "requestId": "request-..." }
```

| HTTP | code | 설명 |
|---:|---|---|
| 400 | `INVALID_REQUEST` | 요청 형식 오류 |
| 400 | `INVALID_SOURCE_TYPE` | 지원하지 않는 sourceType |
| 400 | `SCHOOL_DATA_MISSING` | 필수 학교 정보 없음(schoolId/schoolName) |
| 400 | `IDEA_REQUIRED` | 아이디어 미입력 |
| 400 | `IDEA_TOO_SHORT` | 아이디어 10자 미만 |
| 400 | `IDEA_TOO_LONG` | 아이디어 1000자 초과 |
| 400 | `SELECTED_RECOMMENDATION_REQUIRED` | 추천 경로에 선택 모델 없음 |
| 400 | `IDEA_EVALUATION_REQUIRED` | 진단 경로에 진단 결과 없음 |
| 429 | `RATE_LIMIT_EXCEEDED` | 요청 한도 초과 |
| 500 | `AI_RESPONSE_FAILED` | AI 응답 생성 실패 |
| 500 | `AI_RESPONSE_INVALID` | 아이디어 진단 응답 형식 오류 |
| 500 | `CHECKPOINT_RESPONSE_INVALID` | 체크포인트 응답 형식 오류 |
| 504 | `AI_REQUEST_TIMEOUT` | AI 요청 시간 초과 |
| 500 | `INTERNAL_SERVER_ERROR` | 서버 내부 오류 |

---

## 백엔드 응답 검증 (구현됨)

**아이디어 진단**: `overallGrade`가 Grade인지, `metrics` 5개 항목 존재, 각 항목 `grade` + `reasons` 2~3개(비어있지 않은 한국어)인지 검증. 실패 시 `AI_RESPONSE_INVALID`.

**최종 체크포인트**: `items` 6개, 6개 카테고리가 각 1회, 명세 순서로 정렬, `priority`가 HIGH/MEDIUM/LOW, `note`·`tip` 비어있지 않은지 검증. 실패 시 `CHECKPOINT_RESPONSE_INVALID`.

**AI 생성 주의(프롬프트에 반영)**: 주민 동의 비율·리모델링 비용·건물 안전등급·전기/소방/수도 상태·이용자 수·대부/매각 확정 여부·근거 없는 법령을 사실처럼 생성 금지. 미확인 사항은 "확인이 필요합니다 / 사전 협의가 필요합니다 / 현장 점검이 필요합니다" 형태로 표현.

> 타임아웃: LLM 응답이 수 초~수십 초 걸릴 수 있으므로 프론트 타임아웃 30초 이상 권장.
