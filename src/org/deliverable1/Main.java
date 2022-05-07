package org.deliverable1;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.InitCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;



import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;


public class Main {
    static ArrayList<Release> releases;
    static ArrayList<JiraTicket> tickets;
    static ArrayList<Commit> commits;
    static ArrayList<JavaFile> javaFiles;

    public static void main(String[] args) throws IOException {
        String projName = "AVRO";
        //Ottieni la lista delle versioni
        releases = getReleases(projName);
        //Ottieni la lista dei ticket di Jira
        tickets = retrieveJiraTickets(projName);
        //Ottieni la lista dei commit di Git
        commits = retrieveGitCommits(projName);
        //Incrocia le informazioni
        associateCommitToTickets();
        adjustFV();
        adjustIV();
        //Arrivati a questo punto nei ticket ci sono i commit associati, e tutte le informazioni su OV/AV/IV/FV

        //Creo la lista dei file toccati
        System.out.println("Creating file list");
        javaFiles = createJavaFilesList(projName);
        //Per la metà delle versioni associo i valori delle metriche
        System.out.println("Computing metrics");
        for(int i = 1; i<= releases.size()/2; i++){
            setJavaFilesInfo(releases.get(i),projName);
        }
        checkAffectedVersionsForBuggyness();
        //Scrivi il csv
        generateCSVMetrics(projName);
    }
    public static void generateCSVMetrics(String projName)
    {

        String outname = projName + "Metrics.csv";
        String b = "";

        try(FileWriter fileWriter = new FileWriter(outname))
        {
            fileWriter.append("VERSION_INDEX;FILE_NAME;LOC;LOC_ADDED;MAX_LOC_ADDED;AVG_LOC_ADDED;NR;NFIX;AUTHORS;CHURN;MAX_CHURN;BUGGYNESS");
            fileWriter.append("\n");
            for (Release r : releases.subList(0,releases.size()/2))
            {
                for(JavaFile f : r.getJavaFiles()){

                    if(f.isBuggy())
                        b = "Yes";
                    else
                        b = "No";
                    fileWriter.append(String.valueOf(releases.indexOf(r)));
                    fileWriter.append(";");
                    fileWriter.append(f.getClassName());
                    fileWriter.append(";");
                    fileWriter.append(f.getLOC().toString());
                    fileWriter.append(";");
                    fileWriter.append(f.getLOCAdded().toString());
                    fileWriter.append(";");
                    fileWriter.append(f.getMaxLOCAdded().toString());
                    fileWriter.append(";");
                    fileWriter.append(f.getAVGLOCAdded().toString());
                    fileWriter.append(";");
                    fileWriter.append(f.getNR().toString());
                    fileWriter.append(";");
                    fileWriter.append(f.getNFIX().toString());
                    fileWriter.append(";");
                    fileWriter.append(f.getNAuth().toString());
                    fileWriter.append(";");
                    fileWriter.append(f.getChurn().toString());
                    fileWriter.append(";");
                    fileWriter.append(f.getMaxChurn().toString());
                    fileWriter.append(";");
                    fileWriter.append(b);
                    fileWriter.append("\n");

                }
            }
        }
        catch (IOException e)
        {
            throw new IllegalStateException("Error in csv Writer");
        }
    }
    public static void checkAffectedVersionsForBuggyness(){
        for(JiraTicket t : tickets){
            for(Commit c : t.getCommits()){
                for (JavaFile f : c.getJavaFiles()){
                    for(Release r : t.getAV()){
                        if(releases.indexOf(r) == f.getVersionIndex())
                            f.setBuggyness(true);
                    }
                }
            }
        }
    }
    public static void setJavaFilesInfo(Release r, String projName) throws IOException {
        //A partire dalla lista di classi imposto in questo metodo le informazioni per le metriche
        String path = "/home/matteo/Documenti/Workspaces/ISW2/"+projName+"/"+projName.toLowerCase()+"/.git";
        Path repoPath = Paths.get(path);
        InitCommand init = Git.init();
        init.setDirectory(repoPath.toFile());
        try (Git git = Git.open(repoPath.toFile()))
        {
            Release prevRelease = releases.get(releases.indexOf(r)-1);
            for (Commit commit : prevRelease.getCommits())//Prendi i commit della versione precedente
            {
                Repository repository = git.log().all().getRepository();
                TreeWalk tw = initializeTreeWalk(repository,commit);
                searchJavaFiles(tw,repository,commit,prevRelease,path);
            }
        }
        catch (IOException e)
        {
            throw new IOException(e);
        }
    }
    private static void searchJavaFiles(TreeWalk tw, Repository repository, Commit commit,Release r, String path) throws IOException {
        //Metodo per ricercare file java
        try
        {
            setBuggyness(r,commit,path);
            while (tw.next())
            {
                if ((tw.getPathString().contains(".java")))
                {
                    //Ho trovato una classe Java in un determinato commit
                    setInfos(r,tw,repository,commit);
                }
            }
            List<DiffEntry> diffs = getDiffs(commit.getCommitObject(), repository);
            if (diffs != null)
                checkNR(diffs,r,commit);

        }
        catch (IOException e)
        {
            throw new IOException(e);
        }
    }
    private static void setBuggyness(Release r, Commit rev, String path) throws IOException
    {
        //Questo metodo viene eseguito per ogni commit
        for (JiraTicket ticket : tickets)
        {//se il ticket non ha av non potrà avere buggyness
            if(ticket.getAV()!=null)
            {//Se trovo uno dei ticket
                String key = ticket.getKey();

                if(rev.getCommitObject().getFullMessage().contains(key))
                {

                    FileRepositoryBuilder repositoryBuilder = new FileRepositoryBuilder();

                    Repository repository = repositoryBuilder.setGitDir(new File(path)).readEnvironment().findGitDir().setMustExist(true).build();

                    List<DiffEntry> diffs = getDiffs(rev.getCommitObject(), repository);
                    //prendo i diffs e se non sono nulli, li analizzo
                    if (diffs != null){
                        analyzeDiffs(diffs,r);
                    }

                }
            }
        }
    }
    private static void analyzeDiffs(List<DiffEntry> diffs, Release r)
    {
        for (DiffEntry diff : diffs)
        {
            String type = diff.getChangeType().toString();
            if ((type.equals("DELETE") || type.equals("MODIFY")) && diff.toString().contains(".java"))
            {
                //se la classe è deleted o modified
                String fileName;
                if (diff.getChangeType() == DiffEntry.ChangeType.DELETE)
                    fileName = diff.getOldPath();//se è stata cancellata devo prendere il path di prima
                else
                    fileName = diff.getNewPath();

                checkCommAndFiles(fileName, r);
            }
        }
    }

