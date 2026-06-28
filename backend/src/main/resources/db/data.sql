-- =============================================================================
-- 默认提示词种子数据
-- -----------------------------------------------------------------------------
-- 将 backend/src/main/resources/analyze_prompt.md 与 module_doc_prompt.md 的
-- 内容维护到 ci_prompt 表，作为「模块提取」与「文档生成」两类提示词的默认模板。
--
-- 设计要点：
--   1. 使用 PostgreSQL 的命名 dollar-quoting（$tag$ ... $tag$）包裹 Markdown 正文，
--      避免转义单引号 / 双引号 / 反引号等特殊字符。两段正文 tag 不同以避免嵌套歧义。
--   2. 幂等性通过「INSERT ... SELECT ... WHERE NOT EXISTS」实现：
--      已存在同 (prompt_type, name, version) 的记录时跳过；启动执行多次也不会重复插入。
--   3. 同一 prompt_type 下只允许一条 is_default=1（schema 中已建部分唯一索引），
--      故先 UPDATE 将同类型其他默认记录降为非默认，再插入新默认。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1) 模块提取提示词（MODULARIZE）— 来源：analyze_prompt.md
-- -----------------------------------------------------------------------------
UPDATE ci_prompt
   SET is_default = 0
 WHERE prompt_type = 'MODULARIZE'
   AND is_default  = 1
   AND (name <> '默认模块提取提示词' OR version <> 1);

INSERT INTO ci_prompt (name, content, version, status, is_default, prompt_type, created_at, updated_at)
SELECT '默认模块提取提示词',
       $analyze_prompt_md$
# Java 代码分析提示词（增量输出模式）

## 角色

你是一位资深 Java 架构师，擅长从代码中识别业务领域并抽象出模块层级。

## 任务

针对下方提供的 Java 源码，**只输出相对已有 `module_hierarchy.json` 的增量模块信息**。
程序会按 `id` 自动合并；你不需要、也不应该输出已存在的节点。

---

## 输入说明

- `{java_code}` —— 待分析的 Java 源码（单文件或一组类，通常是入口 Controller/Service/Scheduler/Consumer 等）
- `{business_knowledge.md}` —— 已确认入库的业务知识库 Markdown 摘要，**仅用于命名参考**
- `{module_hierarchy.json}` —— 当前任务的已有模块层级，**JSON 字符串**，结构见下文

`module_hierarchy.json` 的当前结构（可能为空对象）：

```json
{
  "modules": [
    {
      "id": "m0B1A",
      "module_name": "存量扫描",
      "keywords": ["配置", "查询"],
      "sub_modules": [
        {
          "id": "s2Xy9",
          "sub_module_name": "存量查询",
          "keywords": ["查询"],
          "functions": [
            {
              "id": "f3AbC",
              "function_name": "功能A",
              "class_paths": ["com.example.Controller"]
            }
          ]
        }
      ]
    }
  ]
}
```

> 字段映射（与代码 DTO 一致）：`module_name` ← `ModuleDto.moduleName`、`sub_module_name` ← `SubModuleDto.subModuleName`、`function_name` ← `FunctionDto.functionName`。

---

## 输出格式

**必须输出纯 JSON**，包裹在 ` ```json ... ``` ` 代码块中，**不要任何解释文字**。

如果本次没有新增模块（例如代码全部归属于已有节点），输出：

```json
{ "modules": [] }
```

### 增量结构模板

```json
{
  "modules": [
    {
      "id": "k7LpQ",
      "module_name": "新模块名",
      "keywords": ["关键词1", "关键词2"],
      "sub_modules": [
        {
          "id": "t5MnB",
          "sub_module_name": "子模块名",
          "keywords": ["关键词"],
          "functions": [
            {
              "id": "p3QwR",
              "function_name": "功能名"
            }
          ]
        }
      ]
    }
  ]
}
```

### 字段硬约束

| 字段 | 类型 | 约束 |
| --- | --- | --- |
| `modules[].id` | string | 5 位 Base62，`m` 前缀，仅新增模块时生成 |
| `modules[].module_name` | string | 业务领域/场景名，禁止具体功能点 |
| `modules[].keywords` | string[] | 3–5 个，**只含名词**，偏向业务/框架 |
| `modules[].sub_modules` | object[] | 子模块列表；新增时整段输出（ID 复用见后） |
| `sub_modules[].id` | string | 5 位 Base62，`s` 前缀 |
| `sub_modules[].sub_module_name` | string | 具体业务功能名，可使用动词 |
| `sub_modules[].keywords` | string[] | 3–5 个，允许动词/形容词 |
| `sub_modules[].functions` | object[] | 功能列表 |
| `functions[].id` | string | 5 位 Base62，`f` 前缀 |
| `functions[].function_name` | string | 业务功能名（动词短语） |
| `functions[].class_paths` | **禁止输出** | 由程序在解析后自动注入 |

