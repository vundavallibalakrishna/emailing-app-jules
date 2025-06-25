package com.wisestep.emailing.dto;

import java.io.Serializable;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

public class BulkEmailRequestDto implements Serializable {

    @NotNull
    @NotEmpty
    @Valid // This ensures validation is cascaded to EmailRequestDto items if they have annotations
    private List<EmailRequestDto> emails;

    public BulkEmailRequestDto() {
    }

    public BulkEmailRequestDto(List<EmailRequestDto> emails) {
        this.emails = emails;
    }

    public List<EmailRequestDto> getEmails() {
        return emails;
    }

    public void setEmails(List<EmailRequestDto> emails) {
        this.emails = emails;
    }
}
