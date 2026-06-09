# 장비 문의 게시판 확장 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 기존 jaminboard 질문 게시판을, Spring Security 인증·비밀글·구조화된 오류 보고 필드를 갖춘 장비 고객 문의 게시판으로 확장한다.

**Architecture:** 기존 `question`/`answer` 패키지는 클래스명을 유지한 채 필드/로직을 확장하고, 인증은 새 `user` 패키지 + 루트의 `SecurityConfig`/`DataInitializer`/`GlobalControllerAdvice`로 추가한다. 화면 권한 분기는 `thymeleaf-extras-springsecurity` 없이 `@ControllerAdvice`가 주입하는 `loginUser`/`isAdmin`/`isLogin` 모델 속성으로 처리한다.

**Tech Stack:** Spring Boot 4.0.3, Java 17, Spring Security(신규, BCrypt 포함), Spring Data JPA, Thymeleaf, H2, Bootstrap(로컬), Lombok.

**검증 방식:** 이 프로젝트는 자동화 테스트가 없고 "웹 브라우저 + H2 Console"로 검증한다. 각 코드 작업은 `gradlew compileJava`로 컴파일을 확인하고, 마일스톤마다 앱을 부팅해 PowerShell 스모크 + 브라우저로 확인한 뒤 커밋한다.

**공통 명령 (Windows PowerShell):**
- 컴파일: `& "D:\2504110113\Spring\stsproject\jaminboard\gradlew.bat" -p "D:\2504110113\Spring\stsproject\jaminboard" compileJava --console=plain`
- 커밋: `cd "D:/2504110113/Spring/stsproject/jaminboard"` 후 `git add -A && git commit -m "..."`

**불변 규칙(전 작업 공통):** 클래스명/패키지명/프로젝트명 변경 금지 · 목록=table · 상세=card · 버튼=btn 클래스 · 전체=container · 공통=layout.html · 표준 HTML · 신규 라이브러리는 Spring Security 외 추가 금지.

---

## File Structure

**신규 파일**
- `src/main/java/com/mysite/jaminboard/SecurityConfig.java` — 시큐리티 필터체인 + BCrypt 빈
- `src/main/java/com/mysite/jaminboard/DataInitializer.java` — admin 계정 시드
- `src/main/java/com/mysite/jaminboard/GlobalControllerAdvice.java` — loginUser/isAdmin/isLogin 모델 주입
- `src/main/java/com/mysite/jaminboard/AdminController.java` — `/admin/login` 화면
- `src/main/java/com/mysite/jaminboard/user/SiteUser.java`
- `src/main/java/com/mysite/jaminboard/user/UserRole.java`
- `src/main/java/com/mysite/jaminboard/user/UserRepository.java`
- `src/main/java/com/mysite/jaminboard/user/UserService.java`
- `src/main/java/com/mysite/jaminboard/user/UserSecurityService.java`
- `src/main/java/com/mysite/jaminboard/user/UserController.java`
- `src/main/resources/templates/signup_form.html`
- `src/main/resources/templates/login_form.html`
- `src/main/resources/templates/admin_login.html`

**수정 파일**
- `build.gradle` — security 의존성
- `src/main/java/com/mysite/jaminboard/question/Question.java` — 구조화 필드 + secret + author
- `src/main/java/com/mysite/jaminboard/question/QuestionService.java` — 생성/수정/삭제 시 필드·권한·작성자
- `src/main/java/com/mysite/jaminboard/question/QuestionController.java` — 권한/비밀글/폼 바인딩
- `src/main/java/com/mysite/jaminboard/answer/Answer.java` — author 추가
- `src/main/java/com/mysite/jaminboard/answer/AnswerService.java` — author 연결
- `src/main/java/com/mysite/jaminboard/answer/AnswerController.java` — 관리자 전용
- `src/main/resources/templates/layout.html` — navbar 로그인 상태
- `src/main/resources/templates/question_list.html` — 컬럼 + 비밀글
- `src/main/resources/templates/question_detail.html` — 구조화 필드 + 답변 폼 + 가드
- `src/main/resources/templates/question_form.html` — 구조화 입력
- `src/main/resources/templates/question_modify.html` — 구조화 입력(값 채움)

---

## Task 1: Spring Security 의존성 + SecurityConfig

**Files:**
- Modify: `build.gradle`
- Create: `src/main/java/com/mysite/jaminboard/SecurityConfig.java`

- [ ] **Step 1: build.gradle에 의존성 추가**

`dependencies { ... }` 블록 안, `spring-boot-starter-data-jpa` 줄 아래에 추가:
```gradle
	implementation 'org.springframework.boot:spring-boot-starter-security'
	testImplementation 'org.springframework.boot:spring-boot-starter-security-test'
```

- [ ] **Step 2: SecurityConfig 작성**

`SecurityConfig.java`:
```java
package com.mysite.jaminboard;

import org.springframework.boot.autoconfigure.security.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	@Bean
	SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(PathRequest.toH2Console()).permitAll()
				.requestMatchers(
						"/", "/question/list", "/question/detail/**",
						"/bootstrap.min.css", "/style.css",
						"/user/signup", "/user/login", "/admin/login").permitAll()
				.requestMatchers("/admin/**").hasRole("ADMIN")
				.requestMatchers("/answer/**").hasRole("ADMIN")
				.anyRequest().authenticated())
			.csrf(csrf -> csrf.ignoringRequestMatchers(PathRequest.toH2Console()))
			.headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
			.formLogin(form -> form
				.loginPage("/user/login")
				.loginProcessingUrl("/user/login")
				.defaultSuccessUrl("/question/list"))
			.logout(logout -> logout
				.logoutUrl("/user/logout")
				.logoutSuccessUrl("/question/list")
				.invalidateHttpSession(true));
		return http.build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
		return configuration.getAuthenticationManager();
	}
}
```

- [ ] **Step 3: 컴파일**

Run: 공통 컴파일 명령
Expected: `BUILD SUCCESSFUL`. (Security 7 API 차이로 `frameOptions`/`logoutUrl` 시그니처 오류가 나면 메시지에 따라 람다 DSL로 보정 — 예: `frameOptions(org.springframework.security.config.Customizer)` 형태.)

- [ ] **Step 4: 부팅 스모크 테스트**

