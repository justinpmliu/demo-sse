package com.example.demo.entity;

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
    private Integer id;

    @Column
    private String name;

    @Column
    private String data;

    @Column
    private LocalDateTime createdDttm;

    public SseEvent(String name, String data) {
        this.name = name;
        this.data = data;
        this.createdDttm = LocalDateTime.now();
    }
}
