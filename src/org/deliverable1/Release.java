package org.deliverable1;

import java.time.LocalDateTime;

public class Release {
    private String versionNumber;
    private LocalDateTime versionDate;
    public Release(String versionNumber, LocalDateTime versionDate)
    {
        this.versionNumber=versionNumber;
        this.versionDate=versionDate;
    }
    public Release()
    {
    }
    public Release(Release v)
    {
        this.versionNumber=v.versionNumber;
        this.versionDate=v.versionDate;
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
}
