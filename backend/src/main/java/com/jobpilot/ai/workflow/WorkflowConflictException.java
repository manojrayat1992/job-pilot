package com.jobpilot.ai.workflow;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class WorkflowConflictException extends RuntimeException {
    public WorkflowConflictException(String message) { super(message); }
}
