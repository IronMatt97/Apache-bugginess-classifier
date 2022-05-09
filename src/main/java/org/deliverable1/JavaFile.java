package org.deliverable1;

import java.util.ArrayList;
import java.util.List;

public class JavaFile
{
    private Boolean buggyness;	//Buggyness di una classe
    private Integer loc;		//Linee di codice
    private Integer locAdded;	//Somma delle loc aggiunte revisione dopo revisione
    private Integer maxLOCAdded;//Massima quantità di loc aggiunte in una volta sola
    private Integer nr; 		//Numero di revisioni
    private final String className;	//Nome del file java
    private Integer versionIndex;	//Versione di appartenenza del file java
    private Integer nfix;		//numero di fix di un dato java file nei commit
    private final ArrayList<String> authors;	//Lista degli autori, necessaria per il calcolo del numero di autori
    private final ArrayList<Integer> locAddedPerRevision;//loc aggiunte per revisione, utile al calcolo dell'avg loc added
    private String text;	//testo del java file
    private Integer churn;	//churn dato da addedlines-removedlines per ogni release
    private Integer maxChurn;//massimo valore d'i-esimo churn all'interno della sommatoria qui sopra

    public JavaFile(String s, Integer v)
    {
        //Il costruttore deve inizializzare le metriche
        this.loc = 0;
        this.locAdded = 0;
        this.maxLOCAdded = 0;
        this.churn = 0;
        this.maxChurn = 0;
        this.nr = 1 ;
        this.nfix = 0;
        this.className = s;
        this.versionIndex = v;
        this.authors = new ArrayList<>();
        this.text = "";
        this.locAddedPerRevision = new ArrayList<>();
        this.buggyness=false;
    }
    public void setVersionIndex(Integer a)
    {
        this.versionIndex=a;
    }
    public void setText(String a)
    {
        this.text=a;
    }
    public String getText()
    {
        return this.text;
    }
    public void addAuthor(String a)
    {
        this.authors.add(a);
    }
    public Integer getNAuth()
    {
        return this.authors.size();
    }
    public List<String> getAuthors()
    {
        return this.authors;
    }
    public void addLOCPerRevision(Integer l)
    {
        this.locAddedPerRevision.add(l);
    }
    public Double getAVGLOCAdded()
    {
        Double avg = 0.0;
        for (Integer integer : this.locAddedPerRevision) {
            avg += integer;
        }
        return (avg/this.locAddedPerRevision.size());
    }
    public void setBuggyness(boolean b)
    {
        this.buggyness=b;
    }
    public boolean isBuggy()
    {
        return this.buggyness;
    }
    public void setLOC (Integer l)
    {
        this.loc = l;
    }
    public Integer getLOCAdded()
    {
        return this.locAdded;
    }
    public void setLOCAdded (Integer l)
    {
        this.locAdded = l;
    }
    public Integer getChurn()
    {
        return this.churn;
    }
    public void setChurn (Integer l)
    {
        this.churn = l;
    }
    public Integer getMaxChurn()
    {
        return this.maxChurn;
    }
    public void setMaxChurn (Integer l)
    {
        this.maxChurn = l;
    }
    public Integer getMaxLOCAdded()
    {
        return this.maxLOCAdded;
    }
    public void setMaxLOCAdded (Integer l)
    {
        this.maxLOCAdded = l;
    }
    public Integer getLOC()
    {
        return this.loc;
    }
    public void increaseNFIX ()
    {
        this.nfix++;
    }
    public Integer getNFIX()
    {
        return this.nfix;
    }
    public void increaseNR ()
    {
        this.nr++;
    }
    public Integer getNR()
    {
        return this.nr;
    }
    public String getClassName()
    {
        return this.className;
    }
    public Integer getVersionIndex()
    {
        return this.versionIndex;
    }
}