```powershell
$j="D:\2504110113\Spring\stsproject\jaminboard"
$p=Start-Process "$j\gradlew.bat" -ArgumentList "-p","$j","bootRun","--console=plain" -PassThru -RedirectStandardOutput "$j\run.log" -RedirectStandardError "$j\run.err" -WindowStyle Hidden
$ok=$false; for($i=0;$i -lt 40;$i++){Start-Sleep 2; try{$r=Invoke-WebRequest "http://localhost:8080/question/list" -UseBasicParsing -TimeoutSec 3; if($r.StatusCode -eq 200){$ok=$true;break}}catch{}}
if($ok){Write-Output "LIST OK"} else {Write-Output "FAIL"; Get-Content "$j\run.err" -Tail 30}
Stop-Process -Id $p.Id -Force -EA SilentlyContinue
Get-Process java -EA SilentlyContinue | Where-Object {$_.StartTime -gt (Get-Date).AddMinutes(-3)} | Stop-Process -Force -EA SilentlyContinue
Remove-Item "$j\run.log","$j\run.err" -Force -EA SilentlyContinue
```
Expected: `LIST OK` (보안 적용 후에도 목록·H2는 공개라 정상 부팅).

- [ ] **Step 5: 커밋**

```bash
git add -A && git commit -m "feat: add Spring Security dependency and base SecurityConfig"
```

---

## Task 2: SiteUser 엔티티 + UserRole + Repository

**Files:**
- Create: `src/main/java/com/mysite/jaminboard/user/UserRole.java`
- Create: `src/main/java/com/mysite/jaminboard/user/SiteUser.java`
- Create: `src/main/java/com/mysite/jaminboard/user/UserRepository.java`

- [ ] **Step 1: UserRole 작성**

```java
package com.mysite.jaminboard.user;

import lombok.Getter;

@Getter
public enum UserRole {
	ADMIN("ROLE_ADMIN"),
	USER("ROLE_USER");

	UserRole(String value) {
		this.value = value;
	}

	private final String value;
}
```

- [ ] **Step 2: SiteUser 작성**

```java
package com.mysite.jaminboard.user;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
public class SiteUser {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(unique = true)
	private String username;

	private String password;

	private String email;

	@Enumerated(EnumType.STRING)
	private UserRole role;

	private LocalDateTime createDate;
}
```

- [ ] **Step 3: UserRepository 작성**

```java
package com.mysite.jaminboard.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<SiteUser, Long> {
	Optional<SiteUser> findByUsername(String username);
}
```

- [ ] **Step 4: 컴파일**

Run: 공통 컴파일 명령
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: 커밋**

```bash
git add -A && git commit -m "feat: add SiteUser entity, UserRole, UserRepository"
```

---

## Task 3: UserService + UserSecurityService

**Files:**
- Create: `src/main/java/com/mysite/jaminboard/user/UserService.java`
- Create: `src/main/java/com/mysite/jaminboard/user/UserSecurityService.java`

- [ ] **Step 1: UserService 작성**

```java
package com.mysite.jaminboard.user;

import java.time.LocalDateTime;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.mysite.jaminboard.DataNotFoundException;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class UserService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	public SiteUser create(String username, String email, String password) {
		SiteUser user = new SiteUser();
		user.setUsername(username);
		user.setEmail(email);
		user.setPassword(passwordEncoder.encode(password));
		user.setRole(UserRole.USER);
		user.setCreateDate(LocalDateTime.now());
		return this.userRepository.save(user);
	}

	public SiteUser getUser(String username) {
		return this.userRepository.findByUsername(username)
				.orElseThrow(() -> new DataNotFoundException("siteuser not found"));
	}
}
```

- [ ] **Step 2: UserSecurityService 작성**

```java
package com.mysite.jaminboard.user;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class UserSecurityService implements UserDetailsService {

	private final UserRepository userRepository;

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		Optional<SiteUser> _siteUser = this.userRepository.findByUsername(username);
		if (_siteUser.isEmpty()) {
			throw new UsernameNotFoundException("사용자를 찾을 수 없습니다.");
		}
		SiteUser siteUser = _siteUser.get();
		List<GrantedAuthority> authorities = new ArrayList<>();
		authorities.add(new SimpleGrantedAuthority(siteUser.getRole().getValue()));
		return new User(siteUser.getUsername(), siteUser.getPassword(), authorities);
	}
}
```

- [ ] **Step 3: 컴파일**

Run: 공통 컴파일 명령
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: 커밋**

```bash
git add -A && git commit -m "feat: add UserService and UserSecurityService"
```

---

## Task 4: 회원가입/로그인 컨트롤러 + 화면

**Files:**
- Create: `src/main/java/com/mysite/jaminboard/user/UserController.java`
- Create: `src/main/resources/templates/signup_form.html`
- Create: `src/main/resources/templates/login_form.html`

- [ ] **Step 1: UserController 작성**

```java
package com.mysite.jaminboard.user;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import lombok.RequiredArgsConstructor;

@RequestMapping("/user")
@RequiredArgsConstructor
@Controller
public class UserController {

	private final UserService userService;

	@GetMapping("/signup")
	public String signup() {
		return "signup_form";
	}

	@PostMapping("/signup")
	public String signup(@RequestParam("username") String username, @RequestParam("email") String email,
			@RequestParam("password1") String password1, @RequestParam("password2") String password2, Model model) {
		if (!password1.equals(password2)) {
			model.addAttribute("errorMessage", "두 비밀번호가 일치하지 않습니다.");
			return "signup_form";
		}
		try {
			this.userService.create(username, email, password1);
		} catch (DataIntegrityViolationException e) {
			model.addAttribute("errorMessage", "이미 등록된 사용자입니다.");
			return "signup_form";
		}
		return "redirect:/user/login";
	}

	@GetMapping("/login")
	public String login() {
		return "login_form";
	}
}
```

- [ ] **Step 2: signup_form.html 작성**

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org"
th:replace="~{layout :: layout(~{::section})}">

