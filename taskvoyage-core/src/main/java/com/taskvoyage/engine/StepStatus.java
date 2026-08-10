package com.taskvoyage.engine;

/**
 * 步骤执行状态
 */
public enum StepStatus {

    /** 待执行 */
    PENDING,

    /** 执行中 */
    RUNNING,

    /** 执行成功 */
    SUCCESS,

    /** 执行失败 */
    FAILED,

    /** 已补偿 */
    COMPENSATED,

    /** 补偿失败 */
    COMPENSATE_FAILED
}
