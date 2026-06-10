# 관리자 답변 수정/삭제 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 관리자가 등록한 답변을 별도 수정 페이지에서 수정하고, 확인창을 거쳐 삭제할 수 있게 한다(수정 시각 표기 포함).

**Architecture:** 기존 `answer` 패키지(클래스명 유지)에 `modifyDate` 필드와 `getAnswer`/`modify`/`delete` 서비스 메서드, modify GET/POST·delete GET 컨트롤러 엔드포인트를 추가한다. 권한은 추가 코드 없이 `SecurityConfig`의 `/answer/** = hasRole("ADMIN")`로 보장하고, 화면 노출은 `isAdmin` 모델 속성으로 분기한다.

**Tech Stack:** Spring Boot 4.0.3, Java 17, Spring Security, Spring Data JPA, Thymeleaf, H2, Bootstrap(로컬), Lombok.

**검증 방식:** 자동화 테스트 없음 — `gradlew compileJava` 컴파일 확인 + 부팅 후 PowerShell 스모크 + 브라우저/H2. (프로젝트 규약상 비-TDD)

**공통 명령 (Windows PowerShell):**
- 컴파일: `& "D:\2504110113\Spring\stsproject\jaminboard\gradlew.bat" -p "D:\2504110113\Spring\stsproject\jaminboard" compileJava --console=plain`
- 커밋: `cd "D:/2504110113/Spring/stsproject/jaminboard"` 후 `git add -A && git commit -m "..."`

**불변 규칙:** 신규 라이브러리 추가 금지(Spring Security 범위 내) · 클래스명/패키지명/프로젝트명 변경 금지(필드/메서드/파일 추가만) · 상세=card · 버튼=btn 클래스 · 전체=container · 공통=layout.html · 표준 HTML.

**재기동 주의:** bootRun 재시작 전 기존 java 프로세스가 8080을 점유 중이면 구코드가 응답하므로, 스모크 전 `Get-Process java | Stop-Process -Force` 후 포트 해제를 확인한다.

---

## File Structure

**수정 파일**
- `src/main/java/com/mysite/jaminboard/answer/Answer.java` — `modifyDate` 필드 추가
- `src/main/java/com/mysite/jaminboard/answer/AnswerService.java` — `getAnswer`/`modify`/`delete` 추가
- `src/main/java/com/mysite/jaminboard/answer/AnswerController.java` — modify GET/POST, delete GET 추가
- `src/main/resources/templates/question_detail.html` — 답변 카드에 관리자용 [수정]/[삭제] + 수정됨 표기

**신규 파일**
- `src/main/resources/templates/answer_modify.html`

---

## Task 1: Answer 엔티티 + AnswerService 확장

**Files:**
- Modify: `src/main/java/com/mysite/jaminboard/answer/Answer.java`
- Modify: `src/main/java/com/mysite/jaminboard/answer/AnswerService.java`

- [ ] **Step 1: Answer.java 전체 교체** (기존 필드 유지 + `modifyDate` 추가)

```java
package com.mysite.jaminboard.answer;

import java.time.LocalDateTime;

import com.mysite.jaminboard.question.Question;
import com.mysite.jaminboard.user.SiteUser;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
public class Answer {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@Column(columnDefinition = "TEXT")
	private String content;

	private LocalDateTime createDate;

	@ManyToOne
	private Question question;

	@ManyToOne
	private SiteUser author;

	private LocalDateTime modifyDate;
}
```

- [ ] **Step 2: AnswerService.java 전체 교체** (기존 `create` 유지 + `getAnswer`/`modify`/`delete` 추가)

