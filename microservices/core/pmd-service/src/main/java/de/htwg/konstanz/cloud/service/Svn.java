package de.htwg.konstanz.cloud.service;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.text.BadLocationException;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;

class Svn {
    private static final Logger LOG = LoggerFactory.getLogger(Svn.class);

    private String sFileSeparator = "";

    String downloadSvnRepo(String svnLink) throws IOException, BadLocationException {
        //Default TargetPath
        return downloadSvnRepo(svnLink,null);
    }

    String downloadSvnRepo(String svnLink, String sPcdString) throws IOException, BadLocationException {
        OperatingSystemCheck oOperatingSystemCheck = new OperatingSystemCheck();
        sFileSeparator = oOperatingSystemCheck.getOperatingSystemSeparator();
        //Parameters to access SVN
        String local = "";
        //Systemvariables
        String name = System.getenv("SVN_USER");
        String password = System.getenv("SVN_PASSWORD");

        //Check credentials
        if((name == null) && (password == null)) {
            LOG.info("invalid VPN credentials");
        }
        else {
            /* Split URL at every Slash */
            String[] parts = svnLink.split("/");

            local = local + parts[parts.length - 1];
            if(sPcdString == null){
                local = "repositories" + sFileSeparator + local + "_" + System.currentTimeMillis() + sFileSeparator;
            }
            else {
                local = sPcdString + sFileSeparator + local + "_" + System.currentTimeMillis() + sFileSeparator;
            }
            //Create Local Directory-Path
            File dir1 = new File(local);
            if(dir1.mkdir()) {
                svnCheckout(svnLink, genAuthString(name, password), local);
            }
            }

        return local;
    }

    private String genAuthString(String name, String pass) {
        // HTTP Authentication
        String authString = name + ":" + pass;
        byte[] authEncBytes = Base64.encodeBase64(authString.getBytes());

        return new String(authEncBytes);
    }

    private void svnCheckout(String mainUrl, String authStringEnc, String localPath) throws
            IOException, BadLocationException {
        // Generate and open the URL Connection
        URL url = new URL(mainUrl);
        URLConnection urlConnection = url.openConnection();
        //Authorization
        urlConnection.setRequestProperty("Authorization", "Basic "
                + authStringEnc);
        // read HTML File
        BufferedReader br = new BufferedReader(new InputStreamReader(
                urlConnection.getInputStream()));
        // Generate Iterator to walk through the HTML File
        HTMLEditorKit editorKit = new HTMLEditorKit();
        HTMLDocument htmlDoc = new HTMLDocument();
        htmlDoc.putProperty("IgnoreCharsetDirective", Boolean.TRUE);
        editorKit.read(br, htmlDoc, 0);
        //Get Strings from HTML-TAG A
        HTMLDocument.Iterator iter = htmlDoc.getIterator(HTML.Tag.A);
        List<String> listValue = iterToList(iter);
        //Start Crawling
        for (String aListValue : listValue) {
            if (URLDecoder.decode(aListValue, "UTF-8").endsWith("/")) {
                // String Magic
                String[] parts = URLDecoder.decode(aListValue, "UTF-8").split("/");
                String localPathn = localPath + sFileSeparator + parts[parts.length - 1];
                // Create new Dir
                if(new File(localPathn).mkdir()) {
                    // start new logic for the located dir

                    svnCheckout(mainUrl + sFileSeparator + URLDecoder.decode(aListValue, "UTF-8"), authStringEnc,
                            localPathn);
                }
            } else {
                // download file
                downloadFile(mainUrl + sFileSeparator + URLDecoder.decode(aListValue, "UTF-8"), localPath
                        + sFileSeparator + URLDecoder.decode(aListValue, "UTF-8"), authStringEnc);
            }
        }
    }

    private List<String> iterToList(HTMLDocument.Iterator iter) {
        List<String> list = new ArrayList<>();
        // Get Headstructure of Svn and store it into List
        do {
            list.add(iter.getAttributes().getAttribute(HTML.Attribute.HREF)
                    .toString());
            iter.next();
        } while (iter.isValid());
        // Remove first and last Object because they contain no relevant data
        list.remove(0);
        list.remove(list.size() - 1);
        return list;
    }

    private void downloadFile(String urlString, String dest,
                              String authStringEnc) throws IOException {
        // Authenticate
        URL url = new URL(urlString);
        URLConnection urlConnection = url.openConnection();
        urlConnection.setRequestProperty("Authorization", "Basic "
                + authStringEnc);
        // Generate InputStream
        InputStream is = urlConnection.getInputStream();

        FileOutputStream outputStream = null;
        // Start to Download the File and save it locally
        try {
            File fi = new File(dest);
            outputStream = new FileOutputStream(fi);
            int read;
            byte[] bytes = new byte[1024];

            while ((read = is.read(bytes)) != -1) {
                outputStream.write(bytes, 0, read);
            }
        } catch (IOException e) {
            /* ignore */
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    /* ignore */
                }
            }
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    /* ignore */
                }
            }
        }

    }
}
