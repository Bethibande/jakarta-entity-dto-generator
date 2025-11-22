package com.bethibande.process.example;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.time.LocalDateTime;

@Embeddable
public class EmbeddableTimestamp {

    @Column(nullable = false)
    public LocalDateTime created;

    @Column(nullable = false)
    public LocalDateTime updated;

}
