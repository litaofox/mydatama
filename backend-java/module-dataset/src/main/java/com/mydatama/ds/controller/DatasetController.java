package com.mydatama.ds.controller;

import com.mydatama.common.api.PageData;
import com.mydatama.common.api.Result;
import com.mydatama.common.security.RequirePerm;
import com.mydatama.ds.entity.Dataset;
import com.mydatama.ds.entity.DatasetVersion;
import com.mydatama.ds.service.DatasetService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 数据集定义/版本/对比。
 */
@RestController
@RequestMapping("/api/dataset")
@RequiredArgsConstructor
public class DatasetController {

    private final DatasetService datasetService;

    @GetMapping("/datasets")
    @RequirePerm("ds:dataset:read")
    public Result<PageData<Dataset>> page(@RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "100") int size) {
        return Result.ok(PageData.of(datasetService.list(page, size), d -> d));
    }

    @PostMapping("/datasets")
    @RequirePerm("ds:dataset:write")
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        String name = body.get("name") == null ? null : body.get("name").toString();
        String scenario = body.get("scenario") == null ? null : body.get("scenario").toString();
        String description = body.get("description") == null ? null : body.get("description").toString();
        @SuppressWarnings("unchecked")
        Map<String, Object> filterCond = (Map<String, Object>) body.get("filterCond");
        Long id = datasetService.create(name, scenario, description, filterCond);
        return Result.ok(Map.of("id", id));
    }

    @PostMapping("/datasets/{id}/versions")
    @RequirePerm("ds:dataset:publish")
    public Result<Map<String, Object>> createVersion(@PathVariable Long id,
                                                     @RequestBody(required = false) Map<String, Object> body) {
        String changeNote = body == null || body.get("changeNote") == null ? null : body.get("changeNote").toString();
        return Result.ok(datasetService.createVersion(id, changeNote));
    }

    @GetMapping("/datasets/{id}/versions")
    @RequirePerm("ds:dataset:read")
    public Result<List<DatasetVersion>> versions(@PathVariable Long id) {
        return Result.ok(datasetService.listVersions(id));
    }

    @GetMapping("/versions/{id}")
    @RequirePerm("ds:dataset:read")
    public Result<Map<String, Object>> versionDetail(@PathVariable Long id) {
        return Result.ok(datasetService.getVersionDetail(id));
    }

    @GetMapping("/versions/{a}/compare/{b}")
    @RequirePerm("ds:dataset:read")
    public Result<Map<String, Object>> compare(@PathVariable("a") Long a, @PathVariable("b") Long b) {
        return Result.ok(datasetService.compare(a, b));
    }
}
