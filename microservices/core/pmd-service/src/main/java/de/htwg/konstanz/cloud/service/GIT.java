package de.htwg.konstanz.cloud.service;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.transport.RemoteSession;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.util.FS;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

public class GIT {

    boolean isValidRepository(URIish repoUri) {
        if (repoUri.isRemote()) {
            return isValidRemoteRepository(repoUri);
        } else {
            return isValidLocalRepository(repoUri);
        }
    }

    boolean isValidLocalRepository(URIish repoUri) {
        boolean result;

        try {
            result = new FileRepository(repoUri.getPath()).getObjectDatabase().exists();
        } catch (IOException e) {
            result = false;
        }

        return result;
    }



    public String downloadGITRepo(String gitRepo) throws InvalidRemoteException, TransportException, GitAPIException, MalformedURLException {
        /* Checkout Git-Repo */
        /* String Magic */
        String directoryName = gitRepo.substring(gitRepo.lastIndexOf("/"),
                gitRepo.length() - 1).replace(".", "_");
        String localDirectory = "repositories/" + directoryName + "_"
                + System.currentTimeMillis() + "/";

        /* Clone Command with jGIT */
        URL f = new URL(gitRepo);
        if (isValidRepository(new URIish(f))) {
            Git.cloneRepository().setURI(gitRepo)
                    .setDirectory(new File(localDirectory)).call();
        }

        /* Local Targetpath */
        return localDirectory;
    }

    boolean isValidRemoteRepository(URIish repoUri) {
        boolean result;

        if (repoUri.getScheme().toLowerCase().startsWith("http") ) {
            String path = repoUri.getPath();
            URIish checkUri = repoUri.setPath(path);

            InputStream ins = null;
            try {
                URLConnection conn = new URL(checkUri.toString()).openConnection();

                conn.setReadTimeout(1000);
                ins = conn.getInputStream();
                result = true;
            } catch (FileNotFoundException e) {
                System.out.println("File not Foud");
                result=false;
            } catch (IOException e) {
                System.out.println("IOException");
                result = false;
                e.printStackTrace();
            } finally {
                try {
                    ins.close();
                }
                catch (Exception e)
                { /* ignore */ }
            }
        } else if (repoUri.getScheme().toLowerCase().startsWith("ssh") ) {

            RemoteSession ssh = null;
            Process exec = null;

            try {
                ssh = SshSessionFactory.getInstance().getSession(repoUri, null, FS.detect(), 1000);
                exec = ssh.exec("cd " + repoUri.getPath() + "; git rev-parse --git-dir", 1000);

                Integer exitValue = null;
                do {
                    try {
                        exitValue = exec.exitValue();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } while (exitValue == null);

                result = exitValue == 0;

            } catch (Exception e) {
                result = false;

            } finally {
                try { exec.destroy(); } catch (Exception e) { /* ignore */ }
                try { ssh.disconnect(); } catch (Exception e) { /* ignore */ }
            }
        } else {
            // TODO need to implement tests for other schemas
            result = true;
        }

        return result;
    }
}