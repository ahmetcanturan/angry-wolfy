package com.buddywolfy.angrywolfy.service;

import com.buddywolfy.angrywolfy.dto.OhaResult;
import com.buddywolfy.angrywolfy.entity.Chart;
import com.buddywolfy.angrywolfy.entity.Config;
import com.buddywolfy.angrywolfy.entity.Target;
import com.buddywolfy.angrywolfy.repository.ChartRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
public class ChartService {

    private final ChartRepository chartRepository;

    public ChartService(ChartRepository chartRepository) {
        this.chartRepository = chartRepository;
    }

    /**
     * Persists a successful run's result, then trims the target's history back
     * to the 50 newest rows. Save + trim run in one transaction so the per-target
     * cap always holds.
     */
    @Transactional
    public Chart save(Target target, Config config, OhaResult result) {
        Chart saved = chartRepository.save(new Chart(target, config, result));
        chartRepository.trimToNewest50(target.getId());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Chart> getByTargetId(Long targetId) {
        return chartRepository.findByTargetIdOrderByCreatedAtDesc(targetId);
    }

    @Transactional(readOnly = true)
    public Chart getById(Long id) {
        return chartRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Chart not found: " + id));
    }
}
