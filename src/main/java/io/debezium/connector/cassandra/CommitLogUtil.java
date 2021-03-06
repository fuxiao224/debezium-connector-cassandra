/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.connector.cassandra;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.debezium.connector.cassandra.exceptions.CassandraConnectorDataException;

/**
 * Utility class used by the {@link CommitLogProcessor} to compare/delete commit log files.
 */
public final class CommitLogUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(CommitLogUtil.class);

    private static final Pattern FILENAME_REGEX_PATTERN = Pattern.compile("CommitLog-\\d+-(\\d+).log");

    private CommitLogUtil() {
    }

    /**
     * Move a commit log to a new directory. If the commit log already exists in the new directory, it woull be replaced.
     */
    public static boolean moveCommitLog(File file, Path toDir) {
        try {
            Matcher filenameMatcher = FILENAME_REGEX_PATTERN.matcher(file.getName());
            if (!filenameMatcher.matches()) {
                LOGGER.warn("Cannot move file {} because it does not appear to be a CommitLog.", file.getName());
                return false;
            }
            Files.move(file.toPath(), toDir.resolve(file.getName()), REPLACE_EXISTING);
            LOGGER.debug("Moved CommitLog file {} from {} to {}.", file.getName(), file.getParent(), toDir);
            return true;
        }
        catch (Exception e) {
            LOGGER.warn("Failed to move CommitLog file {} from {} to {}. Error:", file.getName(), file.getParent(), toDir, e);
            return false;
        }
    }

    /**
     * Delete a commit log and logs the error in the case the deletion failed.
     */
    public static boolean deleteCommitLog(File file) {
        try {
            Matcher filenameMatcher = FILENAME_REGEX_PATTERN.matcher(file.getName());
            if (!filenameMatcher.matches()) {
                LOGGER.warn("Cannot delete file {} because it does not appear to be a CommitLog", file.getName());
                return false;
            }
            Files.delete(file.toPath());
            LOGGER.debug("Deleted CommitLog file {} from {}.", file.getName(), file.getParent());
            return true;
        }
        catch (Exception e) {
            LOGGER.warn("Failed to delete CommitLog file {} from {}. Error: ", file.getName(), file.getParent(), e);
            return false;
        }
    }

    /**
     * Given a directory, return an array of commit logs in this directory.
     * If the directory does not contain any commit logs, an empty array is returned.
     */
    public static File[] getCommitLogs(File directory) {
        if (!directory.isDirectory()) {
            throw new IllegalArgumentException("Given directory does not exist: " + directory);
        }

        return directory.listFiles(f -> f.isFile() && FILENAME_REGEX_PATTERN.matcher(f.getName()).matches());
    }

    /**
     * Comparing two commit log files provided the {@link File} instances;
     * Returns 0 if they are the same, -1 if first file is older, 1 if first file is newer.
     */
    public static int compareCommitLogs(File file1, File file2) {
        if (file1.equals(file2)) {
            return 0;
        }
        long ts1 = extractTimestamp(file1.getName());
        long ts2 = extractTimestamp(file2.getName());
        return Long.compare(ts1, ts2);
    }

    /**
     * Comparing two commit log files provided the file names.
     * Returns 0 if they are the same, -1 if first file is older, 1 if first file is newer.
     */
    public static int compareCommitLogs(String filename1, String filename2) {
        if (filename1.equals(filename2)) {
            return 0;
        }
        long ts1 = extractTimestamp(filename1);
        long ts2 = extractTimestamp(filename2);
        return Long.compare(ts1, ts2);
    }

    private static long extractTimestamp(String commitLogFileName) {
        Matcher filenameMatcher = FILENAME_REGEX_PATTERN.matcher(commitLogFileName);
        if (!filenameMatcher.matches()) {
            throw new CassandraConnectorDataException("Cannot extract timestamp because " + commitLogFileName + " does not appear to be a CommitLog");
        }
        return Long.parseLong(filenameMatcher.group(1));
    }
}
