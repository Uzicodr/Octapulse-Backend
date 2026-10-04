package com.octapulse.backend.service;

import com.octapulse.backend.domain.ApiUsage;
import com.octapulse.backend.repository.ApiUsageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class UsageBudgetService {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final ApiUsageRepository repository;

    public UsageBudgetService(ApiUsageRepository repository) {
        this.repository = repository;
    }

    public String currentMonth() {
        return ZonedDateTime.now(ZoneOffset.UTC).format(MONTH_FORMAT);
    }

    @Transactional
    public void checkAndIncrement(String source, int cap) {
        String month = currentMonth();
        ApiUsage usage = repository.findBySourceAndMonth(source, month).orElseGet(() -> {
            ApiUsage u = new ApiUsage();
            u.setSource(source);
            u.setMonth(month);
            u.setCount(0);
            return u;
        });

        if (usage.getCount() >= cap) {
            throw new BudgetExceededException(source + " monthly cap of " + cap + " reached");
        }

        usage.setCount(usage.getCount() + 1);
        repository.save(usage);
    }
}
