package com.company.codeinsight.modules.parser.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ParsedClassInfo {

    private String className;

    private String packageName;

    private String type; // CONTROLLER, SERVICE, MAPPER, ENTITY, DTO, VO, ENUM, CONFIG, JOB, ANNOTATION, UNKNOWN

    private String requestMapping;

    private List<String> annotations = new ArrayList<>();

    private List<String> dependencies = new ArrayList<>();

    private List<MethodInfo> methods = new ArrayList<>();

    private List<MethodCallInfo> methodCalls = new ArrayList<>();

    private List<String> tables = new ArrayList<>();

    private List<SqlReference> sqlReferences = new ArrayList<>();

    @Data
    public static class MethodInfo {
        private String name;
        private String returnType;
        private String arguments;
        private String requestMapping;
        private String httpMethod;
    }

    @Data
    public static class MethodCallInfo {
        private String callerMethod;
        private String dependencyName;
        private String targetMethod;
        private String expression;
        private Integer lineNumber;
    }

    @Data
    public static class SqlReference {
        private String operation;
        private List<String> tables = new ArrayList<>();
        private List<String> selectedFields = new ArrayList<>();
        private List<String> conditionFields = new ArrayList<>();
        private List<String> insertedFields = new ArrayList<>();
        private List<String> updatedFields = new ArrayList<>();
        private List<String> joinedTables = new ArrayList<>();
    }
}
