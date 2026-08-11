package com.taskvoyage.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 步骤执行日志
 */
@Data
@TableName("step_execution_log")
public class StepExecutionLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** TaskVoyage 实例 ID */
    private Long taskVoyageInstanceId;

    /** 步骤名称 */
    private String stepName;

    /** 步骤顺序（从 0 开始） */
    private Integer stepOrder;

    /** 状态 */
    private String status;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 错误信息 */
    private String errorMessage;

    /** 重试次数 */
    private Integer retryCount;

    /** 输入数据（JSON） */
    private String inputData;

    /** 输出数据（JSON） */
    private String outputData;
}
