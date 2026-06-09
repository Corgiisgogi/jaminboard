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
