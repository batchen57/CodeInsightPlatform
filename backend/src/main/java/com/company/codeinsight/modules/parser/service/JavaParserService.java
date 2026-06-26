package com.company.codeinsight.modules.parser.service;

import com.company.codeinsight.modules.parser.model.ParsedClassInfo;
import java.io.File;
import java.util.List;

public interface JavaParserService {

    /**
     * 解析单个 Java 文件
     */
    ParsedClassInfo parseFile(File file);

    /**
     * 解析整个目录下的所有 Java 文件
     */
    List<ParsedClassInfo> parseDirectory(File directory);
}
