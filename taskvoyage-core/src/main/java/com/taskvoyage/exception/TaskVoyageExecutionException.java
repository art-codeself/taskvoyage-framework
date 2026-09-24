package com.taskvoyage.exception;

/**
 * TaskVoyage 执行异常
 */
public class TaskVoyageExecutionException extends RuntimeException {

    private final Long taskVoyageInstanceId;
    private final Integer failedStepIndex;

    public TaskVoyageExecutionException(String message, Long taskVoyageInstanceId, Integer failedStepIndex) {
        super(message);
        this.taskVoyageInstanceId = taskVoyageInstanceId;
        this.failedStepIndex = failedStepIndex;
    }

    public TaskVoyageExecutionException(String message, Throwable cause, Long taskVoyageInstanceId, Integer failedStepIndex) {
        super(message, cause);
        this.taskVoyageInstanceId = taskVoyageInstanceId;
        this.failedStepIndex = failedStepIndex;
    }

    public Long getTaskVoyageInstanceId() {
        return taskVoyageInstanceId;
    }

    public Integer getFailedStepIndex() {
        return failedStepIndex;
    }
}
