package org.deliverable1;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.revwalk.RevCommit;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Locale;


public class Main {
    static ArrayList<Release> releases;
    static ArrayList<JiraTicket> tickets;
    static ArrayList<Commit> commits;

    public static void main(String[] args) {
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
            printTicketInfo(ticket);
        }
    }
    public static ArrayList<JiraTicket> reverseList(ArrayList<JiraTicket> initialList){
        ArrayList<JiraTicket> newList = new ArrayList<>();
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