<section class="container my-3" style="max-width: 480px;">
	<h2 class="border-bottom py-2">회원가입</h2>
	<div th:if="${errorMessage}" class="alert alert-danger" th:text="${errorMessage}"></div>
	<form th:action="@{/user/signup}" method="post">
		<div class="mb-3">
			<label for="username" class="form-label">사용자 ID</label>
			<input type="text" name="username" id="username" class="form-control" required>
		</div>
		<div class="mb-3">
			<label for="email" class="form-label">이메일</label>
			<input type="email" name="email" id="email" class="form-control">
		</div>
		<div class="mb-3">
			<label for="password1" class="form-label">비밀번호</label>
			<input type="password" name="password1" id="password1" class="form-control" required>
		</div>
		<div class="mb-3">
			<label for="password2" class="form-label">비밀번호 확인</label>
			<input type="password" name="password2" id="password2" class="form-control" required>
		</div>
		<button type="submit" class="btn btn-primary">회원가입</button>
		<a th:href="@{/user/login}" class="btn btn-secondary">로그인</a>
	</form>
</section>

</html>
```

- [ ] **Step 3: login_form.html 작성**

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org"
th:replace="~{layout :: layout(~{::section})}">

<section class="container my-3" style="max-width: 480px;">
	<h2 class="border-bottom py-2">로그인</h2>
	<div th:if="${param.error}" class="alert alert-danger">아이디 또는 비밀번호가 올바르지 않습니다.</div>
	<form th:action="@{/user/login}" method="post">
		<div class="mb-3">
			<label for="username" class="form-label">사용자 ID</label>
			<input type="text" name="username" id="username" class="form-control" required>
		</div>
		<div class="mb-3">
			<label for="password" class="form-label">비밀번호</label>
			<input type="password" name="password" id="password" class="form-control" required>
		</div>
		<button type="submit" class="btn btn-primary">로그인</button>
		<a th:href="@{/user/signup}" class="btn btn-secondary">회원가입</a>
	</form>
</section>

</html>
```

- [ ] **Step 4: 컴파일 + 부팅 스모크 (회원가입/로그인)**

먼저 공통 컴파일 명령으로 `BUILD SUCCESSFUL` 확인. 이후:
```powershell
$j="D:\2504110113\Spring\stsproject\jaminboard"
$p=Start-Process "$j\gradlew.bat" -ArgumentList "-p","$j","bootRun","--console=plain" -PassThru -RedirectStandardOutput "$j\run.log" -RedirectStandardError "$j\run.err" -WindowStyle Hidden
$ok=$false; for($i=0;$i -lt 40;$i++){Start-Sleep 2; try{$r=Invoke-WebRequest "http://localhost:8080/user/login" -UseBasicParsing -TimeoutSec 3; if($r.StatusCode -eq 200){$ok=$true;break}}catch{}}
if($ok){
  $s=New-Object Microsoft.PowerShell.Commands.WebRequestSession
  $login=Invoke-WebRequest "http://localhost:8080/user/login" -UseBasicParsing -WebSession $s
  if($login.Content -match 'name="_csrf"'){Write-Output "CSRF hidden auto-added OK"}
  Write-Output "LOGIN PAGE OK"
} else {Write-Output "FAIL"; Get-Content "$j\run.err" -Tail 30}
Stop-Process -Id $p.Id -Force -EA SilentlyContinue
Get-Process java -EA SilentlyContinue | Where-Object {$_.StartTime -gt (Get-Date).AddMinutes(-3)} | Stop-Process -Force -EA SilentlyContinue
Remove-Item "$j\run.log","$j\run.err" -Force -EA SilentlyContinue
```
Expected: `CSRF hidden auto-added OK` + `LOGIN PAGE OK`. (브라우저로 `/user/signup` 가입 → `/user/login` 로그인까지 수동 확인 권장.)

- [ ] **Step 5: 커밋**

```bash
git add -A && git commit -m "feat: add signup/login controller and templates"
```

---

## Task 5: 관리자 계정 시드 (DataInitializer)

**Files:**
- Create: `src/main/java/com/mysite/jaminboard/DataInitializer.java`

- [ ] **Step 1: DataInitializer 작성**

```java
package com.mysite.jaminboard;

import java.time.LocalDateTime;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.mysite.jaminboard.user.SiteUser;
import com.mysite.jaminboard.user.UserRepository;
import com.mysite.jaminboard.user.UserRole;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	@Override
	public void run(String... args) {
		if (this.userRepository.findByUsername("admin").isEmpty()) {
			SiteUser admin = new SiteUser();
			admin.setUsername("admin");
			admin.setPassword(this.passwordEncoder.encode("admin1234"));
			admin.setEmail("admin@jaminboard.com");
			admin.setRole(UserRole.ADMIN);
			admin.setCreateDate(LocalDateTime.now());
			this.userRepository.save(admin);
		}
	}
}
```

- [ ] **Step 2: 컴파일 + 부팅 후 admin 확인**

공통 컴파일 명령으로 `BUILD SUCCESSFUL` 확인. 부팅 후 브라우저 H2 콘솔(`/h2-console`, `jdbc:h2:~/jaminboard`)에서 `SELECT * FROM SITE_USER;` 실행 → `admin` 행(ROLE=ADMIN) 존재 확인. (스모크 스크립트는 Task 4와 동일하게 부팅만 확인해도 됨.)

- [ ] **Step 3: 커밋**

```bash
git add -A && git commit -m "feat: seed admin account on startup"
```

---

## Task 6: GlobalControllerAdvice + navbar 로그인 상태

**Files:**
- Create: `src/main/java/com/mysite/jaminboard/GlobalControllerAdvice.java`
- Modify: `src/main/resources/templates/layout.html`

- [ ] **Step 1: GlobalControllerAdvice 작성**

```java
package com.mysite.jaminboard;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.mysite.jaminboard.user.SiteUser;
import com.mysite.jaminboard.user.UserRepository;

import lombok.RequiredArgsConstructor;

@ControllerAdvice
@RequiredArgsConstructor
public class GlobalControllerAdvice {

	private final UserRepository userRepository;

	@ModelAttribute
	public void addUserInfo(Model model, Authentication authentication) {
		boolean isLogin = authentication != null && authentication.isAuthenticated()
				&& !(authentication instanceof AnonymousAuthenticationToken);
		SiteUser loginUser = null;
		boolean isAdmin = false;
		if (isLogin) {
			loginUser = this.userRepository.findByUsername(authentication.getName()).orElse(null);
			isAdmin = authentication.getAuthorities().stream()
					.anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
		}
		model.addAttribute("isLogin", isLogin);
		model.addAttribute("loginUser", loginUser);
		model.addAttribute("isAdmin", isAdmin);
	}
}
```

