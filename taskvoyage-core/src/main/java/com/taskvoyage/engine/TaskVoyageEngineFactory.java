package com.taskvoyage.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskvoyage.storage.TaskVoyageInstanceRepository;
import com.taskvoyage.storage.StepExecutionLogRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * TaskVoyage 引擎工厂
 * <p>
 * 根据步骤列表和重试策略创建 TaskVoyageEngine 实例。
 * 支持多种 TaskVoyage 类型，每种类型可以有不同的步骤组合。
 * <p>
 * 工厂注入存储层抽象接口（Repository），与具体存储后端解耦，
 * 由自动配置根据 {@code taskvoyage.storage.type} 决定使用 MySQL 或 MongoDB 实现。
 */
@Component
public class TaskVoyageEngineFactory {

    private final TaskVoyageInstanceRepository instanceRepository;
    private final StepExecutionLogRepository stepLogRepository;
    private final ObjectMapper objectMapper;

    public TaskVoyageEngineFactory(TaskVoyageInstanceRepository instanceRepository,
                             StepExecutionLogRepository stepLogRepository,
                             ObjectMapper objectMapper) {
        this.instanceRepository = instanceRepository;
        this.stepLogRepository = stepLogRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 创建 TaskVoyage 引擎
     *
     * @param steps       步骤处理器列表
     * @param retryPolicy 重试策略
     * @param <T>         业务上下文类型
     * @return TaskVoyageEngine 实例
     */
    public <T> TaskVoyageEngine<T> createEngine(List<StepHandler<T>> steps, RetryPolicy retryPolicy) {
        return new TaskVoyageEngine<>(steps, retryPolicy, instanceRepository, stepLogRepository, objectMapper);
    }

    /**
     * 创建 TaskVoyage 引擎（使用默认重试策略）
     */
    public <T> TaskVoyageEngine<T> createEngine(List<StepHandler<T>> steps) {
        return createEngine(steps, RetryPolicy.defaultPolicy());
    }
}
