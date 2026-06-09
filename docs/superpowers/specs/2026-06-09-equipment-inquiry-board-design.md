# jaminboard 장비 문의 게시판 확장 — 설계 문서

작성일: 2026-06-09

> 참고: 이 문서는 개발 계획용 산출물입니다. 최종 제출 시 AI 관련 자료와 함께 `docs/` 폴더를 삭제해도 빌드/실행에 영향이 없습니다.

## 1. 배경 (Context)

기존 `jaminboard`(수업 질문 게시판)를 기반으로, **장비 소프트웨어 오류를 사용자가 보고하고 관리자가 답변하는 고객 문의 게시판**으로 확장한다. 로그인/회원가입/관리자 인증, 비밀글, 구조화된 오류 보고 입력, UI 개선을 추가한다.

### 확정된 방향
- **인증**: Spring Security 사용 (교수님 권장). `spring-boot-starter-security` **하나만** 새로 추가하는 것을 규칙 예외로 허용.
- **게시판 구조**: 기존 `Question` 클래스를 (이름 유지) 문의 게시판으로 확장. 사용자/인증 기능은 새 패키지·클래스로 추가.

### 불변 규칙 (반드시 준수)
- 프로젝트명 `jaminboard`, 기본 패키지 `com.mysite.jaminboard`, **기존 클래스명 변경 금지** (필요 시 새 패키지/클래스 추가).
- 목록 화면 = Bootstrap **table**, 상세 화면 = Bootstrap **card**, 등록/수정/삭제/목록 버튼 = Bootstrap **button** 클래스, 전체 화면 = **container**, 공통 구조 = **layout.html**, 모든 화면 표준 HTML 구조.
- **새 라이브러리 추가 금지** — 단, `spring-boot-starter-security`(+test) 1건만 예외. `thymeleaf-extras-springsecurity`는 추가하지 않는다.

## 2. 의존성 변경

`build.gradle`에 추가:
```gradle
implementation 'org.springframework.boot:spring-boot-starter-security'
testImplementation 'org.springframework.boot:spring-boot-starter-security-test'
```
- BCrypt는 Spring Security에 포함되어 별도 라이브러리 불필요.
- 화면 권한 분기는 `thymeleaf-extras-springsecurity` 없이, `@ControllerAdvice`로 현재 사용자/관리자 여부를 모델에 주입해 `th:if`로 처리.

## 3. 패키지 / 클래스 구조

### 신규 `com.mysite.jaminboard.user`
- `SiteUser` — 회원 엔티티
- `UserRole` — enum (`USER`, `ADMIN`); 각 권한 문자열 `ROLE_USER`, `ROLE_ADMIN`
- `UserRepository` — `JpaRepository<SiteUser, Long>`, `findByUsername`
- `UserService` — 회원가입(중복 검사 + BCrypt 인코딩), 조회
- `UserSecurityService` — `UserDetailsService` 구현 (로그인 시 사용자 조회 + 권한 부여)
- `UserController` — 회원가입/로그인 화면 (`/user/signup`, `/user/login`)

### 신규 루트 패키지 `com.mysite.jaminboard`
- `SecurityConfig` — `SecurityFilterChain`, `PasswordEncoder`(BCrypt) 빈
- `DataInitializer` — `CommandLineRunner`, 앱 시작 시 `admin` 계정(ADMIN) 없으면 생성
- `GlobalControllerAdvice` — `@ControllerAdvice`, `@ModelAttribute`로 `loginUser`, `isAdmin`, `isLogin` 주입
- `AdminController` — `/admin/login` (관리자 전용 로그인 화면) 등 관리자 진입점

### 확장 `com.mysite.jaminboard.question` (클래스명 유지)
- `Question` — 필드 추가
- `QuestionService` — 권한 검사, 비밀글 접근 제어, 구조화 필드 처리, 작성자 연결
- `QuestionController` — create/modify/delete 권한 적용, 비밀글 가드, 폼 바인딩

### 확장 `com.mysite.jaminboard.answer` (클래스명 유지)
- `Answer` — 답변자(author) 필드 추가
- `AnswerService` — 답변 생성 시 작성자 연결
- `AnswerController` — 답변 등록을 **관리자 전용**으로 제한

## 4. 엔티티 설계

### SiteUser (신규)
| 필드 | 타입 | 비고 |
|---|---|---|
| id | Long | `@Id @GeneratedValue(IDENTITY)` |
| username | String | unique, not null |
| password | String | BCrypt 해시 |
| email | String | (선택) |
| role | UserRole | USER / ADMIN |
| createDate | LocalDateTime | |

### Question (필드 추가 — 기존 필드 유지)
기존: `id(Integer)`, `subject`, `content`(오류 증상 내용), `createDate`, `answerList`
추가:
| 필드 | 타입 | 입력 UI |
|---|---|---|
| equipmentName | String | text |
| softwareVersion | String | text |
| occurredDate | LocalDate | date |
| severity | String | select (낮음/보통/높음/긴급) |
| errorType | String | select (설치/실행/네트워크/기타) |
| secret | boolean | checkbox |
| author | SiteUser (`@ManyToOne`) | 로그인 사용자 자동 |

