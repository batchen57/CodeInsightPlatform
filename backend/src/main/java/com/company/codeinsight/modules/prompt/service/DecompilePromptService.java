package com.company.codeinsight.modules.prompt.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.company.codeinsight.modules.prompt.dto.PromptTestResultDto;
import com.company.codeinsight.modules.prompt.entity.DecompilePrompt;

import java.util.Map;

public interface DecompilePromptService extends IService<DecompilePrompt> {
    Page<DecompilePrompt> listPromptsPage(int current, int size, String name, Integer status);
    DecompilePrompt clonePrompt(Long id);
    void changeStatus(Long id, Integer status);
    String replaceVariables(String template, Map<String, String> variables);
    PromptTestResultDto testRun(Long id, String sampleCode, Long modelId);
}