> **功能节点不再包含 `keywords` 字段**：`function_name` 本身就是关键词，重复会污染检索。

---

## 命名原则（核心约束）

### 1. 模块名 = 业务领域/场景，不是功能点

向上抽象，找「最大公约数」。多个具体功能共享同一业务领域时，领域名才是模块名。

| ❌ 太具体（功能点） | ✅ 正确（业务领域/场景） |
| --- | --- |
| 房产授权 | 房管局业务 |
| 房产备案 | 房管局业务 |
| 征信查询 | 人行征信 |
| 征信上报 | 人行征信 |
| 定时跑批任务 | 资产包管理 |
| 消息消费 | 报文数据 |
| 异步服务 | 行为数据处理 |
| 消息推送服务 | 源头治理 |

### 2. 子模块名 = 具体业务功能

子模块允许使用业务功能名，如「重庆房管局」「白名单管理」「公积金贷后」。

### 3. 命名禁忌清单（模块层级）

- 具体功能点（房产授权、名单查询、白名单维护）
- 系统名/包名（PH-CRS 系统、core 包）
- 技术化通用术语（数据服务、基础服务、核心接口、平台能力）
- 技术词汇（定时跑批、MQ 消费、异步服务、接口服务、调度任务）

### 4. 关键词要「少而准」，且只用名词

- 数量：模块 3–5 个；子模块 3–5 个
- 模块关键词：只使用**名词**，去掉动词/形容词；以业务领域/系统框架为主
- 子模块关键词：允许动词/形容词，可以更具体
- 优先选「大词」便于后续模块合并（合并时按共同名词判定）

| ❌ 关键词过细 | ✅ 关键词（业务/框架） |
| --- | --- |
| 配置管理、查询、导出、导入 | 配置 |
| 用户权限、角色管理、菜单管理 | 权限 |
| 贷款审批、贷款申请、贷款展期 | 贷款 |
| 公积金查询、征信查询、人行查询 | 征信 |

### 5. 功能名 = 业务动作

形如「白名单查询」「公积金上报」「存量扫描执行」这类「业务实体 + 动作」短语。
**不要**直接复用类名或方法名（如 `getUserInfo`、`UserController`）。

---

## 匹配与复用流程（按顺序执行）

### 第 0 步：业务知识库优先

如果 `business_knowledge.md` 中包含业务领域描述，**优先用其中的命名**（模块名、子模块名、功能名）。
匹配命中后，跳到「ID 复用」节检查是否要复用已有节点。

### 第 1 步：在已有 `module_hierarchy.json` 中精确匹配

按以下顺序尝试命中已有节点：

1. **类路径命中**：`functions[].class_paths` 中已有此入口类的全限定名 → 直接复用所属功能/子模块/模块，不输出
2. **关键词命中**：当前代码业务关键词与模块/子模块 `keywords` 高度重合（≥70%）
3. **名称命中**：模块名/子模块名语义相同或包含共同前缀

任一命中即视为「属于已有节点」，**不输出**该节点。

### 第 2 步：智能提取（精确匹配失败时）

按以下线索提取业务领域：

1. **类名业务含义**：先看入口类名，向上抽象到业务领域
   - `StockScanController` → 「存量扫描」
   - `WhiteListController` → 「白名单管理」
   - 多个 Controller 共用同一业务时，提取公共领域作为模块名

2. **技术类识别**：包装类要追溯业务本质
   - `*Scheduler / *Job / *Task` → 看调度内容，提取业务领域（如「资产包管理」）
   - `*Consumer / *Listener / *Handler` → 看消息内容，提取业务数据分类（如「报文数据」）
   - `*MQ / *Message / *Topic` → 提取消息处理的业务领域

3. **公共部分抽象（最大公约数）**：把共同业务概念提升为模块名
   - 「房管局业务」（包含授权、备案、查询等多个功能）
   - 「人行征信」（包含查询、上报、解析等多个功能）

4. **功能名语义化**：从类名/方法名提取业务动词短语，不要直译代码标识符

### 第 3 步：业务相关性校验（关键！）

**关键词命中 ≠ 业务相关**。必须再用类路径中的包名做最终确认：