- [ ] **Step 2: layout.html navbar 교체**

기존 `<nav>...</nav>` 블록 전체를 아래로 교체:
```html
	<nav class="navbar navbar-expand-lg navbar-dark bg-dark mb-4">
		<div class="container-fluid">
			<a class="navbar-brand" th:href="@{/question/list}">박재민 게시판</a>
			<div class="navbar-nav me-auto">
				<a class="nav-link" th:href="@{/question/list}">문의 목록</a>
				<a class="nav-link" th:href="@{/question/create}" th:if="${isLogin}">문의 등록</a>
			</div>
			<div class="navbar-nav">
				<a class="nav-link" th:href="@{/admin/login}" th:if="${!isLogin}">관리자</a>
				<a class="nav-link" th:href="@{/user/login}" th:if="${!isLogin}">로그인</a>
				<a class="nav-link" th:href="@{/user/signup}" th:if="${!isLogin}">회원가입</a>
				<span class="navbar-text text-light me-2" th:if="${isLogin}"
					th:text="|${loginUser != null ? loginUser.username : ''} 님${isAdmin ? ' (관리자)' : ''}|"></span>
				<form th:if="${isLogin}" th:action="@{/user/logout}" method="post" class="d-inline">
					<button type="submit" class="btn btn-outline-light btn-sm">로그아웃</button>
				</form>
			</div>
		</div>
	</nav>
```

- [ ] **Step 3: 컴파일 + 부팅 스모크 (로그인 상태 navbar)**

공통 컴파일 명령 후 부팅. 브라우저에서 비로그인 시 [관리자][로그인][회원가입] 표시, 로그인 후 "사용자명 님" + [로그아웃] 표시 확인. (자동 스모크는 Task 4 스크립트로 부팅만 확인.)

- [ ] **Step 4: 커밋**

```bash
git add -A && git commit -m "feat: expose login state to views and update navbar"
```

---

## Task 7: 관리자 로그인 화면

**Files:**
- Create: `src/main/java/com/mysite/jaminboard/AdminController.java`
- Create: `src/main/resources/templates/admin_login.html`

- [ ] **Step 1: AdminController 작성**

```java
package com.mysite.jaminboard;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminController {

	@GetMapping("/admin/login")
	public String adminLogin() {
		return "admin_login";
	}
}
```
참고: `/admin/login`은 SecurityConfig에서 permitAll(비로그인 접근 가능). `/admin/**`의 ADMIN 제한보다 구체적인 permitAll 매처가 먼저 선언돼 있어 로그인 화면 자체는 누구나 접근 가능하다.

- [ ] **Step 2: admin_login.html 작성**

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org"
th:replace="~{layout :: layout(~{::section})}">

<section class="container my-3" style="max-width: 480px;">
	<div class="card border-dark">
		<div class="card-header bg-dark text-white">관리자 로그인</div>
		<div class="card-body">
			<div th:if="${param.error}" class="alert alert-danger">아이디 또는 비밀번호가 올바르지 않습니다.</div>
			<form th:action="@{/user/login}" method="post">
				<div class="mb-3">
					<label for="username" class="form-label">관리자 ID</label>
					<input type="text" name="username" id="username" class="form-control" required>
				</div>
				<div class="mb-3">
					<label for="password" class="form-label">비밀번호</label>
					<input type="password" name="password" id="password" class="form-control" required>
				</div>
				<button type="submit" class="btn btn-dark">관리자 로그인</button>
			</form>
		</div>
	</div>
</section>

</html>
```
참고: 관리자 로그인 폼도 Spring Security 인증 처리 URL(`/user/login`)로 POST한다. 로그인 후 권한(ADMIN)에 따라 관리자 메뉴가 노출된다.

- [ ] **Step 3: 컴파일 + 부팅 스모크**

공통 컴파일 명령 후 부팅. 브라우저에서 `/admin/login` → admin/admin1234 로그인 → navbar에 "(관리자)" 표시 확인.

- [ ] **Step 4: 커밋**

```bash
git add -A && git commit -m "feat: add separate admin login page"
```

---

## Task 8: Question 엔티티 확장 (구조화 필드 + secret + author)

**Files:**
- Modify: `src/main/java/com/mysite/jaminboard/question/Question.java`

- [ ] **Step 1: Question.java 전체 교체**

```java
package com.mysite.jaminboard.question;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.mysite.jaminboard.answer.Answer;
import com.mysite.jaminboard.user.SiteUser;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
public class Question {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@Column(length = 200)
	private String subject;

	@Column(columnDefinition = "TEXT")
	private String content;

	private LocalDateTime createDate;

	@OneToMany(mappedBy = "question", cascade = CascadeType.REMOVE)
	private List<Answer> answerList;

	// === 장비 오류 보고 구조화 필드 ===
	private String equipmentName;

	private String softwareVersion;

	private LocalDate occurredDate;

	private String severity;

	private String errorType;

	private boolean secret;

