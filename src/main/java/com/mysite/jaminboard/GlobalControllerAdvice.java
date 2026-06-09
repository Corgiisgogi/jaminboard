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
