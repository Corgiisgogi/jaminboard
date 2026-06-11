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
