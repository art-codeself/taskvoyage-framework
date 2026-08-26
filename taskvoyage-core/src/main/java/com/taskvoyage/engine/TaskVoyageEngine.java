package com.taskvoyage.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskvoyage.entity.TaskVoyageInstance;
import com.taskvoyage.entity.StepExecutionLog;
import com.taskvoyage.exception.TaskVoyageExecutionException;
import com.taskvoyage.storage.TaskVoyageInstanceRepository;
import com.taskvoyage.storage.StepExecutionLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * TaskVoyage 引擎核心
 * <p>
 * 负责按顺序执行步骤、记录日志、处理重试和补偿。
 *
 * @param <T> 业务上下文类型
 */
@Slf4j
public class TaskVoyageEngine<T> {

    private final List<StepHandler<T>> steps;
    private final RetryPolicy retryPolicy;
    private final TaskVoyageInstanceRepository instanceRepository;
    private final StepExecutionLogRepository stepLogRepository;
    private final ObjectMapper objectMapper;

    public TaskVoyageEngine(List<StepHandler<T>> steps,
                      RetryPolicy retryPolicy,
                      TaskVoyageInstanceRepository instanceRepository,
                      StepExecutionLogRepository stepLogRepository,
                      ObjectMapper objectMapper) {
        this.steps = steps;
        this.retryPolicy = retryPolicy;
        this.instanceRepository = instanceRepository;
        this.stepLogRepository = stepLogRepository;
        this.objectMapper = objectMapper;
    }

    // ==================== 核心执行 ====================

    /**
     * 执行 TaskVoyage
     *
     * @param instance TaskVoyage 实例
     * @param context  业务上下文
     */
    public void execute(TaskVoyageInstance instance, T context) {
        log.info("开始执行 TaskVoyage: id={}, name={}, totalSteps={}",
                instance.getId(), instance.getTaskVoyageName(), instance.getTotalSteps());

        // 更新状态为 RUNNING
        instance.setStatus(TaskVoyageStatus.RUNNING.name());
        instance.setUpdateTime(LocalDateTime.now());
        instanceRepository.updateById(instance);

        try {
            executeSteps(instance, context, 0);
            instance.setStatus(TaskVoyageStatus.SUCCESS.name());
            instance.setUpdateTime(LocalDateTime.now());
            instanceRepository.updateById(instance);
            log.info("TaskVoyage 执行成功: id={}", instance.getId());

        } catch (Exception e) {
            log.error("TaskVoyage 执行失败: id={}, error={}", instance.getId(), e.getMessage(), e);
            instance.setStatus(TaskVoyageStatus.FAILED.name());
            instance.setErrorMessage(e.getMessage());
            instance.setUpdateTime(LocalDateTime.now());
            instanceRepository.updateById(instance);
            throw new TaskVoyageExecutionException("TaskVoyage 执行失败: " + e.getMessage(), e,
                    instance.getId(), instance.getCurrentStepIndex());
        }
    }

