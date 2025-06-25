package com.wisestep.emailing.dto;

import java.io.Serializable;
import java.util.List;

public class JobScheduledResponseDto implements Serializable {

    private String message;
    private String batchId; // Optional: could be a UUID for the entire batch
    private List<Long> jobIds; // Individual job IDs created in the database

    public JobScheduledResponseDto() {
    }

    public JobScheduledResponseDto(String message, String batchId, List<Long> jobIds) {
        this.message = message;
        this.batchId = batchId;
        this.jobIds = jobIds;
    }

    public JobScheduledResponseDto(String message, List<Long> jobIds) {
        this.message = message;
        this.jobIds = jobIds;
    }


    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getBatchId() {
        return batchId;
    }

    public void setBatchId(String batchId) {
        this.batchId = batchId;
    }

    public List<Long> getJobIds() {
        return jobIds;
    }

    public void setJobIds(List<Long> jobIds) {
        this.jobIds = jobIds;
    }
}
