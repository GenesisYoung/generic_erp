package com.gsgd.generic_erp.entity;

/**
 * Status of a chunked upload session, matching the
 * {@code upload_session.status} enum column.
 */
public enum UploadSessionStatus {
    FINISHED,
    UPLOADING,
    MERGING
}
