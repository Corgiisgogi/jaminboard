# 관리자 답변 수정/삭제 설계

## 목표

장비 고객 문의 게시판(jaminboard)에서 **관리자가 등록한 답변을 수정·삭제**할 수 있게 한다. 현재는 답변 등록(`POST /answer/create/{id}`)만 가능하다.

## 결정 사항 (브레인스토밍 합의)

1. **권한 범위:** ROLE_ADMIN이면 누구나 모든 답변을 수정·삭제. 답변 작성자(author) 일치 여부는 검사하지 않는다. (`SecurityConfig`가 `/answer/**` 전체를 `hasRole("ADMIN")`으로 보호하므로 컨트롤러 추가 권한 코드 불필요.)
2. **수정 UI:** 별도 수정 페이지 `answer_modify.html` (기존 `question_modify.html` 패턴과 일관). GET 폼 → POST 저장 → 상세로 redirect.
3. **수정 시각:** `Answer`에 `modifyDate` 추가, 수정 시 `now()` 기록, 상세에서 수정된 답변에 `(수정됨: yyyy-MM-dd HH:mm)` 표기.
4. **삭제 방식:** `GET /answer/delete/{id}` + JS `confirm` (기존 `question` 삭제 패턴과 일관).

## 불변 규칙 (전 작업 공통)

신규 라이브러리는 Spring Security 외 추가 금지 · 클래스명/패키지명/프로젝트명 변경 금지(필드/메서드/파일만 추가) · 상세=card · 버튼=btn 클래스 · 전체=container · 공통=layout.html · 표준 HTML · 검증은 브라우저 + H2(자동 테스트 없음).

## 변경 대상

**수정 파일**
- `src/main/java/com/mysite/jaminboard/answer/Answer.java` — `modifyDate` 필드 추가
- `src/main/java/com/mysite/jaminboard/answer/AnswerService.java` — `getAnswer`, `modify`, `delete` 추가
- `src/main/java/com/mysite/jaminboard/answer/AnswerController.java` — modify GET/POST, delete GET 추가
- `src/main/resources/templates/question_detail.html` — 답변 카드에 관리자용 [수정]/[삭제] 버튼 + 수정됨 표기

**신규 파일**
- `src/main/resources/templates/answer_modify.html`

## 컴포넌트 설계

### Answer 엔티티
기존 필드(`id`, `content`, `createDate`, `question`, `author`)에 추가:
```java
private LocalDateTime modifyDate;   // 수정 전 null, 수정 시 now()
```

### AnswerService
```java
public Answer getAnswer(Integer id)              // 없으면 DataNotFoundException
public void modify(Answer answer, String content) // content 갱신 + modifyDate=now() + save
public void delete(Answer answer)                 // 삭제
```
기존 `create(Question, String content, SiteUser author)`는 그대로 유지.

### AnswerController (`@RequestMapping("/answer")`, 전부 ROLE_ADMIN으로 보호됨)
| 메서드 | 동작 |
|---|---|
| `GET /answer/modify/{id}` | 답변 조회 → 모델에 `answer` 담아 `answer_modify` 반환 |
| `POST /answer/modify/{id}` | `@RequestParam content` 받아 수정 → `redirect:/question/detail/{answer.question.id}` |
| `GET /answer/delete/{id}` | 삭제 전에 `question.id` 확보 → 삭제 → `redirect:/question/detail/{questionId}` |

`POST /answer/create/{id}`(기존)는 변경 없음.

### 템플릿

**answer_modify.html** — `question_modify.html`과 동일 톤. `<section class="container">` + 폼:
- `th:action="@{|/answer/modify/${answer.id}|}" method="post"`
- `textarea name="content"`에 `th:text="${answer.content}"`로 기존 내용 채움
- `[저장하기]`(btn btn-primary), `[취소]`(상세로, btn btn-secondary)
- CSRF hidden은 Thymeleaf `th:action` 폼에 자동 주입

**question_detail.html** — 답변 카드 영역에 추가:
- 메타 배지에 `th:if="${answer.modifyDate != null}"`로 `(수정됨: yyyy-MM-dd HH:mm)` 표기
- `th:if="${isAdmin}"` 블록에 답변별 `[수정]`(`@{|/answer/modify/${answer.id}|}`, btn btn-sm btn-warning)·`[삭제]`(`@{|/answer/delete/${answer.id}|}`, btn btn-sm btn-danger + `onclick="return confirm('답변을 삭제하시겠습니까?');"`)

## 데이터 흐름

수정: 상세 [수정] → `GET /answer/modify/{id}` 폼 → `POST` → service.modify(content, now) → 상세 redirect → 수정 내용 + 수정됨 표기.
삭제: 상세 [삭제] → confirm → `GET /answer/delete/{id}` → service.delete → 상세 redirect → 해당 답변 사라짐.

## 권한/에러 처리

- 비관리자/익명: 상세에 [수정]/[삭제] 버튼 미표시. `/answer/modify|delete`로 직접 접근 시 SecurityConfig가 403(비로그인은 로그인 페이지로). 컨트롤러 별도 권한 코드 없음.
- 존재하지 않는 답변 id → `getAnswer`가 `DataNotFoundException`(404, 기존 `/error` permitAll로 404 페이지 렌더).
- 질문 삭제 시 답변 cascade(REMOVE) 동작은 기존대로 유지.

## 검증 (브라우저 + H2)

1. 관리자 로그인 → 문의 상세에서 답변 등록.
2. 답변 [수정] → 내용 변경·저장 → 상세에 변경 내용 + `(수정됨: …)` 표기.
3. 답변 [삭제] → confirm → 상세에서 답변 사라짐, 답변 수 감소.
4. 일반 사용자/익명: 상세에 [수정]/[삭제] 미표시, `/answer/modify/{id}`·`/answer/delete/{id}` 직접 접근 시 403.
5. H2 콘솔: `SELECT * FROM ANSWER;` — `MODIFY_DATE` 컬럼 존재, 수정 시 값 채워짐.

## 규칙 준수 체크리스트

- [ ] 신규 라이브러리 0 (Spring Security 범위 내)
- [ ] 클래스/패키지/프로젝트명 무변경 (Answer에 필드, Service/Controller에 메서드, 템플릿 1개 추가만)
- [ ] 상세=card · 버튼=btn · container · layout.html · 표준 HTML 유지
- [ ] 답변 수정/삭제 ROLE_ADMIN 전용, 비관리자 403
- [ ] modifyDate 추가 + 수정됨 표기