- 包名/类名中的业务实体（如 `houseFund`、`crs`、`pboc`）优先级 > 关键词表面匹配
- 示例（公共模块「人行征信」）：
  - 功能 A「公积金贷后核心处理」—— 关键词「贷后」命中，但包名含 `houseFund` → **业务相关 ✓**
  - 功能 B「统一贷后 job 名单查询」—— 关键词「贷后」命中，但包名是通用「统一贷后」→ **业务不相关 ✗**，应归到「贷后管理」或「名单管理」
  - 功能 C「CRS 批次号详情列表获取」—— 关键词「批次号」不命中，但 CRS 是征信上报 → **业务相关 ✓**

**判定优先级：类路径的业务包名 > 表面关键词匹配。**

---

## ID 生成规则

### 字符集与格式

- 字符集：`0-9 a-z A-Z`（共 62 个字符）
- 长度：**固定 5 位**
- 前缀（区分层级）：
  - 模块：`m` 开头，如 `m0B1A`
  - 子模块：`s` 开头，如 `s2Xy9`
  - 功能：`f` 开头，如 `f3AbC`

### 复用优先于新建（重要！）

1. **先判断是否已存在相同业务主题**：在 `module_hierarchy.json` 中搜索
   - 模块名有共同前缀（如「房管局业务-重庆」与「房管局业务-佛山」合并为「房管局业务」）
   - 关键词相似度 ≥ 70%
   - 业务主题一致
2. **同主题必须复用原 ID**：包括模块、子模块、功能节点的 ID 都不能新建
3. **仅对真正全新的业务主题生成新 ID**
4. **新增节点只输出差异部分**：合并到已有节点时，只输出新增的子模块/功能，附带其完整 ID

---

## 边界场景处理

| 场景 | 处理方式 |
| --- | --- |
| 代码无法识别（语法错乱、缺关键信息） | 输出 `{ "modules": [] }`，不强行编造 |
| 通用工具类（Util、Constants、Exception） | **不**纳入业务模块；输出空 |
| 跨多个业务领域的类（少见） | 按主业务归到一个模块，其他业务在 `function_name` 中说明 |
| 类路径已在某个功能的 `class_paths` 中 | 视为已有节点，不输出 |
| 与已有节点业务相关但不在任何 `class_paths` 中 | 仍可归属到已有功能/子模块（命中后不输出该节点） |

---

## 端到端示例

### 输入

`module_hierarchy.json`：

```json
{
  "modules": [
    {
      "id": "m0B1A",
      "module_name": "存量扫描",
      "keywords": ["配置", "查询"],
      "sub_modules": [
        {
          "id": "s2Xy9",
          "sub_module_name": "存量查询",
          "keywords": ["查询"],
          "functions": [
            { "id": "f3AbC", "function_name": "存量查询执行", "class_paths": ["com.example.scan.ScanController"] }
          ]
        }
      ]
    }
  ]
}
```

待分析代码：`com.example.fang.gov.ChongqingAuthController`（重庆房管局授权接口，与「房管局业务」主题一致，但当前任务此前未出现）。

### 期望输出

```json
{
  "modules": [
    {
      "id": "mK7pQ",
      "module_name": "房管局业务",
      "keywords": ["房产", "授权", "备案"],
      "sub_modules": [
        {
          "id": "sP3wR",
          "sub_module_name": "重庆房管局",
          "keywords": ["重庆", "授权", "房管局"],
          "functions": [
            { "id": "fL9xN", "function_name": "重庆房管局授权" }
          ]
        }
      ]
    }
  ]
}
```

> 说明：模块 ID、子模块 ID、功能 ID 都是新生成的（因为 `module_hierarchy.json` 中此前没有房管局业务相关内容）；`class_paths` 不输出，由程序后续注入。

---

## 自检清单（提交前必过）

