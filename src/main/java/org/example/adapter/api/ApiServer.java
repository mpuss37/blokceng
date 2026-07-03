package org.example.adapter.api;

import org.example.application.NodeService;
import org.example.domain.chain.BlockStorage;
import org.example.domain.model.Block;
import org.example.domain.model.Transaction;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public class ApiServer {

    private final BlockStorage storage;
    private final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    public ApiServer(BlockStorage storage) {
        this.storage = storage;
    }

    public void start(int port) throws Exception {
        Server server = new Server(port);
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        server.setHandler(context);

        context.addServlet(new ServletHolder(new ChainServlet()), "/chain");
        context.addServlet(new ServletHolder(new BlocksServlet()), "/blocks");
        context.addServlet(new ServletHolder(new PendingServlet()), "/pending");
        context.addServlet(new ServletHolder(new StatusServlet()), "/status");

        server.start();
        System.out.println("API server started on port " + port);
        server.join();
    }

    class ChainServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            resp.setContentType("application/json");
            var blocks = storage.readAllBlocks();
            var result = new java.util.LinkedHashMap<String, Object>();
            result.put("size", blocks.size());
            result.put("blocks", blocks);
            mapper.writeValue(resp.getWriter(), result);
        }
    }

    class BlocksServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            resp.setContentType("application/json");
            String indexParam = req.getParameter("index");
            if (indexParam != null) {
                int index = Integer.parseInt(indexParam);
                var block = storage.readBlock(index);
                if (block.isPresent()) {
                    mapper.writeValue(resp.getWriter(), block.get());
                } else {
                    resp.setStatus(404);
                    mapper.writeValue(resp.getWriter(), java.util.Map.of("error", "Block not found"));
                }
            } else {
                mapper.writeValue(resp.getWriter(), storage.readAllBlocks());
            }
        }
    }

    class PendingServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            resp.setContentType("application/json");
            mapper.writeValue(resp.getWriter(), storage.getPendingTransactions());
        }
    }

    class StatusServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            resp.setContentType("application/json");
            var status = new java.util.LinkedHashMap<String, Object>();
            status.put("chainSize", storage.blockCount());
            status.put("pendingTx", storage.getPendingTransactions().size());
            status.put("status", "running");
            mapper.writeValue(resp.getWriter(), status);
        }
    }
}
