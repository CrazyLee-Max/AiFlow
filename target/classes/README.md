# BPMN MCP Server

一个基于 Model Context Protocol (MCP) 的 BPMN 建模服务器，提供 BPMN 图形化建模的完整功能。

## 功能特性

- **BPMN 元素创建**: 支持创建各种 BPMN 元素（开始事件、结束事件、用户任务、服务任务、网关等）
- **连线管理**: 支持序列流、消息流等连线类型
- **自动布局**: 智能分析流程结构并自动排列元素位置
- **模型验证**: 完整的 BPMN 模型语法和语义验证
- **文件操作**: 支持模型的保存和加载
- **MCP 协议**: 完全兼容 MCP 协议，可与支持 MCP 的客户端集成

## 使用规则

### 基本原则

1. **模型管理**: 每个 BPMN 模型必须有唯一的 `modelId`，用于标识和管理模型，不能包含中文
2. **元素标识**: 每个 BPMN 元素必须有唯一的 `id`，在同一模型内不能重复，不能包含中文
3. **连线规则**: 连线必须连接已存在的元素，源元素和目标元素必须在同一模型中
4. **类型约束**: 元素类型必须是系统支持的类型，详见下方支持的节点类型列表
5. **坐标系统**: 使用标准的二维坐标系统，原点 (0,0) 在左上角，X 轴向右，Y 轴向下

### 操作顺序

1. **创建模型**: 使用 `create_bpmn_model` 创建新模型
2. **添加元素**: 使用 `add_bpmn_element` 添加 BPMN 元素
3. **添加连线**: 使用 `add_bpmn_connection` 连接元素
4. **自动布局**: 使用 `auto_layout_bpmn` 优化元素位置（可选）
5. **验证模型**: 使用 `validate_bpmn_model` 验证模型正确性
6. **保存模型**: 使用 `save_bpmn_model` 保存到文件

## 支持的节点类型

### 事件类型 (Events)
- **`startEvent`** - 开始事件：流程的起始点
- **`endEvent`** - 结束事件：流程的结束点
- **`intermediateEvent`** - 中间事件：流程中的中间事件

### 任务类型 (Tasks)
- **`userTask`** - 用户任务：需要人工参与的任务
- **`serviceTask`** - 服务任务：系统自动执行的任务
- **`scriptTask`** - 脚本任务：执行脚本代码的任务
- **`manualTask`** - 手动任务：需要手动执行但不通过系统的任务
- **`businessRuleTask`** - 业务规则任务：执行业务规则的任务

### 网关类型 (Gateways)
- **`exclusiveGateway`** - 排他网关：基于条件选择一个分支
- **`parallelGateway`** - 并行网关：并行执行多个分支
- **`inclusiveGateway`** - 包容网关：基于条件选择一个或多个分支

### 流类型 (Flows)
- **`sequenceFlow`** - 序列流：连接流程元素的主要流向
- **`messageFlow`** - 消息流：不同参与者之间的消息传递

### 子流程类型 (Sub-processes)
- **`subProcess`** - 子流程：包含其他活动的复合活动
- **`callActivity`** - 调用活动：调用外部流程或任务

## 如何调用

### MCP 协议调用

本服务器实现了标准的 MCP (Model Context Protocol) 协议，支持 JSON-RPC 2.0 格式的请求。

#### 1. 获取工具列表

```json
{
  "jsonrpc": "2.0",
  "id": "1",
  "method": "tools/list",
  "params": {}
}
```

#### 2. 调用具体工具

```json
{
  "jsonrpc": "2.0",
  "id": "2",
  "method": "tools/call",
  "params": {
    "name": "工具名称",
    "arguments": {
      // 工具参数
    }
  }
}
```

### 命令行调用

#### 启动服务器
```bash
# 编译项目
mvn clean compile

# 启动服务器
mvn exec:java -Dexec.mainClass="com.example.bpmn.mcp.BpmnMcpServer"
```

#### 使用 curl 调用
```bash
# 获取工具列表
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "1",
    "method": "tools/list",
    "params": {}
  }'

# 创建 BPMN 模型
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "2",
    "method": "tools/call",
    "params": {
      "name": "create_bpmn_model",
      "arguments": {
        "modelId": "my-process",
        "processId": "process_001",
        "processName": "我的业务流程"
      }
    }
  }'
```

### 编程语言调用示例

