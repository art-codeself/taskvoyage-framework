# TaskVoyage Framework

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/java-1.8%2B-green.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/spring--boot-2.7.18-brightgreen.svg)](https://spring.io/projects/spring-boot)

> Spring Boot 2.x 轻量级  Task Execution Framework —— 给你的任务一场可靠的旅程

**TaskVoyage** 是一个 Task Execution Framework.
它提供步骤编排、自动补偿、多种重试策略和人工介入能力，帮助你轻松处理多任务执行的数据一致性。

---

## 核心特性

| 特性 | 说明 |
|---|---|
| **步骤编排** | 按顺序执行多个步骤，每个步骤具备正向执行 + 补偿回滚 |
| **自动补偿** | 步骤失败时自动反向执行已成功步骤的 compensate |
| **三种重试** | 单步重试 / 断点重试 / 整体重试 |
| **指数退避** | 可配置重试次数、间隔和退避乘数 |
| **上下文持久化** | 业务上下文 JSON 序列化到数据库，重试时自动恢复 |
| **人工介入** | 挂起 / 恢复 / 标记步骤已处理 |
| **泛型设计** | `TaskVoyageEngine<T>` 适配任意业务上下文类型 |
| **开箱即用** | Spring Boot AutoConfiguration，引入依赖即可使用 |

---