    /**
     * 从指定步骤开始执行
     */
    private void executeSteps(TaskVoyageInstance instance, T context, int fromStep) {
        for (int i = fromStep; i < steps.size(); i++) {
            StepHandler<T> step = steps.get(i);
            instance.setCurrentStepIndex(i);
            instance.setUpdateTime(LocalDateTime.now());
            instanceRepository.updateById(instance);

            // 创建步骤日志
            StepExecutionLog stepLog = createStepLog(instance.getId(), step, i);
            stepLogRepository.insert(stepLog);

            StepContext<T> stepContext = new StepContext<>(instance.getId(), i, step.getStepName(), context);

            try {
                // 记录 RUNNING
                stepLog.setStatus(StepStatus.RUNNING.name());
                stepLog.setStartTime(LocalDateTime.now());
                stepLogRepository.updateById(stepLog);

                log.info("执行步骤 [{}/{}]: {}, taskvoyageId={}", i + 1, steps.size(), step.getStepName(), instance.getId());

                // 带重试执行
                executeWithRetry(stepLog, step, stepContext);

                // 成功
                stepLog.setStatus(StepStatus.SUCCESS.name());
                stepLog.setEndTime(LocalDateTime.now());
                stepLog.setOutputData(serializeOutput(stepContext.getOutputData()));
                stepLogRepository.updateById(stepLog);

            } catch (Exception e) {
                // 失败
                stepLog.setStatus(StepStatus.FAILED.name());
                stepLog.setEndTime(LocalDateTime.now());
                stepLog.setErrorMessage(e.getMessage());
                stepLogRepository.updateById(stepLog);

                log.error("步骤 [{}] 执行失败: taskvoyageId={}, stepName={}, error={}",
                        i, instance.getId(), step.getStepName(), e.getMessage());

                // 补偿已成功的步骤
                compensateSuccessfulSteps(instance.getId(), i, context);

                throw new TaskVoyageExecutionException(
                        String.format("步骤 [%s] 执行失败: %s", step.getStepName(), e.getMessage()),
                        e, instance.getId(), i);
            }
        }
    }

    /**
     * 带重试执行单个步骤
     */
    private void executeWithRetry(StepExecutionLog stepLog, StepHandler<T> step, StepContext<T> context) throws Exception {
        int retryCount = 0;
        Exception lastException = null;

        while (retryCount <= retryPolicy.getMaxRetries()) {
            try {
                step.execute(context);
                return;
            } catch (Exception e) {
                lastException = e;
                if (retryCount < retryPolicy.getMaxRetries()) {
                    retryCount++;
                    stepLog.setRetryCount(retryCount);
                    stepLogRepository.updateById(stepLog);

                    long waitTime = (long) (retryPolicy.getRetryInterval() * Math.pow(retryPolicy.getBackoffMultiplier(), retryCount - 1));
                    log.warn("步骤 [{}] 第 {} 次重试，等待 {}ms: {}", step.getStepName(), retryCount, waitTime, e.getMessage());
                    Thread.sleep(waitTime);
                } else {
                    break;
                }
            }
        }
        throw lastException;
    }

    // ==================== 重试策略 ====================

    /**
     * 单步重试：重新执行指定步骤（不补偿其他步骤）
     *
     * @param taskVoyageInstanceId TaskVoyage 实例 ID
     * @param stepOrder      步骤顺序
     * @param context        业务上下文
     */
    @Transactional(rollbackFor = Exception.class)
    public void retrySingleStep(Long taskVoyageInstanceId, int stepOrder, T context) {
        log.info("单步重试: taskvoyageId={}, stepOrder={}", taskVoyageInstanceId, stepOrder);

        TaskVoyageInstance instance = instanceRepository.selectById(taskVoyageInstanceId);
        if (instance == null) {
            throw new TaskVoyageExecutionException("TaskVoyage 实例不存在: " + taskVoyageInstanceId, taskVoyageInstanceId, stepOrder);
        }

        instance.setRetryCount(instance.getRetryCount() + 1);
        instance.setStatus(TaskVoyageStatus.RUNNING.name());
        instance.setUpdateTime(LocalDateTime.now());
        instanceRepository.updateById(instance);

        StepHandler<T> step = steps.get(stepOrder);

        // 查找该步骤的日志记录
        List<StepExecutionLog> logs = getStepLogs(taskVoyageInstanceId);
        StepExecutionLog stepLog = logs.stream()
                .filter(l -> l.getStepOrder() == stepOrder)
                .max(Comparator.comparing(StepExecutionLog::getId))
                .orElseGet(() -> {
                    StepExecutionLog newLog = createStepLog(taskVoyageInstanceId, step, stepOrder);
                    stepLogRepository.insert(newLog);
                    return newLog;
                });

        // 重置状态为 RUNNING
        stepLog.setStatus(StepStatus.RUNNING.name());
        stepLog.setStartTime(LocalDateTime.now());
        stepLog.setEndTime(null);
        stepLog.setErrorMessage(null);
        stepLog.setRetryCount(stepLog.getRetryCount() + 1);
        stepLogRepository.updateById(stepLog);

        StepContext<T> stepContext = new StepContext<>(taskVoyageInstanceId, stepOrder, step.getStepName(), context);

        try {
            step.execute(stepContext);
            stepLog.setStatus(StepStatus.SUCCESS.name());
            stepLog.setEndTime(LocalDateTime.now());
            stepLog.setOutputData(serializeOutput(stepContext.getOutputData()));
            stepLogRepository.updateById(stepLog);

            // 检查是否所有步骤都已完成
            checkAndUpdateTaskVoyageCompletion(instance, logs);

        } catch (Exception e) {
            stepLog.setStatus(StepStatus.FAILED.name());
            stepLog.setEndTime(LocalDateTime.now());
            stepLog.setErrorMessage(e.getMessage());
            stepLogRepository.updateById(stepLog);

            instance.setStatus(TaskVoyageStatus.FAILED.name());
            instance.setErrorMessage(e.getMessage());
            instance.setUpdateTime(LocalDateTime.now());
            instanceRepository.updateById(instance);

            throw new TaskVoyageExecutionException(
                    String.format("单步重试失败 [%s]: %s", step.getStepName(), e.getMessage()),
                    e, taskVoyageInstanceId, stepOrder);
        }
    }

