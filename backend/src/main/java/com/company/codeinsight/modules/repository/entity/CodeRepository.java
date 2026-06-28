package com.company.codeinsight.modules.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.codeinsight.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

/**
 * Git 代码仓库配置实体类
 * 对应数据库中的 ci_repository 表，存储 Git 仓库地址、克隆分支、访问帐密、扫描过滤规则及最近一次分析详情。
 * 通过 deleted_at + @TableLogic 实现软删除。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ci_repository")
public class CodeRepository extends BaseEntity {

    /**
     * 自增主键 ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属业务系统 ID
     */
    private Long systemId;

    /**
     * Git 仓库的远程 clone URL 地址（支持 HTTP/HTTPS，或本地文件绝对目录路径以支持本地测试）
     */
    private String gitUrl;

    /**
     * 目标拉取的分支名（如 main, master）
     */
    private String branch;

    /**
     * 访问 Git 的用户名（私有仓库）
     */
    private String username;

    /**
     * 访问 Git 的密码或 Access Token（私有仓库）
     */
    private String password;

    /**
     * 项目扫描时的起始相对路径根目录（默认为项目根路径）
     */
    private String scanRoot;

    /**
     * 排除不进行扫描的文件夹名（以逗号分隔，如 target, .git, node_modules）
     */
    private String excludeDirs;

    /**
     * 排除不进行解析分析的文件后缀类型（以逗号分隔，如 .png, .jpg, .zip, .pdf）
     */
    private String excludeFileTypes;

    /**
     * 扫描时所拉取的最近一次 Git 提交 Commit ID
     */
    private String lastCommitId;

    /**
     * 最近一次触发反编译或分析任务的执行时间
     */
    private LocalDateTime lastDecompileAt;

    /**
     * 逻辑删除时间。NULL=未删除，非空=已删除时间。
     */
    @TableLogic(value = "null", delval = "now()")
    private LocalDateTime deletedAt;
}