	@ManyToOne
	private SiteUser author;
}
```

- [ ] **Step 2: 컴파일**

Run: 공통 컴파일 명령
Expected: `BUILD SUCCESSFUL` (QuestionService/Controller는 아직 신규 필드를 안 써도 컴파일됨).

- [ ] **Step 3: 커밋**

```bash
git add -A && git commit -m "feat: extend Question with structured fields, secret, author"
```

---

## Task 9: QuestionService 확장 (필드/작성자/권한)

**Files:**
- Modify: `src/main/java/com/mysite/jaminboard/question/QuestionService.java`

- [ ] **Step 1: QuestionService.java 전체 교체**

```java
package com.mysite.jaminboard.question;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.mysite.jaminboard.DataNotFoundException;
import com.mysite.jaminboard.user.SiteUser;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class QuestionService {

	private final QuestionRepository questionRepository;

	public List<Question> getList() {
		return this.questionRepository.findAll();
	}

	public Question getQuestion(Integer id) {
		Optional<Question> question = this.questionRepository.findById(id);
		if (question.isPresent()) {
			return question.get();
		} else {
			throw new DataNotFoundException("question not found");
		}
	}

	public void create(String subject, String content, String equipmentName, String softwareVersion,
			LocalDate occurredDate, String severity, String errorType, boolean secret, SiteUser author) {
		Question q = new Question();
		q.setSubject(subject);
		q.setContent(content);
		q.setEquipmentName(equipmentName);
		q.setSoftwareVersion(softwareVersion);
		q.setOccurredDate(occurredDate);
		q.setSeverity(severity);
		q.setErrorType(errorType);
		q.setSecret(secret);
		q.setAuthor(author);
		q.setCreateDate(LocalDateTime.now());
		this.questionRepository.save(q);
	}

	public void modify(Question question, String subject, String content, String equipmentName,
			String softwareVersion, LocalDate occurredDate, String severity, String errorType, boolean secret) {
		question.setSubject(subject);
		question.setContent(content);
		question.setEquipmentName(equipmentName);
		question.setSoftwareVersion(softwareVersion);
		question.setOccurredDate(occurredDate);
		question.setSeverity(severity);
		question.setErrorType(errorType);
		question.setSecret(secret);
		this.questionRepository.save(question);
	}

	public void delete(Question question) {
		this.questionRepository.delete(question);
	}

	// 작성자 본인 여부
	public boolean isAuthor(Question question, String username) {
		return username != null && question.getAuthor() != null
				&& question.getAuthor().getUsername().equals(username);
	}
}
```

- [ ] **Step 2: 컴파일**

Run: 공통 컴파일 명령
Expected: 기존 QuestionController가 옛 `create(subject, content)` 시그니처를 호출하므로 **컴파일 에러 발생 예상**(메서드 시그니처 변경). → 바로 Task 10에서 컨트롤러를 교체하므로, 이 단계는 Task 10과 묶어 컴파일한다. (개별 커밋을 원하면 Step 1 후 즉시 Task 10 진행)

- [ ] **Step 3: (Task 10과 함께 커밋)**

QuestionController 교체 후 일괄 컴파일·커밋. (서비스만 단독 커밋하지 않음)

---

## Task 10: QuestionController 확장 (폼 바인딩 + 권한 + 비밀글 가드)

**Files:**
- Modify: `src/main/java/com/mysite/jaminboard/question/QuestionController.java`

- [ ] **Step 1: QuestionController.java 전체 교체**

```java
package com.mysite.jaminboard.question;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.mysite.jaminboard.user.SiteUser;
import com.mysite.jaminboard.user.UserService;

import lombok.RequiredArgsConstructor;

@RequestMapping("/question")
@RequiredArgsConstructor
@Controller
public class QuestionController {

	private final QuestionService questionService;
	private final UserService userService;

	private boolean hasAdminRole(Authentication authentication) {
		return authentication != null && authentication.getAuthorities().stream()
				.anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
	}

	@GetMapping("/list")
	public String list(Model model) {
		List<Question> questionList = this.questionService.getList();
		model.addAttribute("questionList", questionList);
		return "question_list";
	}

	@GetMapping(value = "/detail/{id}")
	public String detail(Model model, @PathVariable("id") Integer id, Principal principal,
			Authentication authentication) {
		Question question = this.questionService.getQuestion(id);
		// 비밀글 접근 제어: 작성자 본인 + 관리자만
		if (question.isSecret()) {
			if (principal == null) {
				return "redirect:/user/login";
			}
			boolean isAuthor = this.questionService.isAuthor(question, principal.getName());
			if (!isAuthor && !hasAdminRole(authentication)) {
				return "redirect:/question/list";
			}
		}
		model.addAttribute("question", question);
		return "question_detail";
	}

	@GetMapping("/create")
	public String questionCreate() {
		return "question_form";
	}

