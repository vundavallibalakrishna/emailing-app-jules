package com.wisestep.emailing.service;

import com.wisestep.emailing.dto.EmailRequestDto;
import com.wisestep.emailing.dto.EmailResponseDto;

public interface EmailSender {
    EmailResponseDto sendEmail(EmailRequestDto request);
}
