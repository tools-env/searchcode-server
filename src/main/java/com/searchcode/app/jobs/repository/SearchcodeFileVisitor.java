package com.searchcode.app.jobs.repository;

import com.searchcode.app.config.Values;
import com.searchcode.app.dto.CodeIndexDocument;
import com.searchcode.app.model.RepoResult;
import com.searchcode.app.service.Singleton;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;


public class SearchcodeFileVisitor<Path> extends SimpleFileVisitor<Path> {

    private final IndexBaseRepoJob indexBaseRepoJob;
    private final RepoResult repoResult;
    private final String fileRepoLocations;
    private final String repoRemoteLocation;

    // Used to hold the reports of what was indexed and what needs to be removed
    public List<String[]> reportList = new ArrayList<>();
    public Map<String, String> fileLocationsMap = new HashMap<>();

    public SearchcodeFileVisitor(IndexBaseRepoJob indexBaseRepoJob, RepoResult repoResult, String fileRepoLocations, String repoRemoteLocation) {
        this.indexBaseRepoJob = indexBaseRepoJob;
        this.repoResult = repoResult;
        this.fileRepoLocations = fileRepoLocations;
        this.repoRemoteLocation = repoRemoteLocation;
    }

    @Override
    public FileVisitResult preVisitDirectory(Object dir, BasicFileAttributes attrs) throws IOException {
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(Object file, BasicFileAttributes attrs) throws IOException {
        
        try {
            java.nio.file.Path filePath = (java.nio.file.Path)file;

            if (this.indexBaseRepoJob.shouldJobPauseOrTerminate()) {
                return FileVisitResult.TERMINATE;
            }

            if (Singleton.getDataService().getPersistentDelete().contains(this.repoResult.getName())) {
                return FileVisitResult.TERMINATE;
            }

            Singleton.getLogger().info("MaxDiOrio convert file path " + filePath.toString());
            // Convert Path file to unix style that way everything is easier to reason about
            String fileParent = FilenameUtils.separatorsToUnix(filePath.getParent().toString());
            String fileToString = FilenameUtils.separatorsToUnix(filePath.toString());
            String fileName = filePath.getFileName().toString();

            Singleton.getLogger().info("MaxDiOrio ignoreFile " + filePath.toString());
            if (this.indexBaseRepoJob.ignoreFile(fileParent)) {
                return FileVisitResult.CONTINUE;
            }

            // This needs to be the primary key of the file
            fileLocationsMap.put(fileToString, null);

            Singleton.getLogger().info("MaxDiOrio isUpdated " + filePath.toString());
            // If the file has not been updated since the last run then we can skip
            if (!this.indexBaseRepoJob.isUpdated(fileToString, repoResult.getData().jobRunTime)) {
                return FileVisitResult.CONTINUE;
            }

            Singleton.getLogger().info("MaxDiOrio getCodeLines " + filePath.toString());
            IndexBaseRepoJob.CodeLinesReturn codeLinesReturn = this.indexBaseRepoJob.getCodeLines(fileToString, reportList);
            if (codeLinesReturn.isError()) {
                fileLocationsMap.remove(fileToString);
                return FileVisitResult.CONTINUE;
            }


            Singleton.getLogger().info("MaxDiOrio isMinified " + filePath.toString());
            IndexBaseRepoJob.IsMinifiedReturn isMinified = this.indexBaseRepoJob.getIsMinified(codeLinesReturn.getCodeLines(), fileName, reportList);
            if (isMinified.isMinified()) {
                return FileVisitResult.CONTINUE;
            }

            Singleton.getLogger().info("MaxDiOrio isEmpty " + filePath.toString());
            if (this.indexBaseRepoJob.checkIfEmpty(codeLinesReturn.getCodeLines(), fileName, reportList)) {
                return FileVisitResult.CONTINUE;
            }

            Singleton.getLogger().info("MaxDiOrio isBinary " + filePath.toString());
            if (this.indexBaseRepoJob.determineBinary(fileToString, fileName, codeLinesReturn.getCodeLines(), reportList)) {
                fileLocationsMap.remove(fileToString);
                return FileVisitResult.CONTINUE;
            }

            Singleton.getLogger().info("MaxDiOrio hash classifier " + filePath.toString());
            String md5Hash = this.indexBaseRepoJob.getFileMd5(fileToString);
            String languageName = Singleton.getFileClassifier().languageGuesser(fileName, codeLinesReturn.getCodeLines());
            String fileLocation = this.indexBaseRepoJob.getRelativeToProjectPath(file.toString(), fileToString);
            String fileLocationFilename = this.indexBaseRepoJob.getFileLocationFilename(fileToString, fileRepoLocations);
            String newString = this.indexBaseRepoJob.getBlameFilePath(fileLocationFilename);
            String codeOwner = this.indexBaseRepoJob.getCodeOwner(codeLinesReturn.getCodeLines(), newString, this.repoResult.getName(), fileRepoLocations, Singleton.getSearchCodeLib());

            Singleton.getLogger().info("MaxDiOrio displayLocation " + filePath.toString());
            String displayLocation = fileLocationFilename.substring(fileLocationFilename.indexOf("/") + 1, fileLocationFilename.length());
            if ("file".equals(this.repoResult.getScm())) {
                displayLocation = fileToString.replace(this.repoResult.getUrl(), "");
                if (displayLocation.startsWith("/")) {
                    displayLocation = displayLocation.substring(displayLocation.indexOf("/") + 1, displayLocation.length());
                }
            }

            if (this.indexBaseRepoJob.LOWMEMORY) {
                Singleton.getLogger().info("MaxDiOrio lowMemoryIndex " + filePath.toString());
                Singleton.getIndexService().indexDocument(new CodeIndexDocument(fileToString, this.repoResult.getName(), fileName, fileLocation, fileLocationFilename, md5Hash, languageName, codeLinesReturn.getCodeLines().size(), StringUtils.join(codeLinesReturn.getCodeLines(), " "), repoRemoteLocation, codeOwner, displayLocation, this.repoResult.getData().source));
            } else {
                Singleton.getLogger().info("MaxDiOrio indexQueue " + filePath.toString());
                Singleton.getIndexService().incrementCodeIndexLinesCount(codeLinesReturn.getCodeLines().size());
                Singleton.getCodeIndexQueue().add(new CodeIndexDocument(fileToString, this.repoResult.getName(), fileName, fileLocation, fileLocationFilename, md5Hash, languageName, codeLinesReturn.getCodeLines().size(), StringUtils.join(codeLinesReturn.getCodeLines(), " "), repoRemoteLocation, codeOwner, displayLocation, this.repoResult.getData().source));
            }

            Singleton.getLogger().info("MaxDiOrio addToLogIndexed " + filePath.toString());
            if (this.indexBaseRepoJob.LOGINDEXED) {
                reportList.add(new String[]{fileToString, "included", Values.EMPTYSTRING});
            }

        }
        catch (Exception ex) {
            Singleton.getLogger().warning("ERROR - caught a " + ex.getClass() + " in " + this.getClass() + " indexDocsByPath walkFileTree with message: " + ex.getMessage() + " for file " + file.toString() + " in path " + file + " in repo " + this.repoResult.getName());
        }

        // Continue at all costs
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFileFailed(Object file, IOException ex) throws IOException {
        java.nio.file.Path filePath = (java.nio.file.Path)file;
        reportList.add(new String[]{filePath.toString(), "excluded", ex.toString()});
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult postVisitDirectory(Object dir, IOException exc) throws IOException {
        return FileVisitResult.CONTINUE;
    }
}
