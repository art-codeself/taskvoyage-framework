package com.taskvoyage.engine;

import lombok.Data;

/**
 * 步骤执行上下文，封装业务数据和 TaskVoyage 实例信息
 *
 * @param <T> 业务上下文类型
 */
@Data
public class StepContext<T> {

    /** TaskVoyage 实例 ID */
    private Long taskVoyageInstanceId;

    /** 当前步骤索引 */
    private Integer stepIndex;

    /** 步骤名称 */
    private String stepName;

    /** 业务上下文数据 */
    private T businessContext;

    /** 步骤输出数据（执行后填充） */
    private Object outputData;

    public StepContext() {
    }

    public StepContext(Long taskVoyageInstanceId, Integer stepIndex, String stepName, T businessContext) {
        this.taskVoyageInstanceId = taskVoyageInstanceId;
        this.stepIndex = stepIndex;
        this.stepName = stepName;
        this.businessContext = businessContext;
    }
}