    private static void checkCommAndFiles(String fileName,Release r)
    {
        for (JavaFile javaFile : r.getJavaFiles())
        {
            //per tutti i file in ogni versione
            if(javaFile.getClassName().equals(fileName))
            {
                //Se ritrovo quel file nella lista dei diffs
                //Arrivato qui devo prendere nella lista dei veri JavaFile quelli con nome javaFile e versione vcomm.getCommits().get(0).getCommitVersionIndex(); poiche essendo vcomm ordinato in 14 liste di commit associate a versioni diverse
                //Quindi se nei diffs del commit ho trovato una classe potenzialmente buggy, ora devo controllare che la versione ed il nome corrispondano
                checkEquals(javaFile, r);
            }
        }
    }

    private static void checkEquals(JavaFile javaFile, Release r)
    {
        for(JavaFile file : r.getJavaFiles())
        {
            if(file.getClassName().equals(javaFile.getClassName()) && file.getVersionIndex().equals(releases.indexOf(r.getCommits().get(0).getRelease())))
            {
                file.setBuggyness(true);
            }
        }
    }
    private static void checkNR(List<DiffEntry> diffs, Release r, Commit commit)
    {


        for (DiffEntry diff : diffs)
        {
            String type = diff.getChangeType().toString();
            if (diff.toString().contains(".java"))
            {
                //se la classe è deleted o modified
                String fileName;
                if (type.equals("DELETE"))
                    fileName = diff.getOldPath();//se è stata cancellata devo prendere il path di prima
                else
                    fileName = diff.getNewPath();
                for(JavaFile file : r.getJavaFiles())
                {
                    if(file.getClassName().equals(fileName))
                    {
                        file.increaseNR();
                    }
                }
            }
        }
    }
    private static void setInfos(Release r, TreeWalk tw, Repository repository, Commit commit)
    {
        //Provo a fare filelist divisa in versioni
        for (JavaFile file : r.getJavaFiles())
        {
            if(file.getClassName().equals(tw.getPathString()))
            {
                //Ho trovato il nome di una classe nella mia lista file

                commit.addJavaFile(file);//Associo il file al commit
                setLOC(tw,repository,file);
                if(commit.getCommitObject().getFullMessage().contains("fix")
                        && !commit.getCommitObject().getFullMessage().contains("prefix")
                        && !commit.getCommitObject().getFullMessage().contains("postfix"))
                    file.increaseNFIX();

                String authname = commit.getCommitObject().getAuthorIdent().getName();
                if(!file.getAuthors().contains(authname))
                    file.addAuthor(authname);
            }
        }
    }
    private static void setLOC(TreeWalk tw, Repository repository, JavaFile f)
    {
        //Imposto le metriche relative a linee di codice
        ObjectId objectId = tw.getObjectId(0);
        ObjectLoader loader;
        try {
            loader = repository.open(objectId);

            String content = new String(loader.getBytes(), StandardCharsets.US_ASCII);

            String[] lines = content.split("\r\n|\r|\n");
            Integer addedLines = 0;
            Integer removedLines = 0;
            Integer[] addedAndRemovedLines = setAddedAndRemovedLines(content,f.getText(),addedLines,removedLines);
            f.setText(content);

            f.setLOCAdded(f.getLOCAdded() + addedAndRemovedLines[0]);
            f.addLOCPerRevision(addedAndRemovedLines[0]);
            f.setChurn(f.getChurn() + (addedAndRemovedLines[0]-addedAndRemovedLines[1]));
            f.setLOC(lines.length);
            if (addedAndRemovedLines[0] > f.getMaxLOCAdded())
                f.setMaxLOCAdded(addedAndRemovedLines[0]);
            if (addedAndRemovedLines[0]-addedAndRemovedLines[1] > f.getMaxChurn())
                f.setMaxChurn(addedAndRemovedLines[0]-addedAndRemovedLines[1]);

        }
        catch (IOException e)
        {
            e.getMessage();
        }
    }
    private static Integer[] setAddedAndRemovedLines(String presentTXT,String previousTXT, Integer addedLines,Integer removedLines)
    {
        ArrayList <String> text = new ArrayList<>();
        ArrayList <String> ptext = new ArrayList<>();

        Collections.addAll(text, presentTXT.split("\r\n|\r|\n"));
        Collections.addAll(ptext, previousTXT.split("\r\n|\r|\n"));

        //Ho acquisito la lista di stringhe del testo della stessa classe in due revisioni diverse
        for(String s1:ptext)
            if (!text.contains(s1))
                removedLines++;

        for(String s2:text)
            if (!ptext.contains(s2))
                addedLines++;
        //Ora posso mettere in un vettore le righe aggiunte e rimosse rispetto a prima
        Integer[] v = new Integer[2];
        v[0] = addedLines;
        v[1] = removedLines;
        return v;
    }
    private static List<DiffEntry> getDiffs(RevCommit commit, Repository repository) throws IOException
    {
        List<DiffEntry> diffs;
        DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE);
        df.setRepository(repository);
        df.setDiffComparator(RawTextComparator.DEFAULT);
        df.setContext(0);
        df.setDetectRenames(true);
        if (commit.getParentCount() != 0)
        {
            RevCommit parent = (RevCommit) commit.getParent(0).getId();
            diffs = df.scan(parent.getTree(), commit.getTree());
        }
        else
        {
            RevWalk rw = new RevWalk(repository);
            ObjectReader reader = rw.getObjectReader();
            diffs = df.scan(new EmptyTreeIterator(), new CanonicalTreeParser(null, reader, commit.getTree()));
            rw.close();
        }
        df.close();
        return diffs;
    }
    private static TreeWalk initializeTreeWalk(Repository repository, Commit commit)
    {
        TreeWalk tw = new TreeWalk(repository);
        tw.setRecursive(true);
        RevCommit commitToCheck = commit.getCommitObject();
        try
        {
            tw.addTree(commitToCheck.getTree());

            for (RevCommit parent : commitToCheck.getParents())
            {
                tw.addTree(parent.getTree());
            }
        }
        catch (IOException e)
        {
            throw new IllegalStateException("An error has occurred during commit file trees exploration");
        }
        return tw;
    }
    public static ArrayList<JavaFile> createJavaFilesList(String projName){
        Path repoPath = Paths.get("/home/matteo/Documenti/Workspaces/ISW2/"+projName+"/"+projName.toLowerCase()+"/.git");
        ArrayList<JavaFile> javaFiles = new ArrayList<>();
        ArrayList<String> javaFilesNamesList = new ArrayList<>();
        InitCommand init = Git.init();
        init.setDirectory(repoPath.toFile());
        try (Git git = Git.open(repoPath.toFile()))
        {
            for (Commit commit : commits)
            {
                if(releases.indexOf(commit.getRelease())<releases.size()/2) {//Tutto questo va fatto solo per la prima metà
                    ObjectId treeId = commit.getCommitObject().getTree();
                    try (TreeWalk treeWalk = new TreeWalk(git.getRepository())) {
                        treeWalk.reset(treeId);
                        treeWalk.setRecursive(true);
                        while (treeWalk.next()) {
                            if ((treeWalk.getPathString().endsWith(".java")) && !javaFilesNamesList.contains(commit.getRelease().getVersionNumber()+treeWalk.getPathString())) {
                                JavaFile f = new JavaFile(treeWalk.getPathString(), releases.indexOf(commit.getRelease()));
                                javaFilesNamesList.add(commit.getRelease().getVersionNumber()+treeWalk.getPathString());
                                f.setVersionIndex(releases.indexOf(commit.getRelease()));
                                javaFiles.add(f);
                                releases.get(releases.indexOf(commit.getRelease())).addJavaFile(f);//Aggiungo il javaFile alla release
                            }
                        }
                    }
                }
            }
        }
        catch (IOException e)
        {
            throw new IllegalStateException("error creating files list");
        }
        return javaFiles;
    }
    public static void adjustIV(){
        int windowSize = tickets.size()/100;
        int p;
        int predictedIV;
        int fv;
        int ov;
        for (JiraTicket ticket : reverseList(tickets)){
            if(ticket.getIV()==null){
                //Arrivato qui devo applicare il proportion perchè significa che non ho l'IV.

                //ora bisogna fixare le vere AV ed IV
                //Nel caso in cui non ho l'iv devo trovarmelo con proportion
                //E calcolare sulla base di quello poi l'AV
                //Una volta fatto salvo nel ticket e ho finito.
                //MOVING WINDOW: calcolo p  come la media dell'ultimo 1% (degli ultimi 4 o 8 ticket)

                if(ticket.getFV().equals(ticket.getOV())) {
                    ticket.setIV(ticket.getFV());
                    ticket.setP(0);
                }
                else{
                    p = computeP(windowSize,tickets.indexOf(ticket));
                    fv = releases.indexOf(ticket.getFV());
                    ov = releases.indexOf(ticket.getOV());
                    predictedIV = fv -(p*(fv-ov));
                    ticket.setIV(releases.get(predictedIV));
                    ticket.setP(p);
                }
                //Calcolo nuovo AV
                setAffectedVersions(ticket,ticket.getIV().getVersionNumber());
            }
        }
    }
    public static ArrayList<JiraTicket> reverseList(ArrayList<JiraTicket> initialList){
        ArrayList<JiraTicket> newList = new ArrayList<>();
        for(int i = initialList.size()-1; i>=0; i--)
            newList.add(initialList.get(i));
        return newList;
    }
    public static ArrayList<Commit> reverseList2(ArrayList<Commit> initialList){
        ArrayList<Commit> newList = new ArrayList<>();
        for(int i = initialList.size()-1; i>=0; i--)
            newList.add(initialList.get(i));
        return newList;
    }
    private static Integer computeP(int wSize, int ticketPosition) {
        //P è calcolato come la media dei p dell'ultimo 1% di tickets (wSize)
        //Il calcolo è fatto tornando indietro dal ticket attuale in ticketPosition
        int p = 0;//la media sarà la somma di questo p tra i ticket precedenti fratto quelli contati
        int countedTickets=0;
        for(int i = 1; i <= wSize; i++){//Parto da 1 per poter tornare indietro di 1 la prima volta
            if(ticketPosition-i > 0){//Se non puoi tornare indietro esci e fai la media su quelli contati
                p+=tickets.get(ticketPosition-i).getP();//Somma il p del ticket precedente
                countedTickets++;//aggiungi uno ai ticket contati
            }
        }
        if (countedTickets==0)
            return p;
        else
            return p/countedTickets;//ritorna la media dei p sui ticket contati
    }
    public static void adjustFV(){
        //Questo metodo aggiorna la FV di JIRA con quella vera presente negli ultimi commit di un certo ticket
        for (JiraTicket ticket:tickets) {
            String ticketKey = ticket.getKey();
            for (Commit commit:ticket.getCommits()){
                String commitMessage = commit.getCommitObject().getFullMessage();
                if(commitMessage.contains(ticketKey)) {
                    //Devo prendere l'ultimo commit relativo al ticket
                    if(commit.getDate().isAfter(ticket.getFV().getVersionDate())){
                        ticket.setFV(commit.getRelease());
                    }
                }
            }
        }
    }
    public static void associateCommitToTickets() {
        //Questo metodo associa tutti i commit con la chiave di qualche ticket agli stessi, scartando i restanti
        for (JiraTicket ticket : tickets) {
            String ticketKey = ticket.getKey();
            for (Commit commit : commits) {
                String commitMessage = commit.getCommitObject().getFullMessage();
                if (commitMessage.contains(ticketKey)) {
                    ticket.addCommit(commit);//Associo il commit al ticket
                }
            }
        }
    }
    public static ArrayList<Commit> retrieveGitCommits(String projName) {
        //Questo metodo restituisce la lista dei commit eseguendo git log sulla cartella locale
        File gitDir = new File("/home/matteo/Documenti/Workspaces/ISW2/"+projName+"/"+projName.toLowerCase(Locale.ROOT)+"/.git");
        RepositoryBuilder builder = new RepositoryBuilder();
        Repository repository;
        ArrayList<Commit> commits = new ArrayList<>();
        try
        {
            repository = builder.setGitDir(gitDir).readEnvironment().findGitDir().build();
            Git git = new Git(repository);
            Iterable<RevCommit> logs = git.log().all().call();
            for(RevCommit rev : logs)
            {
                Commit c = new Commit(rev);
                LocalDateTime commitDate = c.getCommitObject().getAuthorIdent().getWhen().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
                c.setDate(commitDate);
                c.setRelease(releaseAfterDate(commitDate));
                releases.get(releases.indexOf(releaseAfterDate(commitDate))).addCommit(c);//Aggiunge il commit alla release
                commits.add(c);
            }
            git.close();
        }
        catch (IOException | GitAPIException e)
        {
            throw new IllegalStateException("git error");
        }
        return commits;
    }
    private static ArrayList<JiraTicket> retrieveJiraTickets(String projName) {
        int j;
        int i = 0;
        int total;
        int affectedVersions;
        ArrayList<JiraTicket> tickets = new ArrayList<>();
        //Get JSON API for closed bugs w/ AV in the project
        do {
            //Only gets a max of 1000 at a time, so must do this multiple times if bugs >1000
            j = i + 1000;
            String url = "https://issues.apache.org/jira/rest/api/2/search?jql=project=%22"
                    + projName + "%22AND%22issueType%22=%22Bug%22AND(%22status%22=%22closed%22OR"
                    + "%22status%22=%22resolved%22)AND%22resolution%22=%22fixed%22&fields=key,resolutiondate,versions,created&startAt="
                    + i + "&maxResults=" + j;
            JSONObject json = null;
            try {
                json = readJsonFromUrl(url);
                JSONArray issues = json.getJSONArray("issues");
                total = json.getInt("total");
                for (; i < total && i < j; i++) {
                    LocalDateTime creationDate = stringToDate(issues.getJSONObject(i%1000).getJSONObject("fields").get("created").toString());
                    LocalDateTime resolutionDate = stringToDate(issues.getJSONObject(i%1000).getJSONObject("fields").get("resolutiondate").toString());
                    LocalDateTime firstReleaseDate = releases.get(0).getVersionDate();
                    LocalDateTime lastReleaseDate = releases.get(releases.size()-1).getVersionDate();
                    if (resolutionDate.isBefore(firstReleaseDate) || resolutionDate.isAfter(lastReleaseDate) || creationDate.isAfter(lastReleaseDate))
                        continue;
                    JiraTicket ticket = new JiraTicket();
                    ticket.setKey(issues.getJSONObject(i%1000).get("key").toString());
                    ticket.setCreationDate(creationDate);
                    ticket.setResolutionDate(resolutionDate);
                    ticket.setOV(releaseAfterDate(creationDate));
                    ticket.setFV(releaseAfterDate(resolutionDate));

                    //Assegna anche IV e AV se presenti su JIRA
                    affectedVersions = issues.getJSONObject(i%1000).getJSONObject("fields").getJSONArray("versions").length();
                    if (affectedVersions > 0){
                        String injectedVersion = issues.getJSONObject(i%1000).getJSONObject("fields").getJSONArray("versions").getJSONObject(0).getString("name");
                        setAffectedVersions(ticket,injectedVersion);
                    }
                    tickets.add(ticket);
                }
            } catch (IOException | JSONException e) {
                throw new IllegalStateException("error");
            }
        } while (i < total);
        return tickets;
    }
    private static void setAffectedVersions(JiraTicket ticket, String iv){
        //Assegna la injected version come la prima affected riportata da Jira
        for(Release r : releases){
            if (iv.equals(r.getVersionNumber())){
                if(r.equals(ticket.getFV()))
                    return;
                ticket.setIV(r);
                ticket.addAV(r);
                break;
            }
        }
        if(ticket.getIV()!=null){
            //Se risulta dopo la opening mettila uguale alla opening, se risulta dopo la fixed scartala
            if(ticket.getIV().getVersionDate().isAfter(ticket.getOV().getVersionDate()))
                ticket.setIV(ticket.getOV());
            if(ticket.getIV().getVersionDate().isAfter(ticket.getFV().getVersionDate()))
                ticket.setIV(null);
            for (Release r : releases){
                if(r.equals(ticket.getFV()))
                    break;
                else{
                    if (r.getVersionDate().isAfter(ticket.getIV().getVersionDate()))
                        ticket.addAV(r);
                }
            }
        }
    }
    private static ArrayList<Release> getReleases(String projName){
        //Restituisce la lista di release che hanno una data di rilascio
        String url = "https://issues.apache.org/jira/rest/api/2/project/" + projName;
        JSONObject json;
        ArrayList<Release> releases = new ArrayList<>();
        try {
            json = readJsonFromUrl(url);
            JSONArray jiraVersions = json.getJSONArray("versions");
            for (int i = 0; i < jiraVersions.length(); i++) {
                if (jiraVersions.getJSONObject(i).get("released").toString().equals("true") && jiraVersions.getJSONObject(i).has("releaseDate")) {
                    Release r = new Release();
                    r.setVersionNumber(jiraVersions.getJSONObject(i).get("name").toString());
                    r.setVersionDate(stringToDate(jiraVersions.getJSONObject(i).get("releaseDate").toString()));
                    releases.add(r);
                }
            }
        } catch (IOException | JSONException e) {
            throw new IllegalStateException("error");
        }
        releases.sort((o1, o2) -> o1.getVersionDate().compareTo(o2.getVersionDate()));
        return releases;
    }
    private static LocalDateTime stringToDate(String s){
        //Funzione per convertire una stringa in una data (inventa l'ora se non è precisata)
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        LocalDateTime dateTime;
        if(s.contains("T"))
        {
            dateTime = LocalDateTime.parse(s.replace("T"," ").substring(0,16), formatter);
        }
        else
        {
            dateTime = LocalDateTime.parse(s + " 12:00", formatter);
        }
        return dateTime;
    }
    private static Release releaseAfterDate(LocalDateTime date){
        //Ritorna la release dopo una certa data, perchè la OV è la versione dopo l'apertura del ticket, così come la FV
        //Il controllo nel for non prende il caso in cui l'apertura del ticket è prima della prime release, controlla solo dopo
        //è per questo motivo che se non ritorna dal ciclo, di default ritorna la prima release. Inoltre l'if finale
        //contempla il caso in cui la data sia dopo ogni release, di default assegna l'ultima.
        Release currentRelease;
        Release nextRelease;
        Release retRelease = releases.get(0);
        for (int i = 0; i < releases.size()-1; i++)
        {
            currentRelease = releases.get(i);
            nextRelease = releases.get(i+1);
            if(date.isAfter(currentRelease.getVersionDate()) && date.isBefore(nextRelease.getVersionDate()))
                return nextRelease;
        }
        if (date.isAfter(releases.get(releases.size()-1).getVersionDate()))
            return releases.get(releases.size()-1);
        return retRelease;
    }
    private static void printTicketInfo(JiraTicket ticket){
        System.out.println("-----------------------------------------");
        System.out.println("Ticket ID - " + ticket.getKey() +"\ncreated - "+ticket.getCreationDate()+"\nsolved - "+ticket.getResolutionDate());
        System.out.println("OV - "+ticket.getOV().getVersionNumber() + "(released in "+ticket.getOV().getVersionDate()+")");
        System.out.println("FV - "+ticket.getFV().getVersionNumber() + "(released in "+ticket.getFV().getVersionDate()+")");
        System.out.print("IV - ");
        if(ticket.getIV()!=null)
            System.out.print(ticket.getIV().getVersionNumber());
        System.out.println();
        System.out.print("AV - [");
        if (ticket.getAV()!=null)
            for (Release av:ticket.getAV())
                System.out.print(" " + av.getVersionNumber());
        System.out.println(" ]");
    }
    private static String readAll(Reader rd) throws IOException {
        StringBuilder sb = new StringBuilder();
        int cp;
        while ((cp = rd.read()) != -1)
            sb.append((char) cp);
        return sb.toString();
    }
    public static JSONObject readJsonFromUrl(String url) throws IOException, JSONException {
        try (InputStream is = new URL(url).openStream()) {
            BufferedReader rd = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String jsonText = readAll(rd);
            return new JSONObject(jsonText);
        }
    }
}
