package org.deliverable1;

import java.util.ArrayList;
import java.util.List;

public class JiraTicket {
    public JiraTicket(){
        this.commits = new ArrayList<>();
        this.av = new ArrayList<>();
    }
    private String key;
    private final ArrayList<Release> av;
    private Release iv;
    private Release fv;
    private Release ov;
    private final ArrayList<Commit> commits;
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
    public String getKey()
    {
        return this.key;
    }
    public List<Release> getAV()
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
    public void addCommit(Commit c){
        commits.add(c);
    }
    public List<Commit> getCommits(){
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