```java
package com.mysite.jaminboard.answer;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.mysite.jaminboard.DataNotFoundException;
import com.mysite.jaminboard.question.Question;
import com.mysite.jaminboard.user.SiteUser;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class AnswerService {

	private final AnswerRepository answerRepository;

	public void create(Question question, String content, SiteUser author) {
		Answer answer = new Answer();
		answer.setContent(content);
		answer.setCreateDate(LocalDateTime.now());
		answer.setQuestion(question);
		answer.setAuthor(author);
		this.answerRepository.save(answer);
	}

	public Answer getAnswer(Integer id) {
		Optional<Answer> answer = this.answerRepository.findById(id);
		if (answer.isPresent()) {
			return answer.get();
		} else {
			throw new DataNotFoundException("answer not found");
		}
	}

	public void modify(Answer answer, String content) {
		answer.setContent(content);
		answer.setModifyDate(LocalDateTime.now());
		this.answerRepository.save(answer);
	}

	public void delete(Answer answer) {
		this.answerRepository.delete(answer);
	}
}
```

- [ ] **Step 3: 컴파일**

Run: 공통 컴파일 명령
Expected: `BUILD SUCCESSFUL` (컨트롤러는 아직 새 메서드를 안 써도 컴파일됨).

- [ ] **Step 4: 커밋**

```bash
git add -A && git commit -m "feat: add Answer.modifyDate and getAnswer/modify/delete service methods"
```

---

## Task 2: AnswerController 확장 (modify GET/POST, delete GET)

**Files:**
- Modify: `src/main/java/com/mysite/jaminboard/answer/AnswerController.java`

- [ ] **Step 1: AnswerController.java 전체 교체** (기존 `create` 유지 + modify/delete 추가)

```java
package com.mysite.jaminboard.answer;

import java.security.Principal;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.mysite.jaminboard.question.Question;
import com.mysite.jaminboard.question.QuestionService;
import com.mysite.jaminboard.user.SiteUser;
import com.mysite.jaminboard.user.UserService;

import lombok.RequiredArgsConstructor;

@RequestMapping("/answer")
@RequiredArgsConstructor
@Controller
public class AnswerController {

	private final QuestionService questionService;
	private final AnswerService answerService;
	private final UserService userService;

	@PostMapping("/create/{id}")
	public String createAnswer(@PathVariable("id") Integer id, @RequestParam("content") String content,
			Principal principal) {
		Question question = this.questionService.getQuestion(id);
		SiteUser author = this.userService.getUser(principal.getName());
		this.answerService.create(question, content, author);
		return String.format("redirect:/question/detail/%s", id);
	}

	@GetMapping("/modify/{id}")
	public String answerModify(Model model, @PathVariable("id") Integer id) {
		Answer answer = this.answerService.getAnswer(id);
		model.addAttribute("answer", answer);
		return "answer_modify";
	}

	@PostMapping("/modify/{id}")
	public String answerModify(@PathVariable("id") Integer id, @RequestParam("content") String content) {
		Answer answer = this.answerService.getAnswer(id);
		this.answerService.modify(answer, content);
		return String.format("redirect:/question/detail/%s", answer.getQuestion().getId());
	}

	@GetMapping("/delete/{id}")
	public String answerDelete(@PathVariable("id") Integer id) {
		Answer answer = this.answerService.getAnswer(id);
		Integer questionId = answer.getQuestion().getId();
		this.answerService.delete(answer);
		return String.format("redirect:/question/detail/%s", questionId);
	}
}
```

참고: `/answer/**`는 SecurityConfig에서 `hasRole("ADMIN")`이므로 비관리자는 403, 익명은 로그인 페이지로 차단된다(컨트롤러 추가 권한 코드 불필요).

- [ ] **Step 2: 컴파일**

Run: 공통 컴파일 명령
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: 커밋**

```bash
git add -A && git commit -m "feat: admin answer modify (GET/POST) and delete (GET) endpoints"
```

---

## Task 3: answer_modify.html 신규 작성

**Files:**
- Create: `src/main/resources/templates/answer_modify.html`

- [ ] **Step 1: answer_modify.html 작성** (`question_modify.html`과 동일 톤)

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org"
th:replace="~{layout :: layout(~{::section})}">

<section class="container my-3" style="max-width: 720px;">
	<h2 class="border-bottom py-2">답변 수정</h2>
	<form th:action="@{|/answer/modify/${answer.id}|}" method="post">
		<div class="mb-3">
			<label for="content" class="form-label">답변 내용</label>
			<textarea name="content" id="content" class="form-control" rows="8" th:text="${answer.content}" required></textarea>
		</div>
		<button type="submit" class="btn btn-primary">저장하기</button>
		<a th:href="@{|/question/detail/${answer.question.id}|}" class="btn btn-secondary">취소</a>
	</form>
