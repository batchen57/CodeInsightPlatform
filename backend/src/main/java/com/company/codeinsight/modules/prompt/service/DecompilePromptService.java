package com.company.codeinsight.modules.prompt.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.company.codeinsight.modules.prompt.dto.PromptTestResultDto;
import com.company.codeinsight.modules.prompt.entity.DecompilePrompt;

import java.util.Map;

/**
 * 提示词模板管理服务接口
 * 定义提示词分页查询、克隆备份、启用状态更改、变量替换渲染以及 AI 测试运行业务规范。
 */
public interface DecompilePromptService extends IService<DecompilePrompt> {

    /**
     * 分页、条件查询提示词模板列表
     */
    Page<DecompilePrompt> listPromptsPage(int current, int size, String name, Integer status);

    /**
     * 克隆复制指定 ID 提示词模板以创建一条全新副本
     *
     * @param id 源模板 ID
     * @return 克隆创建出的新模板对象
     */
    DecompilePrompt clonePrompt(Long id);

    /**
     * 变更指定提示词的使用状态（1-启用, 0-禁用）
     */
    void changeStatus(Long id, Integer status);

    /**
     * 对提示词模板中的各种自定义占位符变量（如 ${code} 等）进行动态文本替换
     *
     * @param template  提示词模板正文
     * @param variables 变量键值对
     * @return 渲染后的可用提示词
     */
    String replaceVariables(String template, Map<String, String> variables);

    /**
     * 用测试代码和指定的模型测试试跑该提示词模板并输出大模型分析结果与 Token 耗时开销统计
     *
     * @param id         提示词模板 ID
     * @param sampleCode 用于分析测试的 Java 类/方法代码片段
     * @param modelId    指定的模型 ID
     * @return 试跑报告数据传输对象
     */
    PromptTestResultDto testRun(Long id, String sampleCode, Long modelId);
}

