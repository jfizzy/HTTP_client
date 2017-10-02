
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Set;
import java.util.Iterator;
import java.util.Map;
import java.util.Date;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Stack;

/**
 * UrlCache Class
 *
 * @author Majid Ghaderi
 * @version	1.1, Sep 30, 2016
 *
 */
public class UrlCache {

    private HashMap<String, String> catMap; // this is the catalog 
    //containing the url -> last-modified time values

    /**
     * Default constructor to initialize data structures used for caching/etc If
     * the cache already exists then load it. If any errors then throw
     * exception.
     *
     * @throws UrlCacheException if encounters any errors/exceptions
     */
    public UrlCache() throws UrlCacheException {
        try {

            File catalog = new File(System.getProperty("user.dir") + "/Cache/catalog.dat");
            catMap = new HashMap();
            if (catalog.exists()) {
                System.out.println("Catalog present - fetching contents...");
                BufferedReader reader = new BufferedReader(new FileReader(catalog));
                String text = null;

                int i = 0;
                while ((text = reader.readLine()) != null) {
                    // need to split line into url and Last-Modified
                    String url = text.substring(0, text.indexOf('('));
                    System.out.println(i + 1 + ") " + url);
                    i++;
                    String lastMod = text.substring(text.lastIndexOf(')') + 1);
                    catMap.put(url, lastMod);
                }
                System.out.println("Contents loaded.");
                reader.close();
            } else {
                System.out.println("No catalog present.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    /**
     * Downloads the object specified by the parameter url if the local copy is
     * out of date.
     * @author James MacIsaac
     * @param url	URL of the object to be downloaded. It is a fully qualified
     * URL.
     * @throws UrlCacheException if encounters any errors/exceptions
     */
    public void getObject(String url) throws UrlCacheException {
        // needs caching
        String[] urlTokens = parseUrl(url);
        if (urlTokens == null) {
            throw new UrlCacheException("Invalid Url");
        }
        String host = urlTokens[0], path = urlTokens[2];
        int port = 80;
        if (urlTokens[1] != null) {
            port = Integer.parseInt(urlTokens[1]); // use default port
        }
        fetchTCPObject(host, port, path);
        writeCatalog();
    }

    /**
     * Method to parse the contents of a url string and split them into host,
     * port, and path
     *
     * @author James MacIsaac
     * @param url
     * @return String[]
     * @throws UrlCacheException
     */
    private String[] parseUrl(String url) throws UrlCacheException {

// URL matching regex
        String urlRegex = "([a-z]+(:\\/\\/))?(([a-zA-Z0-9\\-]+\\.)+[a-z]+)(:[0-9]+)?\\/([a-zA-Z0-9+&@#%?=~_!-]+\\/)*[a-zA-Z0-9+&@#%?=~_!-]+(\\.[a-zA-Z0-9]+){1,1}";
        Pattern patt = Pattern.compile(urlRegex);
        Matcher matcher = patt.matcher(url);

        if (!matcher.matches()) {
            throw new UrlCacheException("Invalid Url");
        }

        // ignore protocols
        if (url.contains("://")) {
            url = url.substring(url.indexOf("://") + 3);
        }

        String host, port = null;
        String path;

        //get the host and port
        if (url.contains(":")) {
            host = url.substring(0, url.indexOf(':'));
            port = url.substring(url.indexOf(':') + 1, url.indexOf('/'));
        } else {
            host = url.substring(0, url.indexOf('/'));
        }
        //get the path
        path = url.substring(url.indexOf('/') + 1, url.length());

        String[] returnVal = new String[3];
        returnVal[0] = host;
        returnVal[1] = port;
        returnVal[2] = path;
        return returnVal;
    }

    /**
     * Method to handle conditionally downloading the object after evaluating
     * whether it has a stored version in the cache, then determining if the
     * object has been changed
     *
     * @param host
     * @param port
     * @param path
     * @throws UrlCacheException
     */
    private void fetchTCPObject(String host, int port, String path) throws UrlCacheException {
        InputStream iStream;
        PrintWriter oStream;
        Response response = null;
        try {
            Socket socket = new Socket(host, port);
            //bind the socket
            oStream = new PrintWriter(new DataOutputStream(socket.getOutputStream()));
            //socket data writer
            iStream = socket.getInputStream();
            //socket data reader
            System.out.println();
            System.out.println("REQUESTING OBJECT [/" + path + "] AT HOST [" + host + "] ON PORT [" + port + "]");
            System.out.println("%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%");
            System.out.println("REQUEST HEADER:");
            System.out.println("@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@");
            
            if (path == null) {
                oStream.println("GET / HTTP/1.1");
                System.out.println("GET / HTTP/1.1");
            } else {
                oStream.println("GET /" + path + " HTTP/1.1");
                System.out.println("GET /" + path + " HTTP/1.1");
            }
            oStream.println("Host: " + host);
            System.out.println("Host: " + host);

            String lm = null;
            if ((lm = getLMDateString(host + ":" + port + "/" + path)) != null) {
                oStream.println("If-Modified-Since: " + lm);
                System.out.println("If-Modified-Since: " + lm);
            }

            oStream.println("Accept-Language: en-us");
            System.out.println("Accept-Language: en-us");
            oStream.println("Accept-Encoding: gzip, deflate");
            System.out.println("Accept-Encoding: gzip, deflate");
            oStream.println("Connection: keep-alive");
            System.out.println("Connection: keep-alive");
            oStream.println(); // empty line to terminate request
            oStream.flush();
            //make the request
            System.out.println("@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@");
            System.out.println("SENT REQUEST TO SERVER");
            System.out.println("AWAITING RESPONSE FROM SERVER...");
            response = new Response(); // object to hold response contents
            response.setHeader(getResponseHeader(iStream));
            if (!response.hasHeader()) {
                throw new Exception("Response Header Error");
            }

            System.out.println("RESPONSE HEADER:");
            System.out.println("**********************************");
            String[] respH = response.getHeader();
            for (int i = 0; i < respH.length; i++) {
                System.out.println(respH[i]);
            }
            System.out.println("**********************************");
            System.out.println("%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%");
            System.out.println();
            int rStatus = response.checkStatus();
            if (!(rStatus >= 200)) // response indicated a serious issue
            {
                throw new Exception("Uncommon Status Code! (< 200)");
            }
            if (rStatus >= 400) // fnf
            {
                throw new Exception("File Not Found or Server Error");
            }
            if(rStatus == 301){ // moved permanently - can't download
                oStream.close();
                iStream.close();
                socket.close();
                throw new UrlCacheException("File Moved Permanently - Cannot Download");
            }
            if(rStatus == 304){
                // 304 - Not Modified - don't download
                oStream.close();
                iStream.close();
                socket.close();
                System.out.println("Cache version is up to date.");
                return;
            }
            if (rStatus == 200) { // 200 - download file
                // if here, need to update cache file and value!
                System.out.print("Downloading File...");
                response.setContent(getResponseContent(iStream));
                if (!response.hasContent()) {
                    throw new Exception("Response Content Error");
                }

                saveFile(host, path, response); // save file
                System.out.println("Done.");
                //close connections gracefully
                oStream.close();
                iStream.close();
                socket.close();
            }else{ // unhandled status code
                oStream.close();
                iStream.close();
                socket.close();
                throw new UrlCacheException("Odd Status Code - Not Downloading");
            }
        } catch (Exception e) {
            throw new UrlCacheException(e.getMessage());
        }
    }

    /**
     * Method which parses the response header from a given buffered reader on
     * an open TCP connection
     *
     * @author James MacIsaac
     * @param b
     * @return String[]
     */
    private String[] getResponseHeader(InputStream i) throws UrlCacheException {
        try {
            byte lastFour[] = new byte[5];
            lastFour[0] = 0;lastFour[1] = 0;lastFour[2] = 0;lastFour[3]=0;
            ByteArrayOutputStream bb = new ByteArrayOutputStream();
            String finish = "\r\n\r\n";
            int runCount = 0;
            while (true){
                lastFour[4] = (byte)i.read();
                
                if(runCount == 0){
                    lastFour[3] = lastFour[4];
                } else if (runCount == 1){
                    lastFour[2] = lastFour[3];
                    lastFour[3] = lastFour[4];
                } else if (runCount == 2){
                    lastFour[1] = lastFour[2];
                    lastFour[2] = lastFour[3];
                    lastFour[3] = lastFour[4];
                } else if (runCount == 3){
                    lastFour[0] = lastFour[1];
                    lastFour[1] = lastFour[2];
                    lastFour[2] = lastFour[3];
                    lastFour[3] = lastFour[4];
                } else {
                    bb.write(lastFour[0]);
                    lastFour[0] = lastFour[1];
                    lastFour[1] = lastFour[2];
                    lastFour[2] = lastFour[3];
                    lastFour[3] = lastFour[4];
                    String tmp = "" + (char)lastFour[0] + (char)lastFour[1] + (char)lastFour[2] + (char)lastFour[3];
                    if(tmp.compareTo(finish) == 0){
                        bb.write(lastFour[0]);
                        bb.write(lastFour[1]);
                        bb.write(lastFour[2]);
                        bb.write(lastFour[3]);
                        break;
                    }
                }
                runCount++;
            }
            bb.flush();
            
            byte header[] = bb.toByteArray();
            Stack<String> hStack = new Stack<String>();
            String hLine = "";
            for(int j = 0; j < header.length-2; j++){
                hLine += (char)header[j];
                if(hLine.charAt(hLine.length()-1) == '\n' && hLine.charAt(hLine.length()-2) == '\r'){
                    hLine = hLine.substring(0, hLine.length()-2);
                    hStack.push(hLine);
                    hLine = "";
                }
                  
            }
            
            String[] headerContent = new String[hStack.size()];
            for (int j = 0; j < headerContent.length; j++) {
                
                headerContent[headerContent.length - j - 1] = hStack.pop();
                }
            return headerContent;
        } catch (Exception e) {
            e.printStackTrace();
            throw new UrlCacheException("Error reading response header");
        }
    }

    /**
     * Method which parses the response content (file data) from a given 
     * input stream on the open TCP connection
     * 
     * @author James MacIsaac
     * @param iStream
     * @return byte[]
     * @throws UrlCacheException 
     */
    private byte[] getResponseContent(InputStream iStream) throws UrlCacheException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        byte[] data = new byte[16384];//build a buffer of 2^13 bytes
        try {
            int nRead;
            while ((nRead = iStream.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            buffer.flush();

            return buffer.toByteArray();
        } catch (Exception e) {
            throw new UrlCacheException("Error reading response content");
        }
    }

    /**
     * Method to save a downloaded file to the cache at it's specified location
     * given the path.
     * 
     * @author James MacIsaac
     * @param host
     * @param path
     * @param response
     * @throws UrlCacheException 
     */
    private void saveFile(String host, String path, Response response) throws UrlCacheException {
        try {
            File file = new File(System.getProperty("user.dir") + "/Cache/Files/" + host + "/" + path);
            if (!file.exists()) {
                File fileFolder = new File(System.getProperty("user.dir") + "/Cache/Files/" + host + "/" + path.substring(0, path.lastIndexOf('/')));
                fileFolder.mkdirs();
                file = new File(System.getProperty("user.dir") + "/Cache/Files/" + host + "/" + path);
            }
            FileOutputStream fos = new FileOutputStream(file, false);
            fos.write(response.getContent());
            fos.close();

            // make a catalog entry
            updateCatalogEntry(host, path, response);

        } catch (Exception e) {
            throw new UrlCacheException("Error saving downloaded file");
        }
    }

    /**
     * Method to generate a string which is used to store a hashmap key/value
     * pair in the catalog file
     * 
     * @author James MacIsaac
     * @param url
     * @param lm
     * @return String
     */
    private String makeCacheString(String url, String lm) {
        String ceStr = "";
        // add url
        ceStr += url;
        //add url/Last-Modified separator
        ceStr += "(*%*)";
        //add Last-Modified Timestamp
        ceStr += lm;
        return ceStr;
    }

    /**
     * Method to update a last-modified time for a mapping in the catalog.
     * This is run when an updated version of the file is downloaded from 
     * the server
     * 
     * @author James MacIsaac
     * @param host
     * @param path
     * @param r 
     */
    private void updateCatalogEntry(String host, String path, Response r) {
        boolean changed = false;
        Set s = catMap.entrySet();
        if (catMap.containsKey(host + "/" + path)) { // entry already exists
            catMap.replace(host + "/" + path, r.getRespLM());
        } else {
            catMap.put(host + "/" + path, r.getRespLM());
        }
    }

    /**
     * Method that writes the contents of the catalog hashmap to an 
     * external file
     * 
     * @author James MacIsaac
     * @throws UrlCacheException 
     */
    private void writeCatalog() throws UrlCacheException {

        try {
            File f = new File(System.getProperty("user.dir") + "/Cache/catalog.dat");

            if (!f.exists()) {
                f.getParentFile().mkdirs();
                f.createNewFile();
            }

            FileOutputStream fStream = new FileOutputStream(f, false); // false overwrites the file
            Set hm = catMap.entrySet();
            Iterator i = hm.iterator();
            while (i.hasNext()) {
                Map.Entry me = (Map.Entry) i.next();
                String key = me.getKey() + ""; // forcing the String type
                String value = me.getValue() + "";
                fStream.write(makeCacheString(key, value).getBytes());
                fStream.write("\n".getBytes()); // need to seperate by line
            }
            fStream.close();
        } catch (Exception e) {
            throw new UrlCacheException("Error writing catalog to file");
        }
    }

    /**
     * Returns the Last-Modified time associated with the object specified by
     * the parameter url.
     *
     * @author James MacIsaac
     * @param url URL of the object
     * @return the Last-Modified time in millisecond as in Date.getTime()
     * @throws UrlCacheException if the specified url is not in the cache, or
     * there are other errors/exceptions
     */
    public long getLastModified(String url) throws UrlCacheException {
        String[] urlTokens = parseUrl(url);
        url = urlTokens[0] + "/" + urlTokens[2];

        if (catMap.containsKey(url)) {
            return dateStringToLong(catMap.get(url));
        }
        throw new UrlCacheException("Error getting last modified time");
    }

    /**
     * Method to fetch the Last-modified time for a given url in the catalog
     * Similar to the getLastModified method but returns the value in the
     * SimpleDateTime format instead of milliseconds
     * 
     * @author James MacIsaac
     * @param url
     * @return String
     * @throws UrlCacheException 
     */
    private String getLMDateString(String url) throws UrlCacheException {
        String[] urlTokens = parseUrl(url);
        url = urlTokens[0] + "/" + urlTokens[2];

        if (catMap.containsKey(url)) {
            return catMap.get(url);
        }
        return null;
    }
    
    /**
     * Method which helps the getLastModified method by taking a Last-Modified
     * time string, and producing it's millisecond value
     * @param dateString
     * @return
     * @throws UrlCacheException 
     */
    private long dateStringToLong(String dateString) throws UrlCacheException{
        try {
            SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");
            Date d = format.parse(dateString);
            return d.getTime();
        } catch (ParseException p) {
            throw new UrlCacheException("Error parsing Last-Modified time");
        }
    }

    /**
     * Class which manages the contents of an HTTP Response.
     * Contains several methods to get and set its fields, as well as provide 
     * quick access to key parts of the response header. (Status Code and 
     * Last-Modified Time)
     */
    private class Response {

        private String[] header;
        private byte[] content;
        public String status;

        Response() {
            header = null;
            content = null;
        }

        public String getRespLM() {
            String lm = null;
            if (header != null) {
                for (int i = 0; i < header.length; i++) {
                    if (header[i].contains("Last-Modified: ")) {
                        lm = header[i];
                        break;
                    }
                }
                lm = lm.substring(lm.indexOf(' ') + 1);
            }
            return lm;
        }

        public int checkStatus() {
            int val;
            if ((val = Integer.parseInt(status.substring(status.indexOf(' ') + 1, status.indexOf(' ') + 4))) >= 200) {
                return val;
            } else {
                return -1;
            }
        }

        public String[] getHeader() {
            return header;
        }

        public void setHeader(String[] header) {
            this.header = header; // full header set
            status = header[0].substring(header[0].indexOf(' ')); // status set
        }

        public byte[] getContent() {
            return content;
        }

        public void setContent(byte[] content) {
            this.content = content;
        }

        public boolean hasHeader() {
            return header != null;
        }

        public boolean hasContent() {
            return content != null;
        }

    }

}
