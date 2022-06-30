package org.deliverable1;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.InitCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.FS;
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
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {
    static final String PROJECTNAME = "BOOKKEEPER";
    static ArrayList<Release> releases;
    static ArrayList<JiraTicket> tickets;
    static ArrayList<Commit> commits;
    static ArrayList<JavaFile> javaFiles;
    static Logger logger =  Logger.getLogger("PROGRAM_EVENT");
    static final String LINES_SPLITTING_FORMAT = "\r\n|\r|\n";
    static final String JAVAFILE_EXTENTION = ".java";
    static final String SEPARATOR = "/";
    static final String INITIAL_PATH = "/home/matteo/Documenti/Workspaces/ISW2/"+PROJECTNAME;
    static final String PATH = INITIAL_PATH + SEPARATOR + PROJECTNAME.toLowerCase()+"/.git";
    static final String REPO_PATH = "https://github.com/apache/"+PROJECTNAME.toLowerCase()+".git";

    public static void main(String[] args) throws IOException {

        //---SEZIONE ZERO: LOCAL REPOSITORY INITIALIZATION
        cloneRepository();

        //---PRIMA SEZIONE: DATA RETRIEVAL
        //Ottieni la lista di tutte le versioni del progetto
        logger.log(Level.INFO," ... Getting "+PROJECTNAME+" releases");
        releases = getReleases();
        //Ottieni la lista di tutti i ticket di Jira
        logger.log(Level.INFO," ... Retrieving Jira tickets");
        tickets = retrieveJiraTickets();
        //Ottieni la lista di tutti i commit di Git
        logger.log(Level.INFO," ... Checking Commits");
        commits = retrieveGitCommits();
        //Associa i commit ai tickets
        associateCommitToTickets();
        //Correggi le FV di Jira con le informazioni trovate nei commit
        adjustFV();
        //Imposta le IV sfruttando le informazioni di Jira o usando il proportion
        adjustIV();
        logger.log(Level.INFO, "Project issues analyzed correctly.");

        //---SECONDA SEZIONE: CSV GENERATION
        //Genera la lista di tutte le classi Java toccate
        logger.log(Level.INFO, "Retrieving class names...");
        javaFiles = createJavaFilesList();
        //Calcola e imposta le feature delle classi Java solo per la prima metà delle release
        logger.log(Level.INFO,"Computing metrics... (this will take a while)");
        computeMetrics();
        //Genera il CSV con i risultati
        generateCSV();
        logger.log(Level.INFO, "Done.");
    }
    private static void cloneRepository() {
        //Metodo per la creazione della repository locale del progetto
        try {
            if (!RepositoryCache.FileKey.isGitRepository(new File(PATH), FS.DETECTED)) {
                logger.log(Level.INFO,PROJECTNAME+ " local repository not found.");
                logger.log(Level.INFO,"Creating a project local repository...");
                Git.cloneRepository().setURI(REPO_PATH).setDirectory(new File(PATH.substring(0,PATH.length()-5))).call();
                logger.log(Level.INFO,"Copy done successfully.");
            }
        } catch (GitAPIException e) {
            logger.log(Level.INFO,"Error in Git services.");
        }
    }
    private static void generateCSV() throws IOException {
        //Generazione del CSV finale
        String outName = PROJECTNAME + "_OutputMetrics.csv";
        String b;

        try(FileWriter fileWriter = new FileWriter(outName)) {
            fileWriter.append("VERSION_INDEX;FILE_NAME;LOC;LOC_ADDED;MAX_LOC_ADDED;AVG_LOC_ADDED;NR;NFIX;AUTHORS;CHURN;MAX_CHURN;BUGGYNESS");
            fileWriter.append("\n");
            for (Release r : releases.subList(0,releases.size()/2)) {
                for(JavaFile f : r.getJavaFiles()) {
                    if(f.isBuggy())
                        b = "Yes";
                    else
                        b = "No";
                    fileWriter.append(String.valueOf(releases.indexOf(r)+1));
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
        } catch (IOException e) {
            throw new IOException(e);
        }
    }
    private static void computeMetrics() {
        //Calcolo delle metriche per ogni file, eseguito release dopo release (solo la prima metà)
        for(int i = 1; i<= releases.size()/2; i++){
            setJavaFilesInfo(releases.get(i));
        }
        checkAffectedVersionsForBuggyness();
    }
    private static void checkAffectedVersionsForBuggyness() {
        //Aggiunge alle classi buggy trovate le informazioni sui bug prese da Jira e dal proportion (AV)
        for(JiraTicket t : tickets){
            for(Commit c : t.getCommits()){
                for (JavaFile f : c.getJavaFiles()){
                    for(Release r : t.getAV()){
                        if(releases.indexOf(r) == f.getVersionIndex()) {
                            //La classe coinvolta nel bug di un ticket è buggy in tutte le sue AV
                            f.setBuggyness(true);
                        }
                    }
                }
            }
        }
    }
    private static void setJavaFilesInfo(Release r) {
        //A partire dalla lista di classi imposto in questo metodo le informazioni per le metriche
        Path repoPath = Paths.get(PATH);
        InitCommand init = Git.init();
        init.setDirectory(repoPath.toFile());
        try (Git git = Git.open(repoPath.toFile()))
        {
            Release prevRelease = releases.get(releases.indexOf(r)-1);
            for (Commit commit : prevRelease.getCommits())//Prendi i commit della versione precedente
            {
                Repository repository = git.log().all().getRepository();
                TreeWalk tw = initializeTreeWalk(repository,commit);
                //Analizza le classi toccate nella versione precedente per poter fare dei confronti con l'attuale
                searchJavaFiles(tw,repository,commit,prevRelease);
            }
        }
        catch (IOException e) {
            e.getMessage();
        }
    }
    private static void searchJavaFiles(TreeWalk tw, Repository repository, Commit commit,Release r) throws IOException {
        //Metodo per ricercare file java
        try
        {
            //Se la classe avrà subito delle modifiche (diffs di tipo MODIFY) potrebbe essere buggy
            setBuggyness(r,commit);
            while (tw.next())
            {
                if ((tw.getPathString().contains(JAVAFILE_EXTENTION)))
                {
                    //La classe compare in un commit, quindi ha subito delle modifiche e bisogna calcolarne le features
                    setInfo(r,tw,repository,commit);
                }
            }
            //Ottieni le modifiche apportate dal commit
            List<DiffEntry> diffs = getDiffs(commit.getCommitObject(), repository);
            if (diffs != null) {
                //Conta il numero di revisioni
                checkNR(diffs,r,commit);
            }
        } catch (IOException e) {
            throw new IOException(e);
        }
    }
    private static void setBuggyness(Release r, Commit rev) throws IOException {
        //Questo metodo viene eseguito per ogni commit
        for (JiraTicket ticket : tickets) {
            //Per ogni commit vado a controllare tutti i ticket che descrivono bug con delle AV
            if(ticket.getAV()!=null) {
                String key = ticket.getKey();
                if(rev.getCommitObject().getFullMessage().contains(key)) {
                    //Se il commit riguarda un bug che affligge delle versioni
                    FileRepositoryBuilder repositoryBuilder = new FileRepositoryBuilder();
                    Repository repository = repositoryBuilder.setGitDir(new File(PATH)).readEnvironment().findGitDir().setMustExist(true).build();
                    List<DiffEntry> diffs = getDiffs(rev.getCommitObject(), repository);
                    //Prendo le modifiche apportate (qualora ci siano) e le analizzo
                    if (diffs != null){
                        analyzeDiffs(diffs,r);
                    }
                }
            }
        }
    }
    private static void analyzeDiffs(List<DiffEntry> diffs, Release r) {
        //Metodo per analizzare le modifiche apportate da un commit
        for (DiffEntry diff : diffs) {
            //Per ogni modifica apportata a una classe
            String type = diff.getChangeType().toString();
            if ((type.equals("DELETE") || type.equals("MODIFY")) && diff.toString().contains(JAVAFILE_EXTENTION)) {
                String fileName;
                //Qualora si tratta di una cancellazione bisogna considerare il nome precedente
                if (diff.getChangeType() == DiffEntry.ChangeType.DELETE)
                    fileName = diff.getOldPath();
                else
                    fileName = diff.getNewPath();
                //A questo punto vado ad analizzare la singola modifica
                checkCommAndFiles(fileName, r);
            }
        }
    }
    private static void checkCommAndFiles(String fileName,Release r) {
        //Metodo per controllare se la modifica apportata tocca una classe d'interesse
        for (JavaFile javaFile : r.getJavaFiles()) {
            //Controllo per ogni file
            if(javaFile.getClassName().equals(fileName)) {
                //Una volta trovata una classe devo controllare che la versione e il nome corrispondano
                checkEquals(javaFile, r);
            }
        }
    }
    private static void checkEquals(JavaFile javaFile, Release r) {
        //Controlla se la versione e il nome del file toccato in una diff corrispondano a una classe d'interesse
        for(JavaFile file : r.getJavaFiles()) {
            if(file.getClassName().equals(javaFile.getClassName()) && file.getVersionIndex().equals(releases.indexOf(r.getCommits().get(0).getRelease()))) {
                //Se il nome e la versione corrispondono, allora la classe è buggy
                file.setBuggyness(true);
            }
        }
    }
    private static void checkNR(List<DiffEntry> diffs, Release r, Commit commit) {
        //Questo metodo conta il numero di revisioni per il calcolo delle metriche
        for (DiffEntry diff : diffs) {
            String type = diff.getChangeType().toString();
            if (diff.toString().contains(JAVAFILE_EXTENTION)) {
                //Per ogni classe modificata
                String fileName;
                //Prendendo il path di prima se la modifica è una cancellazione
                if (type.equals("DELETE"))
                    fileName = diff.getOldPath();
                else
                    fileName = diff.getNewPath();
                for(JavaFile file : r.getJavaFiles()) {
                    if(file.getClassName().equals(fileName) && file.getVersionIndex().equals(releases.indexOf(commit.getRelease()))) {
                        //Aumento le revisioni di un JavaFile se nome e versione corrispondono
                        file.increaseNR();
                    }
                }
            }
        }
    }
    private static void setInfo(Release r, TreeWalk tw, Repository repository, Commit commit) throws IOException {
        //Metodo dedito all'impostazione di metriche
        for (JavaFile file : r.getJavaFiles()) {
            if(file.getClassName().equals(tw.getPathString())) {
                //Per ogni classe associo il file al commit e ne calcolo le metriche
                commit.addJavaFile(file);
                setLOC(tw,repository,file);
                if(commit.getCommitObject().getFullMessage().contains("fix")
                        && !commit.getCommitObject().getFullMessage().contains("prefix")
                        && !commit.getCommitObject().getFullMessage().contains("postfix"))
                    file.increaseNFIX();
                String authName = commit.getCommitObject().getAuthorIdent().getName();
                if(!file.getAuthors().contains(authName))
                    file.addAuthor(authName);
            }
        }
    }
    private static void setLOC(TreeWalk tw, Repository repository, JavaFile f) throws IOException {
        //Imposto le metriche relative a linee di codice
        ObjectId objectId = tw.getObjectId(0);
        ObjectLoader loader;
        try {
            loader = repository.open(objectId);
            String content = new String(loader.getBytes(), StandardCharsets.US_ASCII);
            String[] lines = content.split(LINES_SPLITTING_FORMAT);
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
        } catch (IOException e) {
            throw new IOException(e);
        }
    }
    private static Integer[] setAddedAndRemovedLines(String presentTXT,String previousTXT, Integer addedLines,Integer removedLines) {
        //Calcolo il vettore delle linee aggiunte e rimosse in una classe da un commit
        ArrayList <String> text = new ArrayList<>();
        ArrayList <String> prevText = new ArrayList<>();
        Collections.addAll(text, presentTXT.split(LINES_SPLITTING_FORMAT));
        Collections.addAll(prevText, previousTXT.split(LINES_SPLITTING_FORMAT));
        //Ho acquisito la lista di stringhe del testo della stessa classe in due revisioni diverse
        for(String s1:prevText)
            if (!text.contains(s1))
                removedLines++;
        for(String s2:text)
            if (!prevText.contains(s2))
                addedLines++;
        //Ora posso mettere in un vettore le righe aggiunte e rimosse rispetto a prima
        Integer[] v = new Integer[2];
        v[0] = addedLines;
        v[1] = removedLines;
        return v;
    }
    private static List<DiffEntry> getDiffs(RevCommit commit, Repository repository) throws IOException {
        //Metodo per l'ottenimento delle diffs, ovvero le modifiche apportate da un commit
        List<DiffEntry> diffs;
        DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE);
        df.setRepository(repository);
        df.setDiffComparator(RawTextComparator.DEFAULT);
        df.setContext(0);
        df.setDetectRenames(true);
        if (commit.getParentCount() != 0) {
            RevCommit parent = (RevCommit) commit.getParent(0).getId();
            diffs = df.scan(parent.getTree(), commit.getTree());
        }
        else {
            RevWalk rw = new RevWalk(repository);
            ObjectReader reader = rw.getObjectReader();
            diffs = df.scan(new EmptyTreeIterator(), new CanonicalTreeParser(null, reader, commit.getTree()));
            rw.close();
        }
        df.close();
        return diffs;
    }
    private static TreeWalk initializeTreeWalk(Repository repository, Commit commit) throws IOException {
        //Questo metodo ottiene il treeWalk per esplorare i commit
        TreeWalk tw = new TreeWalk(repository);
        tw.setRecursive(true);
        RevCommit commitToCheck = commit.getCommitObject();
        try {
            tw.addTree(commitToCheck.getTree());
            for (RevCommit parent : commitToCheck.getParents()) {
                tw.addTree(parent.getTree());
            }
        } catch (IOException e) {
            throw new IOException(e);
        }
        return tw;
    }
    private static ArrayList<JavaFile> createJavaFilesList() throws IOException {
        //Questo metodo crea la lista delle classi Java
        Path repoPath = Paths.get(PATH);
        ArrayList<JavaFile> javaFiles = new ArrayList<>();
        ArrayList<String> javaFilesNamesList = new ArrayList<>();
        InitCommand init = Git.init();
        init.setDirectory(repoPath.toFile());
        try (Git git = Git.open(repoPath.toFile())) {
            for (Commit commit : commits) {
                //Prendi il treeWalk per ogni commit
                ObjectId treeId = commit.getCommitObject().getTree();
                try (TreeWalk treeWalk = new TreeWalk(git.getRepository())) {
                    treeWalk.reset(treeId);
                    treeWalk.setRecursive(true);
                    while (treeWalk.next()) {
                        //Se trovi delle classi che non sono ancora state aggiunte alla lista aggiungile
                        if ((treeWalk.getPathString().endsWith(JAVAFILE_EXTENTION)) && !javaFilesNamesList.contains(commit.getRelease().getVersionNumber()+treeWalk.getPathString())) {
                            //Le classi possono avere stesso nome ma versioni differenti ovviamente
                            JavaFile f = new JavaFile(treeWalk.getPathString(), releases.indexOf(commit.getRelease()));
                            javaFilesNamesList.add(commit.getRelease().getVersionNumber()+treeWalk.getPathString());
                            f.setVersionIndex(releases.indexOf(commit.getRelease()));
                            javaFiles.add(f);
                            releases.get(releases.indexOf(commit.getRelease())).addJavaFile(f);//Aggiungo il javaFile alla release
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new IOException(e);
        }
        return javaFiles;
    }
    private static void adjustIV() {
        //Metodo per l'impostazione delle IV
        int windowSize = tickets.size()/100;
        int p;
        int predictedIV;
        int fv;
        int ov;
        int iv;
        int num;
        int den;
        for (JiraTicket ticket : reverseList(tickets)){
            if(ticket.getIV()==null){
                /*
                Se l'IV non è disponibile significa che Jira non riportava AV.
                A questo punto quindi utilizzo il proportion per trovare tutte le IV sconosciute.
                Sulla base di tutte le IV trovate in questo modo verranno poi ricalcolate le AV.
                MOVING WINDOW: calcolo p come la media dell'ultimo 1% di difetti risolti
                */
                if(ticket.getFV().equals(ticket.getOV())) {
                    ticket.setIV(ticket.getFV());
                    ticket.setP(1);
                }
                else {
                    p = computeP(windowSize,tickets.indexOf(ticket));
                    fv = releases.indexOf(ticket.getFV());
                    ov = releases.indexOf(ticket.getOV());
                    predictedIV = fv -(p*(fv-ov));
                    ticket.setIV(releases.get(predictedIV));
                    ticket.setP(p);
                }
                //Ottenuto l'IV calcolo le nuove AV
                setAffectedVersions(ticket,ticket.getIV().getVersionNumber());
            }
            else{
                /*
                 * Se l'IV è disponibile significa che bisogna calcolare solo la P, per poterla
                 * utilizzare nei ticket successivi.
                 */
                fv = releases.indexOf(ticket.getFV());
                ov = releases.indexOf(ticket.getOV());
                iv = releases.indexOf(ticket.getIV());
                num = fv - iv;
                den = fv - ov;
                if (num == 0)
                    num = 1;
                if (den == 0)
                    den = 1;
                ticket.setP(num/den);
                //Le AV qui non bisogna calcolarle dato che l'IV appunto era presente.
            }
        }
    }
    private static ArrayList<JiraTicket> reverseList(ArrayList<JiraTicket> initialList) {
        //Metodo per invertire la lista di ticket, necessario per preservare l'ordine
        ArrayList<JiraTicket> newList = new ArrayList<>();
        for(int i = initialList.size()-1; i>=0; i--)
            newList.add(initialList.get(i));
        return newList;
    }
    private static Integer computeP(int wSize, int ticketPosition) {
        //Metodo per il calcolo progressivo del proportion
        int countedTickets = 0;
        //p è calcolato come la media dei p dell'ultimo 1% di tickets (wSize), tornando indietro dal ticket attuale (ticketPosition)
        int p = 0;
        //Parto da uno in quanto i ticket vanno contati da uno indietro in poi per tutta la wSize
        for(int i = 1; i <= wSize; i++) {
            //Se non puoi tornare indietro esci e fai la media su quelli contati
            if(ticketPosition-i > 0) {
                //Somma il p del ticket precedente
                p+=tickets.get(ticketPosition-i).getP();
                countedTickets++;
            }
        }
        if (countedTickets == 0)
            return 1;
        else
            return p/countedTickets;//ritorna la media dei p sui ticket contati
    }
    private static void adjustFV() {
        //Questo metodo aggiorna la FV di JIRA con quella vera presente negli ultimi commit di un certo ticket
        for (JiraTicket ticket:tickets) {
            String ticketKey = ticket.getKey();
            for (Commit commit:ticket.getCommits()) {
                String commitMessage = commit.getCommitObject().getFullMessage();
                if(commitMessage.contains(ticketKey) && commit.getDate().isAfter(ticket.getFV().getVersionDate())) {
                    //Devo prendere l'ultimo commit relativo al ticket
                    ticket.setFV(commit.getRelease());
                }
            }
        }
    }
    private static void associateCommitToTickets() {
        //Questo metodo associa tutti i commit con la chiave di qualche ticket agli stessi, scartando i restanti
        for (JiraTicket ticket : tickets) {
            String ticketKey = ticket.getKey();
            for (Commit commit : commits) {
                String commitMessage = commit.getCommitObject().getFullMessage();
                if (commitMessage.contains(ticketKey)) {
                    //Associo il commit al ticket
                    ticket.addCommit(commit);
                }
            }
        }
    }
    private static ArrayList<Commit> retrieveGitCommits() throws IOException {
        //Questo metodo restituisce la lista dei commit eseguendo git log sulla cartella locale
        File gitDir = new File(PATH);
        RepositoryBuilder builder = new RepositoryBuilder();
        Repository repository;
        ArrayList<Commit> commits = new ArrayList<>();
        try {
            repository = builder.setGitDir(gitDir).readEnvironment().findGitDir().build();
            Git git = new Git(repository);
            Iterable<RevCommit> logs = git.log().all().call();
            for(RevCommit rev : logs) {
                Commit c = new Commit(rev);
                LocalDateTime commitDate = c.getCommitObject().getAuthorIdent().getWhen().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
                c.setDate(commitDate);
                c.setRelease(releaseAfterDate(commitDate));
                releases.get(releases.indexOf(releaseAfterDate(commitDate))).addCommit(c);//Aggiunge il commit alla release
                commits.add(c);
            }
            git.close();
        } catch (IOException | GitAPIException e) {
            throw new IOException(e);
        }
        return commits;
    }
    private static ArrayList<JiraTicket> retrieveJiraTickets() throws IOException {
        //Metodo per recuperare la lista di ticket da Jira eseguendo una query
        int j;
        int i = 0;
        int total;
        int affectedVersions;
        String fields = "fields";
        String versions = "versions";
        ArrayList<JiraTicket> tickets = new ArrayList<>();
        do {
            j = i + 1000;
            String url = "https://issues.apache.org/jira/rest/api/2/search?jql=project=%22"
                    + PROJECTNAME + "%22AND%22issueType%22=%22Bug%22AND(%22status%22=%22closed%22OR"
                    + "%22status%22=%22resolved%22)AND%22resolution%22=%22fixed%22&fields=key,resolutiondate,versions,created&startAt="
                    + i + "&maxResults=" + j;
            JSONObject json;
            try {
                json = readJsonFromUrl(url);
                JSONArray issues = json.getJSONArray("issues");
                total = json.getInt("total");
                //Eseguita la query aggiungo un ticket alla volta alla lista
                for (; i < total && i < j; i++) {
                    //Assegna ogni informazione trovata su Jira all'oggetto ticket
                    LocalDateTime creationDate = stringToDate(issues.getJSONObject(i%1000).getJSONObject(fields).get("created").toString());
                    LocalDateTime resolutionDate = stringToDate(issues.getJSONObject(i%1000).getJSONObject(fields).get("resolutiondate").toString());
                    LocalDateTime firstReleaseDate = releases.get(0).getVersionDate();
                    LocalDateTime lastReleaseDate = releases.get(releases.size()-1).getVersionDate();
                    if (resolutionDate.isBefore(firstReleaseDate) || resolutionDate.isAfter(lastReleaseDate) || creationDate.isAfter(lastReleaseDate))
                        continue;
                    JiraTicket ticket = new JiraTicket();
                    ticket.setKey(issues.getJSONObject(i%1000).get("key").toString());
                    ticket.setOV(releaseAfterDate(creationDate));
                    ticket.setFV(releaseAfterDate(resolutionDate));

                    //Assegna anche IV e AV se presenti su JIRA
                    affectedVersions = issues.getJSONObject(i%1000).getJSONObject(fields).getJSONArray(versions).length();
                    if (affectedVersions > 0){
                        String injectedVersion = issues.getJSONObject(i%1000).getJSONObject(fields).getJSONArray(versions).getJSONObject(0).getString("name");
                        setAffectedVersions(ticket,injectedVersion);
                    }
                    tickets.add(ticket);
                }
            } catch (IOException | JSONException e) {
                throw new IOException(e);
            }
        } while (i < total);
        return tickets;
    }
    private static void setAffectedVersions(JiraTicket ticket, String iv) {
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
            //Se risulta dopo la opening mettila uguale alla suddetta, mentre se risulta dopo la fixed scartala
            correctJiraIV(ticket);
        }
    }
    private static void correctJiraIV(JiraTicket ticket) {
        //Metodo per controllare la validità dell'IV di Jira
        if(ticket.getIV().getVersionDate().isAfter(ticket.getOV().getVersionDate())) {
            //Se l'IV risulta dopo la OV ponila uguale a quest'ultima
            ticket.setIV(ticket.getOV());
        }
        if(ticket.getIV().getVersionDate().isAfter(ticket.getFV().getVersionDate())){
            //Se l'IV risulta dopo la FV scartala
            ticket.setIV(null);
        }
        for (Release r : releases){
            //Aggiungi le AV una per una fino a che non arrivi alla Fixed
            if(r.equals(ticket.getFV()))
                break;
            else {
                if (r.getVersionDate().isAfter(ticket.getIV().getVersionDate()))
                    ticket.addAV(r);
            }
        }
    }
    private static ArrayList<Release> getReleases() throws IOException {
        //Restituisce la lista di release che hanno una data di rilascio
        String url = "https://issues.apache.org/jira/rest/api/2/project/" + PROJECTNAME;
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
            throw new IOException(e);
        }
        releases.sort(Comparator.comparing(Release::getVersionDate));
        return releases;
    }
    private static LocalDateTime stringToDate(String s) {
        //Funzione per convertire una stringa in una data (inventa l'ora se non è precisata)
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        LocalDateTime dateTime;
        if(s.contains("T")) {
            dateTime = LocalDateTime.parse(s.replace("T"," ").substring(0,16), formatter);
        }
        else {
            dateTime = LocalDateTime.parse(s + " 12:00", formatter);
        }
        return dateTime;
    }
    private static Release releaseAfterDate(LocalDateTime date) {
        /*Ritorna la release dopo una certa data in quanto la OV è la versione dopo l'apertura del ticket, così come la FV.
        Il controllo nel for non prende il caso in cui l'apertura del ticket è prima della prime release, controlla solo dopo:
        è per questo motivo che se non ritorna dal ciclo, di default ritorna la prima release. Inoltre il controllo finale
        contempla il caso in cui la data sia dopo ogni release, di default assegna l'ultima.*/
        Release currentRelease;
        Release nextRelease;
        Release retRelease = releases.get(0);
        for (int i = 0; i < releases.size()-1; i++) {
            currentRelease = releases.get(i);
            nextRelease = releases.get(i+1);
            if(date.isAfter(currentRelease.getVersionDate()) && date.isBefore(nextRelease.getVersionDate()))
                return nextRelease;
        }
        if (date.isAfter(releases.get(releases.size()-1).getVersionDate()))
            return releases.get(releases.size()-1);
        return retRelease;
    }
    private static String readAll(Reader rd) throws IOException {
        //Metodo per leggere le informazioni dal JSON
        StringBuilder sb = new StringBuilder();
        int cp;
        while ((cp = rd.read()) != -1)
            sb.append((char) cp);
        return sb.toString();
    }
    private static JSONObject readJsonFromUrl(String url) throws IOException, JSONException {
        //Metodo per recuperare l'oggetto JSON letto dalla query
        try (InputStream is = new URL(url).openStream()) {
            BufferedReader rd = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String jsonText = readAll(rd);
            return new JSONObject(jsonText);
        }
    }
}
