package com.company.codeinsight.modules.scanwindow.service;

import com.company.codeinsight.modules.scanwindow.entity.ScanWindowEntity;

import java.util.List;

public interface ScanWindowService {
    ScanWindowEntity getByRepository(Long repositoryId);
    ScanWindowEntity upsert(ScanWindowEntity w);
    void deleteByRepository(Long repositoryId);
    List<ScanWindowEntity> listAll();
    List<ScanWindowEntity> listEnabled();
}
