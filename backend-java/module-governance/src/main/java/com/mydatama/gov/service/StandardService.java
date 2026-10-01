package com.mydatama.gov.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mydatama.common.api.ErrorCode;
import com.mydatama.common.exception.BizException;
import com.mydatama.gov.entity.DataStandard;
import com.mydatama.gov.entity.QualityRule;
import com.mydatama.gov.mapper.DataStandardMapper;
import com.mydatama.gov.mapper.QualityRuleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StandardService {

    private final DataStandardMapper dataStandardMapper;
    private final QualityRuleMapper qualityRuleMapper;

    public List<DataStandard> listStandards() {
        return dataStandardMapper.selectList(
                new LambdaQueryWrapper<DataStandard>().orderByAsc(DataStandard::getId));
    }

    public Long createStandard(Map<String, Object> body) {
        String code = body.get("code") == null ? null : body.get("code").toString();
        Long exists = dataStandardMapper.selectCount(
                new LambdaQueryWrapper<DataStandard>().eq(DataStandard::getCode, code));
        if (exists != null && exists > 0) {
            throw new BizException(ErrorCode.CONFLICT);
        }
        DataStandard standard = new DataStandard();
        standard.setCode(code);
        standard.setName(body.get("name") == null ? null : body.get("name").toString());
        standard.setRuleExpr(body.get("ruleExpr") == null ? null : body.get("ruleExpr").toString());
        standard.setDescription(body.get("description") == null ? null : body.get("description").toString());
        standard.setEnabled(body.get("enabled") instanceof Number n ? n.intValue() : 1);
        dataStandardMapper.insert(standard);
        return standard.getId();
    }

    public List<QualityRule> listQualityRules(String targetRef) {
        LambdaQueryWrapper<QualityRule> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(targetRef)) {
            qw.eq(QualityRule::getTargetRef, targetRef);
        }
        qw.orderByAsc(QualityRule::getId);
        return qualityRuleMapper.selectList(qw);
    }
}
