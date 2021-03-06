package de.htwg.konstanz.cloud.service;

import de.htwg.konstanz.cloud.model.Class;
import de.htwg.konstanz.cloud.model.Error;
import de.htwg.konstanz.cloud.util.*;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.swing.text.BadLocationException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.List;

class Checkstyle {
    private static final Logger LOG = LoggerFactory.getLogger(Checkstyle.class);

    private final OperatingSystemCheck oOperatingSystemCheck = new OperatingSystemCheck();

    private List<Class> lFormattedClassList;

    private final Util oUtil = new Util();

    private File oRepoDir;

    private final OwnJson oOwnJson = new OwnJson();

    private final Git oGit = new Git();

    private final Svn oSvn = new Svn();

    private final String sSvnServerIp;

    private final String sRuleSetPath;

    /**
     * Constructor to initialize the Svn server ip and ruleset path of checkstyle.
     * They are stored in the config file
     * @param sSvnServerIp - SVN Server ip address
     * @param sRuleSetPath - local path to the ruleset of checkstyle
     */
    public Checkstyle(String sSvnServerIp, String sRuleSetPath) {
        this.sSvnServerIp = sSvnServerIp;
        this.sRuleSetPath = sRuleSetPath;
    }

    /**
     * entry point for an incoming post request
     * @param gitRepository - given git repository from post request
     * @return - problems that checkstyle has found in a json file
     * @throws IOException - throw for the handling in CheckstyleService
     * @throws ParserConfigurationException - throw for the handling in CheckstyleService
     * @throws SAXException - throw for the handling in CheckstyleService
     * @throws GitAPIException - throw for the handling in CheckstyleService
     * @throws BadLocationException - throw for the handling in CheckstyleService
     */
    String startIt(String gitRepository) throws IOException, ParserConfigurationException,
            SAXException, GitAPIException, BadLocationException {
        lFormattedClassList = new ArrayList<>();
        long lStartTime = System.currentTimeMillis();
        JSONObject oJsonResult;
        String sResult;

        //checks if the post contains a repository of git or svn
        oJsonResult = determineVersionControlSystem(gitRepository, lStartTime);
        if (oRepoDir == null) {
            LOG.info("Error: Local Directory is null!");
        } else {
            FileUtils.deleteDirectory(oRepoDir);
        }

        //tests the result to avoid a nullpointer exception
        sResult = oUtil.checkJsonResult(oJsonResult);

        return sResult;
    }

    /**
     * checks if the given repository belongs to git or svn. After the checkout of the repository
     * this method executes the analysis of checkstyle
     * @param sRepoUrl - within POST request given repository url (svn or git)
     * @param lStartTime - start time of the service execution
     * @return - The generated JSON file with all problems pmd has found
     * @throws IOException - throw for the handling in CheckstyleService
     * @throws BadLocationException - throw for the handling in CheckstyleService
     * @throws GitAPIException - throw for the handling in CheckstyleService
     * @throws ParserConfigurationException - throw for the handling in CheckstyleService
     * @throws SAXException - throw for the handling in CheckstyleService
     */
    private JSONObject determineVersionControlSystem(String sRepoUrl, long lStartTime) throws
            IOException, BadLocationException, GitAPIException, ParserConfigurationException, SAXException {
        JSONObject oJson = null;
        String sLocalDir;
        String[] sLocalDirArray;
        StringBuilder oStringBuilder = new StringBuilder();

        LOG.info("Repository URL: " + sRepoUrl);
        //to run checkstyle, a .jar file is needed. If its not present locally, this method will download it automatically
        checkLocalCheckstyle();

        /* Svn Checkout */
        if (sRepoUrl.contains(this.sSvnServerIp)) {
            /* URL needs to start with HTTP:// */
            if (!sRepoUrl.startsWith("http://")) {
                oStringBuilder.append("http://");
            }
            /* remove the last / */
            if (sRepoUrl.endsWith("/")) {
                oStringBuilder.append(sRepoUrl.substring(0, sRepoUrl.length() - 1));
            } else {
                oStringBuilder.append(sRepoUrl);
            }

            LOG.info("Svn");
            //download the given svn repository and returns the locally path of it
            sLocalDir = oSvn.downloadSvnRepo(oStringBuilder.toString());
            /* Last Update Time (last parameter) of SVN is empty because it does not provide this information */
            // generate some parsable checkstyle data and run checkstyle for the locally stored svn repository
            oJson = checkStyle(generateCheckStyleData(sLocalDir), sRepoUrl, lStartTime, "");
            oRepoDir = new File(sLocalDir);
        }
        /* Git Checkout */
        else if (sRepoUrl.contains("github.com")) {
            LOG.info("Git");
            //download the given git repository and returns the locally path of it and the last updated time
            // [0] --> locally Path
            // [1] --> Last Update Time
            sLocalDirArray = oGit.downloadGitRepo(sRepoUrl);
            /* Last Update Time (last parameter) of the git repositry */
            // generate some parsable checkstyle data and run checkstyle for the locally stored git repository
            oJson = checkStyle(generateCheckStyleData(sLocalDirArray[0]), sRepoUrl, lStartTime, sLocalDirArray[1]);
            oRepoDir = new File(sLocalDirArray[0]);
        } else {
            LOG.info("Repository URL has no valid Svn/Git attributes. (" + sRepoUrl + ")");
        }

        return oJson;
    }

