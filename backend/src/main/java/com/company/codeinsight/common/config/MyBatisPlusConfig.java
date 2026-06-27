package com.company.codeinsight.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.LocalDateTime;

/**
 * MyBatis-Plus 框架配置类
 * 包含分页拦截器配置，以及用于审计字段（如创建时间、更新时间）自动填充的 MetaObjectHandler。
 */
@Configuration
public class MyBatisPlusConfig implements MetaObjectHandler {

    /**
     * 配置 MyBatis-Plus 拦截器链
     * 注册针对 PostgreSQL 数据库的分页拦截器，使 MyBatis-Plus 的 Page 分页查询能自动拼装 LIMIT/OFFSET 语句。
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 添加 PostgreSQL 分页插件
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
        return interceptor;
    }

    /**
     * 数据库新增记录时的字段自动填充拦截器
     * 在调用 mapper.insert() 时，自动为实体类中标记了 TableField(fill = FieldFill.INSERT) 的字段填充初始值。
     *
     * @param metaObject 元数据反射对象
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        // 新增时自动填充创建时间（createdAt）与最后更新时间（updatedAt）为当前时间
        this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }

    /**
     * 数据库更新记录时的字段自动填充拦截器
     * 在调用 mapper.update() 或 updateById() 时，自动为实体中标记了 TableField(fill = FieldFill.UPDATE) 的字段填充更新值。
     *
     * @param metaObject 元数据反射对象
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        // 更新时自动修改更新时间（updatedAt）为当前最新的操作时间
        this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }
}