</section>

</html>
```

참고: `th:action` 폼이라 CSRF hidden은 Thymeleaf가 자동 주입한다. 취소 링크는 `answer.question.id`로 원 문의 상세로 돌아간다.

- [ ] **Step 2: 컴파일 (정적 리소스 변경 후 빌드 확인)**

Run: 공통 컴파일 명령
Expected: `BUILD SUCCESSFUL` (템플릿은 컴파일 대상이 아니지만 빌드 정상 확인).

- [ ] **Step 3: 커밋**

```bash
git add -A && git commit -m "feat: add answer_modify template"
```

---

## Task 4: question_detail.html 답변 카드에 [수정]/[삭제] + 수정됨 표기

**Files:**
- Modify: `src/main/resources/templates/question_detail.html`

- [ ] **Step 1: 답변 목록 카드 블록 교체**

다음 기존 블록을:
```html
	<!-- 답변 목록 -->
	<h5 class="border-bottom py-2" th:text="|${#lists.size(question.answerList)}개의 답변이 있습니다.|"></h5>
	<div class="card my-3" th:each="answer : ${question.answerList}">
		<div class="card-body">
			<div class="card-text" style="white-space: pre-line;" th:text="${answer.content}"></div>
			<div class="d-flex justify-content-end">
				<div class="badge bg-light text-dark p-2">
					<span th:if="${answer.author != null}" th:text="|관리자: ${answer.author.username} · |"></span>
					<span th:text="${#temporals.format(answer.createDate, 'yyyy-MM-dd HH:mm')}"></span>
				</div>
			</div>
		</div>
	</div>
```

아래로 교체:
```html
	<!-- 답변 목록 -->
	<h5 class="border-bottom py-2" th:text="|${#lists.size(question.answerList)}개의 답변이 있습니다.|"></h5>
	<div class="card my-3" th:each="answer : ${question.answerList}">
		<div class="card-body">
			<div class="card-text" style="white-space: pre-line;" th:text="${answer.content}"></div>
			<div class="d-flex justify-content-between align-items-end mt-2">
				<div th:if="${isAdmin}">
					<a th:href="@{|/answer/modify/${answer.id}|}" class="btn btn-sm btn-warning">수정</a>
					<a th:href="@{|/answer/delete/${answer.id}|}" class="btn btn-sm btn-danger"
						onclick="return confirm('답변을 삭제하시겠습니까?');">삭제</a>
				</div>
				<div class="badge bg-light text-dark p-2 ms-auto">
					<span th:if="${answer.author != null}" th:text="|관리자: ${answer.author.username} · |"></span>
					<span th:text="${#temporals.format(answer.createDate, 'yyyy-MM-dd HH:mm')}"></span>
					<span th:if="${answer.modifyDate != null}"
						th:text="| (수정됨: ${#temporals.format(answer.modifyDate, 'yyyy-MM-dd HH:mm')})|"></span>
				</div>
			</div>
		</div>
	</div>
