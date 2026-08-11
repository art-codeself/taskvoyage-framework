package com.taskvoyage.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * TaskVoyage 实例
 */
@Data
@TableName("taskvoyage_instance")
public class TaskVoyageInstance {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** TaskVoyage 名称 */
    private String taskVoyageName;

    /** 状态 */
    private String status;

    /** 当前步骤索引 */
    private Integer currentStepIndex;

    /** 总步骤数 */
    private Integer totalSteps;

    /** 已重试次数 */
    private Integer retryCount;

    /** 最大重试次数 */
    private Integer maxRetries;

    /** 错误信息 */
    private String errorMessage;

    /** 操作人 ID */
    private String operatorId;

    /** 上下文数据（JSON 格式，用于持久化业务上下文） */
    private String contextData;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
