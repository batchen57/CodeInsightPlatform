package com.company.codeinsight.modules.system.vo;

import com.company.codeinsight.modules.system.entity.SystemApplication;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 系统列表聚合视图
 * 在 SystemApplication 基础上扩展 3 个运营指标：
 * <ul>
 *     <li>repositoryCount：该系统下的有效代码库数量</li>
 *     <li>knowledgeVersionCount：基于该系统产出的知识版本数</li>
 *     <li>lastDecompileAt：最近一次扫描/反编译触发时间（跨所有仓库）</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SystemSummaryVO extends SystemApplication {

    /** 关联代码库数（含未删除） */
    private Long repositoryCount;

    /** 关联知识版本数 */
    private Long knowledgeVersionCount;

    /** 最近一次扫描时间（跨所有仓库） */
    private LocalDateTime lastDecompileAt;
}
