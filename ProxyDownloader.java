import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Ege Çenberci
 * 21801618
 * CS-421 Programming Assignment 1 - Simple Proxy Server
 * Spring 2023
 */
public class ProxyDownloader {
    public static final int HTTP_PORT = 80;
    public static final int CONNECT_PORT = 443;
    public static void main(String[] args) throws IOException {
        int port=0;
        if (args.length < 1) {
            System.err.println("Usage: java ProxyDownloader <port>");
            System.exit(1);
        }
        else if(args.length > 1){
            System.err.println("Too many arguments. Usage: java ProxyDownloader <port>");
            System.exit(1);
        }
        else{
            port = Integer.parseInt(args[0]);
        }

        ServerSocket serverSocket = new ServerSocket(port);

        while (!serverSocket.isClosed()) {
            Socket clientSocket = serverSocket.accept();
            
            InputStream clientIn = clientSocket.getInputStream();
            OutputStream clientOut = clientSocket.getOutputStream();

            // Read request from client
            byte[] buffer = new byte[4096]; // 4096 bytes is therequest length that can be read in a single time
            int readBytesNum = clientIn.read(buffer);
            String request = new String(buffer, 0, readBytesNum);

            // Extract host from the message
            boolean downloadFile = false; // bool to get around file downloads in firefox's auto requests
            String host;
            String fullURL;
            String fileName = "";
            String requestType = request.substring(0, request.indexOf(" "));
            String firstline = request.substring(0,request.indexOf("\n"));

            // Checks to avoid processing unneccessary automatic requests
            boolean skipSteps = false;
            boolean connectSkip = false;
            if (firstline.contains("mozilla") | firstline.contains("firefox") | firstline.contains("r3.o.lencr.org") | firstline.contains("ocsp.digicert.com") | firstline.contains("ocsp.pki.goog") ) {
                skipSteps = true;
            }
            if (!skipSteps) {
                int firstSpace = request.indexOf(" ");
                int secondSpace = request.indexOf(" ", firstSpace+1);
                fullURL = request.substring(firstSpace, secondSpace);
                int dslash = request.indexOf("//");
                int slash = request.indexOf("/", dslash+2);
                host = request.substring(dslash+2, slash);
                fileName = fullURL.substring(fullURL.lastIndexOf("/")+1);
                if (firstline.contains(".txt")) {
                    downloadFile = true;
                }
            }
            else{
                // Additional check to detect CONNECT requests, these have to be forwarded to a different port
                if (requestType.equals("CONNECT")) {
                    host = request.substring(request.indexOf(" ")+1, request.indexOf(":"));    
                    connectSkip = true;
                }
                else{
                    int dslash = request.indexOf("//");
                    int slash = request.indexOf("/", dslash+2);
                    host = request.substring(dslash+2, slash);
                }
            }
            // The request is not an recognized automated one, print relevant output
            if (!connectSkip && !skipSteps) {
                System.out.println("Retrieved request from Firefox:\n");
                System.out.println(request);    
            }
            
            
            // Connect to the requested remote server
            Socket remoteSocket;
            if (requestType.equals("CONNECT")) {
                // forward connect requests to the correct port
                remoteSocket = new Socket(host, CONNECT_PORT);    
                connectSkip = true;
            }
            else{
                remoteSocket = new Socket(host, HTTP_PORT);
            }
            InputStream remoteIn = remoteSocket.getInputStream();
            OutputStream remoteOut = remoteSocket.getOutputStream();

            // Forward the HTTP message to the remote server
            remoteOut.write(buffer, 0, readBytesNum);
            remoteOut.flush();

            // Read response from the remote server
            byte[] buffer2 = new byte[4096]; // 4096 bytes is the response length that can be read in a single time           
            int readBytesNum2 = remoteIn.read(buffer2);
            String response;
            // Check for edge cases and change variables accordingly
            if (readBytesNum2 == -1) {
                response = "";
                skipSteps = true;
            }
            else{
                response = new String(buffer2, 0, readBytesNum2);
            }
            
            // Get ready to process server response
            int contentLengthInt = 0;
            String responseToken = "";
            int responseCodeInt = 0;
            // Recognized automated request check
            if (!skipSteps) {
                try {
                    int contentLengthLineIndex = response.indexOf("Content-Length");
                    String contentLengthLine = response.substring(contentLengthLineIndex);
                    String contentLength = contentLengthLine.substring(contentLengthLine.indexOf(" ")+1, contentLengthLine.indexOf("\n"));    
                    contentLengthInt = Integer.parseInt(contentLength.strip());
                } catch (Exception e) {
                    System.err.println("Content Length field doesn't exist");
                }
                int firstSpaceServer = response.indexOf(" ");
                int firstlineEndServer = response.indexOf("\n");
                responseToken = response.substring(firstSpaceServer+1, firstlineEndServer);
                String responseCode = responseToken.substring(0, responseToken.indexOf(" "));
                //String responseWord = responseToken.substring(responseToken.indexOf(" ")+1);
                responseCodeInt = Integer.parseInt(responseCode);
            }

            // Declare output file object and create it if required conditions are met
            FileOutputStream fileOut = null;
            if (downloadFile && responseCodeInt == 200) {
                fileOut = new FileOutputStream(fileName);
            }
            String responseString = new String(buffer2, StandardCharsets.UTF_8); // response String is only to get index to seperate header from the message
            int fileBoundaryIndex = responseString.indexOf("\r\n\r\n") + 4; // start index of file body
            
            if (!skipSteps) {
                // If the response message is longer than 4090 bytes, process it multiple times
                if (contentLengthInt > 4090) {
                    // Forward remote server's response's first section to client
                    clientOut.write(buffer2, 0, readBytesNum2);
                    clientOut.flush();
                    if (downloadFile && responseCodeInt == 200) {
                        // Write the contents of the response body to output file
                        fileOut.write(buffer2, fileBoundaryIndex, readBytesNum2 - fileBoundaryIndex);
                    }
                    // while condition to keep going until response is exhausted
                    while (readBytesNum2 != -1) {
                        readBytesNum2 = remoteIn.read(buffer2);
                        if (readBytesNum2 != -1) {
                            response = new String(buffer2, 0, readBytesNum2);  
                            // Forward remote server's response to client
                            clientOut.write(buffer2, 0, readBytesNum2);
                            clientOut.flush();
                            if (downloadFile && responseCodeInt == 200) {
                                // Write remaining contents of the response body to output file
                                fileOut.write(buffer2, 0, readBytesNum2);
                            }
                        }
                    }
                }
                else{   
                    // Forward remote server's response to client
                    clientOut.write(buffer2, 0, readBytesNum2);
                    clientOut.flush();
                    if (downloadFile && responseCodeInt == 200) {
                        // Write the contents of the response body to output file
                        fileOut.write(buffer2, fileBoundaryIndex, readBytesNum2 - fileBoundaryIndex);
                    }
                }
            }
            else{
                // Process the automated requests that are not CONNECT requests, (connect requests crashed the code)
                if (!connectSkip) {
                    // Forward remote server's response to client
                    clientOut.write(buffer2, 0, readBytesNum2);
                    clientOut.flush();    
                }
            }

            // Output message logic from variables acquired earlier
            if (responseCodeInt != 0 && !skipSteps && !connectSkip) {
                if (responseCodeInt == 200) {
                    System.out.printf("Downloading file '%s'...\n", fileName);
                    System.out.printf("Retrieved: %s\n", responseToken);
                    System.out.println("Saving file...");
                }
                else if (responseCodeInt == 304) {
                    System.out.printf("Retrieved: %s\n", responseToken);
                    System.out.printf("No changes made to file '%s'.\n", fileName);
                }
                else if (responseCodeInt == 404) {
                    System.out.printf("Retrieved: %s\n", responseToken);
                    System.out.println("The requested URL was not found on this server.");
                }
                else{
                    System.out.printf("Retrieved: %s\n", responseToken);
                }
            }
            // Transfer is complete, close the connection to the remote host
            remoteSocket.close();
            // If file was created, close it
            if (fileOut != null) {
                fileOut.close();    
            }
            // Print appropriate automated request outputs and user prompt
            if (connectSkip | skipSteps) {
                System.out.println("Processed an automatic Mozilla request.");
            }
            System.out.println("\nAWAITING NEW ACTION\n");
        }
        // the code will never reach this point at its current form, but this line gets rid of warnings
        serverSocket.close();
    }
}