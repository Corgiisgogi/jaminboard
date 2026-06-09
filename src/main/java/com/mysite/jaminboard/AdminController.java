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
