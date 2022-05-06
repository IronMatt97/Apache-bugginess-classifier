package org.deliverable1;

import java.time.LocalDateTime;
import java.util.ArrayList;

public class JiraTicket {
    public JiraTicket(){
        this.commits = new ArrayList<>();
        this.av = new ArrayList<>();
    }
    private String key;
    private ArrayList<Release> av;
    private Release iv;
    private Release fv;
    private Release ov;
    private LocalDateTime creationDate;
    private LocalDateTime resolutionDate;
    private ArrayList<Commit> commits;
    private int p;

    public void setKey(String key)
    {
        this.key=key;
    }
    public void addAV(Release av)
    {
        this.av.add(av);
    }
    public void setIV(Release iv)
    {
        this.iv=iv;
    }
    public void setFV(Release fv)
    {
        this.fv=fv;
    }
    public void setOV(Release ov)
    {
        this.ov=ov;
    }
    public void setCreationDate(LocalDateTime creationDate)
    {
        this.creationDate=creationDate;
    }
    public void setResolutionDate(LocalDateTime resolutionDate)
    {
        this.resolutionDate=resolutionDate;
    }
    public String getKey()
    {
        return this.key;
    }
    public ArrayList<Release> getAV()
    {
        return this.av;
    }
    public Release getIV()
    {
        return this.iv;
    }
    public Release getFV()
    {
        return this.fv;
    }
    public Release getOV()
    {
        return this.ov;
    }
    public LocalDateTime getCreationDate()
    {
        return this.creationDate;
    }
    public LocalDateTime getResolutionDate()
    {
        return this.resolutionDate;
    }
    public void addCommit(Commit c){
        commits.add(c);
    }
    public ArrayList<Commit> getCommits(){
        return this.commits;
    }
    public int getP(){
        return this.p;
    }
    public void setP(int p)
    {
        this.p=p;
    }
}
