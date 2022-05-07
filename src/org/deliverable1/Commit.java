package org.deliverable1;

import org.eclipse.jgit.revwalk.RevCommit;

import java.time.LocalDateTime;
import java.util.ArrayList;

public class Commit
{
    private RevCommit commitObject;
    private Release release;
    private LocalDateTime date;
    private ArrayList<JavaFile> javaFiles;

    public Commit(RevCommit rev)
    {
        this.commitObject = rev;
        this.javaFiles = new ArrayList<>();
    }
    public RevCommit getCommitObject()
    {
        return this.commitObject;
    }
    public void setCommitObject(RevCommit rev)
    {
        this.commitObject = rev;
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
    public ArrayList<JavaFile> getJavaFiles(){
        return this.javaFiles;
    }
    public void addJavaFile(JavaFile f){
        this.javaFiles.add(f);
    }
}