#### JavaScript/Node.js
```javascript
const axios = require('axios');

async function createBpmnModel() {
  const response = await axios.post('http://localhost:8080/mcp', {
    jsonrpc: "2.0",
    id: "1",
    method: "tools/call",
    params: {
      name: "create_bpmn_model",
      arguments: {
        modelId: "order-process",
        processId: "order_001",
        processName: "订单处理流程"
      }
    }
  });
  
  console.log(response.data);
}
```

#### Python
```python
import requests
import json

def create_bpmn_model():
    url = "http://localhost:8080/mcp"
    payload = {
        "jsonrpc": "2.0",
        "id": "1",
        "method": "tools/call",
        "params": {
            "name": "create_bpmn_model",
            "arguments": {
                "modelId": "order-process",
                "processId": "order_001",
                "processName": "订单处理流程"
            }
        }
    }
    
    response = requests.post(url, json=payload)
    return response.json()
```

#### Java
```java
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;

public class BpmnMcpClient {
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    
    public String createBpmnModel(String modelId, String processId, String processName) {
        Map<String, Object> request = Map.of(
            "jsonrpc", "2.0",
            "id", "1",
            "method", "tools/call",
            "params", Map.of(
                "name", "create_bpmn_model",
                "arguments", Map.of(
                    "modelId", modelId,
                    "processId", processId,
                    "processName", processName
                )
            )
        );
        
        try {
            String json = mapper.writeValueAsString(request);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8080/mcp"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
                
            HttpResponse<String> response = client.send(httpRequest, 
                HttpResponse.BodyHandlers.ofString());
            return response.body();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
```

## 技术栈

- **Java 17+**: 核心开发语言
- **Camunda BPMN Model**: BPMN 模型处理
- **Jackson**: JSON 序列化/反序列化
- **SLF4J + Logback**: 日志记录
- **JUnit 5**: 单元测试
- **Mockito**: 测试模拟

## 快速开始

### 环境要求

- Java 17 或更高版本
- Maven 3.6 或更高版本

### 构建项目

```bash
mvn clean compile
```

### 运行测试

```bash
mvn test
```

### 启动服务器

```bash
mvn exec:java -Dexec.mainClass="com.example.bpmn.mcp.BpmnMcpServer"
```

## MCP 工具列表

### 1. create_bpmn_model
创建新的 BPMN 模型

**参数:**
- `modelId` (string): 模型唯一标识符
- `processId` (string): 流程 ID
- `processName` (string): 流程名称

**示例:**
```json
{
  "name": "create_bpmn_model",
  "arguments": {
    "modelId": "my-process",
    "processId": "process_001",
    "processName": "My Business Process"
  }
}
```

### 2. add_bpmn_element
向模型添加 BPMN 元素

**参数:**
- `modelId` (string): 模型 ID
- `element` (object): 元素定义
  - `id` (string): 元素 ID
  - `type` (string): 元素类型 (startEvent, endEvent, userTask, serviceTask, exclusiveGateway, parallelGateway, inclusiveGateway)
  - `name` (string, 可选): 元素名称
  - `x` (number, 可选): X 坐标
  - `y` (number, 可选): Y 坐标

**示例:**
```json
{
  "name": "add_bpmn_element",
  "arguments": {
    "modelId": "my-process",
    "element": {
      "id": "start_event_1",
      "type": "startEvent",
      "name": "Process Start",
      "x": 100,
      "y": 100
    }
  }
}
```

### 3. add_bpmn_connection
添加元素间的连线

**参数:**
- `modelId` (string): 模型 ID
- `connection` (object): 连线定义
  - `id` (string): 连线 ID
  - `sourceRef` (string): 源元素 ID
  - `targetRef` (string): 目标元素 ID
  - `type` (string): 连线类型 (sequenceFlow, messageFlow)
  - `name` (string, 可选): 连线名称

**示例:**
```json
{
  "name": "add_bpmn_connection",
  "arguments": {
    "modelId": "my-process",
    "connection": {
      "id": "flow_1",
      "sourceRef": "start_event_1",
      "targetRef": "task_1",
      "type": "sequenceFlow"
    }
  }
}
```

### 4. auto_layout_bpmn
自动布局模型中的元素

**参数:**
- `modelId` (string): 模型 ID

**示例:**
```json
{
  "name": "auto_layout_bpmn",
  "arguments": {
    "modelId": "my-process"
  }
}
```

