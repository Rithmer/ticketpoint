package ru.ticketpoint.repository;

import java.util.List;

public interface DatabaseMetadataRepository {
    List<String> findTableNames();
}
