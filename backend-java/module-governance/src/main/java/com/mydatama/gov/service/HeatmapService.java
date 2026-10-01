package com.mydatama.gov.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mydatama.gov.entity.AssetUsageStat;
import com.mydatama.gov.mapper.AssetUsageStatMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

@Service
@RequiredArgsConstructor
public class HeatmapService {

    private final AssetUsageStatMapper assetUsageStatMapper;

    public Map<String, Object> heatmap(int days) {
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(days - 1L);

        List<AssetUsageStat> stats = assetUsageStatMapper.selectList(
                new LambdaQueryWrapper<AssetUsageStat>().ge(AssetUsageStat::getStatDate, from));

        Map<String, Map<LocalDate, Long>> aggregated = new TreeMap<>();
        for (AssetUsageStat s : stats) {
            if (s.getBizDomain() == null || s.getStatDate() == null) {
                continue;
            }
            aggregated.computeIfAbsent(s.getBizDomain(), k -> new TreeMap<>())
                    .merge(s.getStatDate(), s.getCnt() == null ? 0L : s.getCnt(), Long::sum);
        }

        List<String> domains = new ArrayList<>(new TreeSet<>(aggregated.keySet()));
        List<String> dates = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(today); d = d.plusDays(1)) {
            dates.add(d.toString());
        }

        List<List<Object>> data = new ArrayList<>();
        for (int di = 0; di < domains.size(); di++) {
            Map<LocalDate, Long> byDate = aggregated.get(domains.get(di));
            for (int ti = 0; ti < dates.size(); ti++) {
                Long cnt = byDate.get(LocalDate.parse(dates.get(ti)));
                if (cnt != null && cnt > 0) {
                    data.add(List.of(di, ti, cnt));
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("domains", domains);
        result.put("dates", dates);
        result.put("data", data);
        return result;
    }
}