### 5. validate_bpmn_model
验证 BPMN 模型

**参数:**
- `modelId` (string): 模型 ID

**示例:**
```json
{
  "name": "validate_bpmn_model",
  "arguments": {
    "modelId": "my-process"
  }
}
```

### 6. save_bpmn_model
保存模型到文件

**参数:**
- `modelId` (string): 模型 ID
- `filePath` (string): 文件路径

**示例:**
```json
{
  "name": "save_bpmn_model",
  "arguments": {
    "modelId": "my-process",
    "filePath": "/path/to/my-process.bpmn"
  }
}
```

### 7. get_bpmn_model
获取模型的 XML 内容

**参数:**
- `modelId` (string): 模型 ID

**示例:**
```json
{
  "name": "get_bpmn_model",
  "arguments": {
    "modelId": "my-process"
  }
}
```

### 8. delete_bpmn_element
删除模型中的元素

**参数:**
- `modelId` (string): 模型 ID
- `elementId` (string): 要删除的元素 ID

**示例:**
```json
{
  "name": "delete_bpmn_element",
  "arguments": {
    "modelId": "my-process",
    "elementId": "task_1"
  }
}
```

### 9. create_complete_bpmn_process
创建完整的 BPMN 流程，包含开始事件、任务序列和结束事件，自动连线和布局

**参数:**
- `modelId` (string): 模型唯一标识符
- `processName` (string): 流程名称
- `tasks` (array): 任务列表，每个任务包含：
  - `name` (string): 任务名称
  - `type` (string, 可选): 任务类型，支持 `userTask`、`serviceTask`、`scriptTask`，默认为 `userTask`

**示例:**
```json
{
  "name": "create_complete_bpmn_process",
  "arguments": {
    "modelId": "order-approval-process",
    "processName": "订单审批流程",
    "tasks": [
      {
        "name": "提交订单",
        "type": "userTask"
      },
      {
        "name": "验证订单信息",
        "type": "serviceTask"
      },
      {
        "name": "经理审批",
        "type": "userTask"
      },
      {
        "name": "生成发票",
        "type": "scriptTask"
      }
    ]
  }
}
```

**功能特性:**
- 自动创建开始事件和结束事件
- 按顺序连接所有任务
- 智能布局，支持水平排列和自动换行
- 自动生成连线路径点
- 内置模型验证
- 返回完整的 BPMN XML 内容

## 架构设计

### 核心组件

1. **BpmnMcpServer**: MCP 服务器主类，处理协议通信
2. **BpmnModelService**: BPMN 模型管理服务
3. **BpmnModelBuilder**: BPMN 模型构建器
4. **LayoutManager**: 自动布局管理器
5. **ParameterValidator**: 参数验证器
6. **BpmnToolHandler**: BPMN 工具处理器
7. **ToolsListHandler**: 工具列表处理器

### 异常处理

项目定义了完整的异常体系：

- `BpmnMcpException`: 基础异常类
- `ValidationException`: 参数验证异常
- `ModelNotFoundException`: 模型未找到异常
- `ElementNotFoundException`: 元素未找到异常
- `ModelValidationException`: 模型验证异常
- `FileOperationException`: 文件操作异常

### 日志配置

使用 Logback 进行日志管理，支持：
- 控制台输出（避免与 MCP 协议冲突）
- 文件输出（支持滚动）
- 异步日志记录
- 分级日志控制

## 开发指南

### 添加新的 BPMN 元素类型

1. 在 `BpmnModelBuilder` 中添加创建方法
2. 在 `ParameterValidator` 中更新验证规则
3. 在 `LayoutManager` 中添加布局支持
4. 更新相关测试用例

### 添加新的 MCP 工具

1. 在 `BpmnToolHandler` 中添加处理逻辑
2. 在 `ToolsListHandler` 中添加工具定义
3. 添加参数验证
4. 编写单元测试

## 测试

项目包含完整的单元测试：

- `BpmnModelServiceTest`: 模型服务测试
- `BpmnToolHandlerTest`: 工具处理器测试
- `ParameterValidatorTest`: 参数验证测试

运行测试：
```bash
mvn test
```

## 许可证

本项目采用 MIT 许可证。详见 LICENSE 文件。

## 贡献

欢迎提交 Issue 和 Pull Request 来改进项目。

## 联系方式

如有问题或建议，请通过 GitHub Issues 联系我们。