    /**
     * 断点重试：补偿已成功步骤到失败点之前，从失败步骤重新开始
     *
     * @param taskVoyageInstanceId TaskVoyage 实例 ID
     * @param context        业务上下文
     */
    @Transactional(rollbackFor = Exception.class)
    public void retryFromBreakpoint(Long taskVoyageInstanceId, T context) {
        log.info("断点重试: taskvoyageId={}", taskVoyageInstanceId);

        TaskVoyageInstance instance = instanceRepository.selectById(taskVoyageInstanceId);
        if (instance == null) {
            throw new TaskVoyageExecutionException("TaskVoyage 实例不存在: " + taskVoyageInstanceId, taskVoyageInstanceId, null);
        }

        instance.setRetryCount(instance.getRetryCount() + 1);
        instance.setStatus(TaskVoyageStatus.RUNNING.name());
        instance.setUpdateTime(LocalDateTime.now());
        instanceRepository.updateById(instance);

        List<StepExecutionLog> logs = getStepLogs(taskVoyageInstanceId);

        // 找到第一个失败的步骤
        int failedStepIndex = findFirstFailedStep(logs);
        if (failedStepIndex < 0) {
            failedStepIndex = instance.getCurrentStepIndex();
        }

        log.info("断点重试从步骤 {} 开始: taskvoyageId={}", failedStepIndex, taskVoyageInstanceId);

        // 补偿 0 到 failedStepIndex-1 的成功步骤
        compensateSteps(instance.getId(), failedStepIndex, context);

        // 重置失败步骤及之后步骤的状态为 PENDING
        resetStepsToPending(taskVoyageInstanceId, failedStepIndex, logs);

        // 重新执行
        try {
            executeSteps(instance, context, failedStepIndex);
            instance.setStatus(TaskVoyageStatus.SUCCESS.name());
            instance.setErrorMessage(null);
            instance.setUpdateTime(LocalDateTime.now());
            instanceRepository.updateById(instance);
            log.info("断点重试成功: taskvoyageId={}", taskVoyageInstanceId);

        } catch (Exception e) {
            instance.setStatus(TaskVoyageStatus.FAILED.name());
            instance.setErrorMessage(e.getMessage());
            instance.setUpdateTime(LocalDateTime.now());
            instanceRepository.updateById(instance);
            throw new TaskVoyageExecutionException("断点重试失败: " + e.getMessage(), e, taskVoyageInstanceId, failedStepIndex);
        }
    }

