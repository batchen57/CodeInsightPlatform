package com.company.codeinsight.common.response;

import lombok.Data;
import java.util.List;

@Data
public class PageResult<T> {
    private long total;
    private long size;
    private long current;
    private List<T> records;

    public PageResult() {}

    public PageResult(long total, long size, long current, List<T> records) {
        this.total = total;
        this.size = size;
        this.current = current;
        this.records = records;
    }
}
