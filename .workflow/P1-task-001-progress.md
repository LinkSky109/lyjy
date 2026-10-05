# TASK-P1-001 监控告警模块开发进度

## 任务状态
- **任务ID**: TASK-P1-001
- **任务名称**: 监控告警（MetricsSampler + AlertService）
- **当前状态**: 进行中（编译错误修复中）
- **最后更新**: 2026-10-02

## 已完成工作

### 1. 数据模型层（4个文件）
- ✅ `AlertRule.java` - 告警规则实体（MyBatis-Plus）
- ✅ `AlertEvent.java` - 告警事件实体（MyBatis-Plus）
- ✅ `MetricSample.java` - 指标采样数据模型（ClickHouse）
- ✅ `MetricSampleMapper.java` - 指标采样数据访问层

### 2. 数据访问层（2个文件）
- ✅ `AlertRuleMapper.java` - 告警规则Mapper
- ✅ `AlertEventMapper.java` - 告警事件Mapper

### 3. 服务层（3个文件）
- ✅ `MetricSampler.java` - 指标采样服务（JVM/系统指标自动采集）
- ✅ `AlertEvaluator.java` - 告警评估引擎（定时评估规则）
- ✅ `AlertNotificationService.java` - 告警通知服务（日志/Webhook/钉钉）

### 4. 控制器层（1个文件）
- ✅ `MonitorController.java` - 监控API控制器（7个接口）
  - GET /api/monitor/metrics/latest - 最新指标快照
  - GET /api/monitor/metrics/history - 指标历史查询
  - POST /api/monitor/alert-rule - 创建/更新告警规则
  - GET /api/monitor/alert-rule/list - 告警规则列表
  - POST /api/monitor/alert-rule/{ruleId}/toggle - 切换规则启停
  - GET /api/monitor/alerts - 告警事件列表
  - POST /api/monitor/alerts/{eventId}/ack - 确认告警
  - POST /api/monitor/alerts/{eventId}/close - 关闭告警

### 5. 配置文件
- ✅ `application.yaml` - 添加监控配置段
- ✅ `init-clickhouse.sql` - 已包含 metric_sample 表DDL

## 当前问题

### 编译错误
**错误现象**: `MetricSampleServiceImpl.java` 报错找不到 `getTs()`, `getMetric()`, `getValue()`, `getLabels()` 方法

**根本原因**: 
- 存在两套监控服务实现：
  1. 新创建的直接实现：`MetricSampler.java`
  2. 已有的接口+实现：`MetricSampleService.java` + `MetricSampleServiceImpl.java`
- `MetricSample.java` 已有 `@Data` 注解，但可能存在以下情况之一：
  - Lombok 注解处理器未生效
  - 编译缓存问题
  - 文件内容与预期不符

**已执行操作**:
- ✅ 确认 `MetricSample.java` 包含 `@Data` 注解
- ✅ 确认 `pom.xml` 包含 Lombok 依赖
- ✅ 执行 `mvn clean` 清理编译缓存

## 待解决问题

### 1. 解决编译错误
- [ ] 验证 Lombok 注解处理器是否正常工作
- [ ] 检查 `MetricSample.java` 文件实际内容（可能读取缓存导致看到的不是最新内容）
- [ ] 如果 Lombok 不生效，手动添加 getter/setter
- [ ] 考虑删除重复的 `MetricSampleService` 接口和实现，统一使用新创建的 `MetricSampler`

### 2. 编译验证
- [ ] 执行 `mvn compile` 验证编译通过
- [ ] 检查是否有其他编译错误

### 3. 功能测试
- [ ] 启动服务验证监控模块正常加载
- [ ] 测试指标采样是否正常（JVM/系统指标）
- [ ] 测试告警规则评估是否触发
- [ ] 测试告警通知是否发送

### 4. 后续任务衔接
- [ ] 完成 TASK-P1-001 后标记为 completed
- [ ] 开始 TASK-P1-002（事中风控模块）

## 关键技术点

### 指标采集
- 采样间隔：10秒（可配置）
- 采集指标：JVM内存、CPU负载、线程数、订单计数
- 存储：ClickHouse metric_sample 表

### 告警评估
- 评估间隔：15秒（可配置）
- 评估逻辑：查询窗口内指标均值，与阈值比较
- 支持条件：GT/LT/GTE/LTE/EQ

### 告警通知
- 默认渠道：日志输出
- 预留渠道：Webhook、钉钉
- 通知内容：事件ID、规则ID、指标值、告警消息

## 下一步行动

1. **立即执行**: 解决编译错误
   - 读取 `MetricSample.java` 实际内容
   - 如果 `@Data` 注解不生效，手动添加 getter/setter
   - 或者删除重复的服务接口，统一使用 `MetricSampler`

2. **编译验证**: 确保 `mvn compile` 成功

3. **功能验证**: 启动服务测试监控功能

4. **任务流转**: 标记 TASK-P1-001 完成，开始 TASK-P1-002

## 相关文件清单

```
core/src/main/java/com/lhjypt/core/
├── model/monitor/
│   ├── AlertRule.java ✅
│   ├── AlertEvent.java ✅
│   └── MetricSample.java ✅ (需要验证)
├── mapper/
│   ├── AlertRuleMapper.java ✅
│   ├── AlertEventMapper.java ✅
│   └── MetricSampleMapper.java ✅
├── service/monitor/
│   ├── MetricSampler.java ✅
│   ├── AlertEvaluator.java ✅
│   ├── AlertNotificationService.java ✅
│   ├── MetricSampleService.java ⚠️ (重复，可能需要删除)
│   ├── AlertRuleService.java ⚠️ (重复，可能需要删除)
│   ├── AlertEventService.java ⚠️ (重复，可能需要删除)
│   └── impl/
│       ├── MetricSampleServiceImpl.java ⚠️ (重复，可能需要删除)
│       ├── AlertRuleServiceImpl.java ⚠️ (重复，可能需要删除)
│       └── AlertEventServiceImpl.java ⚠️ (重复，可能需要删除)
└── controller/
    └── MonitorController.java ✅
```

## 备注

- 项目中已存在一套监控服务接口和实现（MetricSampleService/AlertRuleService/AlertEventService）
- 新创建的实现更简洁直接（MetricSampler/AlertEvaluator/AlertNotificationService）
- 建议统一使用新创建的实现，删除旧的接口和实现以避免冲突
- 如果保留旧接口，需要让新实现适配旧接口
