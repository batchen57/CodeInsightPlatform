package com.company.codeinsight.modules.scanwindow.controller;

import com.company.codeinsight.common.response.ApiResponse;
import com.company.codeinsight.modules.scanwindow.dto.ScanWindowDto;
import com.company.codeinsight.modules.scanwindow.entity.ScanWindowEntity;
import com.company.codeinsight.modules.scanwindow.service.ScanWindowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/scan-windows")
@RequiredArgsConstructor
public class ScanWindowController {

    private final ScanWindowService service;

    @GetMapping("/by-repository/{repositoryId}")
    public ApiResponse<ScanWindowEntity> getByRepository(@PathVariable Long repositoryId) {
        return ApiResponse.success(service.getByRepository(repositoryId));
    }

    @PostMapping
    public ApiResponse<ScanWindowEntity> upsert(@RequestBody @Valid ScanWindowDto dto) {
        ScanWindowEntity w = new ScanWindowEntity();
        w.setRepositoryId(dto.getRepositoryId());
        w.setWeekDays(dto.getWeekDays());
        w.setHour(dto.getHour());
        w.setMinute(dto.getMinute());
        w.setEnabled(dto.getEnabled());
        return ApiResponse.success(service.upsert(w));
    }

    @DeleteMapping("/by-repository/{repositoryId}")
    public ApiResponse<Void> deleteByRepository(@PathVariable Long repositoryId) {
        service.deleteByRepository(repositoryId);
        return ApiResponse.success();
    }

    @GetMapping
    public ApiResponse<List<ScanWindowEntity>> list() {
        return ApiResponse.success(service.listAll());
    }
}
