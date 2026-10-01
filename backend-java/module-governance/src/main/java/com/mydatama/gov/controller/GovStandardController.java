package com.mydatama.gov.controller;

import com.mydatama.common.api.Result;
import com.mydatama.common.security.RequirePerm;
import com.mydatama.gov.entity.DataStandard;
import com.mydatama.gov.entity.QualityRule;
import com.mydatama.gov.service.StandardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 数据标准与质量规则。
 */
@RestController
@RequestMapping("/api/governance")
@RequiredArgsConstructor
public class GovStandardController {

    private final StandardService standardService;

    @GetMapping("/standards")
    @RequirePerm("gov:asset:read")
    public Result<List<DataStandard>> listStandards() {
        return Result.ok(standardService.listStandards());
    }

    @PostMapping("/standards")
    @RequirePerm("gov:standard:write")
    public Result<Map<String, Object>> createStandard(@RequestBody Map<String, Object> body) {
        Long id = standardService.createStandard(body);
        return Result.ok(Map.of("id", id));
    }

    @GetMapping("/quality-rules")
    @RequirePerm("gov:asset:read")
    public Result<List<QualityRule>> listQualityRules(@RequestParam(required = false) String targetRef) {
        return Result.ok(standardService.listQualityRules(targetRef));
    }
}