- [ ] 输出是**纯 JSON**，无解释文字、无 Markdown 包装（除非用 ` ```json ` 包裹）
- [ ] **只输出增量**：已存在节点一律不复述
- [ ] 模块名是**业务领域**，不在禁忌清单内
- [ ] 模块关键词 3–5 个，**只含名词**
- [ ] ID 5 位 Base62，前缀对应层级（`m` / `s` / `f`）
- [ ] **未输出** `class_paths` 字段
- [ ] 边界场景已正确处理（无法识别/通用工具类 → 空输出）
$analyze_prompt_md$,
       1,
       1,
       1,
       'MODULARIZE',
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
 WHERE NOT EXISTS (
    SELECT 1 FROM ci_prompt
     WHERE prompt_type = 'MODULARIZE'
       AND name        = '默认模块提取提示词'
       AND version     = 1
);

-- -----------------------------------------------------------------------------
-- 2) 文档生成提示词（DOCUMENT_GENERATION）— 来源：module_doc_prompt.md
-- -----------------------------------------------------------------------------
UPDATE ci_prompt
   SET is_default = 0
 WHERE prompt_type      = 'DOCUMENT_GENERATION'
   AND is_default       = 1
   AND (name <> '默认文档生成提示词' OR version <> 1);

INSERT INTO ci_prompt (name, content, version, status, is_default, prompt_type, created_at, updated_at)
SELECT '默认文档生成提示词',
       $module_doc_prompt_md$
# 模块说明文档生成提示词

### 一、文档定位
- **目标读者**：产品经理、开发工程师、集成测试工程师
- **内容侧重**：业务逻辑描述、边界情况和异常处理

### 二、信息
- **业务名称**：{公共模块名称}
- **module_hierarchy.json 内容**:
{module_hierarchy.json}
- **java 内容**:
{java.code}

### 三、文档结构与内容规范
根据读取到的所有代码文件，提炼业务逻辑，转换为业务语言描述。

1. **概述**
   - 公共模块名称：【从配置中读取的模块名】
   - 包含子模块：【列出所有子模块名称】
   - 业务背景：简述业务问题
   - 业务目标：期望效果
   - 功能描述：核心能力（2-3 句话）

2. **涉及类清单**
   | 序号 | 子模块 | 类路径 | 功能说明 |
   | --- | --- | --- | --- |
   | 1 | 因子验证 | com.peig.prep.xxx.VerifyController | xxx |
   | 2 | 因子配置 | com.peig.prep.xxx.ConfigController | xxx |

3. **输入输出**
   - 输入：列出所有输入数据源，说明关键字段含义及业务含义
   - 输出接口 URL：列出所有 HTTP 接口的 URL 地址，格式如 `http://host:port/api/...`
   - 输出：列出所有输出数据，说明字段维度

4. **核心业务流程图**
   - 使用 Mermaid 语法绘制流程图
   - 按业务阶段划分，突出业务动作和判断逻辑
   - 清晰展示条件分支和循环逻辑
   - 展示各子模块之间的协作关系

5. **核心业务逻辑**
   使用中文描述，满足以下要求：
   - 字段中文命名；首次出现时标注字段含义
   - 禁止出现代码片段、类名、方法名（可用"处理模块"等抽象描述替代）

6. **调用链路说明**
   | 调用类型 | 目标服务/系统 | 调用地址/接口 | 说明 |
   | --- | --- | --- | --- |
   | HTTP | xxx-service | /api/xxx | 获取 xxx 数据 |
   | Feign | xxx-service | XxxApi | 调用 xxx 接口 |
   | RocketMQ | - | topic: xxx | 发送 xxx 消息 |
   | Redis | - | key: xxx:* | 缓存 xxx 数据 |
   | MySQL | - | table: xxx | 查询 xxx 数据 |

### 四、格式要求
- 使用 Markdown 格式编写
- 标题层级清晰（H1-H3）
- 表格用于结构化展示
- 流程图使用 Mermaid 语法

### 五、示例展示

#### 正确示例

```markdown
## 订单处理模块
### 业务背景
随着用户量增长，原有订单处理机制已无法满足高并发场景需求，需要引入异步处理。

### 核心业务逻辑
1. 系统首先接收订单请求，解析订单信息
2. 校验订单状态，确保订单处于待处理状态
3. 将订单信息写入消息队列，由消费模块异步处理
```

#### 错误示例

```markdown
## 订单处理模块
### 业务背景
...（业务问题描述）
### 核心业务逻辑
...（错误：出现了代码注释）
orderService.process(order);
...（错误：直接使用类名）
SceneMonitorServiceImpl.doSomething();
```

### 六、输出要求
- 仅输出 Markdown 正文，不要输出任何解释性文字
- 不输出代码块（除非 Mermaid 流程图）
- 不要臆造未在源码中出现的数据表名、接口路径、配置项
- 章节标题严格使用中文数字（一、二、三、...）
- 如果某章节没有相关信息，输出"暂无相关信息"占位，不要省略章节
$module_doc_prompt_md$,
       1,
       1,
       1,
       'DOCUMENT_GENERATION',
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
 WHERE NOT EXISTS (
    SELECT 1 FROM ci_prompt
     WHERE prompt_type = 'DOCUMENT_GENERATION'
       AND name        = '默认文档生成提示词'
       AND version     = 1
);