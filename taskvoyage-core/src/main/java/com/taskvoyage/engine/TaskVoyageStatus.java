package com.taskvoyage.engine;

/**
 * TaskVoyage 实例状态
 */
public enum TaskVoyageStatus {

    /** 刚创建，尚未执行 */
    CREATED,

    /** 正在执行中 */
    RUNNING,

    /** 所有步骤执行成功 */
    SUCCESS,

    /** 执行失败（已补偿或无法继续） */
    FAILED,

    /** 已挂起，等待人工介入 */
    SUSPENDED,

    /** 已补偿完成 */
    COMPENSATED
}
