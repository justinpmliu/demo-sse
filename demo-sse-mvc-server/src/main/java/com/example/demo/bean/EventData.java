package com.example.demo.bean;

import lombok.Data;

import java.util.List;

@Data
public class EventData<T> {
    private String name;
    private List<T> data;
}
