package com.taskvoyage.engine;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 重试策略配置
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetryPolicy {

    /**
     * 最大重试次数（默认 3）
     */
    @Builder.Default
    private int maxRetries = 3;

    /**
     * 重试间隔（毫秒，默认 1000ms）
     */
    @Builder.Default
    private long retryInterval = 1000L;

    /**
     * 退避乘数（指数退避，默认 1.0 即等间隔）
     */
    @Builder.Default
    private double backoffMultiplier = 1.0;

    /**
     * 创建默认重试策略
     */
    public static RetryPolicy defaultPolicy() {
        return RetryPolicy.builder()
                .maxRetries(3)
                .retryInterval(1000L)
                .backoffMultiplier(1.0)
                .build();
    }
}
