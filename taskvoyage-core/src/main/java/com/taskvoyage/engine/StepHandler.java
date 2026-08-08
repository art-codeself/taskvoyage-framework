package com.taskvoyage.engine;

/**
 * 步骤处理器接口
 * <p>
 * 每个业务步骤需要实现此接口，提供正向执行和补偿操作。
 *
 * @param <T> 业务上下文类型
 */
public interface StepHandler<T> {

    /**
     * 正向执行操作
     *
     * @param context 步骤上下文
     * @throws Exception 执行异常
     */
    void execute(StepContext<T> context) throws Exception;

    /**
     * 补偿操作（可选，默认空实现表示无需补偿）
     *
     * @param context 步骤上下文
     * @throws Exception 补偿异常
     */
    default void compensate(StepContext<T> context) throws Exception {
        // 默认无需补偿
    }

    /**
     * 获取步骤名称
     *
     * @return 步骤名称
     */
    String getStepName();
}
