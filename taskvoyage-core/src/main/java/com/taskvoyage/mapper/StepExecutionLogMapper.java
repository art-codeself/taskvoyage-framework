package com.taskvoyage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.taskvoyage.entity.StepExecutionLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface StepExecutionLogMapper extends BaseMapper<StepExecutionLog> {

    /**
     * 查询指定 TaskVoyage 实例的所有步骤日志（按 step_order 排序）
     */
    List<StepExecutionLog> selectByTaskVoyageInstanceId(@Param("taskVoyageInstanceId") Long taskVoyageInstanceId);
}
