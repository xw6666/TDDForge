package com.tddforge.git;

import java.util.List;

public record GitStatus(
        String branch,
        String trackingBranch,
        List<String> stagedFiles,
        List<String> unstagedFiles,
        List<String> untrackedFiles
) {

    public GitStatus {
        if (branch == null) branch = "";
        if (trackingBranch == null) trackingBranch = "";
        if (stagedFiles == null) stagedFiles = List.of();
        if (unstagedFiles == null) unstagedFiles = List.of();
        if (untrackedFiles == null) untrackedFiles = List.of();
    }

    public boolean isClean() {
        return stagedFiles.isEmpty() && unstagedFiles.isEmpty() && untrackedFiles.isEmpty();
    }

    public List<String> allChangedFiles() {
        return List.copyOf(
                java.util.stream.Stream.of(stagedFiles, unstagedFiles, untrackedFiles)
                        .flatMap(List::stream)
                        .distinct()
                        .toList()
        );
    }
}
