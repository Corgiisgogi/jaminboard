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