    /**
     * 整体重试：补偿所有已完成步骤，从头执行整个 TaskVoyage
     *
     * @param taskVoyageInstanceId TaskVoyage 实例 ID
     * @param context        业务上下文
     */
    @Transactional(rollbackFor = Exception.class)
    public void retryAll(Long taskVoyageInstanceId, T context) {
        log.info("整体重试: taskvoyageId={}", taskVoyageInstanceId);

        TaskVoyageInstance instance = instanceRepository.selectById(taskVoyageInstanceId);
        if (instance == null) {
            throw new TaskVoyageExecutionException("TaskVoyage 实例不存在: " + taskVoyageInstanceId, taskVoyageInstanceId, null);
        }

        instance.setRetryCount(instance.getRetryCount() + 1);
        instance.setStatus(TaskVoyageStatus.RUNNING.name());
        instance.setCurrentStepIndex(0);
        instance.setUpdateTime(LocalDateTime.now());
        instanceRepository.updateById(instance);

        // 补偿所有已成功的步骤
        compensateSteps(instance.getId(), steps.size(), context);

        // 重置所有步骤状态为 PENDING
        List<StepExecutionLog> logs = getStepLogs(taskVoyageInstanceId);
        resetStepsToPending(taskVoyageInstanceId, 0, logs);

        // 从头执行
        try {
            executeSteps(instance, context, 0);
            instance.setStatus(TaskVoyageStatus.SUCCESS.name());
            instance.setErrorMessage(null);
            instance.setUpdateTime(LocalDateTime.now());
            instanceRepository.updateById(instance);
            log.info("整体重试成功: taskvoyageId={}", taskVoyageInstanceId);

        } catch (Exception e) {
            instance.setStatus(TaskVoyageStatus.FAILED.name());
            instance.setErrorMessage(e.getMessage());
            instance.setUpdateTime(LocalDateTime.now());
            instanceRepository.updateById(instance);
            throw new TaskVoyageExecutionException("整体重试失败: " + e.getMessage(), e, taskVoyageInstanceId, null);
        }
    }

    // ==================== 补偿逻辑 ====================

    /**
     * 补偿已成功的步骤（反向执行）
     *
     * @param taskVoyageInstanceId    TaskVoyage 实例 ID
     * @param beforeStepIndex   补偿到此索引之前（不包含此索引）
     * @param context           业务上下文
     */
    private void compensateSuccessfulSteps(Long taskVoyageInstanceId, int beforeStepIndex, T context) {
        log.info("开始补偿: taskvoyageId={}, 补偿步骤 0~{}", taskVoyageInstanceId, beforeStepIndex - 1);
        compensateSteps(taskVoyageInstanceId, beforeStepIndex, context);
    }

