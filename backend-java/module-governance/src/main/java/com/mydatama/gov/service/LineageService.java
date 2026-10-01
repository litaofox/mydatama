package com.mydatama.gov.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mydatama.gov.entity.Asset;
import com.mydatama.gov.entity.LineageEdge;
import com.mydatama.gov.mapper.AssetMapper;
import com.mydatama.gov.mapper.LineageEdgeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LineageService {

    private final LineageEdgeMapper lineageEdgeMapper;
    private final AssetMapper assetMapper;

    public Map<String, Object> graph(Long assetId, int depth) {
        Map<Long, Integer> levels = new LinkedHashMap<>();
        List<LineageEdge> allEdges = new ArrayList<>();
        levels.put(assetId, 0);

        Deque<Long> queue = new ArrayDeque<>();
        queue.add(assetId);
        while (!queue.isEmpty()) {
            Long current = queue.poll();
            int level = levels.get(current);

            List<LineageEdge> downstream = lineageEdgeMapper.selectList(
                    new LambdaQueryWrapper<LineageEdge>().eq(LineageEdge::getFromAssetId, current));
            for (LineageEdge e : downstream) {
                allEdges.add(e);
                Long next = e.getToAssetId();
                int nextLevel = level + 1;
                if (Math.abs(nextLevel) <= depth && !levels.containsKey(next)) {
                    levels.put(next, nextLevel);
                    queue.add(next);
                }
            }

            List<LineageEdge> upstream = lineageEdgeMapper.selectList(
                    new LambdaQueryWrapper<LineageEdge>().eq(LineageEdge::getToAssetId, current));
            for (LineageEdge e : upstream) {
                allEdges.add(e);
                Long next = e.getFromAssetId();
                int nextLevel = level - 1;
                if (Math.abs(nextLevel) <= depth && !levels.containsKey(next)) {
                    levels.put(next, nextLevel);
                    queue.add(next);
                }
            }
        }

        Map<Long, Asset> assetMap = levels.isEmpty()
                ? Map.of()
                : assetMapper.selectBatchIds(levels.keySet()).stream()
                .collect(Collectors.toMap(Asset::getId, Function.identity()));

        List<Map<String, Object>> nodes = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : levels.entrySet()) {
            Asset a = assetMap.get(entry.getKey());
            if (a == null) {
                continue;
            }
            Map<String, Object> node = new HashMap<>();
            node.put("id", a.getId());
            node.put("name", a.getName());
            node.put("assetType", a.getAssetType());
            node.put("modality", a.getModality());
            node.put("level", entry.getValue());
            nodes.add(node);
        }

        List<Map<String, Object>> edges = new ArrayList<>();
        for (LineageEdge e : allEdges) {
            Map<String, Object> edge = new HashMap<>();
            edge.put("from", e.getFromAssetId());
            edge.put("to", e.getToAssetId());
            edge.put("relType", e.getRelType());
            edges.add(edge);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("nodes", nodes);
        result.put("edges", edges);
        return result;
    }
}
