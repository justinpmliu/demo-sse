package com.example.demo.dao.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "SSE_EVENT")
@Data
@NoArgsConstructor
public class SseEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    private String name;

    @Column(name = "REF_ID")
    private String refId;

    @Column
    private String data;

    @Column(name = "CREATED_DTTM")
    private LocalDateTime createdDttm;

    @Transient
    private String comment;

    public SseEvent(String name, String refId, String data) {
        this.name = name;
        this.refId = refId;
        this.data = data;
        this.createdDttm = LocalDateTime.now();
    }
}