    /**
     * 补偿指定范围内的步骤（反向执行）
     */
    private void compensateSteps(Long taskVoyageInstanceId, int upToStepIndex, T context) {
        List<StepExecutionLog> logs = getStepLogs(taskVoyageInstanceId);

        // 获取已成功的步骤（反向执行）
        List<StepExecutionLog> successfulLogs = logs.stream()
                .filter(l -> l.getStepOrder() < upToStepIndex)
                .filter(l -> StepStatus.SUCCESS.name().equals(l.getStatus()) ||
                        StepStatus.COMPENSATED.name().equals(l.getStatus()))
                .sorted((a, b) -> Integer.compare(b.getStepOrder(), a.getStepOrder())) // 反向
                .collect(Collectors.toList());

        for (StepExecutionLog stepLog : successfulLogs) {
            int stepIndex = stepLog.getStepOrder();
            StepHandler<T> step = steps.get(stepIndex);

            StepContext<T> stepContext = new StepContext<>(taskVoyageInstanceId, stepIndex, step.getStepName(), context);

            try {
                log.info("补偿步骤 [{}]: {}, taskvoyageId={}", stepIndex, step.getStepName(), taskVoyageInstanceId);
                step.compensate(stepContext);

                stepLog.setStatus(StepStatus.COMPENSATED.name());
                stepLog.setEndTime(LocalDateTime.now());
                stepLogRepository.updateById(stepLog);

            } catch (Exception e) {
                log.error("补偿步骤 [{}] 失败: {}, taskvoyageId={}, error={}",
                        stepIndex, step.getStepName(), taskVoyageInstanceId, e.getMessage(), e);
                stepLog.setStatus(StepStatus.COMPENSATE_FAILED.name());
                stepLog.setErrorMessage("补偿失败: " + e.getMessage());
                stepLogRepository.updateById(stepLog);
                // 继续补偿其他步骤
            }
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建步骤日志
     */
    private StepExecutionLog createStepLog(Long taskVoyageInstanceId, StepHandler<T> step, int stepOrder) {
        StepExecutionLog stepLog = new StepExecutionLog();
        stepLog.setTaskVoyageInstanceId(taskVoyageInstanceId);
        stepLog.setStepName(step.getStepName());
        stepLog.setStepOrder(stepOrder);
        stepLog.setStatus(StepStatus.PENDING.name());
        stepLog.setRetryCount(0);
        return stepLog;
    }

    /**
     * 获取 TaskVoyage 实例的所有步骤日志
     */
    private List<StepExecutionLog> getStepLogs(Long taskVoyageInstanceId) {
        List<StepExecutionLog> allLogs = stepLogRepository.selectByTaskVoyageInstanceId(taskVoyageInstanceId);
        if (allLogs == null) {
            return new ArrayList<>();
        }
        return allLogs;
    }

    /**
     * 找到第一个失败的步骤索引
     */
    private int findFirstFailedStep(List<StepExecutionLog> logs) {
        return logs.stream()
                .filter(l -> StepStatus.FAILED.name().equals(l.getStatus()))
                .mapToInt(StepExecutionLog::getStepOrder)
                .min()
                .orElse(-1);
    }

    /**
     * 重置步骤状态为 PENDING
     */
    private void resetStepsToPending(Long taskVoyageInstanceId, int fromStepIndex, List<StepExecutionLog> logs) {
        logs.stream()
                .filter(l -> l.getStepOrder() >= fromStepIndex)
                .forEach(l -> {
                    l.setStatus(StepStatus.PENDING.name());
                    l.setErrorMessage(null);
                    l.setEndTime(null);
                    stepLogRepository.updateById(l);
                });
    }

    /**
     * 检查并更新 TaskVoyage 完成状态
     */
    private void checkAndUpdateTaskVoyageCompletion(TaskVoyageInstance instance, List<StepExecutionLog> logs) {
        boolean allSuccess = steps.size() == (int) logs.stream()
                .filter(l -> StepStatus.SUCCESS.name().equals(l.getStatus()))
                .map(StepExecutionLog::getStepOrder)
                .distinct()
                .count();

        if (allSuccess) {
            instance.setStatus(TaskVoyageStatus.SUCCESS.name());
            instance.setErrorMessage(null);
            instance.setUpdateTime(LocalDateTime.now());
            instanceRepository.updateById(instance);
            log.info("TaskVoyage 全部步骤完成: id={}", instance.getId());
        }
    }

    /**
     * 序列化输出数据
     */
    private String serializeOutput(Object output) {
        if (output == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(output);
        } catch (JsonProcessingException e) {
            log.warn("序列化输出数据失败: {}", e.getMessage());
            return output.toString();
        }
    }

    /**
     * 获取步骤列表
     */
    public List<StepHandler<T>> getSteps() {
        return steps;
    }

    /**
     * 获取重试策略
     */
    public RetryPolicy getRetryPolicy() {
        return retryPolicy;
    }
}