```

참고: 비관리자/익명은 `th:if="${isAdmin}"` 블록이 사라지고 `ms-auto`로 배지가 우측 정렬된다. 수정 전 답변은 `modifyDate == null`이라 수정됨 표기 없음.

- [ ] **Step 2: 컴파일**

Run: 공통 컴파일 명령
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: 커밋**

```bash
git add -A && git commit -m "feat: admin edit/delete buttons and modified mark on answers"
```

---

## Task 5: End-to-End 검증

**Files:** (없음 — 검증만)

- [ ] **Step 1: 부팅 + 스모크 (관리자 답변 수정/삭제 + 비관리자 403)**

기존 java 종료 후 부팅하고, 아래 시나리오를 PowerShell로 검증한다(헬퍼 이름은 PowerShell 별칭과 충돌 금지 — 예: `Gc`/`Sc` 사용 금지, `Read-Token` 등 사용).

```powershell
$j="D:\2504110113\Spring\stsproject\jaminboard"
Get-Process java -EA SilentlyContinue | Stop-Process -Force -EA SilentlyContinue
Start-Sleep 3
$p=Start-Process "$j\gradlew.bat" -ArgumentList "-p","$j","bootRun","--console=plain" -PassThru -RedirectStandardOutput "$j\sm.log" -RedirectStandardError "$j\sm.err" -WindowStyle Hidden
$ok=$false; for($i=0;$i -lt 60;$i++){Start-Sleep 2; try{$r=Invoke-WebRequest "http://localhost:8080/question/list" -UseBasicParsing -TimeoutSec 3; if($r.StatusCode -eq 200){$ok=$true;break}}catch{}}
if(-not $ok){Write-Output "BOOT FAIL"; Get-Content "$j\sm.err" -Tail 30; Stop-Process -Id $p.Id -Force -EA SilentlyContinue; exit}
function Read-Token($h){ if($h -match 'name="_csrf"\s+value="([^"]+)"'){return $Matches[1]} else {return $null} }
function New-WebSess { New-Object Microsoft.PowerShell.Commands.WebRequestSession }
# 일반 사용자로 문의 생성
$A=New-WebSess
$t=Read-Token (Invoke-WebRequest "http://localhost:8080/user/signup" -UseBasicParsing -WebSession $A).Content
Invoke-WebRequest "http://localhost:8080/user/signup" -Method Post -UseBasicParsing -WebSession $A -Body @{username="usr";email="u@u.com";password1="pw1234";password2="pw1234";_csrf=$t} | Out-Null
$t=Read-Token (Invoke-WebRequest "http://localhost:8080/user/login" -UseBasicParsing -WebSession $A).Content
Invoke-WebRequest "http://localhost:8080/user/login" -Method Post -UseBasicParsing -WebSession $A -Body @{username="usr";password="pw1234";_csrf=$t} -MaximumRedirection 5 | Out-Null
$t=Read-Token (Invoke-WebRequest "http://localhost:8080/question/create" -UseBasicParsing -WebSession $A).Content
Invoke-WebRequest "http://localhost:8080/question/create" -Method Post -UseBasicParsing -WebSession $A -Body @{subject="질문A";content="본문";equipmentName="Dev1";severity="보통";errorType="실행";occurredDate="2026-06-01";_csrf=$t} -MaximumRedirection 5 | Out-Null
$qid=[regex]::Matches((Invoke-WebRequest "http://localhost:8080/question/list" -UseBasicParsing -WebSession $A).Content,'/question/detail/([0-9]+)') | ForEach-Object {[int]$_.Groups[1].Value} | Measure-Object -Maximum | ForEach-Object {$_.Maximum}
# 관리자 로그인 + 답변 등록
$AD=New-WebSess
$t=Read-Token (Invoke-WebRequest "http://localhost:8080/admin/login" -UseBasicParsing -WebSession $AD).Content
Invoke-WebRequest "http://localhost:8080/user/login" -Method Post -UseBasicParsing -WebSession $AD -Body @{username="admin";password="admin1234";_csrf=$t} -MaximumRedirection 5 | Out-Null
$d=Invoke-WebRequest "http://localhost:8080/question/detail/$qid" -UseBasicParsing -WebSession $AD
$t=Read-Token $d.Content
Invoke-WebRequest "http://localhost:8080/answer/create/$qid" -Method Post -UseBasicParsing -WebSession $AD -Body @{content="원본답변";_csrf=$t} -MaximumRedirection 5 | Out-Null
$aid=[regex]::Matches((Invoke-WebRequest "http://localhost:8080/question/detail/$qid" -UseBasicParsing -WebSession $AD).Content,'/answer/modify/([0-9]+)') | ForEach-Object {[int]$_.Groups[1].Value} | Measure-Object -Maximum | ForEach-Object {$_.Maximum}
Write-Output "answer id=$aid"
# 수정 폼 열기 + 수정 저장
$mf=Invoke-WebRequest "http://localhost:8080/answer/modify/$aid" -UseBasicParsing -WebSession $AD
Write-Output ("modify form status=$($mf.StatusCode) prefilled=" + ($mf.Content -match '원본답변'))
$t=Read-Token $mf.Content
Invoke-WebRequest "http://localhost:8080/answer/modify/$aid" -Method Post -UseBasicParsing -WebSession $AD -Body @{content="수정된답변";_csrf=$t} -MaximumRedirection 5 | Out-Null
$d2=(Invoke-WebRequest "http://localhost:8080/question/detail/$qid" -UseBasicParsing -WebSession $AD).Content
Write-Output ("after modify: newContent=" + ($d2 -match '수정된답변') + " modifiedMark=" + ($d2 -match '수정됨'))
# 비관리자 직접 접근 403
function Loc-Status($url,$sess){ $req=[System.Net.HttpWebRequest]::Create($url); $req.AllowAutoRedirect=$false; $req.CookieContainer=$sess.Cookies; try{ $resp=$req.GetResponse(); $sc=[int]$resp.StatusCode; $resp.Close(); return $sc }catch [System.Net.WebException]{ $r=$_.Exception.Response; if($r){ return [int]$r.StatusCode } else { return "ERR" } } }
Write-Output ("nonadmin modify GET = " + (Loc-Status "http://localhost:8080/answer/modify/$aid" $A))
Write-Output ("nonadmin delete GET = " + (Loc-Status "http://localhost:8080/answer/delete/$aid" $A))
# 관리자 삭제
Invoke-WebRequest "http://localhost:8080/answer/delete/$aid" -UseBasicParsing -WebSession $AD -MaximumRedirection 5 | Out-Null
$d3=(Invoke-WebRequest "http://localhost:8080/question/detail/$qid" -UseBasicParsing -WebSession $AD).Content
Write-Output ("after delete: answerGone=" + (-not ($d3 -match '수정된답변')) + " count0=" + ($d3 -match '0개의 답변'))
Stop-Process -Id $p.Id -Force -EA SilentlyContinue
Get-Process java -EA SilentlyContinue | Stop-Process -Force -EA SilentlyContinue
```

Expected:
- `modify form status=200 prefilled=True`
- `after modify: newContent=True modifiedMark=True`
- `nonadmin modify GET = 403`, `nonadmin delete GET = 403`
- `after delete: answerGone=True count0=True`

- [ ] **Step 2: H2 콘솔 확인 (브라우저, 선택)**

`/h2-console` (`jdbc:h2:~/jaminboard`, `sa`)에서 `SELECT * FROM ANSWER;` → `MODIFY_DATE` 컬럼 존재, 수정한 답변 행에 값이 채워졌는지 확인.

- [ ] **Step 3: 최종 커밋**

```bash
git add -A && git commit -m "docs: verified admin answer edit/delete end-to-end" --allow-empty
```

---

## Self-Review (작성자 점검)

- **Spec 커버리지:** modifyDate(Task 1) · getAnswer/modify/delete(Task 1) · modify GET·POST/delete GET(Task 2) · answer_modify.html(Task 3) · 상세 버튼+수정됨 표기(Task 4) · 권한 403 및 E2E(Task 5) — 설계 문서 모든 항목이 작업에 매핑됨.
- **Placeholder:** 모든 코드 스텝에 실제 코드 포함, TBD 없음. (검증은 프로젝트 규약상 브라우저+H2 — 의도된 비-TDD.)
- **타입 일관성:** `getAnswer(Integer)` 정의(Task 1)/사용(Task 2) 일치 · `modify(Answer, String)` 정의/호출 일치 · `delete(Answer)` 일치 · `answer.getQuestion().getId()`로 redirect 대상 일관 · 템플릿이 참조하는 `answer.id`/`answer.content`/`answer.modifyDate`/`answer.question.id`/`isAdmin` 모두 존재(엔티티 필드 + GlobalControllerAdvice 주입).
- **권한:** modify/delete는 `/answer/**` ROLE_ADMIN으로 보호, 비관리자 403 — 컨트롤러 추가 코드 없음(이중 방어는 화면 `isAdmin` 분기).