    /**
     * generates data for the CheckstyleService
     * @param localDirectory - Locale directory of the outchecked SVN or GIT repository
     * @return - A list with all directories and files
     */
    private List<List<String>> generateCheckStyleData(String localDirectory) {
        ArrayList<List<String>> list = new ArrayList<>();
        File mainDir;
        LOG.info("Local Directory: " + localDirectory);

        /* Structure according to the specifications */
        mainDir = oUtil.checkLocalSrcDir(localDirectory);

        /* List all files for CheckstyleService */
        if (mainDir.exists()) {
            File[] files = mainDir.listFiles();
            //fill list with relevant Data
            if (files != null) {
                for (File file : files) {
                    //Head-Dir
                    File[] filesSub = new File(file.getPath()).listFiles();
                    List<String> pathsSub = new ArrayList<>();

                    if (filesSub != null) {
                        //Class-Files
                        for (File aFilesSub : filesSub) {
                            if (aFilesSub.getPath().endsWith(".java")) {
                                pathsSub.add(aFilesSub.getPath());
                            }
                        }
                    }
                    if (!pathsSub.isEmpty()) {
                        list.add(pathsSub);
                    }
                }
            }
        }

        checkUnregularRepository(localDirectory, list);

        return list;
    }

    /**
     * If the directory structure of the given url is not like defined in the manual, this method
     * just gets all other java files without a correct assignment allocation
     * @param localDirectory - local directory that should be checked
     * @param list - list that contains all founded java files
     */
    private void checkUnregularRepository(String localDirectory, ArrayList<List<String>> list) {
        /* Other Structure Workaround */
        if (list.isEmpty()) {
            //Unregular Repo
            LOG.info("unregular repository");
            List<String> javaFiles = new ArrayList<>();
            list.add(oUtil.getAllJavaFiles(localDirectory, javaFiles));
        }
    }

    /**
     * checks if the .jar of checkstyle exists. If not the method will download it
     * @throws IOException - Error during download process of checkstyle.jar
     */
    private void checkLocalCheckstyle() throws IOException {
        //it is important to use the xx-all version of checkstyle! It contains additional libraries for a better usability
        final String sCheckstyleJar = "checkstyle-6.17-all.jar";
        final String sDownloadCheckStyleJar = "http://downloads.sourceforge.net/project/checkstyle/checkstyle/6.17/checkstyle-6.17-all.jar?r=https%3A%2F%2Fsourceforge.net%2Fp%2Fcheckstyle%2Factivity%2F%3Fpage%3D0%26limit%3D100&ts=1463416596&use_mirror=vorboss";
        File oFile = new File(sCheckstyleJar);
        ReadableByteChannel oReadableByteChannel;
        FileOutputStream oFileOutput;
        URL oUrl;

        if (oFile.exists()) {
            LOG.info("Checkstyle .jar already exists! (" + oFile.toString() + ")");
        } else {
            LOG.info("Checkstyle .jar does not exist, starting download");
            oUrl = new URL(sDownloadCheckStyleJar);
            oReadableByteChannel = Channels.newChannel(oUrl.openStream());
            oFileOutput = new FileOutputStream(sCheckstyleJar);
            oFileOutput.getChannel().transferFrom(oReadableByteChannel, 0, Long.MAX_VALUE);
        }
    }

