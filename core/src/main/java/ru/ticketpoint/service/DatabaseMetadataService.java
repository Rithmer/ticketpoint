package ru.ticketpoint.service;

import ru.ticketpoint.repository.DatabaseMetadataRepository;

import java.util.List;
import java.util.Objects;

public final class DatabaseMetadataService {
    private final DatabaseMetadataRepository metadata;

    public DatabaseMetadataService(DatabaseMetadataRepository metadata) {
        this.metadata = Objects.requireNonNull(metadata);
    }

    public List<String> tableNames() {
        return metadata.findTableNames();
    }
}
