package com.example.demo.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Entity
@Table(name = "SSE_LAST_EVENT_ID")
@Data
@NoArgsConstructor
public class SseLastEventId {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column
    private String name;

    @Column
    private String lastEventId;

    public SseLastEventId(String name, String lastEventId) {
        this.name = name;
        this.lastEventId = lastEventId;
    }
}
