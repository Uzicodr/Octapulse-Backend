package com.octapulse.backend.domain;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "api_usage", uniqueConstraints = @UniqueConstraint(columnNames = {"source", "month"}))
public class ApiUsage {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String source;

    @Column(nullable = false)
    private String month;

    @Column(nullable = false)
    private int count = 0;

    @Version
    private int version;

    public UUID getId() { return id; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getMonth() { return month; }
    public void setMonth(String month) { this.month = month; }
    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }
}
