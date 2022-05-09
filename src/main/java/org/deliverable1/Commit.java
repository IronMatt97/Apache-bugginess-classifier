package org.deliverable1;

import org.eclipse.jgit.revwalk.RevCommit;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Commit
{
    private final RevCommit commitObject;
    private Release release;
    private LocalDateTime date;
    private final ArrayList<JavaFile> javaFiles;

    public Commit(RevCommit rev)
    {
        this.commitObject = rev;
        this.javaFiles = new ArrayList<>();
    }
    public RevCommit getCommitObject()
    {
        return this.commitObject;
    }
    public Release getRelease(){
        return this.release;
    }
    public void setRelease(Release release){
        this.release = release;
    }
    public LocalDateTime getDate(){
        return this.date;
    }
    public void setDate(LocalDateTime date)
    {
        this.date = date;
    }
    public List<JavaFile> getJavaFiles(){
        return this.javaFiles;
    }
    public void addJavaFile(JavaFile f){
        this.javaFiles.add(f);
    }
}
