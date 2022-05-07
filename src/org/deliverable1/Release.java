package org.deliverable1;

import java.time.LocalDateTime;
import java.util.ArrayList;

public class Release {
    private String versionNumber;
    private LocalDateTime versionDate;

    private ArrayList<JavaFile> javaFiles;
    private ArrayList<Commit> commits;

    public Release(String versionNumber, LocalDateTime versionDate)
    {
        this.versionNumber=versionNumber;
        this.versionDate=versionDate;
        this.commits = new ArrayList<>();
        this.javaFiles = new ArrayList<>();
    }
    public Release()
    {
        this.commits = new ArrayList<>();
        this.javaFiles = new ArrayList<>();
    }
    public Release(Release v)
    {
        this.versionNumber=v.versionNumber;
        this.versionDate=v.versionDate;
        this.commits = new ArrayList<>();
        this.javaFiles = new ArrayList<>();
    }
    public String getVersionNumber()
    {
        return this.versionNumber;
    }
    public void setVersionNumber(String versionNumber)
    {
        this.versionNumber=versionNumber;
    }
    public LocalDateTime getVersionDate()
    {
        return this.versionDate;
    }
    public void setVersionDate(LocalDateTime versionDate)
    {
        this.versionDate=versionDate;
    }
    public void addCommit(Commit c){
        this.commits.add(c);
    }
    public ArrayList<Commit> getCommits(){
        return this.commits;
    }
    public void addJavaFile(JavaFile f){
        this.javaFiles.add(f);
    }
    public ArrayList<JavaFile> getJavaFiles(){
        return this.javaFiles;
    }
}
