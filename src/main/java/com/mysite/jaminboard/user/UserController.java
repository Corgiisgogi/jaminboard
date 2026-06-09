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
