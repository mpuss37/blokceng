package org.example.node;

import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ApiServer {
    public void RunEndpointApi() {
        int port = 8000;
        Server server = new Server(port);
        try {
            // Create Jetty server on port 8000

            // Set up servlet context handler
            ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
            context.setContextPath("/");
            server.setHandler(context);

            // Add servlet to handle requests
            ServletHolder holder = new ServletHolder(new DataServlet());
            context.addServlet(holder, "/*");

            // Start server
            server.start();
            System.out.println("Server Api started on port " + port);
            server.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public class DataServlet extends HttpServlet {
        private static final String DATA_DIRECTORY = System.getProperty("user.dir"); // Menggunakan direktori root dari user.dir

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            String pathInfo = request.getPathInfo();

            if (pathInfo != null && pathInfo.startsWith("/")) {
                String hash = pathInfo.substring(1); // Remove leading '/'

                if (hash.isEmpty()) {
                    // List all .her files in the directory
                    File directory = new File(DATA_DIRECTORY);
                    File[] files = directory.listFiles((dir, name) -> name.endsWith(".her"));

                    if (files != null) {
                        response.setContentType("application/json");
                        response.setStatus(HttpServletResponse.SC_OK);
                        response.getWriter().write("{\"files\": [");
                        for (int i = 0; i < files.length; i++) {
                            File file = files[i];
                            response.getWriter().write("{\"fileName\": \"" + file.getName() + "\", \"content\": " + getFileContent(file) + "}");
                            if (i < files.length - 1) {
                                response.getWriter().write(", ");
                            }
                        }
                        response.getWriter().write("]}");
                    } else {
                        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                        response.getWriter().write("{\"error\": \"Error reading directory.\"}");
                    }
                } else {
                    // If hash is provided, find and serve the corresponding .her file
                    String filePath = DATA_DIRECTORY + "/" + hash;
                    File file = new File(filePath);

                    if (file.exists() && file.isFile()) {
                        response.setContentType("application/json");
                        response.setStatus(HttpServletResponse.SC_OK);
                        response.getWriter().write("{\"fileName\": \"" + file.getName() + "\", \"content\": " + getFileContent(file) + "}");
                    } else {
                        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                        response.getWriter().write("{\"error\": \"Data not found. File path: " + filePath + "\"}");
                    }
                }
            } else {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("{\"error\": \"Invalid request.\"}");
            }
        }

        private String getFileContent(File file) throws IOException {
            // Read file content as a string and escape special characters for JSON
            String content = new String(Files.readAllBytes(file.toPath()));
            // Escape special characters and wrap content in quotes to be valid JSON
            return "\"" + content.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
    }

}