	@PostMapping("/create")
	public String questionCreate(@RequestParam("subject") String subject, @RequestParam("content") String content,
			@RequestParam(value = "equipmentName", required = false) String equipmentName,
			@RequestParam(value = "softwareVersion", required = false) String softwareVersion,
			@RequestParam(value = "occurredDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate occurredDate,
			@RequestParam(value = "severity", required = false) String severity,
			@RequestParam(value = "errorType", required = false) String errorType,
			@RequestParam(value = "secret", required = false, defaultValue = "false") boolean secret,
			Principal principal) {
		SiteUser author = this.userService.getUser(principal.getName());
		this.questionService.create(subject, content, equipmentName, softwareVersion, occurredDate, severity,
				errorType, secret, author);
		return "redirect:/question/list";
	}

	@GetMapping("/modify/{id}")
	public String questionModify(Model model, @PathVariable("id") Integer id, Principal principal) {
		Question question = this.questionService.getQuestion(id);
		if (!this.questionService.isAuthor(question, principal.getName())) {
			return "redirect:/question/detail/" + id;
		}
		model.addAttribute("question", question);
		return "question_modify";
	}

	@PostMapping("/modify/{id}")
	public String questionModify(@PathVariable("id") Integer id, @RequestParam("subject") String subject,
			@RequestParam("content") String content,
			@RequestParam(value = "equipmentName", required = false) String equipmentName,
			@RequestParam(value = "softwareVersion", required = false) String softwareVersion,
			@RequestParam(value = "occurredDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate occurredDate,
			@RequestParam(value = "severity", required = false) String severity,
			@RequestParam(value = "errorType", required = false) String errorType,
			@RequestParam(value = "secret", required = false, defaultValue = "false") boolean secret,
			Principal principal) {
		Question question = this.questionService.getQuestion(id);
		if (!this.questionService.isAuthor(question, principal.getName())) {
			return "redirect:/question/detail/" + id;
		}
		this.questionService.modify(question, subject, content, equipmentName, softwareVersion, occurredDate,
				severity, errorType, secret);
		return "redirect:/question/detail/" + id;
	}

	@GetMapping("/delete/{id}")
	public String questionDelete(@PathVariable("id") Integer id, Principal principal,
			Authentication authentication) {
		Question question = this.questionService.getQuestion(id);
		// 작성자 본인 또는 관리자만 삭제
		if (!this.questionService.isAuthor(question, principal.getName()) && !hasAdminRole(authentication)) {
			return "redirect:/question/detail/" + id;
		}
		this.questionService.delete(question);
		return "redirect:/question/list";
	}
}
```

- [ ] **Step 2: 컴파일 (Task 9 + 10 일괄)**

Run: 공통 컴파일 명령
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: 커밋**

```bash
git add -A && git commit -m "feat: question create/modify/delete with fields, author, secret guard"
```

---

## Task 11: 등록/수정 폼 템플릿 (구조화 입력)

**Files:**
- Modify: `src/main/resources/templates/question_form.html`
- Modify: `src/main/resources/templates/question_modify.html`

- [ ] **Step 1: question_form.html 전체 교체**

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org"
th:replace="~{layout :: layout(~{::section})}">

<section class="container my-3" style="max-width: 720px;">
	<h2 class="border-bottom py-2">장비 오류 문의 등록</h2>
	<form th:action="@{/question/create}" method="post">
		<div class="mb-3">
			<label for="subject" class="form-label">제목</label>
			<input type="text" name="subject" id="subject" class="form-control" required>
		</div>
		<div class="row">
			<div class="col-md-6 mb-3">
				<label for="equipmentName" class="form-label">장비명/모델명</label>
				<input type="text" name="equipmentName" id="equipmentName" class="form-control">
			</div>
			<div class="col-md-6 mb-3">
				<label for="softwareVersion" class="form-label">소프트웨어/펌웨어 버전</label>
				<input type="text" name="softwareVersion" id="softwareVersion" class="form-control">
			</div>
		</div>
		<div class="row">
			<div class="col-md-4 mb-3">
				<label for="occurredDate" class="form-label">발생 일자</label>
				<input type="date" name="occurredDate" id="occurredDate" class="form-control">
			</div>
			<div class="col-md-4 mb-3">
				<label for="severity" class="form-label">심각도</label>
				<select name="severity" id="severity" class="form-select">
					<option value="낮음">낮음</option>
					<option value="보통" selected>보통</option>
					<option value="높음">높음</option>
					<option value="긴급">긴급</option>
				</select>
			</div>
			<div class="col-md-4 mb-3">
				<label for="errorType" class="form-label">오류 유형</label>
				<select name="errorType" id="errorType" class="form-select">
					<option value="설치">설치</option>
					<option value="실행" selected>실행</option>
					<option value="네트워크">네트워크</option>
					<option value="기타">기타</option>
				</select>
			</div>
		</div>
		<div class="mb-3">
			<label for="content" class="form-label">오류 증상 (상세)</label>
			<textarea name="content" id="content" class="form-control" rows="8" required></textarea>
		</div>
		<div class="form-check mb-3">
			<input type="checkbox" name="secret" id="secret" value="true" class="form-check-input">
			<label for="secret" class="form-check-label">비밀글로 등록 (작성자와 관리자만 열람)</label>
		</div>
		<button type="submit" class="btn btn-primary">저장하기</button>
		<a th:href="@{/question/list}" class="btn btn-secondary">목록</a>
	</form>
</section>

</html>
```

- [ ] **Step 2: question_modify.html 전체 교체**

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org"
th:replace="~{layout :: layout(~{::section})}">

<section class="container my-3" style="max-width: 720px;">
	<h2 class="border-bottom py-2">문의 수정</h2>
	<form th:action="@{|/question/modify/${question.id}|}" method="post">
		<div class="mb-3">
			<label for="subject" class="form-label">제목</label>
			<input type="text" name="subject" id="subject" class="form-control" th:value="${question.subject}" required>
		</div>
		<div class="row">
			<div class="col-md-6 mb-3">
				<label for="equipmentName" class="form-label">장비명/모델명</label>
				<input type="text" name="equipmentName" id="equipmentName" class="form-control" th:value="${question.equipmentName}">
			</div>
			<div class="col-md-6 mb-3">
				<label for="softwareVersion" class="form-label">소프트웨어/펌웨어 버전</label>
				<input type="text" name="softwareVersion" id="softwareVersion" class="form-control" th:value="${question.softwareVersion}">
			</div>
		</div>
		<div class="row">
			<div class="col-md-4 mb-3">
				<label for="occurredDate" class="form-label">발생 일자</label>
				<input type="date" name="occurredDate" id="occurredDate" class="form-control" th:value="${question.occurredDate}">
			</div>
			<div class="col-md-4 mb-3">
				<label for="severity" class="form-label">심각도</label>
				<select name="severity" id="severity" class="form-select">
					<option th:each="s : ${ {'낮음','보통','높음','긴급'} }" th:value="${s}" th:text="${s}"
						th:selected="${s == question.severity}"></option>
				</select>
			</div>
			<div class="col-md-4 mb-3">
				<label for="errorType" class="form-label">오류 유형</label>
				<select name="errorType" id="errorType" class="form-select">
					<option th:each="e : ${ {'설치','실행','네트워크','기타'} }" th:value="${e}" th:text="${e}"
						th:selected="${e == question.errorType}"></option>
				</select>
			</div>
		</div>
		<div class="mb-3">
			<label for="content" class="form-label">오류 증상 (상세)</label>
			<textarea name="content" id="content" class="form-control" rows="8" th:text="${question.content}" required></textarea>
		</div>
		<div class="form-check mb-3">
			<input type="checkbox" name="secret" id="secret" value="true" class="form-check-input" th:checked="${question.secret}">
			<label for="secret" class="form-check-label">비밀글로 등록 (작성자와 관리자만 열람)</label>
		</div>
		<button type="submit" class="btn btn-primary">수정하기</button>
		<a th:href="@{|/question/detail/${question.id}|}" class="btn btn-secondary">취소</a>
	</form>
</section>

</html>
```

- [ ] **Step 3: 컴파일 + 부팅 스모크 (문의 등록)**

공통 컴파일 명령 후 부팅. 브라우저에서 로그인 → `/question/create`로 구조화 필드 입력·저장 → 목록 표시 확인. (자동 스모크는 Task 4 스크립트로 부팅 확인.)

- [ ] **Step 4: 커밋**

```bash
git add -A && git commit -m "feat: structured inputs in question create/modify forms"
```

---

## Task 12: 목록 템플릿 (컬럼 + 비밀글 잠금)

**Files:**
- Modify: `src/main/resources/templates/question_list.html`

- [ ] **Step 1: question_list.html 전체 교체**

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org"
th:replace="~{layout :: layout(~{::section})}">

<section class="container my-3">
	<div class="d-flex justify-content-between align-items-center mb-3">
		<h2>장비 고객 문의 게시판</h2>
		<a th:href="@{/question/create}" class="btn btn-primary" th:if="${isLogin}">문의 등록하기</a>
	</div>
	<table class="table table-bordered table-hover text-center align-middle">
		<thead class="table-dark">
			<tr>
				<th>번호</th>
				<th>제목</th>
				<th>장비명</th>
				<th>발생일</th>
				<th>심각도</th>
				<th>답변</th>
				<th>작성일</th>
			</tr>
		</thead>
		<tbody>
			<tr th:if="${#lists.isEmpty(questionList)}">
				<td colspan="7">등록된 게시글이 없습니다.</td>
			</tr>
			<tr th:each="question, loop : ${questionList}">
				<td th:text="${loop.count}"></td>
				<td class="text-start">
					<!-- 열람 가능: 공개글 이거나, 관리자 이거나, 작성자 본인 -->
					<th:block th:with="canView=${!question.secret or isAdmin or (loginUser != null and question.author != null and loginUser.username == question.author.username)}">
						<a th:if="${canView}" th:href="@{|/question/detail/${question.id}|}">
							<span th:if="${question.secret}">🔒 </span>
							<span th:text="${question.subject}"></span>
						</a>
						<span th:unless="${canView}" class="text-muted">🔒 비밀글입니다</span>
					</th:block>
				</td>
				<td th:text="${question.equipmentName}"></td>
				<td th:text="${question.occurredDate != null ? #temporals.format(question.occurredDate, 'yyyy-MM-dd') : '-'}"></td>
				<td>
					<span class="badge"
						th:classappend="${question.severity == '긴급'} ? ' bg-danger' : (${question.severity == '높음'} ? ' bg-warning text-dark' : (${question.severity == '보통'} ? ' bg-secondary' : ' bg-light text-dark'))"
						th:text="${question.severity != null ? question.severity : '-'}"></span>
				</td>
				<td>
					<span th:if="${#lists.isEmpty(question.answerList)}" class="badge bg-light text-dark">미답변</span>
					<span th:unless="${#lists.isEmpty(question.answerList)}" class="badge bg-success">답변완료</span>
				</td>
				<td th:text="${#temporals.format(question.createDate, 'yyyy-MM-dd HH:mm')}"></td>
			</tr>
		</tbody>
	</table>
</section>

</html>
```

- [ ] **Step 2: 컴파일 + 부팅 스모크 (목록 컬럼/비밀글)**

공통 컴파일 명령 후 부팅. 브라우저에서 목록의 장비명/발생일/심각도 badge/답변 상태 표시 확인. 다른 사용자로 로그인 시 타인 비밀글이 "🔒 비밀글입니다"로 잠김 확인.

- [ ] **Step 3: 커밋**

```bash
git add -A && git commit -m "feat: inquiry list columns and secret-post locking"
```

---

## Task 13: 상세 템플릿 (구조화 필드 + 관리자 답변 폼)

**Files:**
- Modify: `src/main/resources/templates/question_detail.html`

- [ ] **Step 1: question_detail.html 전체 교체**

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org"
th:replace="~{layout :: layout(~{::section})}">

<section class="container my-3">
	<h2 class="border-bottom py-2">
		<span th:if="${question.secret}">🔒 </span>
		<span th:text="${question.subject}"></span>
	</h2>

	<div class="card my-3">
		<div class="card-body">
			<table class="table table-sm mb-3">
				<tbody>
					<tr><th style="width:160px;">장비명/모델명</th><td th:text="${question.equipmentName}"></td></tr>
					<tr><th>SW/펌웨어 버전</th><td th:text="${question.softwareVersion}"></td></tr>
					<tr><th>발생 일자</th><td th:text="${question.occurredDate != null ? #temporals.format(question.occurredDate, 'yyyy-MM-dd') : '-'}"></td></tr>
					<tr><th>심각도</th><td th:text="${question.severity}"></td></tr>
					<tr><th>오류 유형</th><td th:text="${question.errorType}"></td></tr>
					<tr><th>작성자</th><td th:text="${question.author != null ? question.author.username : '-'}"></td></tr>
				</tbody>
			</table>
			<div class="card-text" style="white-space: pre-line;" th:text="${question.content}"></div>
			<div class="d-flex justify-content-end mt-3">
				<div class="badge bg-light text-dark p-2">
					<span th:text="${#temporals.format(question.createDate, 'yyyy-MM-dd HH:mm')}"></span>
				</div>
			</div>
		</div>
	</div>

	<div class="my-3">
		<a th:href="@{|/question/modify/${question.id}|}" class="btn btn-warning"
			th:if="${loginUser != null and question.author != null and loginUser.username == question.author.username}">수정</a>
		<a th:href="@{|/question/delete/${question.id}|}" class="btn btn-danger"
			th:if="${isAdmin or (loginUser != null and question.author != null and loginUser.username == question.author.username)}"
			onclick="return confirm('정말 삭제하시겠습니까?');">삭제</a>
		<a th:href="@{/question/list}" class="btn btn-secondary">목록</a>
	</div>

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

	<!-- 답변 등록: 관리자만 -->
	<form th:if="${isAdmin}" th:action="@{|/answer/create/${question.id}|}" method="post" class="my-3">
		<div class="mb-3">
			<label for="content" class="form-label">관리자 답변</label>
			<textarea name="content" id="content" class="form-control" rows="5" required></textarea>
		</div>
		<button type="submit" class="btn btn-primary">답변 등록</button>
	</form>
	<div th:unless="${isAdmin}" class="alert alert-secondary my-3">답변은 관리자만 등록할 수 있습니다.</div>

</section>

</html>
```

- [ ] **Step 2: 컴파일 + 부팅 스모크 (상세/답변 폼 가시성)**

공통 컴파일 명령 후 부팅. 일반 사용자: 답변 폼 대신 안내문 표시. 관리자: 답변 폼 표시. 작성자 본인에게만 [수정], 작성자/관리자에게 [삭제] 표시 확인.

- [ ] **Step 3: 커밋**

```bash
git add -A && git commit -m "feat: detail page with structured fields and admin-only answer form"
```

---

## Task 14: Answer 확장 + 관리자 전용 답변 등록

**Files:**
- Modify: `src/main/java/com/mysite/jaminboard/answer/Answer.java`
- Modify: `src/main/java/com/mysite/jaminboard/answer/AnswerService.java`
- Modify: `src/main/java/com/mysite/jaminboard/answer/AnswerController.java`

- [ ] **Step 1: Answer.java 전체 교체**

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
}
```

- [ ] **Step 2: AnswerService.java 전체 교체**

```java
package com.mysite.jaminboard.answer;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

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
}
```

- [ ] **Step 3: AnswerController.java 전체 교체**

```java
package com.mysite.jaminboard.answer;

import java.security.Principal;

import org.springframework.stereotype.Controller;
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
}
```
참고: `/answer/**`는 SecurityConfig에서 `hasRole("ADMIN")`이므로 일반 사용자가 직접 POST해도 403으로 차단된다(이중 방어).

- [ ] **Step 4: 컴파일**

Run: 공통 컴파일 명령
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: 커밋**

```bash
git add -A && git commit -m "feat: answer author and admin-only answer creation"
```

---

## Task 15: frontend-design 스킬로 UI 개선

**Files:**
- Modify: `src/main/resources/templates/*.html`, `src/main/resources/static/style.css`

- [ ] **Step 1: frontend-design 스킬 호출**

`Skill(frontend-design)`를 호출하고, 아래 제약·목표를 전달한다.

**준수(절대 위반 금지):** 목록=Bootstrap table, 상세=Bootstrap card, 버튼=btn 클래스, 전체=container, 공통=layout.html, 표준 HTML 구조. **새 라이브러리/CDN 추가 금지**(로컬 bootstrap.min.css + style.css만). 클래스명/패키지명 변경 금지. 폼의 `name` 속성과 `th:*` 바인딩은 변경 금지(백엔드 연동 깨짐 방지).

**개선 목표:** 헤더/네비 정돈, 카드·표 여백과 정렬, 심각도 badge 색상 일관성, 폼 그룹 간격, 빈 상태(목록 비었을 때) 안내 시각화, 반응형 정리. style.css에 보조 스타일 추가.

- [ ] **Step 2: 컴파일 + 부팅 + 브라우저 확인**

공통 컴파일 명령 후 부팅. 전 화면(목록/상세/등록/수정/로그인/회원가입/관리자로그인)에서 레이아웃 깨짐 없는지, 기능(링크/폼 submit) 정상인지 확인.

- [ ] **Step 3: 규칙 준수 셀프 점검**

다음을 직접 확인: 목록이 여전히 `<table class="table ...">`인가 · 상세가 `card`인가 · 버튼이 `btn ...`인가 · `container` 사용 · `layout.html` 유지 · 새 외부 리소스 링크가 추가되지 않았는가(grep로 `https://`, `cdn` 확인) · 폼 `name` 속성 유지.

- [ ] **Step 4: 커밋**

```bash
git add -A && git commit -m "style: polish UI within Bootstrap rules"
```

---

## Task 16: 전체 End-to-End 검증

**Files:** (없음 — 검증/문서만)

- [ ] **Step 1: 앱 부팅**

```powershell
$j="D:\2504110113\Spring\stsproject\jaminboard"
Start-Process "$j\gradlew.bat" -ArgumentList "-p","$j","bootRun","--console=plain" -WindowStyle Hidden
Start-Sleep 25
```

- [ ] **Step 2: 시나리오 수동 검증 (브라우저)**

다음을 순서대로 확인하고 결과를 기록:
1. `/user/signup`으로 일반 사용자 `userA` 가입 → `/user/login` 로그인.
2. `/question/create`에서 구조화 필드 입력 + 비밀글 체크하여 등록 → 목록에 🔒 표시.
3. 로그아웃 후 `userB` 가입·로그인 → `userA`의 비밀글 상세 접근 시 목록으로 차단됨.
4. `userB`에게는 답변 폼 대신 "관리자만" 안내, 답변 POST(`/answer/create/{id}`) 직접 시도 시 403.
5. 로그아웃 → `/admin/login`에서 `admin` / `admin1234` 로그인 → navbar "(관리자)".
6. 관리자가 비밀글 상세 열람 + 답변 등록 → 상세에 답변 표시, 목록 "답변완료" badge.
7. `userA`로 로그인 → 본인 글 [수정] 가능, 타인 글엔 [수정] 미표시.
8. 작성자/관리자만 [삭제] 가능, 삭제 확인창 동작, 삭제 시 답변 동반 삭제.

- [ ] **Step 3: H2 콘솔 확인**

`/h2-console` (`jdbc:h2:~/jaminboard`, `sa`)에서:
- `SELECT * FROM SITE_USER;` — admin + 가입 사용자, ROLE 컬럼.
- `SELECT * FROM QUESTION;` — 신규 컬럼(EQUIPMENT_NAME, SOFTWARE_VERSION, OCCURRED_DATE, SEVERITY, ERROR_TYPE, SECRET, AUTHOR_ID).
- `SELECT * FROM ANSWER;` — AUTHOR_ID 컬럼.

- [ ] **Step 4: 앱 종료 + 최종 커밋**

```powershell
Get-Process java -EA SilentlyContinue | Where-Object {$_.StartTime -gt (Get-Date).AddMinutes(-30)} | Stop-Process -Force -EA SilentlyContinue
```
```bash
git add -A && git commit -m "docs: verified equipment inquiry board end-to-end" --allow-empty
```

---

## Self-Review (작성자 점검 완료)

- **Spec 커버리지:** 로그인/회원가입(Task 4)·관리자(Task 5,7)·비밀글(Task 8,10,12,13)·구조화 필드(Task 8~13)·관리자 전용 답변(Task 13,14)·UI 개선(Task 15)·Security 의존성(Task 1) — 설계 9개 섹션 모두 작업에 매핑됨.
- **Placeholder:** 모든 코드 스텝에 실제 코드 포함, TBD 없음. (검증 방식은 프로젝트 규약상 브라우저+H2 — 의도된 비-TDD 적용.)
- **타입 일관성:** `create(...)`/`modify(...)` 시그니처가 Task 9 정의와 Task 10 호출 일치 · `isAuthor(question, username)` 정의(Task 9)/사용(Task 10) 일치 · `AnswerService.create(question, content, author)` 정의(Task 14)/호출(Task 14 컨트롤러) 일치 · `loginUser`/`isAdmin`/`isLogin` 주입(Task 6)/사용(Task 6,12,13) 일치 · SiteUser id=Long, Question/Answer id=Integer 유지.
- **주의(실행 시 보정):** Spring Security 7(Boot 4.0) 일부 DSL 시그니처는 컴파일 단계에서 메시지에 따라 보정(주로 `headers.frameOptions`, `logout`). 기능/구조는 불변.
