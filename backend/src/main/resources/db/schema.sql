-- 数据库初始化脚本 (PostgreSQL 兼容版)

-- 1. 系统管理表
CREATE TABLE IF NOT EXISTS ci_system (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    owner VARCHAR(50) NOT NULL,
    status SMALLINT DEFAULT 1 NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);
COMMENT ON TABLE ci_system IS '系统管理表';
COMMENT ON COLUMN ci_system.name IS '系统名称';
COMMENT ON COLUMN ci_system.description IS '系统描述';
COMMENT ON COLUMN ci_system.owner IS '系统负责人';
COMMENT ON COLUMN ci_system.status IS '启用状态：0-停用，1-启用';

-- 2. 代码库配置表
CREATE TABLE IF NOT EXISTS ci_repository (
    id BIGSERIAL PRIMARY KEY,
    system_id BIGINT NOT NULL,
    git_url VARCHAR(255) NOT NULL,
    branch VARCHAR(100) DEFAULT 'master' NOT NULL,
    username VARCHAR(100),
    password VARCHAR(255),
    scan_root VARCHAR(255) DEFAULT '/' NOT NULL,
    exclude_dirs VARCHAR(500),
    exclude_file_types VARCHAR(200),
    last_commit_id VARCHAR(100),
    last_decompile_at TIMESTAMP,
    entry_scan_config TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_repo_system_id ON ci_repository (system_id);
COMMENT ON TABLE ci_repository IS '代码库配置表';
COMMENT ON COLUMN ci_repository.system_id IS '关联系统ID';
COMMENT ON COLUMN ci_repository.git_url IS 'Git仓库地址';
COMMENT ON COLUMN ci_repository.branch IS '默认分支';
COMMENT ON COLUMN ci_repository.username IS '凭证用户名';
COMMENT ON COLUMN ci_repository.password IS '凭证密码/Token';
COMMENT ON COLUMN ci_repository.scan_root IS '扫描根目录';
COMMENT ON COLUMN ci_repository.exclude_dirs IS '排除目录，逗号分隔';
COMMENT ON COLUMN ci_repository.exclude_file_types IS '排除文件类型，逗号分隔';
COMMENT ON COLUMN ci_repository.last_commit_id IS '最后确认 Commit ID';
COMMENT ON COLUMN ci_repository.last_decompile_at IS '最后反编译时间';
ALTER TABLE ci_repository ADD COLUMN IF NOT EXISTS entry_scan_config TEXT;
COMMENT ON COLUMN ci_repository.entry_scan_config IS '仓库级入口扫描配置 JSON：含 includeAnnotations/includeClasspaths/includeExtends 与 excludeClasspaths/excludePackages/excludeAnnotations；新建任务时默认带出，任务可覆盖';

-- 3. 提示词模板表
CREATE TABLE IF NOT EXISTS ci_prompt (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    content TEXT NOT NULL,
    version INT DEFAULT 1 NOT NULL,
    status SMALLINT DEFAULT 1 NOT NULL,
    is_default SMALLINT DEFAULT 0 NOT NULL,
    prompt_type VARCHAR(32) DEFAULT 'MODULARIZE' NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);
COMMENT ON TABLE ci_prompt IS '提示词模板表';
COMMENT ON COLUMN ci_prompt.name IS '提示词名称';
COMMENT ON COLUMN ci_prompt.content IS '提示词内容';
COMMENT ON COLUMN ci_prompt.version IS '版本号';
COMMENT ON COLUMN ci_prompt.status IS '状态：0-禁用，1-启用';
COMMENT ON COLUMN ci_prompt.is_default IS '是否默认：0-否，1-是';
ALTER TABLE ci_prompt ADD COLUMN IF NOT EXISTS prompt_type VARCHAR(32) DEFAULT 'MODULARIZE' NOT NULL;
COMMENT ON COLUMN ci_prompt.prompt_type IS '提示词用途：MODULARIZE-模块提取（用于 AI_ANALYZING / MODULE_HIERARCHY 阶段），DOCUMENT_GENERATION-文档生成（用于 GENERATING_DOC 阶段）';

-- 3.1 提示词类型迁移：兼容历史 schema（无 prompt_type 列时补齐）

-- 3.2 同一 prompt_type 下只允许一条 is_default=1 的记录（约束已默认提示词唯一）
CREATE UNIQUE INDEX IF NOT EXISTS uk_ci_prompt_type_default
    ON ci_prompt (prompt_type) WHERE is_default = 1;

-- 4. 反编译任务表
CREATE TABLE IF NOT EXISTS ci_task (
    id BIGSERIAL PRIMARY KEY,
    system_id BIGINT NOT NULL,
    repository_id BIGINT NOT NULL,
    prompt_version INT,
    modularize_prompt_version INT,
    document_prompt_version INT,
    model_name VARCHAR(100),
    status VARCHAR(50) NOT NULL,
    type VARCHAR(50) DEFAULT 'INITIAL' NOT NULL,
    progress INT DEFAULT 0 NOT NULL,
    log_uri VARCHAR(255),
    error_reason TEXT,
    duration_ms BIGINT DEFAULT 0 NOT NULL,
    started_at TIMESTAMP,
    ended_at TIMESTAMP,
    entry_scan_config TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_task_system_id ON ci_task (system_id);
CREATE INDEX IF NOT EXISTS idx_task_status ON ci_task (status);
ALTER TABLE ci_task ADD COLUMN IF NOT EXISTS modularize_prompt_version INT;
ALTER TABLE ci_task ADD COLUMN IF NOT EXISTS document_prompt_version INT;
ALTER TABLE ci_task ADD COLUMN IF NOT EXISTS modularize_prompt_id BIGINT;
ALTER TABLE ci_task ADD COLUMN IF NOT EXISTS document_prompt_id BIGINT;
COMMENT ON COLUMN ci_task.modularize_prompt_id IS '模块提取提示词 ID（按主键查 ci_prompt，替代已废弃的 modularize_prompt_version）';
COMMENT ON COLUMN ci_task.document_prompt_id IS '文档生成提示词 ID（按主键查 ci_prompt，替代已废弃的 document_prompt_version）';
ALTER TABLE ci_task ADD COLUMN IF NOT EXISTS model_name VARCHAR(100);
ALTER TABLE ci_task ADD COLUMN IF NOT EXISTS entry_scan_config TEXT;
ALTER TABLE ci_task ADD COLUMN IF NOT EXISTS require_hierarchy_review BOOLEAN DEFAULT TRUE NOT NULL;
COMMENT ON TABLE ci_task IS '反编译任务表';
COMMENT ON COLUMN ci_task.system_id IS '关联系统ID';
COMMENT ON COLUMN ci_task.repository_id IS '关联仓库ID';
COMMENT ON COLUMN ci_task.prompt_version IS '使用的提示词版本（已废弃，请使用 modularize_prompt_version / document_prompt_version）';
COMMENT ON COLUMN ci_task.modularize_prompt_version IS '模块提取提示词版本（AI_ANALYZING / MODULE_HIERARCHY 阶段使用，对应 ci_prompt.prompt_type=MODULARIZE）';
COMMENT ON COLUMN ci_task.document_prompt_version IS '文档生成提示词版本（GENERATING_DOC 阶段使用，对应 ci_prompt.prompt_type=DOCUMENT_GENERATION）';
COMMENT ON COLUMN ci_task.status IS '任务状态';
COMMENT ON COLUMN ci_task.type IS '任务类型：INITIAL-全量/初始化，INCREMENTAL-增量';
COMMENT ON COLUMN ci_task.progress IS '进度百分比：0-100';
COMMENT ON COLUMN ci_task.log_uri IS '任务日志在对象存储中的地址';
COMMENT ON COLUMN ci_task.error_reason IS '失败原因';
COMMENT ON COLUMN ci_task.duration_ms IS '耗时（毫秒）';
COMMENT ON COLUMN ci_task.started_at IS '启动时间';
COMMENT ON COLUMN ci_task.ended_at IS '结束时间';

-- 5. 代码文件快照表
CREATE TABLE IF NOT EXISTS ci_file_snapshot (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL,
    file_path VARCHAR(255) NOT NULL,
    file_type VARCHAR(50),
    line_count INT DEFAULT 0 NOT NULL,
    file_hash VARCHAR(100) NOT NULL,
    content_uri VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_snapshot_task_id ON ci_file_snapshot (task_id);
COMMENT ON TABLE ci_file_snapshot IS '代码文件快照表';
COMMENT ON COLUMN ci_file_snapshot.task_id IS '任务ID';
COMMENT ON COLUMN ci_file_snapshot.file_path IS '相对路径';
COMMENT ON COLUMN ci_file_snapshot.file_type IS '文件类型';
COMMENT ON COLUMN ci_file_snapshot.line_count IS '行数';
COMMENT ON COLUMN ci_file_snapshot.file_hash IS '文件MD5哈希值';
COMMENT ON COLUMN ci_file_snapshot.content_uri IS '代码快照在存储中的地址';

-- 6. 代码切片表
CREATE TABLE IF NOT EXISTS ci_chunk (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL,
    file_path VARCHAR(255) NOT NULL,
    class_name VARCHAR(255),
    method_name VARCHAR(100),
    chunk_type VARCHAR(50) NOT NULL,
    content_hash VARCHAR(100) NOT NULL,
    start_line INT NOT NULL,
    end_line INT NOT NULL,
    token_estimate INT DEFAULT 0 NOT NULL,
    status VARCHAR(50) DEFAULT 'PENDING' NOT NULL,
    error_reason TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_chunk_task_id ON ci_chunk (task_id);
CREATE INDEX IF NOT EXISTS idx_chunk_file_path ON ci_chunk (file_path);
COMMENT ON TABLE ci_chunk IS '代码切片表';
COMMENT ON COLUMN ci_chunk.task_id IS '任务ID';
COMMENT ON COLUMN ci_chunk.file_path IS '相对路径';
COMMENT ON COLUMN ci_chunk.class_name IS '类名';
COMMENT ON COLUMN ci_chunk.method_name IS '方法名';
COMMENT ON COLUMN ci_chunk.chunk_type IS '切片类型：FILE, CLASS, METHOD, DIFF';
COMMENT ON COLUMN ci_chunk.content_hash IS '切片内容哈希';
COMMENT ON COLUMN ci_chunk.start_line IS '起始行';
COMMENT ON COLUMN ci_chunk.end_line IS '结束行';
COMMENT ON COLUMN ci_chunk.token_estimate IS '预估 Token 数';
COMMENT ON COLUMN ci_chunk.status IS '切片分析状态：PENDING, ANALYZED, FAILED';
COMMENT ON COLUMN ci_chunk.error_reason IS '切片分析错误原因';

-- 7. AI模型调用记录表
CREATE TABLE IF NOT EXISTS ci_ai_call_record (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL,
    chunk_id BIGINT,
    prompt_id BIGINT,
    prompt_version INT,
    model_name VARCHAR(100) NOT NULL,
    input_token INT DEFAULT 0 NOT NULL,
    output_token INT DEFAULT 0 NOT NULL,
    request_uri VARCHAR(255),
    response_uri VARCHAR(255),
    is_success SMALLINT DEFAULT 1 NOT NULL,
    error_reason TEXT,
    duration_ms BIGINT DEFAULT 0 NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_ai_task_id ON ci_ai_call_record (task_id);
CREATE INDEX IF NOT EXISTS idx_ai_chunk_id ON ci_ai_call_record (chunk_id);
COMMENT ON TABLE ci_ai_call_record IS 'AI模型调用记录表';
COMMENT ON COLUMN ci_ai_call_record.task_id IS '任务ID';
COMMENT ON COLUMN ci_ai_call_record.chunk_id IS '关联切片ID';
COMMENT ON COLUMN ci_ai_call_record.prompt_id IS '使用的提示词ID';
COMMENT ON COLUMN ci_ai_call_record.prompt_version IS '使用的提示词版本';
COMMENT ON COLUMN ci_ai_call_record.model_name IS '模型名称';
COMMENT ON COLUMN ci_ai_call_record.input_token IS '输入Token数';
COMMENT ON COLUMN ci_ai_call_record.output_token IS '输出Token数';
COMMENT ON COLUMN ci_ai_call_record.request_uri IS '请求正文在存储中的地址';
COMMENT ON COLUMN ci_ai_call_record.response_uri IS '响应正文在存储中的地址';
COMMENT ON COLUMN ci_ai_call_record.is_success IS '是否成功：0-失败，1-成功';
COMMENT ON COLUMN ci_ai_call_record.error_reason IS '失败原因';
COMMENT ON COLUMN ci_ai_call_record.duration_ms IS '耗时（毫秒）';

-- 8. 草稿工作区表
CREATE TABLE IF NOT EXISTS ci_draft_workspace (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    repository_id BIGINT NOT NULL,
    status VARCHAR(50) DEFAULT 'ACTIVE' NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT uk_task_id UNIQUE (task_id)
);
COMMENT ON TABLE ci_draft_workspace IS '草稿工作区表';
COMMENT ON COLUMN ci_draft_workspace.task_id IS '任务ID';
COMMENT ON COLUMN ci_draft_workspace.system_id IS '系统ID';
COMMENT ON COLUMN ci_draft_workspace.repository_id IS '仓库ID';
COMMENT ON COLUMN ci_draft_workspace.status IS '状态：ACTIVE, COMPLETED, ARCHIVED';

-- 9. Markdown知识草稿表
CREATE TABLE IF NOT EXISTS ci_knowledge_draft (
    id BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    file_path VARCHAR(255) NOT NULL,
    module_name VARCHAR(100) NOT NULL,
    content_uri VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    hash VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_draft_workspace_id ON ci_knowledge_draft (workspace_id);
CREATE INDEX IF NOT EXISTS idx_draft_status ON ci_knowledge_draft (status);
COMMENT ON TABLE ci_knowledge_draft IS 'Markdown知识草稿表';
COMMENT ON COLUMN ci_knowledge_draft.workspace_id IS '关联草稿工作区ID';
COMMENT ON COLUMN ci_knowledge_draft.file_path IS '模块/文件 Markdown 路径';
COMMENT ON COLUMN ci_knowledge_draft.module_name IS '模块名称';
COMMENT ON COLUMN ci_knowledge_draft.content_uri IS '草稿内容在存储中的地址';
COMMENT ON COLUMN ci_knowledge_draft.status IS '草稿状态';
COMMENT ON COLUMN ci_knowledge_draft.hash IS '草稿内容的 MD5 Hash';

-- 10. 草稿修订历史表
CREATE TABLE IF NOT EXISTS ci_draft_revision (
    id BIGSERIAL PRIMARY KEY,
    draft_id BIGINT NOT NULL,
    content_uri VARCHAR(255) NOT NULL,
    author VARCHAR(50) NOT NULL,
    remark VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_revision_draft_id ON ci_draft_revision (draft_id);
COMMENT ON TABLE ci_draft_revision IS '草稿修订历史表';
COMMENT ON COLUMN ci_draft_revision.draft_id IS '关联草稿ID';
COMMENT ON COLUMN ci_draft_revision.content_uri IS '修改后正文在存储中的地址';
COMMENT ON COLUMN ci_draft_revision.author IS '修改者';
COMMENT ON COLUMN ci_draft_revision.remark IS '修改备注';

-- 11. 草稿评审意见表
CREATE TABLE IF NOT EXISTS ci_draft_review_comment (
    id BIGSERIAL PRIMARY KEY,
    draft_id BIGINT NOT NULL,
    author VARCHAR(50) NOT NULL,
    comment TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_comment_draft_id ON ci_draft_review_comment (draft_id);
COMMENT ON TABLE ci_draft_review_comment IS '草稿评审意见表';
COMMENT ON COLUMN ci_draft_review_comment.draft_id IS '关联草稿ID';
COMMENT ON COLUMN ci_draft_review_comment.author IS '评审人';
COMMENT ON COLUMN ci_draft_review_comment.comment IS '评审意见';

-- 12. 草稿代码来源引用表
CREATE TABLE IF NOT EXISTS ci_draft_source_reference (
    id BIGSERIAL PRIMARY KEY,
    draft_id BIGINT NOT NULL,
    file_path VARCHAR(255) NOT NULL,
    start_line INT NOT NULL,
    end_line INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_ref_draft_id ON ci_draft_source_reference (draft_id);
COMMENT ON TABLE ci_draft_source_reference IS '草稿代码来源引用表';
COMMENT ON COLUMN ci_draft_source_reference.draft_id IS '关联草稿ID';
COMMENT ON COLUMN ci_draft_source_reference.file_path IS '引用源文件路径';
COMMENT ON COLUMN ci_draft_source_reference.start_line IS '起始行号';
COMMENT ON COLUMN ci_draft_source_reference.end_line IS '结束行号';

-- 13. 知识版本表
CREATE TABLE IF NOT EXISTS ci_knowledge_version (
    id BIGSERIAL PRIMARY KEY,
    system_id BIGINT NOT NULL,
    repository_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    version_num VARCHAR(50) NOT NULL,
    source_branch VARCHAR(100) NOT NULL,
    source_commit VARCHAR(100) NOT NULL,
    target_branch VARCHAR(100) NOT NULL,
    target_commit VARCHAR(100),
    prompt_version INT,
    model_name VARCHAR(100),
    status VARCHAR(50) NOT NULL,
    confirmed_by VARCHAR(50) NOT NULL,
    confirmed_at TIMESTAMP NOT NULL,
    pushed_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_version_system_id ON ci_knowledge_version (system_id);
CREATE INDEX IF NOT EXISTS idx_version_number ON ci_knowledge_version (version_num);
COMMENT ON TABLE ci_knowledge_version IS '知识版本表';
COMMENT ON COLUMN ci_knowledge_version.system_id IS '关联系统ID';
COMMENT ON COLUMN ci_knowledge_version.repository_id IS '关联代码库ID';
COMMENT ON COLUMN ci_knowledge_version.task_id IS '关联任务ID';
COMMENT ON COLUMN ci_knowledge_version.version_num IS '知识版本号 (如 v1.0.0)';
COMMENT ON COLUMN ci_knowledge_version.source_branch IS '源分支';
COMMENT ON COLUMN ci_knowledge_version.source_commit IS '源提交 Commit ID';
COMMENT ON COLUMN ci_knowledge_version.target_branch IS '目标推送分支';
COMMENT ON COLUMN ci_knowledge_version.target_commit IS '推送后的提交 Commit ID';
COMMENT ON COLUMN ci_knowledge_version.prompt_version IS '所用提示词版本';
COMMENT ON COLUMN ci_knowledge_version.model_name IS '所用 AI 模型名称';
COMMENT ON COLUMN ci_knowledge_version.status IS '状态：DRAFT, PUSHING, PUSHED, FAILED';
COMMENT ON COLUMN ci_knowledge_version.confirmed_by IS '确认人';
COMMENT ON COLUMN ci_knowledge_version.confirmed_at IS '确认时间';
COMMENT ON COLUMN ci_knowledge_version.pushed_at IS '推送时间';

-- 14. Token使用审计表
CREATE TABLE IF NOT EXISTS ci_token_usage_audit (
    id BIGSERIAL PRIMARY KEY,
    system_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    user_id BIGINT,
    prompt_version INT,
    model_name VARCHAR(100) NOT NULL,
    input_tokens INT DEFAULT 0 NOT NULL,
    output_tokens INT DEFAULT 0 NOT NULL,
    total_tokens INT DEFAULT 0 NOT NULL,
    cost DECIMAL(10,4) DEFAULT 0.0000 NOT NULL,
    type VARCHAR(50) DEFAULT 'INITIAL' NOT NULL,
    status SMALLINT DEFAULT 1 NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_audit_system_id ON ci_token_usage_audit (system_id);
CREATE INDEX IF NOT EXISTS idx_audit_task_id ON ci_token_usage_audit (task_id);
CREATE INDEX IF NOT EXISTS idx_audit_created_at ON ci_token_usage_audit (created_at);
COMMENT ON TABLE ci_token_usage_audit IS 'Token使用审计表';
COMMENT ON COLUMN ci_token_usage_audit.system_id IS '关联系统ID';
COMMENT ON COLUMN ci_token_usage_audit.task_id IS '关联任务ID';
COMMENT ON COLUMN ci_token_usage_audit.user_id IS '用户ID';
COMMENT ON COLUMN ci_token_usage_audit.prompt_version IS '提示词版本';
COMMENT ON COLUMN ci_token_usage_audit.model_name IS '模型名称';
COMMENT ON COLUMN ci_token_usage_audit.input_tokens IS '输入Token数';
COMMENT ON COLUMN ci_token_usage_audit.output_tokens IS '输出Token数';
COMMENT ON COLUMN ci_token_usage_audit.total_tokens IS '总Token数';
COMMENT ON COLUMN ci_token_usage_audit.cost IS '预估消耗成本(美元)';
COMMENT ON COLUMN ci_token_usage_audit.type IS '调用类型：INITIAL, INCREMENTAL, TEST';
COMMENT ON COLUMN ci_token_usage_audit.status IS '调用结果：0-失败，1-成功';

-- 15. 操作日志审计表
CREATE TABLE IF NOT EXISTS ci_operation_log (
    id BIGSERIAL PRIMARY KEY,
    system_id BIGINT,
    task_id BIGINT,
    user_id BIGINT,
    username VARCHAR(50) NOT NULL,
    action_type VARCHAR(50) NOT NULL,
    detail VARCHAR(1000) NOT NULL,
    ip_address VARCHAR(50),
    exception_msg TEXT,
    is_success SMALLINT DEFAULT 1 NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_op_system_id ON ci_operation_log (system_id);
CREATE INDEX IF NOT EXISTS idx_op_task_id ON ci_operation_log (task_id);
CREATE INDEX IF NOT EXISTS idx_op_created_at ON ci_operation_log (created_at);
COMMENT ON TABLE ci_operation_log IS '操作日志审计表';
COMMENT ON COLUMN ci_operation_log.system_id IS '关联系统ID';
COMMENT ON COLUMN ci_operation_log.task_id IS '关联任务ID';
COMMENT ON COLUMN ci_operation_log.user_id IS '操作人ID';
COMMENT ON COLUMN ci_operation_log.username IS '操作人用户名';
COMMENT ON COLUMN ci_operation_log.action_type IS '操作类型(CREATE_SYSTEM, EDIT_REPO, RUN_TASK, SAVE_DRAFT, CONFIRM_KNOWLEDGE, PUSH_GIT, etc.)';
COMMENT ON COLUMN ci_operation_log.detail IS '操作详情描述';
COMMENT ON COLUMN ci_operation_log.ip_address IS 'IP地址';
COMMENT ON COLUMN ci_operation_log.exception_msg IS '异常日志信息';
COMMENT ON COLUMN ci_operation_log.is_success IS '操作是否成功：0-失败，1-成功';

-- 16. AI模型配置表
CREATE TABLE IF NOT EXISTS ci_model (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    identifier VARCHAR(100) NOT NULL,
    provider VARCHAR(100) NOT NULL,
    api_key VARCHAR(255),
    base_url VARCHAR(255),
    is_default VARCHAR(10) DEFAULT 'false' NOT NULL,
    capabilities VARCHAR(255),
    description VARCHAR(500),
    sort_order INT DEFAULT 0 NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);
COMMENT ON TABLE ci_model IS 'AI模型配置表';
COMMENT ON COLUMN ci_model.name IS '模型显示名称';
COMMENT ON COLUMN ci_model.identifier IS '模型调用ID';
COMMENT ON COLUMN ci_model.provider IS '技术供应商';
COMMENT ON COLUMN ci_model.api_key IS 'API Key (密钥)';
COMMENT ON COLUMN ci_model.base_url IS 'Endpoint URL (接口地址)';
COMMENT ON COLUMN ci_model.is_default IS '是否默认模型：true/false';
COMMENT ON COLUMN ci_model.capabilities IS '支持能力，逗号分隔 (text,image,video)';
COMMENT ON COLUMN ci_model.description IS '功能描述';
COMMENT ON COLUMN ci_model.sort_order IS '排序权重';

-- 17. 迁移或兼容性字段维护
ALTER TABLE ci_task ADD COLUMN IF NOT EXISTS model_name VARCHAR(100);

-- 18. 方法调用链路表（AST 静态分析结果）
CREATE TABLE IF NOT EXISTS ci_method_call (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    class_name VARCHAR(255),
    caller_method VARCHAR(255),
    dependency_name VARCHAR(255),
    target_method VARCHAR(255),
    expression VARCHAR(1000),
    line_number INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_method_call_task_id ON ci_method_call (task_id);
CREATE INDEX IF NOT EXISTS idx_method_call_class ON ci_method_call (task_id, class_name, caller_method);
COMMENT ON TABLE ci_method_call IS '方法调用链路表（AST 静态分析）';
COMMENT ON COLUMN ci_method_call.task_id IS '关联任务ID';
COMMENT ON COLUMN ci_method_call.file_path IS '源文件相对路径';
COMMENT ON COLUMN ci_method_call.class_name IS 'Java 类名';
COMMENT ON COLUMN ci_method_call.caller_method IS '调用方方法名';
COMMENT ON COLUMN ci_method_call.dependency_name IS '被调依赖的类型名';
COMMENT ON COLUMN ci_method_call.target_method IS '被调用的目标方法名';
COMMENT ON COLUMN ci_method_call.expression IS '调用表达式原始文本';
COMMENT ON COLUMN ci_method_call.line_number IS '源文件行号';

-- 18.1 ci_method_call 增加方法完整签名列（按方法粒度反查调用链用）
ALTER TABLE ci_method_call ADD COLUMN IF NOT EXISTS caller_signature VARCHAR(500);
ALTER TABLE ci_method_call ADD COLUMN IF NOT EXISTS target_signature VARCHAR(500);
CREATE INDEX IF NOT EXISTS idx_method_call_caller_sig ON ci_method_call (task_id, caller_signature);
CREATE INDEX IF NOT EXISTS idx_method_call_target_sig ON ci_method_call (task_id, target_signature);
COMMENT ON COLUMN ci_method_call.caller_signature IS '调用方方法完整签名：className#methodName(ParamType1,ParamType2)';
COMMENT ON COLUMN ci_method_call.target_signature IS '被调方方法完整签名：className#methodName(ParamType1,ParamType2)';

-- 19. 模块层级表（AI 提炼入口的业务归属，DTO 持久化）
CREATE TABLE IF NOT EXISTS ci_module_hierarchy (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    level VARCHAR(20) NOT NULL,
    parent_id BIGINT,
    node_id VARCHAR(10),
    name VARCHAR(255) NOT NULL,
    keywords TEXT,
    class_paths TEXT,
    method_signatures TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_module_hierarchy_task ON ci_module_hierarchy (task_id);
CREATE INDEX IF NOT EXISTS idx_module_hierarchy_parent ON ci_module_hierarchy (parent_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_module_hierarchy_task_node ON ci_module_hierarchy (task_id, node_id);
COMMENT ON TABLE ci_module_hierarchy IS '模块层级表（AI 提炼的业务归属 DTO 落表）';
COMMENT ON COLUMN ci_module_hierarchy.level IS '层级：MODULE / SUB_MODULE / FUNCTION';
COMMENT ON COLUMN ci_module_hierarchy.parent_id IS '上级节点 ID（module.parent_id = NULL）';
COMMENT ON COLUMN ci_module_hierarchy.node_id IS '5位 Base62 ID（m/s/f 前缀），同任务内唯一';
COMMENT ON COLUMN ci_module_hierarchy.name IS '模块/子模块/功能名称';
COMMENT ON COLUMN ci_module_hierarchy.keywords IS '关键词 JSON 数组字符串';
COMMENT ON COLUMN ci_module_hierarchy.class_paths IS '入口类全限定名集合（仅 FUNCTION 级）JSON 数组';
ALTER TABLE ci_module_hierarchy ADD COLUMN IF NOT EXISTS method_signatures TEXT;
COMMENT ON COLUMN ci_module_hierarchy.method_signatures IS '该功能涉及的方法签名 JSON 数组（仅 FUNCTION 级），如 ["listUsers(Integer,Integer)","createUser(UserDTO)"]；用于阶段 2 按方法签名粒度反查调用链';

-- 20. 任务级入口扫描配置（每任务独立，配置只跟任务绑定）
ALTER TABLE ci_task ADD COLUMN IF NOT EXISTS entry_scan_config TEXT;
COMMENT ON COLUMN ci_task.entry_scan_config IS '入口扫描配置 JSON：含 includeAnnotations/includeClasspaths/includeExtends 三类入口规则与 excludeClasspaths/excludePackages/excludeAnnotations 三类排除规则，null 时走默认 Controller/JOB/MQ 兜底';

-- 21. 是否启用模块层级调试（人工复核断点）
ALTER TABLE ci_task ADD COLUMN IF NOT EXISTS require_hierarchy_review BOOLEAN DEFAULT TRUE NOT NULL;
COMMENT ON COLUMN ci_task.require_hierarchy_review IS '是否启用模块层级调试断点：TRUE-模块层级提炼完成后停在 MODULE_HIERARCHY_REVIEW 等待人工调试；FALSE-跳过断点直接进入 GENERATING_DOC。默认 TRUE';

-- 22. 任务分别记录"模块提取提示词版本"与"文档生成提示词版本"
ALTER TABLE ci_task ADD COLUMN IF NOT EXISTS modularize_prompt_version INT;
COMMENT ON COLUMN ci_task.modularize_prompt_version IS '模块提取提示词版本（AI_ANALYZING / MODULE_HIERARCHY 阶段使用）';
ALTER TABLE ci_task ADD COLUMN IF NOT EXISTS document_prompt_version INT;
COMMENT ON COLUMN ci_task.document_prompt_version IS '文档生成提示词版本（GENERATING_DOC 阶段使用）';