    /**
     * executes checkstyle within the command line interface
     * @param lRepoList - List of all repositories that were stored locally
     * @param versionControlRepository - String that represents the analyzed repository (SVN or GIT)
     * @param lStartTime - Start time of the execution
     * @param sLastUpdateTime - Last update of the repository
     * @return - returns a JSON Object with all provided information of checkstyle
     * @throws ParserConfigurationException - Error while parsing xml document
     * @throws SAXException - Error within SAX XML Parser
     * @throws IOException - general io exception
     */
    private JSONObject checkStyle(List<List<String>> lRepoList, String versionControlRepository, long lStartTime,
                                  String sLastUpdateTime) throws ParserConfigurationException, SAXException, IOException {
        final String sCheckStylePath = "checkstyle-6.17-all.jar";
        JSONObject oJson;

		/* reduce the content of the repository list for the json creation */
        formatList(lRepoList);

        for (int nClassPos = 0; nClassPos < lFormattedClassList.size(); nClassPos++) {
            String sFullPath = lFormattedClassList.get(nClassPos).getFullPath();

            if (sFullPath.endsWith(".java")) {
                sFullPath = sFullPath.substring(0, sFullPath.length() - 5);
            }

            //builds the execution command of checkstyle for the command line interace
            String sCheckStyleCommand = "java -jar " + sCheckStylePath + " -c " + sRuleSetPath + " " + sFullPath
                    + ".java -f xml -o " + sFullPath + ".xml";
            LOG.info("Checkstyle execution path: " + sCheckStyleCommand);

            //execute it and validate the return code to get files which were not parsable by checkstyle
            int nReturnCode = oUtil.execCommand(sCheckStyleCommand);
            LOG.info("Process Return Code: " + nReturnCode);

            //compilable for checkstyle --> valid file
            if(nReturnCode == 0) {
                /* store Checkstyle Informationen in the global List */
                storeCheckstyleInformation(sFullPath + ".xml", nClassPos);
            }
            //uncompilable for checkstyle -->  invalid file
            else if(nReturnCode == -2){
                Error oError = new Error(-1, -1, "error", "FATAL ERROR: NO NORMAL TERMINATION OF THE "
                        + "CHECKSTYLE PROCESS! PLEASE CHECK THE JAVA FILE!!!", "");
                lFormattedClassList.get(nClassPos).getErrorList().add(oError);
                lFormattedClassList.get(nClassPos).incErrorType("error");
            }
        }

        /* generate a JSON File */
        oJson = oOwnJson.buildJson(versionControlRepository, lStartTime, sLastUpdateTime, lFormattedClassList);

        return oJson;
    }

    /**
     * removes unnecessary information
     * @param lRepoList - List with all given repositories that should be redurced
     */
    private void formatList(List<List<String>> lRepoList) {
        for (List<String> sRepoListInList : lRepoList) {
            Class oClass;
            for (String sRepo : sRepoListInList) {
                //splits the repository by the given file separator
                String[] sFullPathSplitArray = sRepo.split(oOperatingSystemCheck.getOperatingSystemSeparator());
                String sTmpExerciseName = sFullPathSplitArray[2];

                oClass = new Class(sRepo, sTmpExerciseName);

                lFormattedClassList.add(oClass);
            }
        }
    }

    /**
     * opens the stored xml file of checkstyle and iterates through all errors to read the attributes-value pairs
     * @param sXmlPath - xml file path the should be read
     * @param nClassPos - actual class position of the whole list --> to assign the founded errors correctly
     * @throws ParserConfigurationException - Error while parsing xml document
     * @throws SAXException - Error within SAX XML Parser
     * @throws IOException - general io exception
     */
    private void storeCheckstyleInformation(String sXmlPath, int nClassPos)
            throws ParserConfigurationException, SAXException, IOException {
        //it is important to define the coding of the xml file --> UTF-8
        InputStream inputStream = new FileInputStream(sXmlPath);
        Reader reader = new InputStreamReader(inputStream, "UTF-8");
        InputSource is = new InputSource(reader);
        is.setEncoding("UTF-8");

        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(is);

        //get a list of all xml nodes which were tagged by error to get addional information --> e.g. line number, column, ...
        NodeList nList = doc.getElementsByTagName("error");

        for (int nNodePos = 0; nNodePos < nList.getLength(); nNodePos++) {
            oOwnJson.readAndSaveAttributes(nClassPos, nList, nNodePos, lFormattedClassList);
        }
    }
}