### Answer (필드 추가)
기존: `id`, `content`, `createDate`, `question`
추가: `author` (`@ManyToOne SiteUser`, 답변한 관리자)

## 5. Spring Security 설정

### 접근 규칙
- **permitAll**: `/`, `/question/list`, `/bootstrap.min.css`, `/style.css`, `/user/signup`, `/user/login`, `/admin/login`, `/h2-console/**`
- **인증 필요**: `/question/create`, `/question/modify/**`, `/question/delete/**`, `/answer/**`
- **ADMIN 전용**: `/admin/**`
- 그 외(`/question/detail/**` 등)는 컨트롤러/서비스에서 세부 권한(비밀글) 추가 검사.

### 로그인 (별도 관리자 화면)
- 사용자 로그인 페이지: `GET /user/login`
- 관리자 로그인 페이지: `GET /admin/login` (별도 화면, 다른 스타일)
- 두 화면 모두 Spring Security 인증 처리 URL(`POST /user/login`, `loginProcessingUrl`)로 제출.
- 로그인 성공 시: 기본 `/question/list`로 이동. (관리자도 동일 목록 사용, 관리자 메뉴는 navbar에 노출)
- 로그아웃: `POST /user/logout` → `/question/list`.

### 기타
- H2 콘솔: `/h2-console/**` CSRF 비활성 + `headers.frameOptions sameOrigin`.
- CSRF: 기본 활성. Thymeleaf `th:action` + `method="post"` 폼은 CSRF hidden 필드가 자동 삽입되므로 폼 템플릿 수정 최소.
- `PasswordEncoder`: `BCryptPasswordEncoder` 빈.

## 6. 권한 규칙 (확정)

| 동작 | 권한 |
|---|---|
| 목록 보기 | 누구나 (비밀글은 🔒 표시, 제목 링크 비활성) |
| 상세 보기 (공개글) | 누구나 |
| 상세 보기 (비밀글) | **작성자 본인 + 관리자만** (그 외 접근 차단) |
| 문의 등록 | 로그인 사용자 |
| 문의 수정 | **작성자 본인** |
| 문의 삭제 | 작성자 본인 (관리자도 삭제 가능) |
| 답변 등록 | **관리자만** |

권한 위반 시 `DataNotFoundException`/접근거부 처리(상세는 목록으로 리다이렉트 또는 403).

## 7. 화면 (frontend-design 스킬로 UI 개선, Bootstrap 규칙 준수)

### 수정
- `layout.html` — navbar: 비로그인 시 [로그인][회원가입], 로그인 시 [사용자명][로그아웃], 관리자 시 [관리자] 메뉴. 게시판 제목 "박재민 게시판" 유지.
- `question_list.html` — table: 번호 / 제목(비밀글 🔒, 접근 가능자만 링크) / 장비명 / 발생일 / 심각도(badge) / 답변상태(답변완료·미답변 또는 개수) / 작성일. 빈 목록 메시지 유지.
- `question_detail.html` — card: 구조화 필드 전체 표시(장비명·SW버전·발생일·심각도·오류유형·증상 내용). 답변 목록. **관리자에게만** 답변 등록 폼. 비밀글 가드. 작성자 본인에게만 [수정], 작성자/관리자에게 [삭제].
- `question_form.html` / `question_modify.html` — 구조화 입력: 제목(text), 장비명(text), SW버전(text), 발생일(date), 심각도(select), 오류유형(select), 증상 내용(textarea), 비밀글(checkbox).

### 신규
- `user/login.html` — 사용자 로그인 폼 (card)
- `user/signup.html` — 회원가입 폼 (card)
- `admin/login.html` — 관리자 로그인 폼 (card, 구분되는 스타일)

## 8. 관리자 계정 초기화
- `DataInitializer`: 시작 시 `admin` 계정(role ADMIN, BCrypt 비밀번호)이 없으면 생성. 최초 비밀번호는 코드 상수(예: `admin1234`)로 두고, 문서에 명시.

## 9. 테스트 / 검증 방법 (end-to-end)
1. 회원가입(`/user/signup`) → 일반 사용자 생성, 로그인.
2. 문의 등록: 구조화 필드 입력 + 비밀글 체크 → 목록에 표시(비밀글 🔒).
3. 다른 일반 사용자 로그인 → 타인의 비밀글 상세 접근 차단 확인.
4. `/admin/login`으로 admin 로그인 → 비밀글 상세 열람 + 답변 등록.
5. 일반 사용자에게는 답변 폼이 보이지 않음 / 답변 POST 차단 확인.
6. 작성자 본인만 수정 가능, 작성자/관리자만 삭제 가능 확인.
7. H2 콘솔(`/h2-console`, `jdbc:h2:~/jaminboard`)에서 `SITE_USER`, `QUESTION`, `ANSWER` 테이블 및 신규 컬럼 확인.
8. 로그아웃 동작 확인.

## 10. 비목표 (Out of scope)
- 이메일 인증, 비밀번호 찾기, 소셜 로그인.
- 페이징/검색 (요구사항 외).
- `thymeleaf-extras-springsecurity` 도입.
