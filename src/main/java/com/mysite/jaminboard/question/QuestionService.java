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
